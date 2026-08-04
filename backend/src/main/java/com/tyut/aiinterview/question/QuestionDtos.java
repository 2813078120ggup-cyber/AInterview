package com.tyut.aiinterview.question;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public final class QuestionDtos {
    private QuestionDtos() {
    }

    public record CategoryRequest(Long parentId, @NotBlank @Size(max = 64) String categoryCode,
                                  @NotBlank @Size(max = 128) String name, Integer sortOrder,
                                  @NotNull Integer status) {
    }

    public record CategoryVO(Long id, Long parentId, String categoryCode, String name, Integer sortOrder,
                             Integer status, List<CategoryVO> children) {
    }

    public record BankQuery(Long pageNo, Long pageSize, String keyword, Long categoryId, Long positionId,
                            Integer status) {
    }

    public record BankRequest(Long categoryId, Long positionId, @NotBlank @Size(max = 64) String bankCode,
                              @NotBlank @Size(max = 128) String name, @Size(max = 500) String description,
                              Integer visibility, Integer status) {
    }

    public record BankVO(Long id, Long categoryId, Long positionId, String bankCode, String name, String description,
                         Integer visibility, Integer status, Long createdBy, LocalDateTime createdAt,
                         LocalDateTime updatedAt) {
    }

    public record QuestionQuery(Long pageNo, Long pageSize, String keyword, String questionType,
                                Integer difficulty, Integer status, String source) {
    }

    public record QuestionRequest(@NotBlank @Size(max = 20) String questionType, @NotNull @Min(1) @Max(3) Integer difficulty,
                                  @NotBlank String content, String options, String correctAnswer, String answerTemplate,
                                  String explanation, String tags, @NotNull @DecimalMin("0.00") BigDecimal score,
                                  String source, Integer sortOrder, Integer status) {
    }

    public record QuestionVO(Long id, Long bankId, String questionType, Integer difficulty, String content,
                             String options, String correctAnswer, String answerTemplate, String explanation,
                             String tags, BigDecimal score, String source, Integer sortOrder, Integer status,
                             Long createdBy, LocalDateTime createdAt, LocalDateTime updatedAt) {
    }
    public record QuestionOption(Long id, Long bankId, String content, String questionType, Integer difficulty, BigDecimal score) {}
}
