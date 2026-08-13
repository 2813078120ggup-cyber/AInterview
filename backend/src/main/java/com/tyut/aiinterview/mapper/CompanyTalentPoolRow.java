package com.tyut.aiinterview.mapper;

import java.time.LocalDateTime;

public record CompanyTalentPoolRow(Long poolId, Long candidateId, String candidateName,
                                   String email, String phone, Integer candidateStatus,
                                   String poolStatus, LocalDateTime lastContactedAt,
                                   LocalDateTime addedAt, LocalDateTime updatedAt,
                                   Long noteCount, Long applicationCount,
                                   LocalDateTime lastApplicationAt, LocalDateTime lastActivityAt,
                                   String tagSummary) {
}
