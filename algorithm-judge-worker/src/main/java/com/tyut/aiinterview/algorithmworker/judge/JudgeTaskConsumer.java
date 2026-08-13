package com.tyut.aiinterview.algorithmworker.judge;

import com.tyut.aiinterview.algorithmworker.AlgorithmJudgeTaskService;
import com.tyut.aiinterview.algorithmworker.AlgorithmJudgeWorkerService;
import com.tyut.aiinterview.algorithmworker.config.AlgorithmJudgeProperties;
import com.tyut.aiinterview.algorithmworker.observability.AlgorithmJudgeMetrics;
import java.time.Duration;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.SmartLifecycle;
import org.springframework.data.domain.Range;
import org.springframework.data.redis.connection.stream.Consumer;
import org.springframework.data.redis.connection.stream.MapRecord;
import org.springframework.data.redis.connection.stream.PendingMessage;
import org.springframework.data.redis.connection.stream.PendingMessages;
import org.springframework.data.redis.connection.stream.ReadOffset;
import org.springframework.data.redis.connection.stream.RecordId;
import org.springframework.data.redis.connection.stream.StreamOffset;
import org.springframework.data.redis.connection.stream.StreamReadOptions;
import org.springframework.data.redis.core.StreamOperations;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

/** Redis Stream consumer; the only process that executes asynchronous submissions. */
@Component
public class JudgeTaskConsumer implements SmartLifecycle {
    private static final Logger log = LoggerFactory.getLogger(JudgeTaskConsumer.class);
    private final StringRedisTemplate redisTemplate;
    private final AlgorithmJudgeTaskService taskService;
    private final AlgorithmJudgeWorkerService workerService;
    private final AlgorithmJudgeProperties properties;
    private final AlgorithmJudgeMetrics metrics;
    private volatile boolean running;
    private Thread worker;

    public JudgeTaskConsumer(StringRedisTemplate redisTemplate,
                             AlgorithmJudgeTaskService taskService,
                             AlgorithmJudgeWorkerService workerService,
                             AlgorithmJudgeProperties properties,
                             AlgorithmJudgeMetrics metrics) {
        this.redisTemplate = redisTemplate;
        this.taskService = taskService;
        this.workerService = workerService;
        this.properties = properties;
        this.metrics = metrics;
    }

    @Override
    public void start() {
        if (!properties.isEnabled() || !properties.isConsumerEnabled()) {
            log.info("algorithm judge consumer disabled");
            return;
        }
        taskService.ensureGroup();
        running = true;
        worker = new Thread(this::loop, "algorithm-judge-worker-consumer");
        worker.setDaemon(true);
        worker.start();
        log.info("algorithm judge consumer started: {}", consumerName());
    }

    @Override
    public void stop() {
        running = false;
        if (worker != null) worker.interrupt();
    }

    @Override
    public boolean isRunning() { return running; }

    @Override
    public boolean isAutoStartup() { return properties.isEnabled() && properties.isConsumerEnabled(); }

    @Override
    public int getPhase() { return Integer.MAX_VALUE; }

    private void loop() {
        StreamOperations<String, Object, Object> stream = redisTemplate.opsForStream();
        Consumer consumer = Consumer.from(AlgorithmJudgeTaskService.GROUP, consumerName());
        while (running) {
            try {
                List<MapRecord<String, Object, Object>> records = stream.read(consumer,
                        StreamReadOptions.empty().count(1).block(Duration.ofSeconds(2)),
                        StreamOffset.create(AlgorithmJudgeTaskService.STREAM, ReadOffset.lastConsumed()));
                if (records == null || records.isEmpty()) {
                    reclaimPending(stream, consumer);
                    continue;
                }
                for (MapRecord<String, Object, Object> record : records) {
                    Long submissionId = parseId(record.getValue().get("submissionId"));
                    if (submissionId != null) processWithRetry(submissionId, false);
                    stream.acknowledge(AlgorithmJudgeTaskService.STREAM,
                            AlgorithmJudgeTaskService.GROUP, record.getId());
                }
            } catch (Exception exception) {
                metrics.recordError("stream");
                log.warn("judge consumer loop error: {}", exception.getMessage());
                sleepQuietly(1000);
            }
        }
    }

    void processWithRetry(Long submissionId, boolean recoverStale) {
        try {
            workerService.judgeSubmission(submissionId, recoverStale);
        } catch (Exception first) {
            metrics.recordRetry();
            metrics.recordError("consumer_retry");
            log.error("judge submission {} failed, retry once", submissionId, first);
            sleepQuietly(1000);
            try {
                workerService.judgeSubmission(submissionId, recoverStale);
            } catch (Exception second) {
                metrics.recordError("consumer_exhausted");
                log.error("judge submission {} failed after retry", submissionId, second);
                workerService.markSystemError(submissionId, second.getMessage());
            }
        }
    }

    private void reclaimPending(StreamOperations<String, Object, Object> stream, Consumer consumer) {
        try {
            PendingMessages pending = stream.pending(AlgorithmJudgeTaskService.STREAM,
                    AlgorithmJudgeTaskService.GROUP, Range.<String>unbounded(), 20L);
            for (int index = 0; index < pending.size(); index++) {
                PendingMessage message = pending.get(index);
                Duration idle = message.getElapsedTimeSinceLastDelivery();
                if (idle == null || idle.compareTo(AlgorithmJudgeWorkerService.STALE_CLAIM_TIMEOUT) < 0) continue;
                RecordId id = message.getId();
                List<MapRecord<String, Object, Object>> records = stream.claim(
                        AlgorithmJudgeTaskService.STREAM, AlgorithmJudgeTaskService.GROUP,
                        consumerName(), AlgorithmJudgeWorkerService.STALE_CLAIM_TIMEOUT, id);
                Long submissionId = records == null || records.isEmpty()
                        ? null : parseId(records.get(0).getValue().get("submissionId"));
                if (submissionId != null) processWithRetry(submissionId, true);
                stream.acknowledge(AlgorithmJudgeTaskService.STREAM,
                        AlgorithmJudgeTaskService.GROUP, id);
            }
        } catch (Exception exception) {
            log.debug("reclaim pending skipped: {}", exception.getMessage());
        }
    }

    private String consumerName() {
        String configured = properties.getConsumerName();
        return configured == null || configured.isBlank()
                ? "judge-consumer-" + System.getenv().getOrDefault("HOSTNAME", "1") : configured.trim();
    }

    private static Long parseId(Object value) {
        if (value == null) return null;
        try { return Long.valueOf(value.toString()); }
        catch (NumberFormatException exception) { return null; }
    }

    private static void sleepQuietly(long millis) {
        try { Thread.sleep(millis); }
        catch (InterruptedException interrupted) { Thread.currentThread().interrupt(); }
    }
}
