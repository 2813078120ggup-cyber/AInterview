package com.tyut.aiinterview.ai;

import java.time.LocalDateTime;

public final class AiGenerationAuditDtos {
    private AiGenerationAuditDtos() {}

    public record Query(Long pageNo, Long pageSize, String status, String generationType,
                        String promptCode, String keyword) {}

    public record RecordView(Long id, String requestId, Long taskId, Long interviewId,
                             Long freeInterviewSessionId, String generationType, String promptCode,
                             Integer promptVersion, String provider, String model, String status,
                             Long latencyMs, Integer inputChars, Integer outputChars,
                             Integer promptTokens, Integer completionTokens, Integer totalTokens,
                             Integer httpStatus, String errorType, String errorMessage,
                             Long createdBy, LocalDateTime startedAt, LocalDateTime finishedAt) {}

    public record Summary(long total, long success, long failed, long running,
                          long averageLatencyMs, long totalTokens) {}
}
