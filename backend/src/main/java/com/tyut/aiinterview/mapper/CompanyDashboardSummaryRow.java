package com.tyut.aiinterview.mapper;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.Data;

@Data
public class CompanyDashboardSummaryRow {
    private Long companyId;
    private String companyName;
    private String companyShortName;
    private String city;
    private Long publishedPositions;
    private Long draftPositions;
    private Long totalApplications;
    private Long pendingApplications;
    private Long todayInterviews;
    private Long overdueItems;
    private Long hiredApplications;
    private BigDecimal averageMatchScore;
    private LocalDateTime lastUpdatedAt;
}
