package com.tyut.aiinterview.algorithm.judge;

import com.tyut.aiinterview.algorithm.AlgorithmJudgeService;
import com.tyut.aiinterview.algorithm.AlgorithmJudgeTaskService;
import com.tyut.aiinterview.algorithm.config.AlgorithmJudgeProperties;
import java.time.Duration;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.SmartLifecycle;
import org.springframework.data.domain.Range;
import org.springframework.data.redis.connection.Limit;
import org.springframework.data.redis.connection.stream.Consumer;
import org.springframework.data.redis.connection.stream.MapRecord;
import org.springframework.data.redis.connection.stream.PendingMessage;
import org.springframework.data.redis.connection.stream.PendingMessages;
import org.springframework.data.redis.connection.stream.ReadOffset;
import org.springframework.data.redis.connection.stream.RecordId;
import org.springframework.data.redis.connection.stream.StreamOffset;
import org.springframework.data.redis.connection.stream.StreamReadOptions;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.StreamOperations;
import org.springframework.stereotype.Component;

/**
 * Redis Stream 判题消费者：阻塞读取判题任务，逐个执行并确认。
 * 处理异常时重试一次，仍失败则将提交标记为 SYSTEM_ERROR。
 */
@Component
public class JudgeTaskConsumer implements SmartLifecycle {
    private static final Logger log = LoggerFactory.getLogger(JudgeTaskConsumer.class);
    private static final String CONSUMER = "judge-consumer-1";

    private final StringRedisTemplate redisTemplate;
    private final AlgorithmJudgeTaskService taskService;
    private final AlgorithmJudgeService judgeService;
    private final AlgorithmJudgeProperties properties;
    private volatile boolean running;
    private Thread worker;

    public JudgeTaskConsumer(StringRedisTemplate redisTemplate,
                             AlgorithmJudgeTaskService taskService,
                             AlgorithmJudgeService judgeService,
                             AlgorithmJudgeProperties properties) {
        this.redisTemplate = redisTemplate;
        this.taskService = taskService;
        this.judgeService = judgeService;
        this.properties = properties;
    }

    @Override
    public void start() {
        if (!properties.isEnabled() || !properties.isConsumerEnabled()) {
            log.info("algorithm judge consumer disabled");
            return;
        }
        taskService.ensureGroup();
        running = true;
        worker = new Thread(this::loop, "algorithm-judge-consumer");
        worker.setDaemon(true);
        worker.start();
        log.info("algorithm judge consumer started");
    }

    @Override
    public void stop() {
        running = false;
        if (worker != null) {
            worker.interrupt();
        }
    }

    @Override
    public boolean isRunning() {
        return running;
    }

    @Override
    public boolean isAutoStartup() {
        return properties.isEnabled() && properties.isConsumerEnabled();
    }

    @Override
    public int getPhase() {
        return Integer.MAX_VALUE;
    }

    private void loop() {
        StreamOperations<String, Object, Object> stream = redisTemplate.opsForStream();
        Consumer consumer = Consumer.from(AlgorithmJudgeTaskService.GROUP, CONSUMER);
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
                    if (submissionId != null) {
                        processWithRetry(submissionId);
                    }
                    stream.acknowledge(AlgorithmJudgeTaskService.STREAM, AlgorithmJudgeTaskService.GROUP,
                            record.getId());
                }
            } catch (Exception exception) {
                log.warn("judge consumer loop error: {}", exception.getMessage());
                sleepQuietly(1000);
            }
        }
    }

    private void processWithRetry(Long submissionId) {
        try {
            judgeService.judgeSubmission(submissionId);
        } catch (Exception first) {
            log.error("judge submission {} failed, retry once", submissionId, first);
            sleepQuietly(1000);
            try {
                judgeService.judgeSubmission(submissionId);
            } catch (Exception second) {
                log.error("judge submission {} failed after retry", submissionId, second);
                judgeService.markSystemError(submissionId, second.getMessage());
            }
        }
    }

    /**
     * 兜底：处理积压超过 2 分钟的未确认消息（上次崩溃/超时遗留）。
     */
    private void reclaimPending(StreamOperations<String, Object, Object> stream, Consumer consumer) {
        try {
            PendingMessages pending = stream.pending(AlgorithmJudgeTaskService.STREAM,
                    AlgorithmJudgeTaskService.GROUP, Range.<String>unbounded(), 20L);
            for (int index = 0; index < pending.size(); index++) {
                PendingMessage message = pending.get(index);
                Duration idle = message.getElapsedTimeSinceLastDelivery();
                if (idle == null || idle.compareTo(Duration.ofMinutes(2)) < 0) {
                    continue;
                }
                RecordId id = message.getId();
                List<MapRecord<String, Object, Object>> records = stream.range(
                        AlgorithmJudgeTaskService.STREAM, Range.closed(id.getValue(), id.getValue()));
                Long submissionId = records == null || records.isEmpty()
                        ? null : parseId(records.get(0).getValue().get("submissionId"));
                if (submissionId != null) {
                    processWithRetry(submissionId);
                }
                stream.acknowledge(AlgorithmJudgeTaskService.STREAM, AlgorithmJudgeTaskService.GROUP, id);
            }
        } catch (Exception exception) {
            log.debug("reclaim pending skipped: {}", exception.getMessage());
        }
    }

    private static Long parseId(Object value) {
        if (value == null) return null;
        try {
            return Long.valueOf(value.toString());
        } catch (NumberFormatException exception) {
            return null;
        }
    }

    private static void sleepQuietly(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
        }
    }
}
