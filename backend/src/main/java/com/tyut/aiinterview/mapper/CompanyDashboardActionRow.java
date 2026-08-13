package com.tyut.aiinterview.mapper;

import java.time.LocalDateTime;
import lombok.Data;

@Data
public class CompanyDashboardActionRow {
    private String actionType;
    private Long applicationId;
    private Long interviewId;
    private String candidateName;
    private String positionName;
    private String status;
    private String matchStatus;
    private LocalDateTime dueAt;
    private LocalDateTime createdAt;
    private Integer priority;
}
