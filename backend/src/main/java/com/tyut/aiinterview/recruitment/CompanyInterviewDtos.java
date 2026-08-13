package com.tyut.aiinterview.recruitment;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDateTime;
import java.util.List;

public final class CompanyInterviewDtos {
    private CompanyInterviewDtos() {
    }

    public record Query(Integer pageNo, Integer pageSize, String range, Long positionId,
                        String keyword, String activityType, String sort) {
    }

    public record Page(List<Item> records, long total, int pageNo, int pageSize, LocalDateTime serverNow) {
    }

    public record Item(String activityId, String interviewKind, Long interviewId, Long offlineInterviewId,
                       Long applicationId, Long positionId, String positionName, Long candidateId,
                       String candidateName, String candidateEmail, String candidatePhone,
                       String activityType, Integer rawStatus, String status, LocalDateTime scheduledAt,
                       Integer durationMinutes, String location, String meetingUrl, String contactName,
                       String contactPhone, String note, String applicationStatus, String notificationStatus,
                       LocalDateTime updatedAt) {
    }

    public record Detail(Item item, String aiTaskStatus, Integer aiTaskAttempts,
                         String aiTaskMessage, List<StatusHistory> statusHistory) {
    }

    public record StatusHistory(String interviewKind, String fromStatus, String toStatus,
                                String reason, String notificationStatus, String operatorName,
                                LocalDateTime createdAt) {
    }

    public record RescheduleRequest(@NotNull @Future LocalDateTime scheduledAt,
                                    @NotNull @Min(15) @Max(480) Integer durationMinutes,
                                    @Size(max = 1000) String reason) {
    }

    public record ActionRequest(@Size(max = 1000) String reason) {
    }

    public record RetryView(String status, Integer attempts, String message) {
    }
}
