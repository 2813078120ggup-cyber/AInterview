package com.tyut.aiinterview.algorithmworker.observability;

import com.tyut.aiinterview.algorithmworker.AlgorithmJudgeTaskService;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.util.concurrent.atomic.AtomicInteger;
import org.springframework.data.redis.connection.stream.PendingMessagesSummary;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

/** Low-cardinality metrics for the isolated asynchronous judge pipeline. */
@Component
public class AlgorithmJudgeMetrics {
    private final MeterRegistry registry;
    private final StringRedisTemplate redisTemplate;
    private final AtomicInteger activeExecutions = new AtomicInteger();

    public AlgorithmJudgeMetrics(MeterRegistry registry, StringRedisTemplate redisTemplate) {
        this.registry = registry;
        this.redisTemplate = redisTemplate;
        Gauge.builder("ai_interview_algorithm_judge_active", activeExecutions, AtomicInteger::get)
                .description("Currently executing algorithm submissions")
                .register(registry);
        Gauge.builder("ai_interview_algorithm_judge_stream_pending", this,
                        AlgorithmJudgeMetrics::pendingMessages)
                .description("Pending Redis Stream messages in the algorithm judge group")
                .register(registry);
    }

    public void recordClaim(boolean recovered) {
        Counter.builder("ai_interview_algorithm_judge_claims_total")
                .description("Algorithm submission claims won by this Worker")
                .tag("recovery", recovered ? "stale" : "new")
                .register(registry)
                .increment();
    }

    public void recordOutcome(String status) {
        Counter.builder("ai_interview_algorithm_judge_outcomes_total")
                .description("Persisted algorithm judge outcomes")
                .tag("status", status == null || status.isBlank() ? "unknown" : status.toLowerCase())
                .register(registry)
                .increment();
    }

    public void recordRetry() {
        Counter.builder("ai_interview_algorithm_judge_retries_total")
                .description("Algorithm judge retry attempts")
                .register(registry)
                .increment();
    }

    public void recordError(String stage) {
        Counter.builder("ai_interview_algorithm_judge_errors_total")
                .description("Algorithm judge processing errors")
                .tag("stage", stage == null || stage.isBlank() ? "unknown" : stage)
                .register(registry)
                .increment();
    }

    public Timer.Sample startExecution() {
        activeExecutions.incrementAndGet();
        return Timer.start(registry);
    }

    public void stopExecution(Timer.Sample sample) {
        try {
            sample.stop(Timer.builder("ai_interview_algorithm_judge_execution_duration")
                    .description("Docker sandbox execution duration")
                    .publishPercentiles(0.5, 0.95, 0.99)
                    .register(registry));
        } finally {
            activeExecutions.updateAndGet(value -> Math.max(0, value - 1));
        }
    }

    private double pendingMessages() {
        try {
            PendingMessagesSummary summary = redisTemplate.opsForStream()
                    .pending(AlgorithmJudgeTaskService.STREAM, AlgorithmJudgeTaskService.GROUP);
            return summary == null ? 0 : summary.getTotalPendingMessages();
        } catch (Exception ignored) {
            return 0;
        }
    }
}
