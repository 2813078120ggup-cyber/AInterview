package com.tyut.aiinterview.algorithm;

import java.util.Map;
import org.springframework.data.redis.connection.stream.ReadOffset;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

/**
 * 判题任务发布：提交代码后写入 Redis Stream，由 Judge Consumer 异步消费。
 */
@Service
public class AlgorithmJudgeTaskService {
    public static final String STREAM = "algorithm:judge:stream";
    public static final String GROUP = "algorithm-judge-group";

    private final StringRedisTemplate redisTemplate;

    public AlgorithmJudgeTaskService(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    public void publish(Long submissionId) {
        ensureGroup();
        redisTemplate.opsForStream().add(
                org.springframework.data.redis.connection.stream.ObjectRecord.create(
                        STREAM, Map.of("submissionId", String.valueOf(submissionId))));
    }

    public void ensureGroup() {
        try {
            redisTemplate.opsForStream().createGroup(STREAM, ReadOffset.from("0"), GROUP);
        } catch (Exception ignored) {
            // 消费者组已存在
        }
    }
}
