package com.tyut.aiinterview.recruitment;

import com.tyut.aiinterview.common.BusinessException;
import com.tyut.aiinterview.mapper.CompanyDashboardActionCountRow;
import com.tyut.aiinterview.mapper.CompanyDashboardActionRow;
import com.tyut.aiinterview.mapper.CompanyDashboardMapper;
import com.tyut.aiinterview.mapper.CompanyDashboardSummaryRow;
import com.tyut.aiinterview.mapper.CompanyFunnelRow;
import com.tyut.aiinterview.mapper.CompanyPositionAnalyticsRow;
import com.tyut.aiinterview.mapper.CompanyUpcomingInterviewRow;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;

@Service
public class CompanyDashboardService {
    private static final int ACTION_ITEM_LIMIT = 30;
    private static final int UPCOMING_INTERVIEW_LIMIT = 12;
    private static final int POSITION_LIMIT = 8;
    private static final List<String> FUNNEL_ORDER = List.of(
            "SUBMITTED", "AI_INTERVIEW_PENDING", "AI_INTERVIEWING", "UNDER_REVIEW",
            "OFFLINE_INTERVIEW", "REJECTED", "HIRED");
    private static final Map<String, String> FUNNEL_LABELS = Map.of(
            "SUBMITTED", "新申请",
            "AI_INTERVIEW_PENDING", "待 AI 面试",
            "AI_INTERVIEWING", "AI 面试中",
            "UNDER_REVIEW", "企业评估",
            "OFFLINE_INTERVIEW", "线下面试",
            "REJECTED", "未通过",
            "HIRED", "已录用");
    private static final List<ActionDefinition> ACTIONS = List.of(
            new ActionDefinition("NEW_APPLICATION", "新申请待查看", "先完成初筛，确认是否进入下一环节"),
            new ActionDefinition("MATCH_FAILED", "匹配失败", "检查岗位和简历信息后重新分析"),
            new ActionDefinition("AI_INTERVIEW_REVIEW", "AI 面试完成待评估", "查看面试结果并补充人工判断"),
            new ActionDefinition("REPORT_TIMEOUT", "报告生成超时", "检查报告任务状态，必要时重新处理"),
            new ActionDefinition("OFFLINE_CONFIRMATION", "线下面试待确认", "确认时间、地点和联系人信息"));

    private final CompanyDashboardMapper dashboardMapper;
    private final CompanyAccessService companyAccess;

    public CompanyDashboardService(CompanyDashboardMapper dashboardMapper, CompanyAccessService companyAccess) {
        this.dashboardMapper = dashboardMapper;
        this.companyAccess = companyAccess;
    }

    public RecruitmentDtos.DashboardSummary summary() {
        CompanyDashboardSummaryRow row = dashboardMapper.selectSummary(companyId());
        if (row == null) throw BusinessException.notFound("企业不存在");
        return new RecruitmentDtos.DashboardSummary(row.getCompanyId(), row.getCompanyName(), row.getCompanyShortName(),
                row.getCity(), value(row.getPublishedPositions()), value(row.getDraftPositions()), value(row.getTotalApplications()),
                value(row.getPendingApplications()), value(row.getTodayInterviews()), value(row.getOverdueItems()),
                value(row.getHiredApplications()), scale(row.getAverageMatchScore()), row.getLastUpdatedAt());
    }

    public RecruitmentDtos.ActionCenter actions() {
        Long companyId = companyId();
        Map<String, Long> counts = new HashMap<>();
        for (CompanyDashboardActionCountRow row : dashboardMapper.selectActionCounts(companyId)) {
            counts.put(row.getActionType(), value(row.getItemCount()));
        }
        Map<String, List<RecruitmentDtos.DashboardActionItem>> items = new HashMap<>();
        for (CompanyDashboardActionRow row : dashboardMapper.selectActionItems(companyId, ACTION_ITEM_LIMIT)) {
            items.computeIfAbsent(row.getActionType(), ignored -> new ArrayList<>()).add(
                    new RecruitmentDtos.DashboardActionItem(row.getActionType(), row.getApplicationId(), row.getInterviewId(),
                            row.getCandidateName(), row.getPositionName(), row.getStatus(), row.getMatchStatus(),
                            row.getDueAt(), row.getCreatedAt()));
        }
        List<RecruitmentDtos.DashboardActionGroup> groups = ACTIONS.stream()
                .map(action -> new RecruitmentDtos.DashboardActionGroup(action.type(), action.label(), action.description(),
                        counts.getOrDefault(action.type(), 0L), items.getOrDefault(action.type(), List.of())))
                .toList();
        return new RecruitmentDtos.ActionCenter(groups, counts.values().stream().mapToLong(Long::longValue).sum(), LocalDateTime.now());
    }

    public List<RecruitmentDtos.UpcomingInterview> upcomingInterviews() {
        return dashboardMapper.selectUpcomingInterviews(companyId(), UPCOMING_INTERVIEW_LIMIT).stream()
                .map(this::toUpcomingInterview).toList();
    }

    public List<RecruitmentDtos.FunnelStage> funnel() {
        Map<String, Long> counts = new HashMap<>();
        for (CompanyFunnelRow row : dashboardMapper.selectFunnel(companyId())) counts.put(row.getStatus(), value(row.getItemCount()));
        long total = counts.values().stream().mapToLong(Long::longValue).sum();
        return FUNNEL_ORDER.stream().map(status -> new RecruitmentDtos.FunnelStage(status, FUNNEL_LABELS.get(status),
                counts.getOrDefault(status, 0L), percentage(counts.getOrDefault(status, 0L), total))).toList();
    }

    public List<RecruitmentDtos.PositionAnalytics> positions() {
        return dashboardMapper.selectPositionAnalytics(companyId(), POSITION_LIMIT).stream().map(row ->
                new RecruitmentDtos.PositionAnalytics(row.getPositionId(), row.getPositionName(), row.getRecruitmentStatus(),
                        value(row.getApplicationCount()), value(row.getPendingCount()), value(row.getHiredCount()), scale(row.getAverageMatchScore())))
                .toList();
    }

    private Long companyId() { return companyAccess.requirePermission("analytics:read"); }

    private RecruitmentDtos.UpcomingInterview toUpcomingInterview(CompanyUpcomingInterviewRow row) {
        return new RecruitmentDtos.UpcomingInterview(row.getSource(), row.getInterviewId(), row.getApplicationId(),
                row.getCandidateName(), row.getPositionName(), row.getScheduledAt(), row.getDurationMinutes(),
                row.getStatus(), row.getLocation());
    }

    private static long value(Long value) { return value == null ? 0 : value; }

    private static BigDecimal scale(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value.setScale(1, RoundingMode.HALF_UP);
    }

    private static BigDecimal percentage(long value, long total) {
        return total == 0 ? BigDecimal.ZERO : BigDecimal.valueOf(value * 100d / total).setScale(1, RoundingMode.HALF_UP);
    }

    private record ActionDefinition(String type, String label, String description) {}
}
