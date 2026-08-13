package com.tyut.aiinterview.mapper;

import java.math.BigDecimal;
import lombok.Data;

@Data
public class CompanyPositionAnalyticsRow {
    private Long positionId;
    private String positionName;
    private String recruitmentStatus;
    private Long applicationCount;
    private Long pendingCount;
    private Long hiredCount;
    private BigDecimal averageMatchScore;
}
