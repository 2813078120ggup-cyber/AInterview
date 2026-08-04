package com.tyut.aiinterview.ai;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.tyut.aiinterview.common.PageResult;
import com.tyut.aiinterview.domain.AiGenerationRecord;
import com.tyut.aiinterview.mapper.AiGenerationRecordMapper;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class AiGenerationAuditService {
    private final AiGenerationRecordMapper mapper;

    public AiGenerationAuditService(AiGenerationRecordMapper mapper) {
        this.mapper = mapper;
    }

    public AiGenerationRecord start(AiGenerationContext context, String promptCode, Integer promptVersion,
                                    String provider, String model, int inputChars) {
        AiGenerationRecord record = new AiGenerationRecord();
        record.setRequestId(UUID.randomUUID().toString());
        record.setTaskId(context.taskId());
        record.setInterviewId(context.interviewId());
        record.setFreeInterviewSessionId(context.freeInterviewSessionId());
        record.setGenerationType(context.generationType());
        record.setPromptCode(promptCode);
        record.setPromptVersionNo(promptVersion);
        record.setProvider(provider);
        record.setModel(model);
        record.setStatus("RUNNING");
        record.setInputChars(Math.max(0, inputChars));
        record.setOutputChars(0);
        record.setCreatedBy(context.createdBy());
        record.setStartedAt(LocalDateTime.now());
        mapper.insert(record);
        return record;
    }

    public void success(AiGenerationRecord record, int outputChars, Integer promptTokens,
                        Integer completionTokens, Integer totalTokens, Integer httpStatus) {
        record.setStatus("SUCCESS");
        record.setOutputChars(Math.max(0, outputChars));
        record.setPromptTokens(promptTokens);
        record.setCompletionTokens(completionTokens);
        record.setTotalTokens(totalTokens);
        record.setHttpStatus(httpStatus);
        finish(record);
    }

    public void failure(AiGenerationRecord record, Throwable error, Integer httpStatus) {
        record.setStatus("FAILED");
        record.setHttpStatus(httpStatus);
        record.setErrorType(error.getClass().getSimpleName());
        record.setErrorMessage(truncate(error.getMessage(), 1000));
        finish(record);
    }

    public PageResult<AiGenerationAuditDtos.RecordView> page(AiGenerationAuditDtos.Query query) {
        long pageNo = query.pageNo() == null ? 1 : Math.max(1, query.pageNo());
        long pageSize = query.pageSize() == null ? 20 : Math.min(100, Math.max(1, query.pageSize()));
        LambdaQueryWrapper<AiGenerationRecord> wrapper = filters(query).orderByDesc(AiGenerationRecord::getId);
        Page<AiGenerationRecord> result = mapper.selectPage(new Page<>(pageNo, pageSize), wrapper);
        return PageResult.of(result.getRecords().stream().map(this::view).toList(), result.getTotal(), pageNo, pageSize);
    }

    public AiGenerationAuditDtos.Summary summary(AiGenerationAuditDtos.Query query) {
        var records = mapper.selectList(filters(query));
        long success = records.stream().filter(item -> "SUCCESS".equals(item.getStatus())).count();
        long failed = records.stream().filter(item -> "FAILED".equals(item.getStatus())).count();
        long running = records.stream().filter(item -> "RUNNING".equals(item.getStatus())).count();
        long averageLatency = Math.round(records.stream().filter(item -> item.getLatencyMs() != null)
                .mapToLong(AiGenerationRecord::getLatencyMs).average().orElse(0));
        long tokens = records.stream().filter(item -> item.getTotalTokens() != null)
                .mapToLong(AiGenerationRecord::getTotalTokens).sum();
        return new AiGenerationAuditDtos.Summary(records.size(), success, failed, running, averageLatency, tokens);
    }

    private LambdaQueryWrapper<AiGenerationRecord> filters(AiGenerationAuditDtos.Query query) {
        LambdaQueryWrapper<AiGenerationRecord> wrapper = new LambdaQueryWrapper<>();
        if (hasText(query.status())) wrapper.eq(AiGenerationRecord::getStatus, query.status().trim().toUpperCase());
        if (hasText(query.generationType())) wrapper.eq(AiGenerationRecord::getGenerationType, query.generationType().trim().toUpperCase());
        if (hasText(query.promptCode())) wrapper.eq(AiGenerationRecord::getPromptCode, query.promptCode().trim());
        if (hasText(query.keyword())) {
            String keyword = query.keyword().trim();
            wrapper.and(item -> item.like(AiGenerationRecord::getRequestId, keyword)
                    .or().like(AiGenerationRecord::getErrorMessage, keyword)
                    .or().like(AiGenerationRecord::getModel, keyword));
        }
        return wrapper;
    }

    private void finish(AiGenerationRecord record) {
        LocalDateTime finishedAt = LocalDateTime.now();
        record.setFinishedAt(finishedAt);
        record.setLatencyMs(Math.max(0, Duration.between(record.getStartedAt(), finishedAt).toMillis()));
        mapper.updateById(record);
    }

    private AiGenerationAuditDtos.RecordView view(AiGenerationRecord item) {
        return new AiGenerationAuditDtos.RecordView(item.getId(), item.getRequestId(), item.getTaskId(),
                item.getInterviewId(), item.getFreeInterviewSessionId(), item.getGenerationType(),
                item.getPromptCode(), item.getPromptVersionNo(), item.getProvider(), item.getModel(),
                item.getStatus(), item.getLatencyMs(), item.getInputChars(), item.getOutputChars(),
                item.getPromptTokens(), item.getCompletionTokens(), item.getTotalTokens(), item.getHttpStatus(),
                item.getErrorType(), item.getErrorMessage(), item.getCreatedBy(), item.getStartedAt(), item.getFinishedAt());
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private String truncate(String value, int max) {
        if (value == null) return null;
        return value.length() <= max ? value : value.substring(0, max);
    }
}
