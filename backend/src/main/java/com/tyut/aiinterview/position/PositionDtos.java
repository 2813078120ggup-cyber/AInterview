package com.tyut.aiinterview.position;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDateTime;

public final class PositionDtos {
    private PositionDtos() {}
    public record PositionQuery(Long pageNo, Long pageSize, String keyword, Integer status) {}
    public record PositionRequest(@NotBlank @Size(max = 64) String positionCode, @NotBlank @Size(max = 128) String name,
                                  String department, String description, String competencyModel, @NotNull Integer status) {}
    public record PositionVO(Long id, String positionCode, String name, String department, String description,
                             String competencyModel, Integer status, Long createdBy, LocalDateTime createdAt, LocalDateTime updatedAt) {}
}
