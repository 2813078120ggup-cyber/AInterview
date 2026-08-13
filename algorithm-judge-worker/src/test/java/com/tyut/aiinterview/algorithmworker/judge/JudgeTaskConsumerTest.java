package com.tyut.aiinterview.algorithmworker.judge;

import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.tyut.aiinterview.algorithmworker.AlgorithmJudgeTaskService;
import com.tyut.aiinterview.algorithmworker.AlgorithmJudgeWorkerService;
import com.tyut.aiinterview.algorithmworker.config.AlgorithmJudgeProperties;
import com.tyut.aiinterview.algorithmworker.observability.AlgorithmJudgeMetrics;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;

class JudgeTaskConsumerTest {
    private AlgorithmJudgeWorkerService workerService;
    private JudgeTaskConsumer consumer;

    @BeforeEach
    void setUp() {
        workerService = mock(AlgorithmJudgeWorkerService.class);
        consumer = new JudgeTaskConsumer(
                mock(StringRedisTemplate.class),
                mock(AlgorithmJudgeTaskService.class),
                workerService,
                new AlgorithmJudgeProperties(),
                mock(AlgorithmJudgeMetrics.class));
    }

    @Test
    void retriesTransientWorkerFailureOnce() {
        doThrow(new IllegalStateException("temporary"))
                .doNothing()
                .when(workerService).judgeSubmission(42L, false);

        consumer.processWithRetry(42L, false);

        verify(workerService, times(2)).judgeSubmission(42L, false);
        verify(workerService, never()).markSystemError(42L, "temporary");
    }

    @Test
    void marksSystemErrorAfterRetryIsExhausted() {
        doThrow(new IllegalStateException("permanent"))
                .when(workerService).judgeSubmission(42L, true);

        consumer.processWithRetry(42L, true);

        verify(workerService, times(2)).judgeSubmission(42L, true);
        verify(workerService).markSystemError(42L, "permanent");
    }
}
