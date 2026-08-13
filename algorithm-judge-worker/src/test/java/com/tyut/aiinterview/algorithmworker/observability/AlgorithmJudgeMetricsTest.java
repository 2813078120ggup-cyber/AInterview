package com.tyut.aiinterview.algorithmworker.observability;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;

class AlgorithmJudgeMetricsTest {
    private SimpleMeterRegistry registry;
    private AlgorithmJudgeMetrics metrics;

    @BeforeEach
    void setUp() {
        registry = new SimpleMeterRegistry();
        metrics = new AlgorithmJudgeMetrics(registry, mock(StringRedisTemplate.class));
    }

    @Test
    void recordsClaimsOutcomesRetriesAndErrorsWithBoundedTags() {
        metrics.recordClaim(false);
        metrics.recordClaim(true);
        metrics.recordOutcome("ACCEPTED");
        metrics.recordOutcome("SYSTEM_ERROR");
        metrics.recordRetry();
        metrics.recordError("sandbox");

        assertEquals(1.0, registry.get("ai_interview_algorithm_judge_claims_total")
                .tag("recovery", "new").counter().count());
        assertEquals(1.0, registry.get("ai_interview_algorithm_judge_claims_total")
                .tag("recovery", "stale").counter().count());
        assertEquals(1.0, registry.get("ai_interview_algorithm_judge_outcomes_total")
                .tag("status", "accepted").counter().count());
        assertEquals(1.0, registry.get("ai_interview_algorithm_judge_retries_total")
                .counter().count());
        assertEquals(1.0, registry.get("ai_interview_algorithm_judge_errors_total")
                .tag("stage", "sandbox").counter().count());
    }

    @Test
    void tracksActiveExecutionAndDuration() {
        var sample = metrics.startExecution();
        assertEquals(1.0, registry.get("ai_interview_algorithm_judge_active").gauge().value());

        metrics.stopExecution(sample);

        assertEquals(0.0, registry.get("ai_interview_algorithm_judge_active").gauge().value());
        assertEquals(1.0, registry.get("ai_interview_algorithm_judge_execution_duration")
                .timer().count());
    }
}
