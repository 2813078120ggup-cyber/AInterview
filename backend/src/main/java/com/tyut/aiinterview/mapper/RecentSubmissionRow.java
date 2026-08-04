package com.tyut.aiinterview.mapper;

import java.time.LocalDateTime;
import lombok.Data;

@Data
public class RecentSubmissionRow {
    private Long id;
    private Long problemId;
    private String problemTitle;
    private String language;
    private String status;
    private String submitType;
    private Integer passedCount;
    private Integer totalCount;
    private Long executionTimeMs;
    private LocalDateTime createdAt;
}
