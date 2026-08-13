package com.tyut.aiinterview.mapper;

import java.math.BigDecimal;
import lombok.Data;

@Data
public class CompanyAnalyticsPositionRow {
    private Long positionId;
    private String positionName;
    private String recruitmentStatus;
    private Long applicationCount;
    private BigDecimal averageMatchScore;
    private Long interviewCount;
    private Long hiredCount;
}
