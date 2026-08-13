package com.tyut.aiinterview.algorithmworker;

import java.util.Map;
import org.springframework.data.redis.connection.stream.ReadOffset;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

@Service
public class AlgorithmJudgeTaskService {
    public static final String STREAM = "algorithm:judge:stream";
    public static final String GROUP = "algorithm-judge-group";

    private final StringRedisTemplate redisTemplate;

    public AlgorithmJudgeTaskService(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    public void ensureGroup() {
        try {
            redisTemplate.opsForStream().createGroup(STREAM, ReadOffset.from("0"), GROUP);
        } catch (Exception ignored) {
            // The group already exists in the shared Redis instance.
        }
    }
}
