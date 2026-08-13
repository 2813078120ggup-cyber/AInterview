package com.tyut.aiinterview.recruitment;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

public final class CompanyAnalyticsDtos {
    private CompanyAnalyticsDtos() {}

    public record Overview(LocalDate from, LocalDate to, int sampleSize, boolean lowSample,
                           List<FunnelStage> funnel, BigDecimal averageInitialScreeningHours,
                           BigDecimal averageTimeToInterviewHours, BigDecimal averageHiringCycleDays,
                           long applicationCount, BigDecimal interviewConversionRate,
                           BigDecimal hireRate, List<ScoreBucket> matchScoreDistribution,
                           LocalDateTime generatedAt) {}

    public record FunnelStage(String status, String label, long count, BigDecimal conversionRate,
                              BigDecimal shareOfApplications) {}

    public record ScoreBucket(String key, String label, long count, BigDecimal percentage) {}

    public record PositionAnalytics(Long positionId, String positionName, String recruitmentStatus,
                                    long applicationCount, BigDecimal averageMatchScore,
                                    long interviewCount, long hiredCount, BigDecimal interviewConversionRate,
                                    BigDecimal hireRate) {}
}
