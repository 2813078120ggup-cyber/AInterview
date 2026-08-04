package com.tyut.aiinterview.mapper;

import java.time.LocalDateTime;
import lombok.Data;

@Data
public class ProblemStatRow {
    private Long id;
    private String title;
    private String slug;
    private String difficulty;
    private String progressStatus;
    private Integer mySubmitCount;
    private Integer submissionCount;
    private Integer acceptedCount;
    private Integer status;
    private Integer sortNo;
    private LocalDateTime createdAt;
}
