package com.tyut.aiinterview.algorithm;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.tyut.aiinterview.domain.AlgorithmProblem;
import com.tyut.aiinterview.domain.AlgorithmSubmission;
import com.tyut.aiinterview.domain.AlgorithmUserProgress;
import com.tyut.aiinterview.mapper.AlgorithmProblemMapper;
import com.tyut.aiinterview.mapper.AlgorithmSubmissionMapper;
import com.tyut.aiinterview.mapper.AlgorithmUserProgressMapper;
import com.tyut.aiinterview.mapper.RecentSubmissionRow;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;

@Service
public class AlgorithmProgressService {
    private final AlgorithmProblemMapper problemMapper;
    private final AlgorithmSubmissionMapper submissionMapper;
    private final AlgorithmUserProgressMapper progressMapper;
    private final AlgorithmProblemService problemService;

    public AlgorithmProgressService(AlgorithmProblemMapper problemMapper,
                                    AlgorithmSubmissionMapper submissionMapper,
                                    AlgorithmUserProgressMapper progressMapper,
                                    AlgorithmProblemService problemService) {
        this.problemMapper = problemMapper;
        this.submissionMapper = submissionMapper;
        this.progressMapper = progressMapper;
        this.problemService = problemService;
    }

    public AlgorithmDtos.DashboardView dashboard(Long userId) {
        long acceptedProblems = progressMapper.selectCount(new LambdaQueryWrapper<AlgorithmUserProgress>()
                .eq(AlgorithmUserProgress::getUserId, userId)
                .eq(AlgorithmUserProgress::getProgressStatus, AlgorithmProgressStatus.ACCEPTED.name()));
        long todayAccepted = submissionMapper.selectCount(new LambdaQueryWrapper<AlgorithmSubmission>()
                .eq(AlgorithmSubmission::getUserId, userId)
                .eq(AlgorithmSubmission::getSubmitType, AlgorithmSubmitType.SUBMIT.name())
                .eq(AlgorithmSubmission::getStatus, AlgorithmSubmissionStatus.ACCEPTED.name())
                .ge(AlgorithmSubmission::getCreatedAt, LocalDate.now().atStartOfDay()));
        long submitCount = submissionMapper.selectCount(new LambdaQueryWrapper<AlgorithmSubmission>()
                .eq(AlgorithmSubmission::getUserId, userId)
                .eq(AlgorithmSubmission::getSubmitType, AlgorithmSubmitType.SUBMIT.name()));
        long acceptedSubmits = submissionMapper.selectCount(new LambdaQueryWrapper<AlgorithmSubmission>()
                .eq(AlgorithmSubmission::getUserId, userId)
                .eq(AlgorithmSubmission::getSubmitType, AlgorithmSubmitType.SUBMIT.name())
                .eq(AlgorithmSubmission::getStatus, AlgorithmSubmissionStatus.ACCEPTED.name()));
        double acceptanceRate = submitCount == 0 ? 0.0
                : BigDecimal.valueOf(acceptedSubmits * 100.0 / submitCount)
                        .setScale(1, RoundingMode.HALF_UP).doubleValue();

        Map<String, AlgorithmDtos.DifficultyProgress> difficultyProgress = new LinkedHashMap<>();
        for (AlgorithmDifficulty difficulty : AlgorithmDifficulty.values()) {
            long total = problemMapper.selectCount(new LambdaQueryWrapper<AlgorithmProblem>()
                    .eq(AlgorithmProblem::getStatus, 1)
                    .eq(AlgorithmProblem::getDifficulty, difficulty.name()));
            long accepted = progressMapper.countAcceptedByDifficulty(userId, difficulty.name());
            difficultyProgress.put(difficulty.name(),
                    new AlgorithmDtos.DifficultyProgress((int) accepted, (int) total));
        }

        List<AlgorithmDtos.RecentPractice> recent = submissionMapper.selectRecent(userId, 8).stream()
                .map(this::toRecentPractice).toList();
        List<AlgorithmDtos.ProblemListItem> recommended =
                problemService.decorate(userId, problemMapper.selectRecommended(userId, 8));
        List<AlgorithmDtos.ProblemListItem> hot =
                problemService.decorate(userId, problemMapper.selectHotProblems(8));

        return new AlgorithmDtos.DashboardView(
                (int) acceptedProblems, (int) todayAccepted, (int) submitCount, acceptanceRate,
                continuousPracticeDays(userId), difficultyProgress, recent, recommended, hot);
    }

    private AlgorithmDtos.RecentPractice toRecentPractice(RecentSubmissionRow row) {
        return new AlgorithmDtos.RecentPractice(
                row.getId(), row.getProblemId(), row.getProblemTitle(), row.getStatus(),
                row.getSubmitType(), row.getLanguage(), row.getPassedCount(), row.getTotalCount(),
                row.getExecutionTimeMs(), row.getCreatedAt());
    }

    private int continuousPracticeDays(Long userId) {
        List<LocalDate> dates = submissionMapper.selectAcceptedDates(userId);
        if (dates.isEmpty()) return 0;
        LocalDate today = LocalDate.now();
        LocalDate expected = dates.get(0).equals(today) ? today : today.minusDays(1);
        if (!dates.get(0).equals(expected)) return 0;
        int days = 0;
        for (LocalDate date : dates) {
            if (date.equals(expected)) {
                days++;
                expected = expected.minusDays(1);
            } else if (date.isBefore(expected)) {
                break;
            }
        }
        return days;
    }
}
