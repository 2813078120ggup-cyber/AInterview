package com.tyut.aiinterview.admin;

import java.time.LocalDateTime;
import java.util.List;

public final class AdminAiOperationsDtos {
    private AdminAiOperationsDtos() {
    }

    public record Overview(LocalDateTime generatedAt, AiSummary ai, TaskSummary tasks,
                           List<ProviderView> providers, List<PromptView> prompts,
                           List<CallView> recentCalls, List<TaskView> recentTasks) {
    }

    public record AiSummary(long total, long success, long failed, long running,
                            long averageLatencyMs, long totalTokens, String windowLabel) {
    }

    public record TaskSummary(long pending, long running, long failed, long backlog,
                              long reportBacklog, LocalDateTime oldestPendingAt) {
    }

    public record ProviderView(Long id, String name, String code, String kind, String model,
                               String state, String stateLabel, boolean enabled,
                               boolean textDefault, boolean voiceDefault,
                               String lastTestState, Integer lastTestStatusCode,
                               Long lastTestLatencyMs, String lastTestMessage,
                               LocalDateTime lastTestedAt) {
    }

    public record PromptView(String code, String name, String category, Integer version,
                             boolean active, LocalDateTime activatedAt) {
    }

    public record CallView(Long id, String requestId, Long taskId, Long interviewId,
                           Long freeInterviewSessionId, String generationType, String promptCode,
                           Integer promptVersion, String provider, String model, String status,
                           Long latencyMs, Integer inputChars, Integer outputChars,
                           Integer totalTokens, Integer httpStatus, String errorSummary,
                           LocalDateTime startedAt, LocalDateTime finishedAt) {
    }

    public record TaskView(Long id, String taskType, String status, Integer attempts,
                           Integer maxAttempts, LocalDateTime scheduledAt,
                           LocalDateTime startedAt, LocalDateTime finishedAt,
                           Long interviewId, Long answerId, String generationRequestId,
                           String provider, String model, String promptCode,
                           Integer promptVersion, boolean retryable, String failureSummary,
                           BusinessRef business) {
    }

    public record Trace(BusinessRef business, TaskView task, GenerationView generation,
                        ProviderRef provider, PromptRef prompt, ResultRef result) {
    }

    public record BusinessRef(String type, Long id, String label, String path) {
    }

    public record GenerationView(Long id, String requestId, String status, String generationType,
                                 Long latencyMs, Integer inputChars, Integer outputChars,
                                 Integer totalTokens, Integer httpStatus,
                                 LocalDateTime startedAt, LocalDateTime finishedAt) {
    }

    public record ProviderRef(String code, String name, String kind, String model) {
    }

    public record PromptRef(String code, String name, Integer version, String category,
                            boolean active) {
    }

    public record ResultRef(String type, String label, String path) {
    }
}
