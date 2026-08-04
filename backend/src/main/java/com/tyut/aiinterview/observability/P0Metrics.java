package com.tyut.aiinterview.observability;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.tyut.aiinterview.domain.AiGenerationRecord;
import com.tyut.aiinterview.domain.AiTask;
import com.tyut.aiinterview.mapper.AiGenerationRecordMapper;
import com.tyut.aiinterview.mapper.AiTaskMapper;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Duration;
import java.time.LocalDateTime;
import org.springframework.stereotype.Component;

@Component
public class P0Metrics {
    private final AiTaskMapper taskMapper;
    private final AiGenerationRecordMapper generationMapper;

    public P0Metrics(MeterRegistry registry, AiTaskMapper taskMapper, AiGenerationRecordMapper generationMapper) {
        this.taskMapper = taskMapper;
        this.generationMapper = generationMapper;
        registerTaskGauge(registry, "PENDING");
        registerTaskGauge(registry, "RUNNING");
        registerTaskGauge(registry, "FAILED");
        registerGenerationGauge(registry, "RUNNING");
        registerGenerationGauge(registry, "FAILED");
        Gauge.builder("ai_interview_ai_tasks_oldest_pending_seconds", this, P0Metrics::oldestPendingSeconds)
                .description("Age of the oldest pending AI task")
                .register(registry);
    }

    private void registerTaskGauge(MeterRegistry registry, String status) {
        Gauge.builder("ai_interview_ai_tasks", this, ignored -> countTasks(status))
                .description("AI tasks by status")
                .tag("status", status.toLowerCase())
                .register(registry);
    }

    private void registerGenerationGauge(MeterRegistry registry, String status) {
        Gauge.builder("ai_interview_ai_generations", this, ignored -> countGenerations(status))
                .description("AI generation records by status")
                .tag("status", status.toLowerCase())
                .register(registry);
    }

    private double countTasks(String status) {
        return taskMapper.selectCount(new LambdaQueryWrapper<AiTask>().eq(AiTask::getStatus, status));
    }

    private double countGenerations(String status) {
        return generationMapper.selectCount(new LambdaQueryWrapper<AiGenerationRecord>().eq(AiGenerationRecord::getStatus, status));
    }

    private double oldestPendingSeconds() {
        AiTask task = taskMapper.selectOne(new LambdaQueryWrapper<AiTask>()
                .eq(AiTask::getStatus, "PENDING")
                .orderByAsc(AiTask::getCreatedAt)
                .last("LIMIT 1"));
        LocalDateTime createdAt = task == null ? null : task.getCreatedAt();
        return createdAt == null ? 0 : Math.max(0, Duration.between(createdAt, LocalDateTime.now()).toSeconds());
    }
}
