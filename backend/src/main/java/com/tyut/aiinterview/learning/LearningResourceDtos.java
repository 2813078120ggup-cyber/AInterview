package com.tyut.aiinterview.learning;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDateTime;
import java.util.List;

public final class LearningResourceDtos {
    private LearningResourceDtos() {}

    public record ResourceQuery(Integer pageNo, Integer pageSize, String keyword, String status) {}

    public record CreateRequest(
            @NotBlank @Size(max = 200) String title,
            @Size(max = 1000) String description,
            String status,
            Boolean allowDownload) {}

    public record UpdateRequest(
            @NotBlank @Size(max = 200) String title,
            @Size(max = 1000) String description,
            @NotBlank String status,
            Boolean allowDownload) {}

    public record PermissionRequest(
            @NotBlank String subjectType,
            @NotBlank String subjectId,
            Boolean canView,
            Boolean canAnnotate,
            LocalDateTime expiresAt) {}

    public record AnnotationRequest(
            @NotNull Integer pageIndex,
            @NotBlank String annotationType,
            String anchorType,
            @NotNull JsonNode geometry,
            @Size(max = 20000) String selectedText,
            @Size(max = 10000) String noteContent,
            JsonNode style,
            String visibility,
            Integer version) {}

    public record ResourceSummary(
            Long id,
            String publicId,
            String title,
            String description,
            String status,
            Boolean allowDownload,
            String originalName,
            Long fileSize,
            Integer pageCount,
            String checksumSha256,
            LocalDateTime createdAt,
            LocalDateTime updatedAt,
            Boolean canView,
            Boolean canAnnotate) {}

    public record PermissionView(
            Long id,
            String subjectType,
            String subjectId,
            String subjectLabel,
            Boolean canView,
            Boolean canAnnotate,
            LocalDateTime expiresAt) {}

    public record AnnotationView(
            String publicId,
            Long resourceId,
            Long versionId,
            Long ownerUserId,
            Integer pageIndex,
            String annotationType,
            String anchorType,
            JsonNode geometry,
            String selectedText,
            String noteContent,
            JsonNode style,
            String visibility,
            Integer version,
            LocalDateTime createdAt,
            LocalDateTime updatedAt) {}

    public record PermissionResponse(String publicId, List<PermissionView> permissions) {}
}
