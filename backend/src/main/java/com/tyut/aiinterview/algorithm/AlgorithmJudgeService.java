package com.tyut.aiinterview.algorithm;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.tyut.aiinterview.algorithm.config.AlgorithmJudgeProperties;
import com.tyut.aiinterview.algorithm.judge.DockerJavaSandbox;
import com.tyut.aiinterview.algorithm.judge.archive.JudgeInputArchiveWriter;
import com.tyut.aiinterview.common.BusinessException;
import com.tyut.aiinterview.domain.AlgorithmCaseResult;
import com.tyut.aiinterview.domain.AlgorithmProblem;
import com.tyut.aiinterview.domain.AlgorithmSubmission;
import com.tyut.aiinterview.domain.AlgorithmTestCase;
import com.tyut.aiinterview.domain.AlgorithmUserProgress;
import com.tyut.aiinterview.mapper.AlgorithmCaseResultMapper;
import com.tyut.aiinterview.mapper.AlgorithmProblemMapper;
import com.tyut.aiinterview.mapper.AlgorithmSubmissionMapper;
import com.tyut.aiinterview.mapper.AlgorithmTestCaseMapper;
import com.tyut.aiinterview.mapper.AlgorithmUserProgressMapper;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 判题核心：运行（同步，自定义输入）与提交（异步，全部隐藏用例）。
 */
@Service
public class AlgorithmJudgeService {
    private static final Logger log = LoggerFactory.getLogger(AlgorithmJudgeService.class);
    private static final String JAVA_LANGUAGE = "JAVA17";
    private static final int MESSAGE_LIMIT = 4000;

    private final AlgorithmProblemMapper problemMapper;
    private final AlgorithmSubmissionMapper submissionMapper;
    private final AlgorithmTestCaseMapper testCaseMapper;
    private final AlgorithmCaseResultMapper caseResultMapper;
    private final AlgorithmUserProgressMapper progressMapper;
    private final AlgorithmJudgeProperties properties;
    private final DockerJavaSandbox sandbox;

    public AlgorithmJudgeService(AlgorithmProblemMapper problemMapper,
                                 AlgorithmSubmissionMapper submissionMapper,
                                 AlgorithmTestCaseMapper testCaseMapper,
                                 AlgorithmCaseResultMapper caseResultMapper,
                                 AlgorithmUserProgressMapper progressMapper,
                                 AlgorithmJudgeProperties properties,
                                 DockerJavaSandbox sandbox) {
        this.problemMapper = problemMapper;
        this.submissionMapper = submissionMapper;
        this.testCaseMapper = testCaseMapper;
        this.caseResultMapper = caseResultMapper;
        this.progressMapper = progressMapper;
        this.properties = properties;
        this.sandbox = sandbox;
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

        DockerJavaSandbox.SandboxResult execution;
        try {
            execution = sandbox.execute(request.sourceCode(),
                    List.of(new JudgeInputArchiveWriter.TestCaseData(
                            request.input() == null ? "" : request.input(), "")),
                    problem.getTimeLimitMs(), problem.getMemoryLimitMb(), false);
        } catch (Exception exception) {
            log.error("algorithm run failed: submissionId={}", submission.getId(), exception);
            submission.setStatus(AlgorithmSubmissionStatus.SYSTEM_ERROR.name());
            submission.setRuntimeMessage(truncate(exception.getMessage()));
            submission.setFinishedAt(LocalDateTime.now());
            submissionMapper.updateById(submission);
            return new AlgorithmDtos.RunResponse(submission.getId(),
                    AlgorithmSubmissionStatus.SYSTEM_ERROR.name(), "", exception.getMessage(), null, null);
        }
        if (execution.systemError() != null) {
            submission.setStatus(AlgorithmSubmissionStatus.SYSTEM_ERROR.name());
            submission.setRuntimeMessage(truncate(execution.systemError()));
            submission.setFinishedAt(LocalDateTime.now());
            submissionMapper.updateById(submission);
            return new AlgorithmDtos.RunResponse(submission.getId(),
                    AlgorithmSubmissionStatus.SYSTEM_ERROR.name(), "", execution.systemError(), null, null);
        }
        String status = execution.status();
        String output = execution.cases().isEmpty() ? "" : execution.cases().get(0).stdout();
        String error = null;
        if (!AlgorithmSubmissionStatus.ACCEPTED.name().equals(status)) {
            String detail = AlgorithmSubmissionStatus.COMPILE_ERROR.name().equals(status)
                    ? detailOf(execution)
                    : (execution.cases().isEmpty() ? "" : execution.cases().get(0).stderr());
            error = truncate(detail.isBlank() ? statusText(status) : detail);
        }
        submission.setStatus(status);
        if (AlgorithmSubmissionStatus.COMPILE_ERROR.name().equals(status)) {
            submission.setCompileMessage(truncate(detailOf(execution)));
        }
        submission.setExecutionTimeMs(execution.maxTimeMs() > 0 ? execution.maxTimeMs() : null);
        submission.setMemoryUsageKb(execution.maxMemoryKb() > 0 ? execution.maxMemoryKb() : null);
        submission.setRuntimeMessage(error);
        submission.setFinishedAt(LocalDateTime.now());
        submissionMapper.updateById(submission);
        return new AlgorithmDtos.RunResponse(submission.getId(), status, output,
                error, submission.getExecutionTimeMs(), submission.getMemoryUsageKb());
    }

    @Transactional
    public void judgeSubmission(Long submissionId) {
        AlgorithmSubmission submission = submissionMapper.selectById(submissionId);
        if (submission == null) return;
        if (!AlgorithmSubmitType.SUBMIT.name().equals(submission.getSubmitType())) return;

        AlgorithmProblem problem = problemMapper.selectById(submission.getProblemId());
        if (problem == null || problem.getStatus() == null || problem.getStatus() != 1) {
            markSystemError(submission, "题目不存在或已停用");
            return;
        }
        List<AlgorithmTestCase> cases = testCaseMapper.selectList(
                new LambdaQueryWrapper<AlgorithmTestCase>()
                        .eq(AlgorithmTestCase::getProblemId, problem.getId())
                        .eq(AlgorithmTestCase::getEnabled, 1)
                        .orderByAsc(AlgorithmTestCase::getSortNo)
                        .orderByAsc(AlgorithmTestCase::getId));
        if (cases.isEmpty()) {
            markSystemError(submission, "题目暂无启用的测试用例");
            return;
        }
        if (cases.size() > properties.getMaxTestCases()) {
            markSystemError(submission, "测试用例数量超过上限");
            return;
        }

        submission.setStatus(AlgorithmSubmissionStatus.COMPILING.name());
        submission.setStartedAt(LocalDateTime.now());
        submissionMapper.updateById(submission);

        List<JudgeInputArchiveWriter.TestCaseData> inputs = cases.stream()
                .map(testCase -> new JudgeInputArchiveWriter.TestCaseData(
                        testCase.getInputData() == null ? "" : testCase.getInputData(),
                        testCase.getExpectedOutput()))
                .toList();
        DockerJavaSandbox.SandboxResult execution;
        try {
            execution = sandbox.execute(submission.getSourceCode(), inputs,
                    problem.getTimeLimitMs(), problem.getMemoryLimitMb(), true);
        } catch (Exception exception) {
            markSystemError(submission, exception.getMessage());
            return;
        }
        if (execution.systemError() != null || AlgorithmSubmissionStatus.SYSTEM_ERROR.name().equals(execution.status())) {
            markSystemError(submission, execution.systemError() == null
                    ? detailOf(execution) : execution.systemError());
            return;
        }
        if (AlgorithmSubmissionStatus.COMPILE_ERROR.name().equals(execution.status())) {
            finish(submission, AlgorithmSubmissionStatus.COMPILE_ERROR.name(), 0, cases.size(), 0,
                    null, truncate(detailOf(execution)), null, cases, List.of());
            return;
        }

        submission.setStatus(AlgorithmSubmissionStatus.RUNNING.name());
        submissionMapper.updateById(submission);

        int passed = 0;
        int score = 0;
        String firstFailure = null;
        String lastError = null;
        List<AlgorithmCaseResult> results = new ArrayList<>();
        for (int index = 0; index < execution.cases().size() && index < cases.size(); index++) {
            AlgorithmTestCase testCase = cases.get(index);
            DockerJavaSandbox.CaseResult runResult = execution.cases().get(index);
            String caseStatus = runResult.status();

            AlgorithmCaseResult result = new AlgorithmCaseResult();
            result.setSubmissionId(submissionId);
            result.setTestCaseId(testCase.getId());
            result.setStatus(caseStatus);
            result.setActualOutput(AlgorithmCaseType.SAMPLE.name().equals(testCase.getCaseType())
                    ? runResult.stdout() : null);
            result.setExecutionTimeMs(runResult.timeMs() > 0 ? runResult.timeMs() : null);
            result.setMemoryUsageKb(runResult.memoryKb() > 0 ? runResult.memoryKb() : null);
            result.setCreatedAt(LocalDateTime.now());
            results.add(result);

            if (AlgorithmSubmissionStatus.ACCEPTED.name().equals(caseStatus)) {
                passed++;
                score += testCase.getScore() == null ? 0 : testCase.getScore();
            } else {
                if (firstFailure == null) firstFailure = caseStatus;
                String detail = runResult.stderr() == null || runResult.stderr().isBlank()
                        ? runResult.stdout() : runResult.stderr();
                if (!detail.isBlank()) lastError = truncate(detail);
            }
        }
        finish(submission, execution.status(), passed, cases.size(), score,
                execution.maxTimeMs() > 0 ? execution.maxTimeMs() : null,
                null, lastError, cases, results);
    }

    public void markSystemError(Long submissionId, String message) {
        AlgorithmSubmission submission = submissionMapper.selectById(submissionId);
        if (submission != null) {
            markSystemError(submission, message);
        }
    }

    private void finish(AlgorithmSubmission submission, String status, int passed, int total, int score,
                        Long maxTime, String compileMessage, String runtimeMessage,
                        List<AlgorithmTestCase> cases, List<AlgorithmCaseResult> results) {
        for (AlgorithmCaseResult result : results) {
            caseResultMapper.insert(result);
        }
        submission.setStatus(status);
        submission.setScore(score);
        submission.setPassedCount(passed);
        submission.setTotalCount(total);
        submission.setExecutionTimeMs(maxTime);
        submission.setCompileMessage(compileMessage);
        submission.setRuntimeMessage(runtimeMessage);
        submission.setFinishedAt(LocalDateTime.now());
        submissionMapper.updateById(submission);

        boolean accepted = AlgorithmSubmissionStatus.ACCEPTED.name().equals(status);
        AlgorithmUserProgress progress = progressMapper.selectOne(
                new LambdaQueryWrapper<AlgorithmUserProgress>()
                        .eq(AlgorithmUserProgress::getUserId, submission.getUserId())
                        .eq(AlgorithmUserProgress::getProblemId, submission.getProblemId()));
        LocalDateTime now = LocalDateTime.now();
        if (progress == null) {
            progress = new AlgorithmUserProgress();
            progress.setUserId(submission.getUserId());
            progress.setProblemId(submission.getProblemId());
            progress.setProgressStatus(accepted
                    ? AlgorithmProgressStatus.ACCEPTED.name()
                    : AlgorithmProgressStatus.ATTEMPTED.name());
            progress.setSubmitCount(0);
            progress.setCreatedAt(now);
        } else if (accepted) {
            progress.setProgressStatus(AlgorithmProgressStatus.ACCEPTED.name());
            if (progress.getFirstAcceptedAt() == null) {
                progress.setFirstAcceptedAt(now);
            }
        } else if (!AlgorithmProgressStatus.ACCEPTED.name().equals(progress.getProgressStatus())) {
            progress.setProgressStatus(AlgorithmProgressStatus.ATTEMPTED.name());
        }
        progress.setSubmitCount((progress.getSubmitCount() == null ? 0 : progress.getSubmitCount()) + 1);
        progress.setLastSubmittedAt(now);
        if (accepted && submission.getExecutionTimeMs() != null
                && (progress.getBestExecutionTimeMs() == null
                    || submission.getExecutionTimeMs() < progress.getBestExecutionTimeMs())) {
            progress.setBestExecutionTimeMs(submission.getExecutionTimeMs());
        }
        if (progress.getId() == null) {
            progressMapper.insert(progress);
        } else {
            progressMapper.updateById(progress);
        }
    }

    private void markSystemError(AlgorithmSubmission submission, String message) {
        submission.setStatus(AlgorithmSubmissionStatus.SYSTEM_ERROR.name());
        submission.setRuntimeMessage(truncate(message));
        submission.setFinishedAt(LocalDateTime.now());
        submissionMapper.updateById(submission);
    }

    private static String detailOf(DockerJavaSandbox.SandboxResult execution) {
        return execution.compileMessage() == null || execution.compileMessage().isBlank()
                ? "编译失败" : execution.compileMessage();
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
