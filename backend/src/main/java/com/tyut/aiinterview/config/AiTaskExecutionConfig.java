package com.tyut.aiinterview.config;

import java.util.concurrent.Executor;
import java.util.concurrent.ThreadPoolExecutor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

@Configuration
public class AiTaskExecutionConfig {

    @Bean(name = "aiTaskWorkerExecutor")
    public Executor aiTaskWorkerExecutor(
            @Value("${app.ai-task.worker-count:3}") int workerCount,
            @Value("${app.ai-task.worker-queue-capacity:50}") int queueCapacity) {
        return executor("ai-task-worker-", workerCount, queueCapacity);
    }

    @Bean(name = "reportScoringExecutor")
    public Executor reportScoringExecutor(
            @Value("${app.ai-task.report-scoring-concurrency:3}") int concurrency,
            @Value("${app.ai-task.report-scoring-queue-capacity:50}") int queueCapacity) {
        return executor("report-scoring-", concurrency, queueCapacity);
    }

    @Bean(name = "aiGovernanceEvaluationExecutor")
    public Executor aiGovernanceEvaluationExecutor(
            @Value("${app.ai-governance.evaluation-concurrency:1}") int concurrency,
            @Value("${app.ai-governance.evaluation-queue-capacity:5}") int queueCapacity) {
        return executor("ai-governance-eval-", concurrency, queueCapacity);
    }

    private ThreadPoolTaskExecutor executor(String threadPrefix, int requestedSize, int requestedQueueCapacity) {
        int size = Math.max(1, Math.min(8, requestedSize));
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(size);
        executor.setMaxPoolSize(size);
        executor.setQueueCapacity(Math.max(1, requestedQueueCapacity));
        executor.setThreadNamePrefix(threadPrefix);
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(30);
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        return executor;
    }
}
