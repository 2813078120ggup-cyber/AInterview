package com.tyut.aiinterview.ticket;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDateTime;
import java.util.List;

public final class FeedbackTicketDtos {
    private FeedbackTicketDtos() {}

    public record CreateRequest(String ticketType, String title, @Size(max = 10000) String description) {}
    public record UpdateDraftRequest(@NotNull String ticketType, @NotBlank @Size(max = 120) String title,
                                     @NotBlank @Size(max = 10000) String description) {}
    public record StatusRequest(@NotBlank String targetStatus, @Size(max = 2000) String resolution, @NotNull Integer version) {}
    public record AssigneeRequest(Long assigneeId, @NotNull Integer version) {}
    public record MessageRequest(@NotBlank @Size(max = 5000) String content, @NotBlank @Size(max = 80) String clientRequestId) {}
    public record TicketQuery(Long pageNo, Long pageSize, String keyword, String ticketType, String status, String assigneeId) {}
    public record ActivityQuery(Long afterId, Integer limit) {}
    public record TicketSummary(Long id, String ticketNo, Long creatorId, String creatorName, String ticketType,
                                String title, String status, Long assigneeId, String assigneeName,
                                LocalDateTime lastActivityAt, LocalDateTime createdAt, long unreadCount) {}
    public record Attachment(Long id, Long mediaId, String originalName, String contentType, Long sizeBytes,
                             String contentUrl, LocalDateTime createdAt) {}
    public record Activity(Long id, Long ticketId, Long actorId, String actorName, String activityType,
                           String content, String fromStatus, String toStatus, Long fromAssigneeId,
                           Long toAssigneeId, LocalDateTime createdAt, List<Attachment> attachments) {}
    public record Permissions(boolean canEdit, boolean canSubmit, boolean canReply, boolean canAssign,
                              boolean canChangeStatus, boolean canClose) {}
    public record Detail(TicketSummary ticket, String description, String resolution, Integer version,
                         List<Attachment> attachments, List<Activity> activities, Permissions permissions) {}
    public record Assignee(Long id, String username, String realName) {}
    public record Notification(Long id, String notificationType, String title, String content,
                               String businessType, Long businessId, boolean read, LocalDateTime createdAt) {}
    public record NotificationPage(List<Notification> records, long total, long pageNo, long pageSize) {}
}
