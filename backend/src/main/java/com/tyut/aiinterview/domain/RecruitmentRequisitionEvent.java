package com.tyut.aiinterview.domain;

import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Data;

@Data
@TableName("recruitment_requisition_event")
public class RecruitmentRequisitionEvent {
    private Long id;
    private Long requisitionId;
    private String eventType;
    private String fromStatus;
    private String toStatus;
    private Long operatorId;
    private String note;
    private LocalDateTime createdAt;
}
