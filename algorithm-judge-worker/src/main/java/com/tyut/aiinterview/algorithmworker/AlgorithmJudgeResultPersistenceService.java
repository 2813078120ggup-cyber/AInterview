package com.tyut.aiinterview.algorithmworker;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.tyut.aiinterview.algorithmworker.domain.AlgorithmCaseResult;
import com.tyut.aiinterview.algorithmworker.domain.AlgorithmSubmission;
import com.tyut.aiinterview.algorithmworker.domain.AlgorithmUserProgress;
import com.tyut.aiinterview.algorithmworker.mapper.AlgorithmCaseResultMapper;
import com.tyut.aiinterview.algorithmworker.mapper.AlgorithmSubmissionMapper;
import com.tyut.aiinterview.algorithmworker.mapper.AlgorithmUserProgressMapper;
import com.tyut.aiinterview.algorithmworker.observability.AlgorithmJudgeMetrics;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Persists one completed judge result in a short database transaction. */
@Service
public class AlgorithmJudgeResultPersistenceService {
    private static final String ACCEPTED = "ACCEPTED";
    private static final String ATTEMPTED = "ATTEMPTED";
    private static final int MESSAGE_LIMIT = 4000;

    private final AlgorithmSubmissionMapper submissionMapper;
    private final AlgorithmCaseResultMapper caseResultMapper;
    private final AlgorithmUserProgressMapper progressMapper;
    private final AlgorithmJudgeMetrics metrics;

    public AlgorithmJudgeResultPersistenceService(AlgorithmSubmissionMapper submissionMapper,
                                                  AlgorithmCaseResultMapper caseResultMapper,
                                                  AlgorithmUserProgressMapper progressMapper,
                                                  AlgorithmJudgeMetrics metrics) {
        this.submissionMapper = submissionMapper;
        this.caseResultMapper = caseResultMapper;
        this.progressMapper = progressMapper;
        this.metrics = metrics;
    }

    @Transactional
    public void finish(AlgorithmSubmission submission, String status, int passed, int total, int score,
                       Long maxTime, String compileMessage, String runtimeMessage,
                       List<AlgorithmCaseResult> results) {
        for (AlgorithmCaseResult result : results) caseResultMapper.insert(result);
        submission.setStatus(status);
        submission.setScore(score);
        submission.setPassedCount(passed);
        submission.setTotalCount(total);
        submission.setExecutionTimeMs(maxTime);
        submission.setCompileMessage(compileMessage);
        submission.setRuntimeMessage(runtimeMessage);
        submission.setFinishedAt(LocalDateTime.now());
        submissionMapper.updateById(submission);

        boolean accepted = ACCEPTED.equals(status);
        AlgorithmUserProgress progress = progressMapper.selectOne(new LambdaQueryWrapper<AlgorithmUserProgress>()
                .eq(AlgorithmUserProgress::getUserId, submission.getUserId())
                .eq(AlgorithmUserProgress::getProblemId, submission.getProblemId()));
        LocalDateTime now = LocalDateTime.now();
        if (progress == null) {
            progress = new AlgorithmUserProgress();
            progress.setUserId(submission.getUserId());
            progress.setProblemId(submission.getProblemId());
            progress.setProgressStatus(accepted ? ACCEPTED : ATTEMPTED);
            progress.setSubmitCount(0);
            progress.setCreatedAt(now);
        } else if (accepted) {
            progress.setProgressStatus(ACCEPTED);
            if (progress.getFirstAcceptedAt() == null) progress.setFirstAcceptedAt(now);
        } else if (!ACCEPTED.equals(progress.getProgressStatus())) {
            progress.setProgressStatus(ATTEMPTED);
        }
        progress.setSubmitCount((progress.getSubmitCount() == null ? 0 : progress.getSubmitCount()) + 1);
        progress.setLastSubmittedAt(now);
        if (accepted && submission.getExecutionTimeMs() != null
                && (progress.getBestExecutionTimeMs() == null
                || submission.getExecutionTimeMs() < progress.getBestExecutionTimeMs())) {
            progress.setBestExecutionTimeMs(submission.getExecutionTimeMs());
        }
        if (progress.getId() == null) progressMapper.insert(progress);
        else progressMapper.updateById(progress);
        metrics.recordOutcome(status);
    }

    @Transactional
    public void markSystemError(AlgorithmSubmission submission, String message) {
        submission.setStatus("SYSTEM_ERROR");
        submission.setRuntimeMessage(truncate(message));
        submission.setFinishedAt(LocalDateTime.now());
        submissionMapper.updateById(submission);
        metrics.recordOutcome("SYSTEM_ERROR");
    }

    private static String truncate(String value) {
        if (value == null) return null;
        return value.length() <= MESSAGE_LIMIT ? value : value.substring(0, MESSAGE_LIMIT);
    }
}
