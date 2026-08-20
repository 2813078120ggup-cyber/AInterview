package com.tyut.aiinterview.admin;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public final class AdminRecruitmentDtos {
    private AdminRecruitmentDtos() {}

    public record Query(Long pageNo, Long pageSize, Long companyId, Long positionId,
                        String status, String companyKeyword, String positionKeyword,
                        String keyword, String from, String to, Boolean staleOnly, Integer staleDays) {}

    public record Summary(LocalDateTime generatedAt, int staleDays, long staleCount,
                          List<FunnelStage> funnel) {}

    public record FunnelStage(String status, String label, long count, boolean terminal) {}

    public record Ref(Long id, String code, String name, String secondary) {}

    public record CandidateRef(Long id, String username, String name) {}

    public record TaskView(Long id, String kind, String taskType, String status,
                           Integer attempts, Integer maxAttempts, LocalDateTime scheduledAt,
                           LocalDateTime startedAt, LocalDateTime finishedAt,
                           boolean retryable, String failureSummary) {}

    public record InterviewView(Long id, String type, Integer status, LocalDateTime scheduledAt,
                                LocalDateTime startedAt, LocalDateTime endedAt,
                                Integer reportStatus, LocalDateTime reportGeneratedAt,
                                LocalDateTime reportPublishedAt, TaskView reportTask) {}

    public record ApplicationView(Long id, String applicationNo, Ref company, Ref position,
                                  CandidateRef candidate, String status, String statusLabel,
                                  BigDecimal matchScore, String matchStatus, TaskView matchTask,
                                  InterviewView interview, LocalDateTime submittedAt,
                                  LocalDateTime updatedAt, boolean stale, String nextAction) {}

    public record StatusEvent(Long id, String fromStatus, String toStatus,
                              Long operatorId, LocalDateTime createdAt) {}

    public record Detail(ApplicationView application, List<StatusEvent> statusHistory) {}

    public record RequisitionQuery(Long pageNo, Long pageSize, Long companyId, String approvalStatus,
                                   Boolean frozen, String keyword) {}

    public record RequisitionView(Long id, String requisitionNo, Ref company, Ref position,
                                  String headcountCode, Integer requestedHeadcount, Integer approvedHeadcount,
                                  String costCenterCode, String costCenterName,
                                  BigDecimal budgetAmount, String budgetCurrency,
                                  String businessJustification, String approvalStatus,
                                  Long submittedBy, LocalDateTime submittedAt,
                                  Long reviewedBy, LocalDateTime reviewedAt, String reviewNote,
                                  boolean frozen, Long frozenBy, LocalDateTime frozenAt,
                                  String freezeReason, LocalDateTime updatedAt) {}

    public record RequisitionEvent(Long id, String eventType, String fromStatus, String toStatus,
                                   Long operatorId, String operatorName, String note,
                                   LocalDateTime createdAt) {}

    public record RequisitionDetail(RequisitionView requisition, List<RequisitionEvent> history) {}

    public record ApprovalRequest(@NotNull @Min(1) @Max(1000) Integer approvedHeadcount,
                                  @Size(max = 1000) String note) {}

    public record DecisionRequest(@NotBlank @Size(max = 1000) String note) {}
}
