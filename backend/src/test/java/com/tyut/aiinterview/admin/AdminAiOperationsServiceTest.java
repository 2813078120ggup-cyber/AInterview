package com.tyut.aiinterview.admin;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tyut.aiinterview.ai.AiTaskService;
import com.tyut.aiinterview.common.BusinessException;
import com.tyut.aiinterview.domain.AiGenerationRecord;
import com.tyut.aiinterview.domain.AiPromptVersion;
import com.tyut.aiinterview.domain.AiProviderConfig;
import com.tyut.aiinterview.domain.AiTask;
import com.tyut.aiinterview.mapper.AdminAiOpsCallRow;
import com.tyut.aiinterview.mapper.AdminAiOpsGenerationSummaryRow;
import com.tyut.aiinterview.mapper.AdminAiOpsTaskRow;
import com.tyut.aiinterview.mapper.AdminAiOpsTaskSummaryRow;
import com.tyut.aiinterview.mapper.AdminAiOperationsMapper;
import com.tyut.aiinterview.mapper.AiGenerationRecordMapper;
import com.tyut.aiinterview.mapper.AiPromptVersionMapper;
import com.tyut.aiinterview.mapper.AiProviderConfigMapper;
import com.tyut.aiinterview.mapper.AiTaskMapper;
import com.tyut.aiinterview.observability.OperationAuditService;
import com.tyut.aiinterview.security.CurrentUser;
import java.util.List;
import org.junit.jupiter.api.Test;

class AdminAiOperationsServiceTest {
    private final AdminAiOperationsMapper mapper = mock(AdminAiOperationsMapper.class);
    private final AiTaskMapper taskMapper = mock(AiTaskMapper.class);
    private final AiGenerationRecordMapper generationMapper = mock(AiGenerationRecordMapper.class);
    private final AiProviderConfigMapper providerMapper = mock(AiProviderConfigMapper.class);
    private final AiPromptVersionMapper promptMapper = mock(AiPromptVersionMapper.class);
    private final AiTaskService taskService = mock(AiTaskService.class);
    private final OperationAuditService auditService = mock(OperationAuditService.class);
    private final CurrentUser currentUser = mock(CurrentUser.class);
    private final AdminAiOperationsService service = new AdminAiOperationsService(
            mapper, taskMapper, generationMapper, providerMapper, promptMapper,
            taskService, auditService, currentUser, new ObjectMapper());

    @Test
    void overviewUsesDatabaseAggregatesAndSafeProjections() {
        when(currentUser.hasRole("ADMIN")).thenReturn(true);
        AdminAiOpsGenerationSummaryRow generation = new AdminAiOpsGenerationSummaryRow();
        generation.setTotal(12L);
        generation.setSuccess(9L);
        generation.setFailed(2L);
        generation.setRunning(1L);
        generation.setAverageLatencyMs(240L);
        generation.setTotalTokens(800L);
        AdminAiOpsTaskSummaryRow tasks = new AdminAiOpsTaskSummaryRow();
        tasks.setBacklog(3L);
        tasks.setReportBacklog(1L);
        when(mapper.selectGenerationSummary()).thenReturn(generation);
        when(mapper.selectTaskSummary()).thenReturn(tasks);

        AiProviderConfig openTalking = provider(1L, "open-talking-virtual-human", "virtual-human");
        AiProviderConfig unsupportedAvatar = provider(2L, "avatar-skill", "virtual-human");
        AiProviderConfig llm = provider(3L, "deepseek", "llm");
        when(providerMapper.selectList(any())).thenReturn(List.of(openTalking, unsupportedAvatar, llm));

        AiPromptVersion prompt = new AiPromptVersion();
        prompt.setPromptCode("interview.report");
        prompt.setPromptName("Interview report");
        prompt.setVersionNo(4);
        prompt.setActive(1);
        when(promptMapper.selectList(any())).thenReturn(List.of(prompt));

        AdminAiOpsCallRow call = new AdminAiOpsCallRow();
        call.setId(10L);
        call.setStatus("FAILED");
        call.setErrorMessage("full resume and provider response must never be returned");
        when(mapper.selectRecentCalls(8)).thenReturn(List.of(call));
        when(mapper.selectRecentTasks(8)).thenReturn(List.of());

        AdminAiOperationsDtos.Overview result = service.overview();

        assertEquals(12L, result.ai().total());
        assertEquals(3L, result.tasks().backlog());
        assertEquals(2, result.providers().size());
        assertTrue(result.providers().stream().noneMatch(item -> "avatar-skill".equals(item.code())));
        assertEquals(4, result.prompts().get(0).version());
        assertEquals("技术任务失败，详情已脱敏", result.recentCalls().get(0).errorSummary());
        verify(mapper).selectGenerationSummary();
        verify(mapper).selectTaskSummary();
    }

    @Test
    void traceLinksBusinessAndDoesNotSerializeRawTaskPayload() throws Exception {
        when(currentUser.hasRole("ADMIN")).thenReturn(true);
        AiGenerationRecord generation = new AiGenerationRecord();
        generation.setId(21L);
        generation.setTaskId(22L);
        generation.setRequestId("request-safe");
        generation.setGenerationType("JOB_MATCH");
        generation.setProvider("deepseek");
        generation.setModel("safe-model");
        generation.setPromptCode("job.match");
        generation.setPromptVersionNo(2);
        generation.setStatus("SUCCESS");
        AiTask task = new AiTask();
        task.setId(22L);
        task.setTaskType(AiTaskService.JOB_MATCH);
        task.setStatus("SUCCESS");
        task.setInputPayload("{\"applicationId\":42,\"answer\":\"private answer\"}");
        AiProviderConfig provider = provider(3L, "deepseek", "llm");
        AiPromptVersion prompt = new AiPromptVersion();
        prompt.setPromptCode("job.match");
        prompt.setPromptName("Job match");
        prompt.setVersionNo(2);
        prompt.setSystemTemplate("private prompt template");
        prompt.setActive(1);
        when(generationMapper.selectById(21L)).thenReturn(generation);
        when(taskMapper.selectById(22L)).thenReturn(task);
        when(generationMapper.selectOne(any())).thenReturn(generation);
        when(providerMapper.selectOne(any())).thenReturn(provider);
        when(promptMapper.selectOne(any())).thenReturn(prompt);

        AdminAiOperationsDtos.Trace trace = service.trace(21L);
        String serialized = new ObjectMapper().writeValueAsString(trace);

        assertEquals("APPLICATION", trace.business().type());
        assertEquals("/admin/recruitment/applications/42", trace.business().path());
        assertFalse(serialized.contains("private answer"));
        assertFalse(serialized.contains("private prompt template"));
        assertFalse(serialized.contains("inputPayload"));
    }

    @Test
    void retryRequiresExplicitConfirmationAndAuditsAcceptedRetry() {
        when(currentUser.hasRole("ADMIN")).thenReturn(true);
        assertThrows(BusinessException.class, () -> service.retry(7L, false));
        verify(auditService).denied("AI_OPERATIONS", "TASK_RETRY", "AI_TASK", 7L, null,
                "重试异步 AI 任务前需要明确确认");

        AiTask failed = new AiTask();
        failed.setId(7L);
        failed.setTaskType(AiTaskService.JOB_MATCH);
        failed.setStatus("FAILED");
        failed.setErrorMessage("provider unavailable");
        when(taskMapper.selectById(7L)).thenReturn(failed);
        when(taskService.retryAdminAiTask(7L)).thenReturn(failed);

        service.retry(7L, true);

        verify(taskService).retryAdminAiTask(7L);
        verify(auditService).success("AI_OPERATIONS", "TASK_RETRY", "AI_TASK", 7L, null,
                "重置异步 AI 任务并保留原去重键");
    }

    @Test
    void nonAdminCannotReadOperations() {
        when(currentUser.hasRole("ADMIN")).thenReturn(false);
        assertThrows(BusinessException.class, service::overview);
    }

    private AiProviderConfig provider(Long id, String code, String kind) {
        AiProviderConfig provider = new AiProviderConfig();
        provider.setId(id);
        provider.setName(code);
        provider.setCode(code);
        provider.setKind(kind);
        provider.setEnabled(1);
        provider.setTextDefault("llm".equals(kind) ? 1 : 0);
        provider.setVoiceDefault("virtual-human".equals(kind) ? 1 : 0);
        provider.setBaseUrl("https://provider.example.test");
        provider.setChatModel("model");
        provider.setAvatarModel("avatar");
        provider.setApiKeyCipher("cipher");
        return provider;
    }
}
