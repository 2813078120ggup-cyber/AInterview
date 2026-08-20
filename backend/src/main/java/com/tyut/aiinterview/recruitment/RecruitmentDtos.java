package com.tyut.aiinterview.recruitment;

import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import jakarta.validation.Valid;
import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.validation.constraints.AssertTrue;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public final class RecruitmentDtos {
    private RecruitmentDtos() {}

    public record JobQuery(Long pageNo, Long pageSize, String keyword, String city, String experience,
                           String education, String jobType, Integer minSalary, String status, String department) {}

    public record CompanyView(Long id, String name, String shortName, String logoUrl, String industry,
                              String companySize, String city, String description) {}

    public record JobView(Long id, String positionCode, CompanyView company, String name, String department,
                          Integer salaryMin, Integer salaryMax, String city, String experienceRequirement,
                          String educationRequirement, String jobType, String description, String requirements,
                          List<String> skillTags, String recruitmentStatus, LocalDateTime publishedAt,
                          LocalDateTime expiresAt, String approvalStatus, boolean frozen,
                          boolean applied, LocalDateTime updatedAt) {}

    public record PositionStatistics(long applicationCount, BigDecimal averageMatchScore,
                                     long interviewCount, long hiredCount) {}

    public record RequisitionView(Long id, String requisitionNo, String headcountCode,
                                  Integer requestedHeadcount, Integer approvedHeadcount,
                                  String costCenterCode, String costCenterName,
                                  BigDecimal budgetAmount, String budgetCurrency,
                                  String businessJustification, String approvalStatus,
                                  Long submittedBy, LocalDateTime submittedAt,
                                  Long reviewedBy, LocalDateTime reviewedAt, String reviewNote,
                                  boolean frozen, Long frozenBy, LocalDateTime frozenAt,
                                  String freezeReason, LocalDateTime updatedAt) {}

    public record RequisitionEventView(Long id, String eventType, String fromStatus, String toStatus,
                                       Long operatorId, String operatorName, String note,
                                       LocalDateTime createdAt) {}

    public record PositionDetail(JobView job, PositionStatistics statistics,
                                 RequisitionView requisition, List<RequisitionEventView> approvalHistory) {}

    public record ApplyRequest(Long resumeId, @Size(max = 1000) String candidateMessage) {}

    public record ResumeView(Long id, String title, String fileName, String summary, List<String> skills,
                             boolean defaultResume, String parseStatus, Integer parseVersion, String parseError,
                             Long mediaId, LocalDateTime parsedAt, LocalDateTime updatedAt) {}

    public record ResumeAnalysisView(Long resumeId, Integer analysisVersion, String status, String summary,
                                     List<String> targetRoles, List<String> skills, List<String> experienceHighlights,
                                     List<ResumeProjectView> projects, List<String> interviewFocus, List<String> riskPoints,
                                     LocalDateTime createdAt, LocalDateTime finishedAt) {}

    /** Company-facing allowlist. Never add extracted text, prompts, provider payloads or storage keys. */
    public record CompanyResumeAnalysisView(List<String> skills, List<String> workExperience,
                                            List<ResumeProjectView> projects, List<String> education,
                                            List<String> strengths, List<String> risks,
                                            List<String> followUpDirections, Integer analysisVersion,
                                            String status) {}

    public record ResumeProjectView(String name, String role, String evidence) {}

    public record ResumeParseTaskView(Long taskId, String status, Integer attempts, Integer maxAttempts,
                                      String errorMessage, LocalDateTime createdAt, LocalDateTime finishedAt) {}

    public record ResumeParseRetryView(String status) {}

    public record MatchEvaluationView(Long id, Long applicationId, Integer evaluationVersion, Integer resumeVersion,
                                      String status, BigDecimal ruleScore, BigDecimal aiScore, BigDecimal finalScore,
                                      String summary, List<String> ruleMatchedSkills, List<String> matchedSkills,
                                      List<String> strengths, List<String> gaps, List<String> risks, List<String> evidence,
                                      String confidence, String providerName, String modelName, Integer promptVersion,
                                      String recommendation, boolean humanReviewRequired, String humanReviewStatus,
                                      String humanReviewDecision, String humanReviewNote, Long humanReviewedBy,
                                      LocalDateTime humanReviewedAt, LocalDateTime createdAt, LocalDateTime finishedAt) {}

    public record MatchReviewRequest(@NotBlank @Size(max = 20) String decision,
                                     @Size(max = 1000) String note) {}

    public record InterviewQuestionBankView(Long id, String name, String description) {}

    public record ApplicationQuery(Long pageNo, Long pageSize, String keyword, String status, Long positionId,
                                   BigDecimal minMatchScore, BigDecimal maxMatchScore,
                                   LocalDateTime submittedFrom, LocalDateTime submittedTo,
                                   String interviewStatus, String sort) {}

    public record OfflineInterviewView(Long id, LocalDateTime scheduledAt, Integer durationMinutes,
                                       String interviewType, String location, String meetingUrl,
                                       String contactName, String contactPhone, String note, String status) {}

    public record HistoryView(String fromStatus, String toStatus, String operatorName, String note,
                              LocalDateTime createdAt) {}

    public record StatusTransition(String status, String label, boolean requiresNote) {}

    public record ApplicationView(Long id, String applicationNo, Long companyId, String companyName,
                                  Long positionId, String positionName, Long candidateId, String candidateName,
                                  String candidateEmail, String candidatePhone, ResumeView resume, String status,
                                  BigDecimal matchScore, String matchStatus, Integer matchVersion, String matchError,
                                  LocalDateTime matchCompletedAt, String matchSummary, String matchDetails,
                                  String candidateMessage, String reviewNote, Long interviewId,
                                  InterviewSummary interview,
                                  OfflineInterviewView offlineInterview, List<HistoryView> history,
                                  LocalDateTime submittedAt, LocalDateTime updatedAt,
                                  List<StatusTransition> allowedTransitions,
                                  String interviewStatus, LocalDateTime recentActivityAt, String nextStep) {}

    public record InterviewSummary(Long id, String title, LocalDateTime scheduledAt, Integer duration,
                                   Integer status, String type) {}

    public record ApplicationInterviewView(Long applicationId, Long interviewId,
                                           InterviewSummary interview,
                                           OfflineInterviewView offlineInterview,
                                           String interviewStatus) {}

    public record ApplicationTimelineEventView(String id, String type, String title,
                                               String description, String actorName,
                                               LocalDateTime occurredAt, String tone) {}

    public record PositionRequest(@NotBlank @Size(max = 64) String positionCode,
                                  @NotBlank @Size(max = 128) String name,
                                  @Size(max = 128) String department,
                                  @Min(0) Integer salaryMin, @Min(0) Integer salaryMax,
                                  @Size(max = 96) String city,
                                  @Size(max = 64) String experienceRequirement,
                                  @Size(max = 64) String educationRequirement,
                                  @NotBlank @Size(max = 32) String jobType,
                                  @Size(max = 10000) String description,
                                  @Size(max = 10000) String requirements,
                                   @Size(max = 20) List<@Size(max = 48) String> skillTags,
                                   @Size(max = 20) String recruitmentStatus,
                                   LocalDateTime expiresAt,
                                   @NotNull @Valid RequisitionRequest requisition) {}

    public record RequisitionRequest(@NotBlank @Size(max = 64) String headcountCode,
                                     @NotNull @Min(1) @Max(1000) Integer requestedHeadcount,
                                     @NotBlank @Size(max = 64) String costCenterCode,
                                     @Size(max = 128) String costCenterName,
                                     @NotNull @DecimalMin("0.00") @Digits(integer = 12, fraction = 2) BigDecimal budgetAmount,
                                     @NotBlank @Pattern(regexp = "[A-Za-z]{3}") String budgetCurrency,
                                     @NotBlank @Size(max = 2000) String businessJustification) {}

    public record PositionStatusRequest(@NotBlank @Size(max = 20) String status,
                                        @Size(max = 1000) String note) {}

    public record StatusUpdateRequest(@NotBlank @Size(max = 32) String status,
                                      @Size(max = 2000) String note, Long interviewId) {
        @AssertTrue(message = "转为企业评估、未通过或已录用时必须填写审核备注或变更原因")
        @JsonIgnore
        public boolean isReasonValid() {
            if (status == null) return true;
            String normalized = status.trim().toUpperCase(Locale.ROOT);
            return !Set.of("UNDER_REVIEW", "REJECTED", "HIRED").contains(normalized)
                    || (note != null && !note.trim().isEmpty());
        }
    }

    public record OfflineInterviewRequest(@NotNull @Future LocalDateTime scheduledAt,
                                          @NotNull @Min(15) @Max(480) Integer durationMinutes,
                                          @NotBlank @Size(max = 20) String interviewType,
                                          @Size(max = 512) String location,
                                          @Size(max = 512) String meetingUrl,
                                          @Size(max = 80) String contactName,
                                          @Size(max = 32) String contactPhone,
                                          @Size(max = 1000) String note) {}

    public record AiInterviewRequest(@NotNull @Future LocalDateTime scheduledAt,
                                     @NotNull @Min(10) @Max(180) Integer durationMinutes,
                                     @NotBlank @Size(max = 32) String type,
                                     @NotNull Long questionBankId,
                                     @NotNull @Min(1) @Max(20) Integer questionCount,
                                     @Size(max = 32) String interviewerStyle,
                                     @Size(max = 500) String remark) {}

    public record Dashboard(long publishedPositions, long draftPositions, long totalApplications,
                            long pendingApplications, long offlineInterviews, long hiredApplications,
                            BigDecimal averageMatchScore) {}

    public record DashboardSummary(Long companyId, String companyName, String companyShortName, String city,
                                   long publishedPositions, long draftPositions, long totalApplications,
                                   long pendingApplications, long todayInterviews, long overdueItems,
                                   long hiredApplications, BigDecimal averageMatchScore,
                                   LocalDateTime lastUpdatedAt) {}

    public record DashboardActionItem(String actionType, Long applicationId, Long interviewId,
                                     String candidateName, String positionName, String status,
                                     String matchStatus, LocalDateTime dueAt, LocalDateTime createdAt) {}

    public record DashboardActionGroup(String actionType, String label, String description,
                                       long count, List<DashboardActionItem> items) {}

    public record ActionCenter(List<DashboardActionGroup> groups, long total, LocalDateTime generatedAt) {}

    public record UpcomingInterview(String source, Long interviewId, Long applicationId,
                                    String candidateName, String positionName, LocalDateTime scheduledAt,
                                    Integer durationMinutes, String status, String location) {}

    public record FunnelStage(String status, String label, long count, BigDecimal percentage) {}

    public record PositionAnalytics(Long positionId, String positionName, String recruitmentStatus,
                                    long applicationCount, long pendingCount, long hiredCount,
                                    BigDecimal averageMatchScore) {}
}
