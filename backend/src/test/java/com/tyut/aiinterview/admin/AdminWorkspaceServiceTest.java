package com.tyut.aiinterview.admin;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.tyut.aiinterview.mapper.AdminWorkspaceActionRow;
import com.tyut.aiinterview.mapper.AdminWorkspaceMapper;
import com.tyut.aiinterview.mapper.AdminWorkspaceSummaryRow;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;

class AdminWorkspaceServiceTest {
    private final AdminWorkspaceMapper mapper = mock(AdminWorkspaceMapper.class);
    private final AdminWorkspaceService service = new AdminWorkspaceService(mapper);

    @Test
    void mapsDatabaseAggregatesAndOnlyReturnsSanitizedActions() {
        AdminWorkspaceSummaryRow row = new AdminWorkspaceSummaryRow();
        row.setCompanyCount(4L);
        row.setActiveUserCount(120L);
        row.setRecruitingPositionCount(16L);
        row.setWeeklyApplicationCount(38L);
        row.setInProgressInterviewCount(5L);
        row.setReportBacklogCount(3L);
        row.setAiFailedTaskCount(2L);
        row.setPendingTicketCount(7L);
        row.setAlgorithmQueuedCount(2L);
        row.setAlgorithmRunningCount(1L);
        row.setAlgorithmOldestQueuedAt(LocalDateTime.now().minusMinutes(2));
        AdminWorkspaceActionRow action = new AdminWorkspaceActionRow();
        action.setActionType("AI_FAILED");
        action.setItemCount(2L);
        when(mapper.selectSummary()).thenReturn(row);
        when(mapper.selectActions()).thenReturn(List.of(action));

        AdminWorkspaceDtos.Summary result = service.summary();

        assertEquals(4L, result.metrics().companyCount());
        assertEquals(38L, result.metrics().weeklyApplicationCount());
        assertEquals("WORKING", result.worker().code());
        assertEquals(1, result.actions().size());
        assertEquals("AI 失败任务", result.actions().get(0).label());
        assertEquals("/admin/ai-generations", result.actions().get(0).targetPath());
        verify(mapper).selectSummary();
        verify(mapper).selectActions();
    }

    @Test
    void marksLongQueueAsAttentionWithoutExposingInfrastructureDetails() {
        AdminWorkspaceSummaryRow row = new AdminWorkspaceSummaryRow();
        row.setAlgorithmQueuedCount(1L);
        row.setAlgorithmOldestQueuedAt(LocalDateTime.now().minusMinutes(20));
        when(mapper.selectSummary()).thenReturn(row);
        when(mapper.selectActions()).thenReturn(List.of());

        AdminWorkspaceDtos.Summary result = service.summary();

        assertEquals("ATTENTION", result.worker().code());
        assertEquals("需要关注", result.worker().label());
        assertEquals(1L, result.worker().queuedCount());
        org.junit.jupiter.api.Assertions.assertFalse(result.worker().recommendation().contains("redis"));
        org.junit.jupiter.api.Assertions.assertFalse(result.worker().recommendation().contains("password"));
    }
}
