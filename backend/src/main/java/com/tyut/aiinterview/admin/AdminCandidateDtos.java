package com.tyut.aiinterview.admin;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public final class AdminCandidateDtos {
    private AdminCandidateDtos() {}

    public record Detail(
            Account account,
            Overview overview,
            List<ResumeSummary> resumes,
            List<ApplicationSummary> applications,
            List<InterviewSummary> interviews,
            List<ReportSummary> reports) {
    }

    public record Account(
            Long id,
            String username,
            String realName,
            Integer status,
            boolean avatarAvailable,
            String email,
            boolean emailVerified,
            String phone,
            boolean phoneVerified,
            List<String> availableLoginMethods,
            List<String> roles,
            boolean identityConsistent,
            LocalDateTime lastLoginAt,
            LocalDateTime createdAt,
            LocalDateTime updatedAt) {
    }

    public record Overview(
            int resumeCount,
            int applicationCount,
            int interviewCount,
            int reportCount,
            BigDecimal latestScore,
            LocalDateTime latestActivityAt) {
    }

    public record ResumeSummary(
            Long id,
            String title,
            String fileName,
            String summary,
            List<String> skills,
            boolean defaultResume,
            String parseStatus,
            Integer parseVersion,
            LocalDateTime parsedAt,
            LocalDateTime updatedAt) {
    }

    public record ApplicationSummary(
            Long id,
            String applicationNo,
            Long companyId,
            String companyName,
            Long positionId,
            String positionName,
            String status,
            BigDecimal matchScore,
            String matchStatus,
            LocalDateTime submittedAt,
            LocalDateTime updatedAt) {
    }

    public record InterviewSummary(
            Long id,
            String title,
            LocalDateTime scheduledAt,
            Integer duration,
            Integer status,
            String type,
            LocalDateTime updatedAt) {
    }

    public record ReportSummary(
            Long id,
            Long interviewId,
            String interviewTitle,
            LocalDateTime scheduledAt,
            BigDecimal totalScore,
            BigDecimal professionalScore,
            BigDecimal expressionScore,
            BigDecimal logicScore,
            BigDecimal adaptabilityScore,
            Integer status,
            LocalDateTime publishedAt,
            LocalDateTime generatedAt) {
    }
}
