package com.tyut.aiinterview.reflection;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.tyut.aiinterview.common.BusinessException;
import com.tyut.aiinterview.domain.Interview;
import com.tyut.aiinterview.domain.InterviewReflection;
import com.tyut.aiinterview.domain.Report;
import com.tyut.aiinterview.mapper.InterviewMapper;
import com.tyut.aiinterview.mapper.InterviewReflectionMapper;
import com.tyut.aiinterview.mapper.ReportMapper;
import com.tyut.aiinterview.security.CurrentUser;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class InterviewReflectionService {
    private final InterviewReflectionMapper reflectionMapper;
    private final InterviewMapper interviewMapper;
    private final ReportMapper reportMapper;
    private final CurrentUser currentUser;

    public InterviewReflectionService(InterviewReflectionMapper reflectionMapper,
                                      InterviewMapper interviewMapper,
                                      ReportMapper reportMapper,
                                      CurrentUser currentUser) {
        this.reflectionMapper = reflectionMapper;
        this.interviewMapper = interviewMapper;
        this.reportMapper = reportMapper;
        this.currentUser = currentUser;
    }

    public ReflectionDtos.ReflectionView get(Long interviewId) {
        Interview interview = requireOwnedInterview(interviewId);
        InterviewReflection reflection = find(interviewId);
        return reflection == null ? null : toView(reflection, interview, publishedReport(interviewId));
    }

    @Transactional
    public ReflectionDtos.ReflectionView save(Long interviewId, ReflectionDtos.SaveRequest request) {
        Interview interview = requireOwnedInterview(interviewId);
        if (!canReflect(interview.getStatus())) {
            throw BusinessException.badRequest("面试结束后才能记录心得");
        }

        LocalDateTime now = LocalDateTime.now();
        InterviewReflection reflection = find(interviewId);
        if (reflection == null) {
            reflection = new InterviewReflection();
            reflection.setInterviewId(interviewId);
            reflection.setCandidateId(currentUser.id());
            reflection.setCreatedAt(now);
            apply(reflection, request, now);
            try {
                reflectionMapper.insert(reflection);
            } catch (DuplicateKeyException exception) {
                reflection = find(interviewId);
                if (reflection == null) throw exception;
                apply(reflection, request, now);
                reflectionMapper.updateById(reflection);
            }
        } else {
            apply(reflection, request, now);
            reflectionMapper.updateById(reflection);
        }
        return toView(reflection, interview, publishedReport(interviewId));
    }

    public ReflectionDtos.CandidateSummary mine() {
        requireCandidateRole();
        Long candidateId = currentUser.id();
        List<InterviewReflection> reflections = reflectionMapper.selectList(
                new LambdaQueryWrapper<InterviewReflection>()
                        .eq(InterviewReflection::getCandidateId, candidateId)
                        .orderByAsc(InterviewReflection::getCreatedAt)
                        .orderByAsc(InterviewReflection::getId));
        if (reflections.isEmpty()) {
            return new ReflectionDtos.CandidateSummary(
                    0, BigDecimal.ZERO, BigDecimal.ZERO, null, null, null, List.of());
        }

        List<Long> interviewIds = reflections.stream().map(InterviewReflection::getInterviewId).distinct().toList();
        Map<Long, Interview> interviews = interviewMapper.selectBatchIds(interviewIds).stream()
                .filter(interview -> candidateId.equals(interview.getCandidateId()))
                .collect(Collectors.toMap(Interview::getId, Function.identity()));
        Map<Long, Report> reports = reportMapper.selectList(new LambdaQueryWrapper<Report>()
                        .in(Report::getInterviewId, interviewIds)
                        .eq(Report::getStatus, 1))
                .stream().collect(Collectors.toMap(Report::getInterviewId, Function.identity()));

        List<ReflectionDtos.ReflectionView> views = reflections.stream()
                .map(reflection -> {
                    Interview interview = interviews.get(reflection.getInterviewId());
                    return interview == null ? null : toView(reflection, interview, reports.get(reflection.getInterviewId()));
                })
                .filter(Objects::nonNull)
                .sorted(Comparator.comparing(
                        ReflectionDtos.ReflectionView::scheduledAt,
                        Comparator.nullsLast(Comparator.naturalOrder())))
                .toList();
        if (views.isEmpty()) {
            return new ReflectionDtos.CandidateSummary(
                    0, BigDecimal.ZERO, BigDecimal.ZERO, null, null, null, List.of());
        }

        ReflectionDtos.ReflectionView latest = views.get(views.size() - 1);
        ReflectionDtos.ReflectionView previous = views.size() > 1 ? views.get(views.size() - 2) : null;
        List<BigDecimal> aiScores = views.stream().map(ReflectionDtos.ReflectionView::aiScore)
                .filter(Objects::nonNull).toList();
        return new ReflectionDtos.CandidateSummary(
                views.size(),
                averageIntegers(views.stream().map(ReflectionDtos.ReflectionView::selfScore).toList()),
                averageIntegers(views.stream().map(ReflectionDtos.ReflectionView::confidenceLevel).toList()),
                aiScores.isEmpty() ? null : averageDecimals(aiScores),
                latest.selfScore(),
                previous == null ? null : latest.selfScore() - previous.selfScore(),
                views);
    }

    private Interview requireOwnedInterview(Long interviewId) {
        requireCandidateRole();
        Interview interview = interviewMapper.selectById(interviewId);
        if (interview == null) throw BusinessException.notFound("面试不存在");
        if (!currentUser.id().equals(interview.getCandidateId())) {
            throw BusinessException.forbidden("无权访问该面试心得");
        }
        return interview;
    }

    private void requireCandidateRole() {
        if (!currentUser.hasRole("CANDIDATE")) {
            throw BusinessException.forbidden("仅候选人可记录面试心得");
        }
    }

    private InterviewReflection find(Long interviewId) {
        return reflectionMapper.selectOne(new LambdaQueryWrapper<InterviewReflection>()
                .eq(InterviewReflection::getInterviewId, interviewId)
                .last("LIMIT 1"));
    }

    private Report publishedReport(Long interviewId) {
        return reportMapper.selectOne(new LambdaQueryWrapper<Report>()
                .eq(Report::getInterviewId, interviewId)
                .eq(Report::getStatus, 1)
                .last("LIMIT 1"));
    }

    private void apply(InterviewReflection reflection, ReflectionDtos.SaveRequest request, LocalDateTime now) {
        reflection.setSelfScore(request.selfScore());
        reflection.setConfidenceLevel(request.confidenceLevel());
        reflection.setContent(request.content().trim());
        reflection.setHighlights(trimToNull(request.highlights()));
        reflection.setImprovements(trimToNull(request.improvements()));
        reflection.setActionPlan(trimToNull(request.actionPlan()));
        reflection.setUpdatedAt(now);
    }

    private ReflectionDtos.ReflectionView toView(InterviewReflection reflection, Interview interview, Report report) {
        return new ReflectionDtos.ReflectionView(
                reflection.getId(),
                reflection.getInterviewId(),
                interview.getTitle(),
                interview.getScheduledAt(),
                reflection.getSelfScore(),
                reflection.getConfidenceLevel(),
                reflection.getContent(),
                reflection.getHighlights(),
                reflection.getImprovements(),
                reflection.getActionPlan(),
                report == null ? null : report.getTotalScore(),
                reflection.getCreatedAt(),
                reflection.getUpdatedAt());
    }

    private BigDecimal averageIntegers(List<Integer> values) {
        long total = values.stream().mapToLong(Integer::longValue).sum();
        return BigDecimal.valueOf(total).divide(BigDecimal.valueOf(values.size()), 1, RoundingMode.HALF_UP);
    }

    private BigDecimal averageDecimals(List<BigDecimal> values) {
        BigDecimal total = values.stream().reduce(BigDecimal.ZERO, BigDecimal::add);
        return total.divide(BigDecimal.valueOf(values.size()), 1, RoundingMode.HALF_UP);
    }

    private boolean canReflect(Integer status) {
        return status != null && (status == Interview.COMPLETED
                || status == Interview.PASSED
                || status == Interview.REPORT_GENERATING
                || status == Interview.REPORT_READY
                || status == Interview.FAILED);
    }

    private String trimToNull(String value) {
        if (value == null || value.isBlank()) return null;
        return value.trim();
    }
}
