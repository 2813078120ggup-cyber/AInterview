package com.tyut.aiinterview.recruitment;

import com.tyut.aiinterview.common.BusinessException;
import com.tyut.aiinterview.common.PageResult;
import com.tyut.aiinterview.mapper.CompanyAnalyticsFunnelRow;
import com.tyut.aiinterview.mapper.CompanyAnalyticsMapper;
import com.tyut.aiinterview.mapper.CompanyAnalyticsPositionRow;
import com.tyut.aiinterview.mapper.CompanyAnalyticsScoreBucketRow;
import com.tyut.aiinterview.mapper.CompanyAnalyticsSummaryRow;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;

@Service
public class CompanyAnalyticsService {
    private static final List<String> FUNNEL_ORDER = List.of(
            "SUBMITTED", "AI_INTERVIEW_PENDING", "AI_INTERVIEWING", "UNDER_REVIEW",
            "OFFLINE_INTERVIEW", "REJECTED", "HIRED");
    private static final Map<String, String> FUNNEL_LABELS = Map.of(
            "SUBMITTED", "已投递", "AI_INTERVIEW_PENDING", "待 AI 面试", "AI_INTERVIEWING", "AI 面试中",
            "UNDER_REVIEW", "企业评估", "OFFLINE_INTERVIEW", "线下面试", "REJECTED", "未通过", "HIRED", "已录用");
    private static final List<String> BUCKET_ORDER = List.of("0_59", "60_69", "70_79", "80_89", "90_100");
    private static final Map<String, String> BUCKET_LABELS = Map.of(
            "0_59", "0–59", "60_69", "60–69", "70_79", "70–79", "80_89", "80–89", "90_100", "90–100");

    private final CompanyAnalyticsMapper analyticsMapper;
    private final CompanyAccessService companyAccess;

    public CompanyAnalyticsService(CompanyAnalyticsMapper analyticsMapper, CompanyAccessService companyAccess) {
        this.analyticsMapper = analyticsMapper;
        this.companyAccess = companyAccess;
    }

    public CompanyAnalyticsDtos.Overview overview(LocalDate from, LocalDate to) {
        Long companyId = companyAccess.requirePermission("analytics:read");
        DateRange range = normalizeRange(from, to);
        CompanyAnalyticsSummaryRow summary = analyticsMapper.selectSummary(companyId, range.fromAt(), range.toExclusive());
        long applicationCount = value(summary == null ? null : summary.getApplicationCount());
        long interviewCount = value(summary == null ? null : summary.getInterviewApplicationCount());
        long hiredCount = value(summary == null ? null : summary.getHiredCount());
        List<CompanyAnalyticsFunnelRow> funnelRows = analyticsMapper.selectFunnel(companyId, range.fromAt(), range.toExclusive());
        Map<String, Long> funnelCounts = funnelRows.stream().collect(Collectors.toMap(
                CompanyAnalyticsFunnelRow::getStatus, row -> value(row.getItemCount()), (left, right) -> right));
        List<CompanyAnalyticsDtos.FunnelStage> funnel = FUNNEL_ORDER.stream().map(status -> {
            long count = funnelCounts.getOrDefault(status, 0L);
            return new CompanyAnalyticsDtos.FunnelStage(status, FUNNEL_LABELS.get(status), count,
                    percentage(count, applicationCount), percentage(count, applicationCount));
        }).toList();
        List<CompanyAnalyticsScoreBucketRow> scoreRows = analyticsMapper.selectScoreBuckets(companyId, range.fromAt(), range.toExclusive());
        Map<String, Long> scores = scoreRows.stream().collect(Collectors.toMap(
                CompanyAnalyticsScoreBucketRow::getBucketKey, row -> value(row.getItemCount()), (left, right) -> right));
        long scoredCount = scores.values().stream().mapToLong(Long::longValue).sum();
        List<CompanyAnalyticsDtos.ScoreBucket> distribution = BUCKET_ORDER.stream()
                .map(key -> new CompanyAnalyticsDtos.ScoreBucket(key, BUCKET_LABELS.get(key), scores.getOrDefault(key, 0L),
                        percentage(scores.getOrDefault(key, 0L), scoredCount)))
                .toList();
        return new CompanyAnalyticsDtos.Overview(range.from(), range.to(), (int) applicationCount,
                applicationCount < 10, funnel, scale(summary == null ? null : summary.getAverageInitialScreeningHours()),
                scale(summary == null ? null : summary.getAverageTimeToInterviewHours()),
                scale(summary == null ? null : summary.getAverageHiringCycleDays()), applicationCount,
                percentage(interviewCount, applicationCount), percentage(hiredCount, applicationCount), distribution,
                LocalDateTime.now());
    }

    public PageResult<CompanyAnalyticsDtos.PositionAnalytics> positions(LocalDate from, LocalDate to,
                                                                         long pageNo, long pageSize) {
        Long companyId = companyAccess.requirePermission("analytics:read");
        DateRange range = normalizeRange(from, to);
        long safePageNo = Math.max(1, pageNo);
        long safePageSize = Math.min(100, Math.max(1, pageSize));
        long offset = (safePageNo - 1) * safePageSize;
        List<CompanyAnalyticsPositionRow> rows = analyticsMapper.selectPositionPage(companyId, range.fromAt(),
                range.toExclusive(), offset, safePageSize);
        List<CompanyAnalyticsDtos.PositionAnalytics> records = rows.stream().map(row -> {
            long applications = value(row.getApplicationCount());
            long interviews = value(row.getInterviewCount());
            long hired = value(row.getHiredCount());
            return new CompanyAnalyticsDtos.PositionAnalytics(row.getPositionId(), row.getPositionName(),
                    row.getRecruitmentStatus(), applications, scale(row.getAverageMatchScore()), interviews, hired,
                    percentage(interviews, applications), percentage(hired, applications));
        }).toList();
        return PageResult.of(records, analyticsMapper.countPositions(companyId), safePageNo, safePageSize);
    }

    private DateRange normalizeRange(LocalDate from, LocalDate to) {
        LocalDate end = to == null ? LocalDate.now() : to;
        LocalDate start = from == null ? end.minusDays(29) : from;
        if (start.isAfter(end)) throw BusinessException.badRequest("统计开始日期不能晚于结束日期");
        if (start.plusDays(366).isBefore(end)) throw BusinessException.badRequest("统计时间范围不能超过一年");
        return new DateRange(start, end, start.atStartOfDay(), end.plusDays(1).atStartOfDay());
    }

    private static long value(Long value) { return value == null ? 0L : value; }

    private static BigDecimal scale(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value.setScale(1, RoundingMode.HALF_UP);
    }

    private static BigDecimal percentage(long value, long total) {
        return total == 0 ? BigDecimal.ZERO : BigDecimal.valueOf(value * 100d / total).setScale(1, RoundingMode.HALF_UP);
    }

    private record DateRange(LocalDate from, LocalDate to, LocalDateTime fromAt, LocalDateTime toExclusive) {}
}
