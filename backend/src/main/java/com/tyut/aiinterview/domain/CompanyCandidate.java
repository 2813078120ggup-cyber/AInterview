package com.tyut.aiinterview.domain;

import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Data;

@Data
@TableName("company_candidate")
public class CompanyCandidate {
    private Long id;
    private Long companyId;
    private Long candidateId;
    private String status;
    private LocalDateTime lastContactedAt;
    private LocalDateTime removedAt;
    private Long removedBy;
    private Long createdBy;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private Integer version;
}
