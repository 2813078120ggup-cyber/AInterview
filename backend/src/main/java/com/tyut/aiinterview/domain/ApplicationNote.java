package com.tyut.aiinterview.domain;

import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Data;

@Data
@TableName("application_note")
public class ApplicationNote {
    private Long id;
    private Long companyId;
    private Long companyCandidateId;
    private Long candidateId;
    private Long applicationId;
    private Long authorId;
    private Long updatedBy;
    private String content;
    private Integer version;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
