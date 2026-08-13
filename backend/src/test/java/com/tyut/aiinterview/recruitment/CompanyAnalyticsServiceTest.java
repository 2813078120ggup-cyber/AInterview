package com.tyut.aiinterview.recruitment;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.tyut.aiinterview.mapper.CompanyAnalyticsFunnelRow;
import com.tyut.aiinterview.mapper.CompanyAnalyticsMapper;
import com.tyut.aiinterview.mapper.CompanyAnalyticsPositionRow;
import com.tyut.aiinterview.mapper.CompanyAnalyticsScoreBucketRow;
import com.tyut.aiinterview.mapper.CompanyAnalyticsSummaryRow;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class CompanyAnalyticsServiceTest {
    private final CompanyAnalyticsMapper analyticsMapper = mock(CompanyAnalyticsMapper.class);
    private final CompanyAccessService companyAccess = mock(CompanyAccessService.class);
    private final CompanyAnalyticsService service = new CompanyAnalyticsService(analyticsMapper, companyAccess);

    @BeforeEach
    void setUp() {
        when(companyAccess.requirePermission("analytics:read")).thenReturn(100L);
    }

    @Test
    void overviewUsesTheExplicitDateRangeAndDatabaseAggregates() {
        LocalDateTime from = LocalDate.of(2026, 8, 1).atStartOfDay();
        LocalDateTime to = LocalDate.of(2026, 9, 1).atStartOfDay();
        CompanyAnalyticsSummaryRow summary = new CompanyAnalyticsSummaryRow();
        summary.setApplicationCount(12L);
        summary.setInterviewApplicationCount(6L);
        summary.setHiredCount(2L);
        summary.setAverageInitialScreeningHours(new BigDecimal("4.24"));
        summary.setAverageTimeToInterviewHours(new BigDecimal("31.26"));
        summary.setAverageHiringCycleDays(new BigDecimal("7.82"));
        when(analyticsMapper.selectSummary(100L, from, to)).thenReturn(summary);
        when(analyticsMapper.selectFunnel(100L, from, to)).thenReturn(List.of(funnel("SUBMITTED", 12), funnel("HIRED", 2)));
        when(analyticsMapper.selectScoreBuckets(100L, from, to)).thenReturn(List.of(bucket("80_89", 8)));

        CompanyAnalyticsDtos.Overview view = service.overview(LocalDate.of(2026, 8, 1), LocalDate.of(2026, 8, 31));

        assertEquals(LocalDate.of(2026, 8, 1), view.from());
        assertEquals(12, view.sampleSize());
        assertEquals(new BigDecimal("50.0"), view.interviewConversionRate());
        assertEquals(new BigDecimal("16.7"), view.hireRate());
        assertEquals(new BigDecimal("100.0"), view.matchScoreDistribution().get(3).percentage());
        verify(analyticsMapper).selectSummary(100L, from, LocalDate.of(2026, 9, 1).atStartOfDay());
    }

    @Test
    void positionsArePagedAndScopedToTheAuthenticatedCompany() {
        CompanyAnalyticsPositionRow row = new CompanyAnalyticsPositionRow();
        row.setPositionId(21L);
        row.setPositionName("Java 工程师");
        row.setRecruitmentStatus("PUBLISHED");
        row.setApplicationCount(10L);
        row.setAverageMatchScore(new BigDecimal("81.25"));
        row.setInterviewCount(4L);
        row.setHiredCount(1L);
        when(analyticsMapper.selectPositionPage(100L, LocalDate.of(2026, 8, 1).atStartOfDay(),
                LocalDate.of(2026, 9, 1).atStartOfDay(), 20L, 20L)).thenReturn(List.of(row));
        when(analyticsMapper.countPositions(100L)).thenReturn(3L);

        var page = service.positions(LocalDate.of(2026, 8, 1), LocalDate.of(2026, 8, 31), 2, 20);

        assertEquals(3L, page.total());
        assertEquals(new BigDecimal("40.0"), page.records().get(0).interviewConversionRate());
        verify(analyticsMapper).countPositions(100L);
    }

    private CompanyAnalyticsFunnelRow funnel(String status, long count) {
        CompanyAnalyticsFunnelRow row = new CompanyAnalyticsFunnelRow();
        row.setStatus(status);
        row.setItemCount(count);
        return row;
    }

    private CompanyAnalyticsScoreBucketRow bucket(String key, long count) {
        CompanyAnalyticsScoreBucketRow row = new CompanyAnalyticsScoreBucketRow();
        row.setBucketKey(key);
        row.setBucketLabel(key);
        row.setItemCount(count);
        return row;
    }
}
