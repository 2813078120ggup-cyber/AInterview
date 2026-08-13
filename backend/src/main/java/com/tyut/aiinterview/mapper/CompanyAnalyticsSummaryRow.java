package com.tyut.aiinterview.mapper;

import java.math.BigDecimal;
import lombok.Data;

@Data
public class CompanyAnalyticsSummaryRow {
    private Long applicationCount;
    private Long interviewApplicationCount;
    private Long hiredCount;
    private BigDecimal averageInitialScreeningHours;
    private BigDecimal averageTimeToInterviewHours;
    private BigDecimal averageHiringCycleDays;
}
