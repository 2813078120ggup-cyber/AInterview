package com.tyut.aiinterview.admin;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tyut.aiinterview.ai.AiTaskService;
import com.tyut.aiinterview.common.BusinessException;
import com.tyut.aiinterview.domain.AiTask;
import com.tyut.aiinterview.domain.JobApplication;
import com.tyut.aiinterview.mapper.AdminRecruitmentApplicationRow;
import com.tyut.aiinterview.mapper.AdminRecruitmentFunnelRow;
import com.tyut.aiinterview.mapper.AdminRecruitmentMapper;
import com.tyut.aiinterview.mapper.AiTaskMapper;
import com.tyut.aiinterview.mapper.JobApplicationMapper;
import com.tyut.aiinterview.mapper.JobApplicationStatusHistoryMapper;
import com.tyut.aiinterview.observability.OperationAuditService;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AdminRecruitmentServiceTest {
    @Mock private AdminRecruitmentMapper mapper;
    @Mock private JobApplicationStatusHistoryMapper historyMapper;
    @Mock private JobApplicationMapper applicationMapper;
    @Mock private AiTaskMapper taskMapper;
    @Mock private AiTaskService taskService;
    @Mock private OperationAuditService auditService;

    private AdminRecruitmentService service;

    @BeforeEach
    void setUp() {
        service = new AdminRecruitmentService(mapper, historyMapper, applicationMapper, taskMapper,
                taskService, auditService, new ObjectMapper());
    }

    @Test
    void pageUsesServerSideCrossCompanyFiltersAndReturnsOnlySafeProjection() {
        AdminRecruitmentApplicationRow row = new AdminRecruitmentApplicationRow();
        row.setId(9L);
        row.setApplicationNo("APP-9");
        row.setCompanyId(2L);
        row.setCompanyName("企业 B");
        row.setPositionId(4L);
        row.setPositionName("后端工程师");
        row.setCandidateId(6L);
        row.setCandidateUsername("candidate");
        row.setCandidateRealName("候选人");
        row.setStatus("UNDER_REVIEW");
        row.setMatchStatus("SUCCESS");
        when(mapper.selectPage(any(), any(), any(), any(), any(), any(), any(), any(), anyInt(), any(), anyLong(), anyLong()))
                .thenReturn(List.of(row));
        when(mapper.count(any(), any(), any(), any(), any(), any(), any(), any(), anyInt(), any())).thenReturn(1L);

        var result = service.page(new AdminRecruitmentDtos.Query(1L, 20L, null, null,
                "UNDER_REVIEW", "企业 B", "后端", "candidate", "2026-08-01", "2026-08-12", false, 7));

        assertEquals(1L, result.total());
        assertEquals("企业 B", result.records().get(0).company().name());
        assertEquals("企业评估中", result.records().get(0).statusLabel());
        verify(mapper).selectPage(isNull(), isNull(), anyString(), anyString(), anyString(), anyString(), any(), any(),
                anyInt(), any(), anyLong(), anyLong());
    }

    @Test
    void summaryBuildsFullFunnelWithZeroStages() {
        AdminRecruitmentFunnelRow row = new AdminRecruitmentFunnelRow();
        row.setStatus("SUBMITTED");
        row.setItemCount(3L);
        when(mapper.selectFunnel(any(), any(), any(), any(), any(), any(), any(), any(), anyInt(), any())).thenReturn(List.of(row));
        when(mapper.count(any(), any(), any(), any(), any(), any(), any(), any(), anyInt(), any())).thenReturn(3L);

        var result = service.summary(new AdminRecruitmentDtos.Query(null, null, null, null,
                null, null, null, null, null, null, null, null));

        assertEquals(7, result.funnel().size());
        assertEquals(3L, result.funnel().stream().filter(item -> item.status().equals("SUBMITTED")).findFirst().orElseThrow().count());
        assertTrue(result.funnel().stream().anyMatch(item -> item.status().equals("HIRED") && item.count() == 0));
    }

    @Test
    void retryRequiresExplicitConfirmation() {
        assertThrows(BusinessException.class, () -> service.retry(1L, false));
        verify(taskMapper, never()).selectById(anyLong());
    }

    @Test
    void retryReusesExistingFailedTaskAndWritesAuditWithoutChangingApplicationStage() {
        AiTask task = new AiTask();
        task.setId(12L);
        task.setTaskType(AiTaskService.JOB_MATCH);
        task.setStatus("FAILED");
        task.setInputPayload("{\"applicationId\":9}");
        JobApplication application = new JobApplication();
        application.setId(9L);
        application.setStatus("UNDER_REVIEW");
        when(taskMapper.selectById(12L)).thenReturn(task);
        when(applicationMapper.selectById(9L)).thenReturn(application);
        when(taskService.retryAdminRecruitmentTask(12L)).thenReturn(task);

        service.retry(12L, true);

        verify(taskService).retryAdminRecruitmentTask(12L);
        verify(auditService).success("ADMIN_RECRUITMENT", "TASK_RETRY", "AI_TASK", 12L, null,
                "重置招聘技术任务并保留原去重键");
        assertEquals("UNDER_REVIEW", application.getStatus());
    }

    @Test
    void nonRecruitmentTaskCannotBeRetried() {
        AiTask task = new AiTask();
        task.setId(18L);
        task.setTaskType("FOLLOW_UP");
        task.setStatus("FAILED");
        when(taskMapper.selectById(18L)).thenReturn(task);

        assertThrows(BusinessException.class, () -> service.retry(18L, true));
        verify(taskService, never()).retryAdminRecruitmentTask(anyLong());
    }

    @Test
    void businessDataFailureCannotBeRetriedAsTechnicalFailure() {
        AiTask task = new AiTask();
        task.setId(19L);
        task.setTaskType(AiTaskService.JOB_MATCH);
        task.setStatus("FAILED");
        task.setErrorMessage("简历尚未完成解析，岗位匹配暂不可执行");
        task.setInputPayload("{\"applicationId\":9}");
        when(taskMapper.selectById(19L)).thenReturn(task);
        when(applicationMapper.selectById(9L)).thenReturn(new JobApplication());
        when(taskService.retryAdminRecruitmentTask(19L))
                .thenThrow(BusinessException.badRequest("业务数据尚未准备完成，不允许技术重试"));

        assertThrows(BusinessException.class, () -> service.retry(19L, true));
        verify(taskService).retryAdminRecruitmentTask(19L);
        verify(auditService).denied("ADMIN_RECRUITMENT", "TASK_RETRY", "AI_TASK", 19L, null,
                "任务重试被拒绝，仅允许技术失败任务");
    }
}
