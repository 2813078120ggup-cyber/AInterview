package com.tyut.aiinterview.freeinterview;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public final class FreeInterviewDtos {
    private FreeInterviewDtos() {}

    public record SubmitTurnRequest(@NotBlank @Size(max = 64) String submissionId,
                                    @NotBlank String question, @NotBlank String answer) {}

    public record SessionView(Long id, String resumeFilename, String targetRole, String resumeSummary, String status,
                              int completedTurns, String openingPrompt, ReportView report, LocalDateTime createdAt,
                              Long activeTaskId, String activeTaskType, String activeTaskStatus) {}

    public record HistoryView(Long id, String resumeFilename, String targetRole, String status, int completedTurns,
                              BigDecimal totalScore, LocalDateTime createdAt, LocalDateTime updatedAt,
                              LocalDateTime completedAt) {}

    public record TurnView(int turnNo, String question, String answer, String nextQuestion, LocalDateTime createdAt) {}

    public record TurnResult(SessionView session, String nextQuestion, Long taskId) {}

    public record DetailView(SessionView session, List<TurnView> turns) {}

    public record TaskResult(SessionView session, Long taskId) {}

    public record ReportView(BigDecimal totalScore, BigDecimal professionalScore, BigDecimal expressionScore,
                             BigDecimal logicScore, BigDecimal adaptabilityScore, String summary, String strengths,
                             String weaknesses, String improvementSuggestions) {}
}
