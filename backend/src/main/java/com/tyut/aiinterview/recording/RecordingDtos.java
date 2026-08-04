package com.tyut.aiinterview.recording;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDateTime;
import java.util.List;

public final class RecordingDtos {
    private RecordingDtos() {
    }

    public record SelectModeRequest(@NotBlank @Size(max = 16) String mode) {
    }

    public record TimelineEventRequest(Long interviewQuestionId,
                                       @NotBlank @Size(max = 32) String eventType,
                                       @NotNull @Min(0) @Max(28_800_000) Long offsetMs,
                                       @Size(max = 4000) String content) {
    }

    public record SegmentView(Long id, Long interviewQuestionId, Long mediaId, Integer segmentNo,
                              Long startedOffsetMs, Long endedOffsetMs, String contentType,
                              String contentPath) {
    }

    public record TimelineEventView(Long id, Long interviewQuestionId, String eventType,
                                    Long offsetMs, String content, LocalDateTime createdAt) {
    }

    public record RecordingView(Long id, Long interviewId, String mode, String status,
                                LocalDateTime startedAt, LocalDateTime endedAt,
                                List<SegmentView> segments, List<TimelineEventView> events) {
    }

    public record RecordingContent(org.springframework.core.io.Resource resource, String contentType,
                                   long sizeBytes, String filename) {
    }
}
