package com.tyut.aiinterview.admin;

import com.tyut.aiinterview.mapper.AdminWorkspaceActionRow;
import com.tyut.aiinterview.mapper.AdminWorkspaceMapper;
import com.tyut.aiinterview.mapper.AdminWorkspaceSummaryRow;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;

@Service
public class AdminWorkspaceService {
    private static final Map<String, ActionDefinition> ACTIONS = Map.of(
            "REPORT_BACKLOG", new ActionDefinition("报告任务积压", "正在生成的报告需要持续关注。", "进入面试管理查看任务状态并按需重试。", "warning", "/admin/interviews"),
            "AI_FAILED", new ActionDefinition("AI 失败任务", "最近失败的 AI 任务需要复核。", "检查失败类型；技术失败不等于业务未通过。", "warning", "/admin/ai-generations"),
            "WORKER_QUEUE", new ActionDefinition("判题排队", "算法提交正在等待判题 Worker。", "观察排队时长，持续增长时检查 Worker 服务。", "info", "/admin/algorithm/problems"),
            "TICKETS", new ActionDefinition("待处理工单", "用户反馈仍有待受理或处理中记录。", "优先处理面试失败和阻断业务的工单。", "warning", "/admin/tickets"),
            "SERVICE_ANOMALY", new ActionDefinition("服务异常", "过去 24 小时出现 AI 或面试异常。", "先查看脱敏状态和时间范围，再进入对应模块处理。", "danger", "/admin/ai-generations"));

    private final AdminWorkspaceMapper mapper;

    public AdminWorkspaceService(AdminWorkspaceMapper mapper) {
        this.mapper = mapper;
    }

    public AdminWorkspaceDtos.Summary summary() {
        AdminWorkspaceSummaryRow row = mapper.selectSummary();
        LocalDate today = LocalDate.now();
        LocalDate periodStart = today.with(TemporalAdjusters.previousOrSame(java.time.DayOfWeek.MONDAY));
        LocalDateTime now = LocalDateTime.now();
        long queued = value(row.getAlgorithmQueuedCount());
        long running = value(row.getAlgorithmRunningCount());
        WorkerView worker = worker(queued, running, row.getAlgorithmOldestQueuedAt(), now);
        Map<String, Long> counts = mapper.selectActions().stream().collect(Collectors.toMap(
                AdminWorkspaceActionRow::getActionType, item -> value(item.getItemCount()), (left, right) -> right));
        List<AdminWorkspaceDtos.ActionItem> actions = new ArrayList<>();
        for (String type : List.of("REPORT_BACKLOG", "AI_FAILED", "WORKER_QUEUE", "TICKETS", "SERVICE_ANOMALY")) {
            long count = counts.getOrDefault(type, 0L);
            if (count == 0) continue;
            ActionDefinition definition = ACTIONS.get(type);
            actions.add(new AdminWorkspaceDtos.ActionItem(type, definition.label(), definition.description(),
                    definition.recommendation(), count, definition.severity(), definition.targetPath()));
        }
        AdminWorkspaceDtos.Metrics metrics = new AdminWorkspaceDtos.Metrics(
                value(row.getCompanyCount()), value(row.getActiveUserCount()), value(row.getRecruitingPositionCount()),
                value(row.getWeeklyApplicationCount()), value(row.getInProgressInterviewCount()),
                value(row.getReportBacklogCount()), value(row.getAiFailedTaskCount()), value(row.getPendingTicketCount()));
        return new AdminWorkspaceDtos.Summary(periodStart, today, now, metrics,
                new AdminWorkspaceDtos.WorkerStatus(worker.code(), worker.label(), worker.summary(), worker.recommendation(),
                        queued, running, row.getAlgorithmOldestQueuedAt()), actions);
    }

    private WorkerView worker(long queued, long running, LocalDateTime oldestQueuedAt, LocalDateTime now) {
        if (oldestQueuedAt != null && Duration.between(oldestQueuedAt, now).toMinutes() >= 10) {
            return new WorkerView("ATTENTION", "需要关注", "队列中存在等待时间较长的提交。", "检查判题 Worker 是否持续消费队列。");
        }
        if (queued > 0 || running > 0) {
            return new WorkerView("WORKING", "处理中", "当前有判题任务排队或执行。", "保持观察，若队列持续增长再介入处理。");
        }
        return new WorkerView("IDLE", "空闲", "当前没有排队或执行中的判题任务。", "暂不需要处理；该状态基于任务队列观测，不暴露内部连接信息。");
    }

    private static long value(Long value) {
        return value == null ? 0 : value;
    }

    private record ActionDefinition(String label, String description, String recommendation,
                                    String severity, String targetPath) {}

    private record WorkerView(String code, String label, String summary, String recommendation) {}
}
