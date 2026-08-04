package com.tyut.aiinterview.prompt;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.tyut.aiinterview.domain.AiPromptActivationLog;
import com.tyut.aiinterview.domain.AiPromptVersion;
import com.tyut.aiinterview.mapper.AiPromptActivationLogMapper;
import com.tyut.aiinterview.mapper.AiPromptVersionMapper;
import com.tyut.aiinterview.security.CurrentUser;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class PromptTemplateServiceTest {
    private final AiPromptVersionMapper versionMapper = mock(AiPromptVersionMapper.class);
    private final AiPromptActivationLogMapper logMapper = mock(AiPromptActivationLogMapper.class);
    private final CurrentUser currentUser = mock(CurrentUser.class);
    private PromptTemplateService service;

    @BeforeEach
    void setUp() {
        service = new PromptTemplateService(versionMapper, logMapper, currentUser);
    }

    @Test
    void renderUsesTheSingleActiveVersionAndEscapesReplacementCharacters() {
        AiPromptVersion active = version(PromptCatalog.SIMULATION_OPENING, 3, true);
        active.setSystemTemplate("风格：${interviewerStyle}");
        active.setUserTemplate("题目：${question}");
        when(versionMapper.selectList(any())).thenReturn(List.of(active));

        PromptTemplateService.RenderedPrompt rendered = service.render(PromptCatalog.SIMULATION_OPENING,
                Map.of("interviewerStyle", "严谨$1", "question", "解释 Java \\ 内存模型"));

        assertEquals(3, rendered.version());
        assertEquals("风格：严谨$1", rendered.systemPrompt());
        assertEquals("题目：解释 Java \\ 内存模型", rendered.userPrompt());
    }

    @Test
    void creatingAndActivatingVersionKeepsOldContentAndWritesActivationLog() {
        AiPromptVersion current = version(PromptCatalog.SIMULATION_OPENING, 1, true);
        AtomicReference<AiPromptVersion> created = new AtomicReference<>();
        when(currentUser.hasRole("ADMIN")).thenReturn(true);
        when(currentUser.id()).thenReturn(9L);
        when(versionMapper.selectList(any())).thenReturn(List.of(current));
        when(versionMapper.selectOne(any())).thenReturn(current);
        when(versionMapper.insert(any(AiPromptVersion.class))).thenAnswer(invocation -> {
            AiPromptVersion value = invocation.getArgument(0);
            value.setId(22L);
            created.set(value);
            return 1;
        });
        when(versionMapper.selectById(22L)).thenAnswer(ignored -> created.get());

        PromptDtos.VersionView result = service.createVersion(PromptCatalog.SIMULATION_OPENING,
                new PromptDtos.CreateVersionRequest("系统 ${interviewerStyle}", "题目 ${question}", "缩短开场", true));

        assertEquals(2, result.version());
        assertTrue(result.active());
        assertEquals("系统 ${interviewerStyle}", created.get().getSystemTemplate());
        assertEquals("题目 ${question}", created.get().getUserTemplate());
        verify(logMapper).insert(any(AiPromptActivationLog.class));
    }

    private AiPromptVersion version(String code, int version, boolean active) {
        AiPromptVersion value = new AiPromptVersion();
        value.setId((long) version);
        value.setPromptCode(code);
        value.setPromptName("测试提示词");
        value.setCategory("SIMULATION_INTERVIEW");
        value.setVersionNo(version);
        value.setSystemTemplate("system");
        value.setUserTemplate("user");
        value.setActive(active ? 1 : 0);
        value.setCreatedAt(LocalDateTime.now());
        return value;
    }
}
