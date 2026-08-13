package com.tyut.aiinterview.mapper;

import java.time.LocalDateTime;

public record CompanyInterviewRow(
        String interviewKind,
        String activityId,
        Long interviewId,
        Long offlineInterviewId,
        Long applicationId,
        Long companyId,
        Long positionId,
        String positionName,
        Long candidateId,
        String candidateName,
        String candidateEmail,
        String candidatePhone,
        String activityType,
        Integer rawStatus,
        String status,
        LocalDateTime scheduledAt,
        Integer durationMinutes,
        String location,
        String meetingUrl,
        String contactName,
        String contactPhone,
        String note,
        String applicationStatus,
        String notificationStatus,
        LocalDateTime updatedAt,
        Long interviewerId) {
}
