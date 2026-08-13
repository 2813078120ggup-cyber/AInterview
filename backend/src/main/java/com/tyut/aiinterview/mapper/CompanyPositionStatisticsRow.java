package com.tyut.aiinterview.mapper;

import java.math.BigDecimal;
import lombok.Data;

@Data
public class CompanyPositionStatisticsRow {
    private Long applicationCount;
    private BigDecimal averageMatchScore;
    private Long interviewCount;
    private Long hiredCount;
}
