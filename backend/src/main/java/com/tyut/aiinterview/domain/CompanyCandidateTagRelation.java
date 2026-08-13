package com.tyut.aiinterview.domain;

import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Data;

@Data
@TableName("company_candidate_tag_relation")
public class CompanyCandidateTagRelation {
    private Long id;
    private Long companyId;
    private Long companyCandidateId;
    private Long tagId;
    private Integer status;
    private Long createdBy;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private Integer version;
}
