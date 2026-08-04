package com.tyut.aiinterview.reflection;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public final class ReflectionDtos {
    private ReflectionDtos() {
    }

    public record SaveRequest(
            @NotNull @Min(0) @Max(100) Integer selfScore,
            @NotNull @Min(1) @Max(5) Integer confidenceLevel,
            @NotBlank @Size(max = 2000) String content,
            @Size(max = 1000) String highlights,
            @Size(max = 1000) String improvements,
            @Size(max = 1000) String actionPlan) {
    }

    public record ReflectionView(
            Long reflectionId,
            Long interviewId,
            String interviewTitle,
            LocalDateTime scheduledAt,
            Integer selfScore,
            Integer confidenceLevel,
            String content,
            String highlights,
            String improvements,
            String actionPlan,
            BigDecimal aiScore,
            LocalDateTime createdAt,
            LocalDateTime updatedAt) {
    }

    public record CandidateSummary(
            long reflectionCount,
            BigDecimal averageSelfScore,
            BigDecimal averageConfidenceLevel,
            BigDecimal averageAiScore,
            Integer latestSelfScore,
            Integer changeFromPrevious,
            List<ReflectionView> reflections) {
    }
}
