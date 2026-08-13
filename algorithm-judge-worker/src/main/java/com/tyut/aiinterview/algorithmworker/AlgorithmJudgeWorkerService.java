package com.tyut.aiinterview.algorithmworker;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.tyut.aiinterview.algorithmworker.config.AlgorithmJudgeProperties;
import com.tyut.aiinterview.algorithmworker.domain.AlgorithmCaseResult;
import com.tyut.aiinterview.algorithmworker.domain.AlgorithmProblem;
import com.tyut.aiinterview.algorithmworker.domain.AlgorithmSubmission;
import com.tyut.aiinterview.algorithmworker.domain.AlgorithmTestCase;
import com.tyut.aiinterview.algorithmworker.judge.DockerJavaSandbox;
import com.tyut.aiinterview.algorithmworker.judge.archive.JudgeInputArchiveWriter;
import com.tyut.aiinterview.algorithmworker.mapper.AlgorithmProblemMapper;
import com.tyut.aiinterview.algorithmworker.mapper.AlgorithmSubmissionMapper;
import com.tyut.aiinterview.algorithmworker.mapper.AlgorithmTestCaseMapper;
import com.tyut.aiinterview.algorithmworker.observability.AlgorithmJudgeMetrics;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import io.micrometer.core.instrument.Timer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/** Owns all Docker execution and asynchronous submission state transitions. */
@Service
public class AlgorithmJudgeWorkerService {
    private static final Logger log = LoggerFactory.getLogger(AlgorithmJudgeWorkerService.class);
    private static final int MESSAGE_LIMIT = 4000;
    private static final String SUBMIT = "SUBMIT";
    private static final String ACCEPTED = "ACCEPTED";
    private static final String SYSTEM_ERROR = "SYSTEM_ERROR";
    private static final String COMPILING = "COMPILING";
    private static final String RUNNING = "RUNNING";
    private static final String COMPILE_ERROR = "COMPILE_ERROR";
    /** Must be longer than the maximum configured sandbox execution window. */
    public static final Duration STALE_CLAIM_TIMEOUT = Duration.ofMinutes(10);

    private final AlgorithmProblemMapper problemMapper;
    private final AlgorithmSubmissionMapper submissionMapper;
    private final AlgorithmTestCaseMapper testCaseMapper;
    private final AlgorithmJudgeProperties properties;
    private final DockerJavaSandbox sandbox;
    private final AlgorithmJudgeResultPersistenceService resultPersistenceService;
    private final AlgorithmJudgeMetrics metrics;

    public AlgorithmJudgeWorkerService(AlgorithmProblemMapper problemMapper,
                                       AlgorithmSubmissionMapper submissionMapper,
                                       AlgorithmTestCaseMapper testCaseMapper,
                                       AlgorithmJudgeProperties properties,
                                       DockerJavaSandbox sandbox,
                                       AlgorithmJudgeResultPersistenceService resultPersistenceService,
                                       AlgorithmJudgeMetrics metrics) {
        this.problemMapper = problemMapper;
        this.submissionMapper = submissionMapper;
        this.testCaseMapper = testCaseMapper;
        this.properties = properties;
        this.sandbox = sandbox;
        this.resultPersistenceService = resultPersistenceService;
        this.metrics = metrics;
    }

    public RunResponse run(String sourceCode, String input, int timeLimitMs, int memoryLimitMb) {
        if (sourceCode == null || sourceCode.isBlank()) {
            return new RunResponse("SYSTEM_ERROR", "", "代码不能为空", 0L, 0L);
        }
        if (sourceCode.length() > properties.getSourceLimitChars()) {
            return new RunResponse("SYSTEM_ERROR", "", "代码长度超过限制", 0L, 0L);
        }
        try {
            Timer.Sample sample = metrics.startExecution();
            DockerJavaSandbox.SandboxResult execution;
            try {
                execution = sandbox.execute(sourceCode,
                        List.of(new JudgeInputArchiveWriter.TestCaseData(input == null ? "" : input, "")),
                        timeLimitMs, memoryLimitMb, false);
            } finally {
                metrics.stopExecution(sample);
            }
            if (execution.systemError() != null) {
                return new RunResponse(SYSTEM_ERROR, "", execution.systemError(), 0L, 0L);
            }
            String output = execution.cases().isEmpty() ? "" : execution.cases().get(0).stdout();
            String error = null;
            if (!ACCEPTED.equals(execution.status())) {
                String detail = COMPILE_ERROR.equals(execution.status())
                        ? execution.compileMessage()
                        : execution.cases().isEmpty() ? "" : execution.cases().get(0).stderr();
                error = truncate(detail == null || detail.isBlank() ? execution.status() : detail);
            }
            return new RunResponse(execution.status(), output, error,
                    execution.maxTimeMs() > 0 ? execution.maxTimeMs() : 0L,
                    execution.maxMemoryKb() > 0 ? execution.maxMemoryKb() : 0L);
        } catch (Exception exception) {
            log.error("synchronous algorithm run failed", exception);
            return new RunResponse(SYSTEM_ERROR, "", truncate(exception.getMessage()), 0L, 0L);
        }
    }

    public void judgeSubmission(Long submissionId) {
        judgeSubmission(submissionId, false);
    }

    /**
     * Judges a queued submission, or a stale claimed submission when Redis has
     * reclaimed a message after a worker crash.
     */
    public void judgeSubmission(Long submissionId, boolean recoverStale) {
        AlgorithmSubmission submission = submissionMapper.selectById(submissionId);
        if (submission == null || !SUBMIT.equals(submission.getSubmitType())) return;
        LocalDateTime claimedAt = LocalDateTime.now();
        int claimed;
        if ("QUEUED".equals(submission.getStatus())) {
            claimed = submissionMapper.claimQueued(submissionId, COMPILING, claimedAt);
        } else if (recoverStale) {
            claimed = submissionMapper.reclaimStale(submissionId, COMPILING, claimedAt,
                    claimedAt.minus(STALE_CLAIM_TIMEOUT));
        } else {
            return;
        }
        if (claimed != 1) return;
        metrics.recordClaim(recoverStale);
        submission.setStatus(COMPILING);
        submission.setStartedAt(claimedAt);
        AlgorithmProblem problem = problemMapper.selectById(submission.getProblemId());
        if (problem == null || !Integer.valueOf(1).equals(problem.getStatus())) {
            metrics.recordError("validation");
            resultPersistenceService.markSystemError(submission, "题目不存在或已停用");
            return;
        }
        List<AlgorithmTestCase> cases = testCaseMapper.selectList(new LambdaQueryWrapper<AlgorithmTestCase>()
                .eq(AlgorithmTestCase::getProblemId, problem.getId())
                .eq(AlgorithmTestCase::getEnabled, 1)
                .orderByAsc(AlgorithmTestCase::getSortNo)
                .orderByAsc(AlgorithmTestCase::getId));
        if (cases.isEmpty()) {
            metrics.recordError("validation");
            resultPersistenceService.markSystemError(submission, "题目暂无启用的测试用例");
            return;
        }
        if (cases.size() > properties.getMaxTestCases()) {
            metrics.recordError("validation");
            resultPersistenceService.markSystemError(submission, "测试用例数量超过上限");
            return;
        }
        List<JudgeInputArchiveWriter.TestCaseData> inputs = cases.stream()
                .map(testCase -> new JudgeInputArchiveWriter.TestCaseData(
                        testCase.getInputData() == null ? "" : testCase.getInputData(),
                        testCase.getExpectedOutput()))
                .toList();
        DockerJavaSandbox.SandboxResult execution;
        try {
            Timer.Sample sample = metrics.startExecution();
            try {
                execution = sandbox.execute(submission.getSourceCode(), inputs,
                        problem.getTimeLimitMs(), problem.getMemoryLimitMb(), true);
            } finally {
                metrics.stopExecution(sample);
            }
        } catch (Exception exception) {
            metrics.recordError("sandbox");
            resultPersistenceService.markSystemError(submission, exception.getMessage());
            return;
        }
        if (execution.systemError() != null || SYSTEM_ERROR.equals(execution.status())) {
            metrics.recordError("sandbox");
            resultPersistenceService.markSystemError(submission, execution.systemError() == null
                    ? detailOf(execution) : execution.systemError());
            return;
        }
        if (COMPILE_ERROR.equals(execution.status())) {
            resultPersistenceService.finish(submission, COMPILE_ERROR, 0, cases.size(), 0, null,
                    truncate(detailOf(execution)), null, List.of());
            return;
        }
        submission.setStatus(RUNNING);
        submissionMapper.updateById(submission);
        int passed = 0;
        int score = 0;
        String lastError = null;
        List<AlgorithmCaseResult> results = new ArrayList<>();
        for (int index = 0; index < execution.cases().size() && index < cases.size(); index++) {
            AlgorithmTestCase testCase = cases.get(index);
            DockerJavaSandbox.CaseResult runResult = execution.cases().get(index);
            AlgorithmCaseResult result = new AlgorithmCaseResult();
            result.setSubmissionId(submissionId);
            result.setTestCaseId(testCase.getId());
            result.setStatus(runResult.status());
            result.setActualOutput("SAMPLE".equals(testCase.getCaseType()) ? runResult.stdout() : null);
            result.setExecutionTimeMs(runResult.timeMs() > 0 ? runResult.timeMs() : null);
            result.setMemoryUsageKb(runResult.memoryKb() > 0 ? runResult.memoryKb() : null);
            result.setCreatedAt(LocalDateTime.now());
            results.add(result);
            if (ACCEPTED.equals(runResult.status())) {
                passed++;
                score += testCase.getScore() == null ? 0 : testCase.getScore();
            } else {
                String detail = runResult.stderr() == null || runResult.stderr().isBlank()
                        ? runResult.stdout() : runResult.stderr();
                if (detail != null && !detail.isBlank()) lastError = truncate(detail);
            }
        }
        resultPersistenceService.finish(submission, execution.status(), passed, cases.size(), score,
                execution.maxTimeMs() > 0 ? execution.maxTimeMs() : null,
                null, lastError, results);
    }

    public void markSystemError(Long submissionId, String message) {
        AlgorithmSubmission submission = submissionMapper.selectById(submissionId);
        if (submission != null) resultPersistenceService.markSystemError(submission, message);
    }

    private static String detailOf(DockerJavaSandbox.SandboxResult execution) {
        return execution.compileMessage() == null || execution.compileMessage().isBlank()
                ? "编译失败" : execution.compileMessage();
    }

    private static String truncate(String value) {
        if (value == null) return null;
        return value.length() <= MESSAGE_LIMIT ? value : value.substring(0, MESSAGE_LIMIT);
    }

    public record RunResponse(String status, String output, String errorMessage,
                              Long executionTimeMs, Long memoryUsageKb) {}
}
