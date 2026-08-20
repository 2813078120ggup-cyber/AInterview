package com.tyut.aiinterview.governance;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public final class RecruitmentAiGovernanceDtos {
    private RecruitmentAiGovernanceDtos() {
    }

    public record PolicyView(Long id, Long companyId, boolean aiEnabled, boolean emergencyStop,
                             String emergencyReason, boolean evaluationGateRequired, Integer evaluationValidDays,
                             BigDecimal minimumPassRate, BigDecimal maximumScoreDrift,
                             BigDecimal maximumFairnessGap, String humanReviewMode,
                             BigDecimal adverseScoreThreshold, String sensitiveDataMode,
                             BigDecimal dailyCostLimitUsd, BigDecimal monthlyCostLimitUsd,
                             BigDecimal inputCostPerMillionUsd, BigDecimal outputCostPerMillionUsd,
                             Integer perRequestTokenLimit, Integer version, LocalDateTime updatedAt) {
    }

    public record PolicyUpdate(@NotNull Boolean aiEnabled,
                               @NotNull Boolean evaluationGateRequired,
                               @NotNull @Min(1) @Max(365) Integer evaluationValidDays,
                               @NotNull @DecimalMin("0") @DecimalMax("100") BigDecimal minimumPassRate,
                               @NotNull @DecimalMin("0") @DecimalMax("100") BigDecimal maximumScoreDrift,
                               @NotNull @DecimalMin("0") @DecimalMax("100") BigDecimal maximumFairnessGap,
                               @NotBlank @Size(max = 24) String humanReviewMode,
                               @NotNull @DecimalMin("0") @DecimalMax("100") BigDecimal adverseScoreThreshold,
                               @NotBlank @Size(max = 24) String sensitiveDataMode,
                               @NotNull @DecimalMin("0") BigDecimal dailyCostLimitUsd,
                               @NotNull @DecimalMin("0") BigDecimal monthlyCostLimitUsd,
                               @NotNull @DecimalMin("0") BigDecimal inputCostPerMillionUsd,
                               @NotNull @DecimalMin("0") BigDecimal outputCostPerMillionUsd,
                               @NotNull @Min(256) @Max(1000000) Integer perRequestTokenLimit,
                               @NotNull @Min(0) Integer version) {
    }

    public record EmergencyStopRequest(@NotNull Boolean enabled, @Size(max = 500) String reason,
                                       @NotNull Boolean confirm, @NotNull @Min(0) Integer version) {
    }

    public record EvalSuiteView(Long id, String suiteCode, String name, String evaluationType,
                                String promptCode, String description, long caseCount,
                                boolean gateReady, String targetProvider, String targetModel,
                                Integer targetPromptVersion,
                                EvalRunView latestRun) {
    }

    public record EvalCaseView(Long id, Long suiteId, String caseCode, String name, String cohortCode,
                               String pairKey, JsonNode input, BigDecimal expectedScoreMin,
                               BigDecimal expectedScoreMax, BigDecimal baselineScore,
                               List<String> requiredTerms, List<String> forbiddenTerms,
                               boolean enabled, LocalDateTime updatedAt) {
    }

    public record EvalCaseRequest(@NotBlank @Size(max = 64) String caseCode,
                                  @NotBlank @Size(max = 160) String name,
                                  @Size(max = 64) String cohortCode,
                                  @Size(max = 64) String pairKey,
                                  @NotNull JsonNode input,
                                  @DecimalMin("0") @DecimalMax("100") BigDecimal expectedScoreMin,
                                  @DecimalMin("0") @DecimalMax("100") BigDecimal expectedScoreMax,
                                  @DecimalMin("0") @DecimalMax("100") BigDecimal baselineScore,
                                  List<@Size(max = 120) String> requiredTerms,
                                  List<@Size(max = 120) String> forbiddenTerms,
                                  @NotNull Boolean enabled) {
    }

    public record EvalRunView(Long id, Long suiteId, String status, String provider, String model,
                              String promptCode, Integer promptVersion, Integer caseCount,
                              Integer passedCaseCount, BigDecimal passRate,
                              BigDecimal maximumScoreDrift, BigDecimal maximumFairnessGap,
                              String failureSummary, Long startedBy, LocalDateTime startedAt,
                              LocalDateTime finishedAt) {
    }

    public record CostUsageView(BigDecimal todayUsd, BigDecimal monthUsd,
                                BigDecimal dailyLimitUsd, BigDecimal monthlyLimitUsd) {
    }

    public record GovernanceEventView(Long id, Long companyId, String eventType, String generationType,
                                      String decision, String reasonCode, String summary,
                                      LocalDateTime createdAt) {
    }

    public record Overview(LocalDateTime generatedAt, String readiness, PolicyView globalPolicy,
                           List<PolicyView> tenantPolicies, List<EvalSuiteView> suites,
                           CostUsageView globalCost, long pendingMatchReviews,
                           long pendingReportReviews, List<GovernanceEventView> recentEvents) {
    }
}
