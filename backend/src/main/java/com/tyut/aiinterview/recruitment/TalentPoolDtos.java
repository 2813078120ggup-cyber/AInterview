package com.tyut.aiinterview.recruitment;

import com.tyut.aiinterview.common.PageResult;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public final class TalentPoolDtos {
    private TalentPoolDtos() {}

    public record Query(Long pageNo, Long pageSize, String keyword, Long tagId, String skill,
                        Long positionId, LocalDateTime lastContactFrom, LocalDateTime lastContactTo,
                        String sort) {}

    public record CandidateView(Long poolId, Long candidateId, String candidateName,
                                String email, String phone, Integer candidateStatus,
                                String poolStatus, LocalDateTime lastContactedAt,
                                LocalDateTime addedAt, LocalDateTime updatedAt,
                                long noteCount, long applicationCount,
                                LocalDateTime lastApplicationAt, LocalDateTime lastActivityAt,
                                List<TagView> tags) {}

    public record TagView(Long id, String name, String color) {}

    public record NoteView(Long id, Long applicationId, String content, Long authorId,
                           String authorName, Long updatedBy, String updatedByName,
                           Integer version, LocalDateTime createdAt, LocalDateTime updatedAt) {}

    public record HistoricalApplication(Long applicationId, String applicationNo, Long positionId,
                                       String positionName, String status, BigDecimal matchScore,
                                       String interviewStatus, LocalDateTime submittedAt,
                                       LocalDateTime updatedAt) {}

    public record Detail(CandidateView candidate, List<TagView> tags,
                         PageResult<NoteView> notes, List<HistoricalApplication> applications) {}

    public record MembershipView(boolean active, Long poolId, Integer version, List<TagView> tags) {}

    public record NoteRequest(@NotBlank @Size(max = 4000) String content, Long applicationId) {}

    public record NoteUpdateRequest(@NotBlank @Size(max = 4000) String content,
                                    @NotNull @Min(0) Integer version) {}

    public record TagRequest(@NotBlank @Size(max = 64) String name, @Size(max = 32) String color) {}
}
