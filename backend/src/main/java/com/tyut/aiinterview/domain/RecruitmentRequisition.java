package com.tyut.aiinterview.domain;

import com.baomidou.mybatisplus.annotation.TableName;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.Data;

@Data
@TableName("recruitment_requisition")
public class RecruitmentRequisition {
    private Long id;
    private String requisitionNo;
    private Long companyId;
    private Long positionId;
    private String headcountCode;
    private Integer requestedHeadcount;
    private Integer approvedHeadcount;
    private String costCenterCode;
    private String costCenterName;
    private BigDecimal budgetAmount;
    private String budgetCurrency;
    private String businessJustification;
    private String approvalStatus;
    private Long submittedBy;
    private LocalDateTime submittedAt;
    private Long reviewedBy;
    private LocalDateTime reviewedAt;
    private String reviewNote;
    private Integer frozen;
    private Long frozenBy;
    private LocalDateTime frozenAt;
    private String freezeReason;
    private Integer version;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
