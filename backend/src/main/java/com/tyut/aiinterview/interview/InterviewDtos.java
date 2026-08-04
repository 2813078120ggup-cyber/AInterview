package com.tyut.aiinterview.interview;

import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import com.tyut.aiinterview.domain.Interview;

public final class InterviewDtos {
    private InterviewDtos() {
    }

    public record CreateRequest(@NotBlank @Size(max = 200) String title, Long positionId, @NotNull Long candidateId,
                                @NotNull @Future LocalDateTime scheduledAt,
                                @NotNull @Min(1) @Max(480) Integer duration, @NotBlank String type,
                                @Size(max = 512) String meetingUrl, @Size(max = 500) String remark,
                                @Size(max = 20) List<Long> questionIds, Long questionBankId,
                                @Min(1) @Max(20) Integer questionCount,
                                @Size(max = 32) String interviewerStyle) {
    }

    public record RescheduleRequest(@NotNull @Future LocalDateTime scheduledAt,
                                    @NotNull @Min(1) @Max(480) Integer duration) {
    }

    public record PracticeRequest(@NotNull Long questionBankId,
                                  @NotNull @Min(1) @Max(10) Integer questionCount,
                                  @NotNull @Min(10) @Max(180) Integer duration,
                                  @Size(max = 32) String interviewerStyle) {
    }

    public record PracticeBankView(Long id, String name, String description, Long questionCount) {
    }

    public record InterviewQuery(Long pageNo, Long pageSize, Integer status, Long candidateId, Long interviewerId,
                                 Long positionId, LocalDateTime scheduledFrom, LocalDateTime scheduledTo) {
    }

    public record AnswerRequest(@Size(max = 10000) String answerContent, String answerData,
                                @Size(max = 512) String audioUrl, @Min(0) Integer durationSeconds) {
    }

    public record QuestionView(Long interviewQuestionId, Long questionId, Integer sequenceNo, BigDecimal maxScore,
                               String content, String options, String questionType, String correctAnswer,
                               String answerTemplate, String explanation) {
    }

    public record AnswerView(Long interviewQuestionId, String answerContent, String answerData, String audioUrl,
                             Integer durationSeconds, LocalDateTime answeredAt) {
    }

    public record ProgressRequest(@NotNull @Min(0) Integer activeQuestionIndex) {
    }

    public record ProgressView(Integer activeQuestionIndex, long remainingSeconds,
                               int followUpCount, int followUpLimit,
                               Long activeTaskId, String activeTaskStatus, Integer activeTaskSequence,
                               LocalDateTime updatedAt) {
    }

    public record EndResponse(Interview interview, Long evaluationTaskId, String evaluationTaskStatus) {
    }
}
