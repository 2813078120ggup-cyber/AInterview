package com.tyut.aiinterview.recruitment;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.tyut.aiinterview.mapper.CompanyDashboardActionCountRow;
import com.tyut.aiinterview.mapper.CompanyDashboardActionRow;
import com.tyut.aiinterview.mapper.CompanyDashboardMapper;
import com.tyut.aiinterview.mapper.CompanyDashboardSummaryRow;
import com.tyut.aiinterview.mapper.CompanyFunnelRow;
import com.tyut.aiinterview.mapper.CompanyPositionAnalyticsRow;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class CompanyDashboardServiceTest {
    private final CompanyDashboardMapper dashboardMapper = mock(CompanyDashboardMapper.class);
    private final CompanyAccessService companyAccess = mock(CompanyAccessService.class);
    private CompanyDashboardService service;

    @BeforeEach
    void setUp() {
        service = new CompanyDashboardService(dashboardMapper, companyAccess);
        when(companyAccess.requirePermission("analytics:read")).thenReturn(100L);
    }

    @Test
    void summaryUsesCurrentCompanyAndMapsDatabaseAggregates() {
        CompanyDashboardSummaryRow row = new CompanyDashboardSummaryRow();
        row.setCompanyId(100L);
        row.setCompanyName("Company A");
        row.setCompanyShortName("A");
        row.setCity("Taiyuan");
        row.setPublishedPositions(4L);
        row.setDraftPositions(2L);
        row.setTotalApplications(18L);
        row.setPendingApplications(7L);
        row.setTodayInterviews(3L);
        row.setOverdueItems(2L);
        row.setHiredApplications(1L);
        row.setAverageMatchScore(new BigDecimal("82.35"));
        row.setLastUpdatedAt(LocalDateTime.of(2026, 8, 11, 10, 30));
        when(dashboardMapper.selectSummary(100L)).thenReturn(row);

        RecruitmentDtos.DashboardSummary result = service.summary();

        assertEquals(100L, result.companyId());
        assertEquals("Company A", result.companyName());
        assertEquals(4L, result.publishedPositions());
        assertEquals(7L, result.pendingApplications());
        assertEquals(new BigDecimal("82.4"), result.averageMatchScore());
        verify(dashboardMapper).selectSummary(100L);
    }

    @Test
    void actionsKeepsAllActionGroupsAndScopesQueriesToCurrentCompany() {
        CompanyDashboardActionCountRow newApplications = new CompanyDashboardActionCountRow();
        newApplications.setActionType("NEW_APPLICATION");
        newApplications.setItemCount(3L);
        CompanyDashboardActionCountRow timeout = new CompanyDashboardActionCountRow();
        timeout.setActionType("REPORT_TIMEOUT");
        timeout.setItemCount(1L);
        when(dashboardMapper.selectActionCounts(100L)).thenReturn(List.of(newApplications, timeout));

        CompanyDashboardActionRow item = new CompanyDashboardActionRow();
        item.setActionType("NEW_APPLICATION");
        item.setApplicationId(501L);
        item.setCandidateName("Candidate A");
        item.setPositionName("Java Engineer");
        item.setStatus("SUBMITTED");
        when(dashboardMapper.selectActionItems(eq(100L), eq(30))).thenReturn(List.of(item));

        RecruitmentDtos.ActionCenter result = service.actions();

        assertEquals(5, result.groups().size());
        assertEquals("NEW_APPLICATION", result.groups().get(0).actionType());
        assertEquals(3L, result.groups().get(0).count());
        assertEquals(501L, result.groups().get(0).items().get(0).applicationId());
        assertEquals(0L, result.groups().get(1).count());
        assertEquals(4L, result.total());
        verify(dashboardMapper).selectActionCounts(100L);
        verify(dashboardMapper).selectActionItems(100L, 30);
    }

    @Test
    void funnelReturnsStableStagesAndCalculatesPercentagesFromAggregatedCounts() {
        CompanyFunnelRow submitted = new CompanyFunnelRow();
        submitted.setStatus("SUBMITTED");
        submitted.setItemCount(3L);
        CompanyFunnelRow hired = new CompanyFunnelRow();
        hired.setStatus("HIRED");
        hired.setItemCount(1L);
        when(dashboardMapper.selectFunnel(100L)).thenReturn(List.of(submitted, hired));

        List<RecruitmentDtos.FunnelStage> result = service.funnel();

        assertEquals(7, result.size());
        assertEquals("SUBMITTED", result.get(0).status());
        assertEquals(3L, result.get(0).count());
        assertEquals(new BigDecimal("75.0"), result.get(0).percentage());
        assertEquals("HIRED", result.get(6).status());
        assertEquals(1L, result.get(6).count());
        assertEquals(new BigDecimal("25.0"), result.get(6).percentage());
        verify(dashboardMapper).selectFunnel(100L);
    }

    @Test
    void positionsUseBoundedDatabaseRankingForCurrentCompany() {
        CompanyPositionAnalyticsRow row = new CompanyPositionAnalyticsRow();
        row.setPositionId(21L);
        row.setPositionName("Backend Engineer");
        row.setRecruitmentStatus("PUBLISHED");
        row.setApplicationCount(12L);
        row.setPendingCount(5L);
        row.setHiredCount(1L);
        row.setAverageMatchScore(new BigDecimal("76.26"));
        when(dashboardMapper.selectPositionAnalytics(100L, 8)).thenReturn(List.of(row));

        List<RecruitmentDtos.PositionAnalytics> result = service.positions();

        assertEquals(1, result.size());
        assertEquals(21L, result.get(0).positionId());
        assertEquals(12L, result.get(0).applicationCount());
        assertEquals(new BigDecimal("76.3"), result.get(0).averageMatchScore());
        verify(dashboardMapper).selectPositionAnalytics(100L, 8);
    }
}
