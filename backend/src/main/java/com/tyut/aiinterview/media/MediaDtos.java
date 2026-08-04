package com.tyut.aiinterview.media;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDateTime;

public final class MediaDtos {
    private MediaDtos() {
    }
    public record AttachMediaRequest(@NotNull Long mediaId, Integer durationSeconds) {
    }
    public record TtsRequest(@NotBlank @Size(max = 4096) String text, @Size(max = 32) String voice) {
    }
    public record FollowUpRequest(@NotNull Long interviewQuestionId,
                                  @NotBlank @Size(max = 10000) String answer,
                                  @NotBlank @Size(max = 4000) String question) {
    }
    public record MediaVO(Long id, String originalName, String contentType, String mediaType, Long sizeBytes, Integer status,
                          LocalDateTime createdAt) {
    }
}
