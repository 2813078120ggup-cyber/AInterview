package com.tyut.aiinterview.algorithm;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.tyut.aiinterview.common.BusinessException;
import com.tyut.aiinterview.common.PageResult;
import com.tyut.aiinterview.domain.AlgorithmCaseResult;
import com.tyut.aiinterview.domain.AlgorithmProblem;
import com.tyut.aiinterview.domain.AlgorithmSubmission;
import com.tyut.aiinterview.domain.AlgorithmTestCase;
import com.tyut.aiinterview.mapper.AlgorithmCaseResultMapper;
import com.tyut.aiinterview.mapper.AlgorithmProblemMapper;
import com.tyut.aiinterview.mapper.AlgorithmSubmissionMapper;
import com.tyut.aiinterview.mapper.AlgorithmTestCaseMapper;
import com.tyut.aiinterview.mapper.WrongProblemRow;
import com.tyut.aiinterview.security.CurrentUser;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class AlgorithmSubmissionService {
    private final AlgorithmSubmissionMapper submissionMapper;
    private final AlgorithmProblemMapper problemMapper;
    private final AlgorithmCaseResultMapper caseResultMapper;
    private final AlgorithmTestCaseMapper testCaseMapper;
    private final AlgorithmJudgeService judgeService;
    private final AlgorithmJudgeTaskService taskService;
    private final CurrentUser currentUser;

    public AlgorithmSubmissionService(AlgorithmSubmissionMapper submissionMapper,
                                      AlgorithmProblemMapper problemMapper,
                                      AlgorithmCaseResultMapper caseResultMapper,
                                      AlgorithmTestCaseMapper testCaseMapper,
                                      AlgorithmJudgeService judgeService,
                                      AlgorithmJudgeTaskService taskService,
                                      CurrentUser currentUser) {
        this.submissionMapper = submissionMapper;
        this.problemMapper = problemMapper;
        this.caseResultMapper = caseResultMapper;
        this.testCaseMapper = testCaseMapper;
        this.judgeService = judgeService;
        this.taskService = taskService;
        this.currentUser = currentUser;
    }

    public AlgorithmDtos.RunResponse run(Long userId, AlgorithmDtos.RunRequest request) {
        return judgeService.run(userId, request);
    }

    public Long submit(Long userId, AlgorithmDtos.SubmitRequest request) {
        if (request.problemId() == null) throw BusinessException.badRequest("题目 ID 不能为空");
        AlgorithmProblem problem = problemMapper.selectById(request.problemId());
        if (problem == null || problem.getStatus() == null || problem.getStatus() != 1) {
            throw BusinessException.notFound("题目不存在或已停用");
        }
        if (!StringUtils.hasText(request.sourceCode())) {
            throw BusinessException.badRequest("代码不能为空");
        }
        if (!request.sourceCode().contains("class Main")) {
            throw BusinessException.badRequest("源码必须包含 public class Main");
        }
        AlgorithmSubmission submission = new AlgorithmSubmission();
        submission.setUserId(userId);
        submission.setProblemId(problem.getId());
        submission.setLanguage(request.language() == null ? "JAVA17" : request.language());
        submission.setSourceCode(request.sourceCode());
        submission.setSubmitType(AlgorithmSubmitType.SUBMIT.name());
        submission.setStatus(AlgorithmSubmissionStatus.QUEUED.name());
        submissionMapper.insert(submission);
        taskService.publish(submission.getId());
        return submission.getId();
    }

    public PageResult<AlgorithmDtos.SubmissionListItem> list(Long userId, Long problemId, String status,
                                                             int pageNo, int pageSize) {
        pageNo = Math.max(1, pageNo);
        pageSize = Math.max(1, Math.min(100, pageSize));
        List<AlgorithmSubmission> all = submissionMapper.selectList(
                new LambdaQueryWrapper<AlgorithmSubmission>()
                        .eq(AlgorithmSubmission::getUserId, userId)
                        .eq(AlgorithmSubmission::getSubmitType, AlgorithmSubmitType.SUBMIT.name())
                        .eq(problemId != null, AlgorithmSubmission::getProblemId, problemId)
                        .eq(StringUtils.hasText(status), AlgorithmSubmission::getStatus, status)
                        .orderByDesc(AlgorithmSubmission::getId));
        long total = all.size();
        int from = Math.min((pageNo - 1) * pageSize, all.size());
        int to = Math.min(from + pageSize, all.size());
        List<AlgorithmSubmission> page = all.subList(from, to);
        Map<Long, String> titles = problemTitles(page);
        return PageResult.of(page.stream()
                .map(item -> new AlgorithmDtos.SubmissionListItem(
                        item.getId(), item.getProblemId(), titles.get(item.getProblemId()),
                        item.getLanguage(), item.getSubmitType(), item.getStatus(),
                        item.getPassedCount(), item.getTotalCount(), item.getExecutionTimeMs(),
                        item.getMemoryUsageKb(), item.getCreatedAt()))
                .toList(), total, pageNo, pageSize);
    }

    public AlgorithmDtos.SubmissionDetailView detail(Long userId, Long submissionId) {
        AlgorithmSubmission submission = submissionMapper.selectById(submissionId);
        if (submission == null) throw BusinessException.notFound("提交记录不存在");
        if (!submission.getUserId().equals(userId) && !currentUser.hasRole("ADMIN")) {
            throw BusinessException.forbidden("无权查看该提交记录");
        }
        AlgorithmProblem problem = problemMapper.selectById(submission.getProblemId());
        String problemTitle = problem == null ? "已删除题目" : problem.getTitle();
        List<AlgorithmCaseResult> results = caseResultMapper.selectList(
                        new LambdaQueryWrapper<AlgorithmCaseResult>()
                                .eq(AlgorithmCaseResult::getSubmissionId, submissionId)
                                .orderByAsc(AlgorithmCaseResult::getId));
        Map<Long, String> caseTypeById = caseTypesOf(results);
        List<AlgorithmDtos.CaseResultView> caseResults = results.stream()
                .map(result -> {
                    String caseType = caseTypeById.getOrDefault(result.getTestCaseId(),
                            AlgorithmCaseType.HIDDEN.name());
                    boolean sample = AlgorithmCaseType.SAMPLE.name().equals(caseType);
                    return new AlgorithmDtos.CaseResultView(
                            sample ? result.getTestCaseId() : null,
                            caseType, result.getStatus(),
                            sample ? result.getActualOutput() : null,
                            result.getExecutionTimeMs(), result.getMemoryUsageKb());
                }).toList();
        return new AlgorithmDtos.SubmissionDetailView(
                submission.getId(), submission.getProblemId(), problemTitle,
                submission.getLanguage(), submission.getSubmitType(), submission.getStatus(),
                submission.getScore(), submission.getPassedCount(), submission.getTotalCount(),
                submission.getExecutionTimeMs(), submission.getMemoryUsageKb(),
                submission.getSourceCode(), submission.getCompileMessage(), submission.getRuntimeMessage(),
                submission.getCreatedAt(), caseResults);
    }

    public List<AlgorithmDtos.WrongProblemView> wrongProblems(Long userId) {
        return submissionMapper.selectWrongProblems(userId).stream()
                .map(this::toWrongView).toList();
    }

    private AlgorithmDtos.WrongProblemView toWrongView(WrongProblemRow row) {
        return new AlgorithmDtos.WrongProblemView(
                row.getId(), row.getTitle(), row.getSlug(), row.getDifficulty(),
                difficultyLabel(row.getDifficulty()),
                row.getMySubmitCount() == null ? 0 : row.getMySubmitCount(),
                row.getFavorited() != null && row.getFavorited() == 1,
                row.getHasNote() != null && row.getHasNote() == 1);
    }

    private Map<Long, String> problemTitles(List<AlgorithmSubmission> submissions) {
        List<Long> ids = submissions.stream().map(AlgorithmSubmission::getProblemId).distinct().toList();
        if (ids.isEmpty()) return Map.of();
        return problemMapper.selectBatchIds(ids).stream()
                .collect(Collectors.toMap(AlgorithmProblem::getId, AlgorithmProblem::getTitle));
    }

    private Map<Long, String> caseTypesOf(List<AlgorithmCaseResult> results) {
        List<Long> caseIds = results.stream().map(AlgorithmCaseResult::getTestCaseId)
                .filter(java.util.Objects::nonNull).distinct().toList();
        if (caseIds.isEmpty()) return Map.of();
        return testCaseMapper.selectBatchIds(caseIds).stream()
                .collect(Collectors.toMap(AlgorithmTestCase::getId, AlgorithmTestCase::getCaseType));
    }

    private static String difficultyLabel(String difficulty) {
        try {
            return AlgorithmDifficulty.valueOf(difficulty).getLabel();
        } catch (IllegalArgumentException exception) {
            return difficulty;
        }
    }
}
