package com.tyut.aiinterview.mapper;

import java.time.LocalDateTime;
import lombok.Data;

@Data
public class AdminProblemStatRow {
    private Long id;
    private String title;
    private String slug;
    private String difficulty;
    private Integer status;
    private Integer sortNo;
    private LocalDateTime createdAt;
    private Integer submissionCount;
    private Integer acceptedCount;
}
