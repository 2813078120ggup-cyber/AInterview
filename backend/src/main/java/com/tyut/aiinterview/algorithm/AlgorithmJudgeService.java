package com.tyut.aiinterview.algorithm;

import com.tyut.aiinterview.algorithm.config.AlgorithmJudgeProperties;
import com.tyut.aiinterview.common.BusinessException;
import com.tyut.aiinterview.domain.AlgorithmProblem;
import com.tyut.aiinterview.domain.AlgorithmSubmission;
import com.tyut.aiinterview.mapper.AlgorithmProblemMapper;
import com.tyut.aiinterview.mapper.AlgorithmSubmissionMapper;
import java.time.LocalDateTime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Backend-facing algorithm facade. Docker execution and async judging live in
 * algorithm-judge-worker; backend keeps authorization and submission records.
 */
@Service
public class AlgorithmJudgeService {
    private static final Logger log = LoggerFactory.getLogger(AlgorithmJudgeService.class);
    private static final String JAVA_LANGUAGE = "JAVA17";
    private static final int MESSAGE_LIMIT = 4000;

    private final AlgorithmProblemMapper problemMapper;
    private final AlgorithmSubmissionMapper submissionMapper;
    private final AlgorithmJudgeProperties properties;
    private final AlgorithmJudgeWorkerClient workerClient;

    public AlgorithmJudgeService(AlgorithmProblemMapper problemMapper,
                                 AlgorithmSubmissionMapper submissionMapper,
                                 AlgorithmJudgeProperties properties,
                                 AlgorithmJudgeWorkerClient workerClient) {
        this.problemMapper = problemMapper;
        this.submissionMapper = submissionMapper;
        this.properties = properties;
        this.workerClient = workerClient;
    }

    public AlgorithmDtos.RunResponse run(Long userId, AlgorithmDtos.RunRequest request) {
        AlgorithmProblem problem = requireEnabled(request.problemId());
        validateSource(request.sourceCode());
        String language = normalizeLanguage(request.language());

        AlgorithmSubmission submission = new AlgorithmSubmission();
        submission.setUserId(userId);
        submission.setProblemId(problem.getId());
        submission.setLanguage(language);
        submission.setSourceCode(request.sourceCode());
        submission.setSubmitType(AlgorithmSubmitType.RUN.name());
        submission.setStatus(AlgorithmSubmissionStatus.QUEUED.name());
        submissionMapper.insert(submission);

        AlgorithmJudgeWorkerClient.RunResult execution = workerClient.run(request.sourceCode(), request.input(),
                problem.getTimeLimitMs(), problem.getMemoryLimitMb());
        if (execution.systemError() != null) {
            log.error("algorithm run failed: submissionId={}, error={}", submission.getId(), execution.systemError());
            markSystemError(submission, execution.systemError());
            return new AlgorithmDtos.RunResponse(submission.getId(),
                    AlgorithmSubmissionStatus.SYSTEM_ERROR.name(), "", execution.systemError(), null, null);
        }

        String status = execution.status();
        if (status == null || status.isBlank()) {
            String message = execution.errorMessage() == null || execution.errorMessage().isBlank()
                    ? "判题 Worker 返回无效状态" : execution.errorMessage();
            markSystemError(submission, message);
            return new AlgorithmDtos.RunResponse(submission.getId(),
                    AlgorithmSubmissionStatus.SYSTEM_ERROR.name(), "", message, null, null);
        }
        String output = execution.output() == null ? "" : execution.output();
        String detail = execution.errorMessage();
        String error = null;
        if (!AlgorithmSubmissionStatus.ACCEPTED.name().equals(status)) {
            error = truncate(detail == null || detail.isBlank() ? statusText(status) : detail);
        }
        submission.setStatus(status);
        if (AlgorithmSubmissionStatus.COMPILE_ERROR.name().equals(status)) {
            submission.setCompileMessage(truncate(detail));
        }
        submission.setExecutionTimeMs(execution.executionTimeMs() > 0 ? execution.executionTimeMs() : null);
        submission.setMemoryUsageKb(execution.memoryUsageKb() > 0 ? execution.memoryUsageKb() : null);
        submission.setRuntimeMessage(error);
        submission.setFinishedAt(LocalDateTime.now());
        submissionMapper.updateById(submission);
        return new AlgorithmDtos.RunResponse(submission.getId(), status, output,
                error, submission.getExecutionTimeMs(), submission.getMemoryUsageKb());
    }

    private void markSystemError(AlgorithmSubmission submission, String message) {
        submission.setStatus(AlgorithmSubmissionStatus.SYSTEM_ERROR.name());
        submission.setRuntimeMessage(truncate(message));
        submission.setFinishedAt(LocalDateTime.now());
        submissionMapper.updateById(submission);
    }

    private AlgorithmProblem requireEnabled(Long problemId) {
        if (problemId == null) throw BusinessException.badRequest("题目 ID 不能为空");
        AlgorithmProblem problem = problemMapper.selectById(problemId);
        if (problem == null || problem.getStatus() == null || problem.getStatus() != 1) {
            throw BusinessException.notFound("题目不存在或已停用");
        }
        return problem;
    }

    private void validateSource(String sourceCode) {
        if (sourceCode == null || sourceCode.isBlank()) {
            throw BusinessException.badRequest("代码不能为空");
        }
        if (sourceCode.length() > properties.getSourceLimitChars()) {
            throw BusinessException.badRequest("代码长度超过限制");
        }
        if (!sourceCode.contains("class Main")) {
            throw BusinessException.badRequest("源码必须包含 public class Main");
        }
    }

    private String normalizeLanguage(String language) {
        return language == null || language.isBlank() ? JAVA_LANGUAGE : language;
    }

    private static String truncate(String value) {
        if (value == null) return null;
        return value.length() <= MESSAGE_LIMIT ? value : value.substring(0, MESSAGE_LIMIT);
    }

    private static String statusText(String status) {
        return switch (status) {
            case "TIME_LIMIT_EXCEEDED" -> "运行超时";
            case "MEMORY_LIMIT_EXCEEDED" -> "内存超限";
            case "OUTPUT_LIMIT_EXCEEDED" -> "输出超限";
            case "RUNTIME_ERROR" -> "运行错误";
            default -> "执行失败";
        };
    }
}
