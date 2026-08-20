package com.tyut.aiinterview.report;

import com.tyut.aiinterview.recording.RecordingDtos;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public final class ReportDtos {
    private ReportDtos() {
    }

    public record ReportQuery(Long pageNo, Long pageSize, String keyword) {
    }

    public record ReportDetail(Long id, Long interviewId, BigDecimal totalScore,
                               BigDecimal professionalScore, BigDecimal expressionScore,
                               BigDecimal logicScore, BigDecimal adaptabilityScore,
                               String summary, String strengths, String weaknesses,
                               String improvementSuggestions, String generationMethod,
                               String scoringPromptCode, Integer scoringPromptVersionNo,
                               String reportPromptCode, Integer reportPromptVersionNo,
                               Long generatedBy, String pdfUrl, Integer status,
                               LocalDateTime publishedAt, long questionCount,
                                String reliabilityWarning) {
    }

    /**
     * Company-scoped allowlist. Technical generation metadata is intentionally
     * excluded from the HR response.
     */
    public record CompanyReportDetail(Long applicationId, Long id, Long interviewId, BigDecimal totalScore,
                                      BigDecimal professionalScore, BigDecimal expressionScore,
                                      BigDecimal logicScore, BigDecimal adaptabilityScore,
                                      String summary, String strengths, String weaknesses,
                                      String improvementSuggestions, Integer status,
                                      LocalDateTime generatedAt, LocalDateTime publishedAt, long questionCount,
                                      String reliabilityWarning, String reportStatus, String taskStatus,
                                       Integer taskAttempts, String taskMessage, boolean canRetry,
                                       List<CompanyQuestionReview> questionReviews,
                                       RecordingDtos.RecordingView recording,
                                       boolean humanReviewRequired, String humanReviewStatus,
                                       String humanReviewDecision, String humanReviewNote,
                                       Long humanReviewedBy, LocalDateTime humanReviewedAt) {
    }

    public record CompanyQuestionReview(Long id, Integer sequenceNo, String question, String questionType,
                                        String answer, LocalDateTime answeredAt, List<String> followUps,
                                        CompanyEvaluationView evaluation) {
    }

    public record CompanyEvaluationView(BigDecimal professionalScore, BigDecimal expressionScore,
                                        BigDecimal logicScore, BigDecimal adaptabilityScore,
                                        BigDecimal overallScore, String comment, String source, Integer status) {
    }

    public record ReportListItem(Long reportId, Long interviewId, String interviewTitle, Long candidateId,
                                 String candidateName, String candidateUsername, LocalDateTime scheduledAt,
                                 BigDecimal totalScore, BigDecimal professionalScore, BigDecimal expressionScore,
                                 BigDecimal logicScore, BigDecimal adaptabilityScore, Integer status,
                                 LocalDateTime publishedAt) {
    }

    public record TrendPoint(Long interviewId, String interviewTitle, LocalDateTime scheduledAt,
                             BigDecimal totalScore, BigDecimal professionalScore, BigDecimal expressionScore,
                             BigDecimal logicScore, BigDecimal adaptabilityScore) {
    }

    public record ScoreChange(BigDecimal totalScore, BigDecimal professionalScore, BigDecimal expressionScore,
                              BigDecimal logicScore, BigDecimal adaptabilityScore) {
    }

    public record CandidateAbilitySummary(long reportCount, TrendPoint latest, TrendPoint previous,
                                          ScoreChange changeFromPrevious, List<TrendPoint> trends) {
    }

    public record TrainingDay(Integer day, String title, List<String> tasks) {
    }

    public record TrainingPlan(String priority, Integer durationDays, List<String> focusAreas,
                               List<TrainingDay> dailyPlan, List<String> recommendedBanks,
                               List<String> interviewDrills, List<String> successCriteria,
                               String generationMethod) {
    }
}
