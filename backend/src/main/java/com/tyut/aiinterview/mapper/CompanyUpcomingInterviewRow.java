package com.tyut.aiinterview.mapper;

import java.time.LocalDateTime;
import lombok.Data;

@Data
public class CompanyUpcomingInterviewRow {
    private String source;
    private Long interviewId;
    private Long applicationId;
    private String candidateName;
    private String positionName;
    private LocalDateTime scheduledAt;
    private Integer durationMinutes;
    private String status;
    private String location;
}
