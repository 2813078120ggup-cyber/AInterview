package com.tyut.aiinterview.domain;

import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Data;

@Data
@TableName("recruitment_ai_governance_event")
public class RecruitmentAiGovernanceEvent {
    private Long id;
    private Long companyId;
    private Long policyId;
    private String eventType;
    private String generationType;
    private String decision;
    private String reasonCode;
    private String summary;
    private LocalDateTime createdAt;
}
