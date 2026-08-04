package com.tyut.aiinterview.report;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.tyut.aiinterview.ai.DeepSeekGateway;
import com.tyut.aiinterview.common.BusinessException;
import com.tyut.aiinterview.common.PageResult;
import com.tyut.aiinterview.domain.Evaluation;
import com.tyut.aiinterview.domain.Interview;
import com.tyut.aiinterview.domain.InterviewQuestion;
import com.tyut.aiinterview.domain.Report;
import com.tyut.aiinterview.domain.UserAccount;
import com.tyut.aiinterview.mapper.EvaluationMapper;
import com.tyut.aiinterview.mapper.InterviewMapper;
import com.tyut.aiinterview.mapper.InterviewQuestionMapper;
import com.tyut.aiinterview.mapper.ReportMapper;
import com.tyut.aiinterview.mapper.UserMapper;
import com.tyut.aiinterview.security.CurrentUser;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Comparator;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ReportService {
    static final int MIN_RELIABLE_QUESTION_COUNT = 5;
    private final ReportMapper reportMapper; private final InterviewMapper interviewMapper; private final InterviewQuestionMapper questionMapper;
    private final EvaluationMapper evaluationMapper; private final UserMapper userMapper; private final CurrentUser currentUser;
    private final DeepSeekGateway deepSeekGateway;
    public ReportService(ReportMapper reportMapper, InterviewMapper interviewMapper, InterviewQuestionMapper questionMapper,
                         EvaluationMapper evaluationMapper, UserMapper userMapper, CurrentUser currentUser, DeepSeekGateway deepSeekGateway) {
        this.reportMapper = reportMapper; this.interviewMapper = interviewMapper; this.questionMapper = questionMapper;
        this.evaluationMapper = evaluationMapper; this.userMapper = userMapper; this.currentUser = currentUser; this.deepSeekGateway = deepSeekGateway;
    }
    @Transactional
    public Report generate(Long interviewId) {
        requireHr(); Interview interview = interviewMapper.selectById(interviewId);
        if (interview == null) throw BusinessException.notFound("面试不存在");
        if (interview.getStatus() != Interview.COMPLETED
                && interview.getStatus() != Interview.REPORT_GENERATING
                && interview.getStatus() != Interview.REPORT_READY
                && interview.getStatus() != Interview.FAILED) {
            throw BusinessException.badRequest("仅已结束或报告生成中的面试可生成报告");
        }
        List<Long> questionIds = questionMapper.selectList(new LambdaQueryWrapper<InterviewQuestion>().eq(InterviewQuestion::getInterviewId, interviewId)).stream().map(InterviewQuestion::getId).toList();
        List<Evaluation> evaluations = questionIds.isEmpty() ? List.of() : evaluationMapper.selectList(new LambdaQueryWrapper<Evaluation>().in(Evaluation::getInterviewQuestionId, questionIds));
        if (evaluations.isEmpty()) throw BusinessException.badRequest("暂无评测数据，无法生成报告");
        Report report = reportMapper.selectOne(new LambdaQueryWrapper<Report>().eq(Report::getInterviewId, interviewId));
        if (report == null) { report = new Report(); report.setInterviewId(interviewId); }
        report.setProfessionalScore(average(evaluations, Evaluation::getProfessionalScore)); report.setExpressionScore(average(evaluations, Evaluation::getExpressionScore));
        report.setLogicScore(average(evaluations, Evaluation::getLogicScore)); report.setAdaptabilityScore(average(evaluations, Evaluation::getAdaptabilityScore));
        report.setTotalScore(reportTotalScore(evaluations)); report.setSummary("根据本次面试作答与评测数据生成的综合评估。");
        report.setStrengths("请结合各维度得分与评语进一步确认候选人优势。"); report.setWeaknesses("请结合各维度得分与评语进一步确认待提升项。");
        report.setImprovementSuggestions("建议围绕得分较低的能力维度进行针对性训练。"); report.setGenerationMethod("manual"); report.setGeneratedBy(currentUser.id());
        report.setStatus(0); report.setPublishedAt(null);
        if (report.getId() == null) reportMapper.insert(report); else reportMapper.updateById(report);
        if (interview.getStatus() == Interview.COMPLETED || interview.getStatus() == Interview.REPORT_GENERATING || interview.getStatus() == Interview.FAILED) {
            interview.setStatus(Interview.REPORT_READY);
            interviewMapper.updateById(interview);
        }
        return report;
    }
    public Report get(Long interviewId) {
        Interview interview = interviewMapper.selectById(interviewId);
        if (interview == null) throw BusinessException.notFound("面试不存在");
        Long id = currentUser.id();
        boolean manager = currentUser.hasRole("ADMIN");
        if (!(id.equals(interview.getCandidateId()) || id.equals(interview.getInterviewerId()) || manager)) throw BusinessException.forbidden("无权查看报告");
        Report report = reportMapper.selectOne(new LambdaQueryWrapper<Report>().eq(Report::getInterviewId, interviewId));
        if (report == null) throw BusinessException.notFound("报告尚未生成");
        if (id.equals(interview.getCandidateId()) && report.getStatus() != 1) throw BusinessException.forbidden("报告尚未发布");
        return report;
    }
    public ReportDtos.ReportDetail detail(Long interviewId) {
        Report report = get(interviewId);
        long questionCount = questionMapper.selectCount(new LambdaQueryWrapper<InterviewQuestion>()
                .eq(InterviewQuestion::getInterviewId, interviewId));
        return new ReportDtos.ReportDetail(report.getId(), report.getInterviewId(), report.getTotalScore(),
                report.getProfessionalScore(), report.getExpressionScore(), report.getLogicScore(),
                report.getAdaptabilityScore(), report.getSummary(), report.getStrengths(), report.getWeaknesses(),
                report.getImprovementSuggestions(), report.getGenerationMethod(), report.getScoringPromptCode(),
                report.getScoringPromptVersionNo(), report.getReportPromptCode(), report.getReportPromptVersionNo(),
                report.getGeneratedBy(), report.getPdfUrl(), report.getStatus(), report.getPublishedAt(), questionCount,
                reliabilityWarning(questionCount));
    }
    public ReportDtos.TrainingPlan trainingPlan(Long interviewId) {
        Interview interview = interviewMapper.selectById(interviewId);
        if (interview == null) throw BusinessException.notFound("面试不存在");
        Report report = get(interviewId);
        try {
            JsonNode node = deepSeekGateway.generateTrainingPlan(trainingContext(interview, report));
            return new ReportDtos.TrainingPlan(
                    text(node, "priority", weakestPriority(report)),
                    integer(node, "durationDays", 7),
                    texts(node.path("focusAreas"), fallbackFocusAreas(report)),
                    days(node.path("dailyPlan"), fallbackDays(report)),
                    texts(node.path("recommendedBanks"), fallbackBanks(report)),
                    texts(node.path("interviewDrills"), fallbackDrills(report)),
                    texts(node.path("successCriteria"), fallbackCriteria(report)),
                    "ai");
        } catch (RuntimeException exception) {
            return fallbackPlan(report);
        }
    }
    @Transactional
    public Report publish(Long interviewId) {
        requireHr();
        Report report = reportMapper.selectOne(new LambdaQueryWrapper<Report>().eq(Report::getInterviewId, interviewId));
        if (report == null) throw BusinessException.notFound("报告尚未生成");
        if (report.getStatus() == 1) return report;
        report.setStatus(1); report.setPublishedAt(LocalDateTime.now()); reportMapper.updateById(report);
        return report;
    }
    public PageResult<ReportDtos.ReportListItem> pageForAdmin(ReportDtos.ReportQuery query) {
        requireHr();
        long pageNo = query.pageNo() == null ? 1 : Math.max(1, query.pageNo());
        long pageSize = query.pageSize() == null ? 20 : Math.min(100, Math.max(1, query.pageSize()));
        Map<Long, Interview> interviews = interviewMapper.selectList(null).stream().collect(Collectors.toMap(Interview::getId, Function.identity()));
        Map<Long, UserAccount> candidates = userMapper.selectList(null).stream().collect(Collectors.toMap(UserAccount::getId, Function.identity()));
        String keyword = query.keyword() == null ? "" : query.keyword().trim().toLowerCase();
        List<ReportDtos.ReportListItem> all = reportMapper.selectList(new LambdaQueryWrapper<Report>().orderByDesc(Report::getPublishedAt).orderByDesc(Report::getId)).stream()
                .map(report -> toListItem(report, interviews.get(report.getInterviewId()), candidates))
                .filter(Objects::nonNull)
                .filter(item -> keyword.isBlank() || contains(item.interviewTitle(), keyword) || contains(item.candidateName(), keyword) || contains(item.candidateUsername(), keyword))
                .toList();
        int from = (int) Math.min((pageNo - 1) * pageSize, all.size());
        int to = (int) Math.min(from + pageSize, all.size());
        return PageResult.of(all.subList(from, to), all.size(), pageNo, pageSize);
    }
    public ReportDtos.CandidateAbilitySummary myAbilitySummary() {
        if (!currentUser.hasRole("CANDIDATE")) throw BusinessException.forbidden("仅候选人可查看能力仪表盘");
        Long candidateId = currentUser.id();
        Map<Long, Interview> interviews = interviewMapper.selectList(new LambdaQueryWrapper<Interview>().eq(Interview::getCandidateId, candidateId))
                .stream().collect(Collectors.toMap(Interview::getId, Function.identity()));
        List<ReportDtos.TrendPoint> trends = reportMapper.selectList(new LambdaQueryWrapper<Report>().eq(Report::getStatus, 1))
                .stream().map(report -> toTrendPoint(report, interviews.get(report.getInterviewId())))
                .filter(Objects::nonNull).sorted(Comparator.comparing(ReportDtos.TrendPoint::scheduledAt)).toList();
        ReportDtos.TrendPoint latest = trends.isEmpty() ? null : trends.get(trends.size() - 1);
        ReportDtos.TrendPoint previous = trends.size() < 2 ? null : trends.get(trends.size() - 2);
        return new ReportDtos.CandidateAbilitySummary(trends.size(), latest, previous, scoreChange(latest, previous), trends);
    }
    private ReportDtos.ReportListItem toListItem(Report report, Interview interview, Map<Long, UserAccount> candidates) {
        if (interview == null) return null;
        UserAccount candidate = candidates.get(interview.getCandidateId());
        return new ReportDtos.ReportListItem(report.getId(), report.getInterviewId(), interview.getTitle(), interview.getCandidateId(),
                candidate == null ? "候选人" : candidate.getRealName(), candidate == null ? "" : candidate.getUsername(), interview.getScheduledAt(),
                report.getTotalScore(), report.getProfessionalScore(), report.getExpressionScore(), report.getLogicScore(), report.getAdaptabilityScore(), report.getStatus(), report.getPublishedAt());
    }
    private ReportDtos.TrendPoint toTrendPoint(Report report, Interview interview) {
        if (interview == null) return null;
        return new ReportDtos.TrendPoint(interview.getId(), interview.getTitle(), interview.getScheduledAt(), report.getTotalScore(),
                report.getProfessionalScore(), report.getExpressionScore(), report.getLogicScore(), report.getAdaptabilityScore());
    }
    private ReportDtos.ScoreChange scoreChange(ReportDtos.TrendPoint latest, ReportDtos.TrendPoint previous) {
        if (latest == null || previous == null) return new ReportDtos.ScoreChange(BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO);
        return new ReportDtos.ScoreChange(diff(latest.totalScore(), previous.totalScore()), diff(latest.professionalScore(), previous.professionalScore()),
                diff(latest.expressionScore(), previous.expressionScore()), diff(latest.logicScore(), previous.logicScore()), diff(latest.adaptabilityScore(), previous.adaptabilityScore()));
    }
    private BigDecimal diff(BigDecimal left, BigDecimal right) { return left.subtract(right).setScale(2, RoundingMode.HALF_UP); }
    private boolean contains(String value, String keyword) { return value != null && value.toLowerCase().contains(keyword); }
    private BigDecimal average(List<Evaluation> records, Function<Evaluation, BigDecimal> getter) { return records.stream().map(getter).reduce(BigDecimal.ZERO, BigDecimal::add).divide(BigDecimal.valueOf(records.size()), 2, RoundingMode.HALF_UP); }
    private BigDecimal reportTotalScore(List<Evaluation> evaluations) {
        BigDecimal professional = average(evaluations, Evaluation::getProfessionalScore);
        BigDecimal expression = average(evaluations, Evaluation::getExpressionScore);
        BigDecimal logic = average(evaluations, Evaluation::getLogicScore);
        BigDecimal adaptability = average(evaluations, Evaluation::getAdaptabilityScore);
        BigDecimal overall = average(evaluations, Evaluation::getOverallScore);
        BigDecimal weightedDimensions = professional.multiply(BigDecimal.valueOf(0.45))
                .add(logic.multiply(BigDecimal.valueOf(0.25)))
                .add(expression.multiply(BigDecimal.valueOf(0.20)))
                .add(adaptability.multiply(BigDecimal.valueOf(0.10)));
        return overall.multiply(BigDecimal.valueOf(0.65))
                .add(weightedDimensions.multiply(BigDecimal.valueOf(0.35)))
                .setScale(2, RoundingMode.HALF_UP);
    }
    static String reliabilityWarning(long questionCount) {
        if (questionCount >= MIN_RELIABLE_QUESTION_COUNT) return null;
        return "本次面试仅包含 " + questionCount + " 道题，题目样本过少，评测结果参考性有限，不能充分代表候选人的整体岗位能力。";
    }
    private void requireHr() { if (!currentUser.hasRole("ADMIN")) throw BusinessException.forbidden("仅管理员可生成报告"); }

    private String trainingContext(Interview interview, Report report) {
        return """
                面试主题：%s
                综合分：%s
                专业能力：%s
                表达能力：%s
                逻辑思维：%s
                应变能力：%s
                综合结论：%s
                优势：%s
                待提升项：%s
                改进建议：%s
                """.formatted(interview.getTitle(), report.getTotalScore(), report.getProfessionalScore(), report.getExpressionScore(),
                report.getLogicScore(), report.getAdaptabilityScore(), report.getSummary(), report.getStrengths(),
                report.getWeaknesses(), report.getImprovementSuggestions());
    }

    private ReportDtos.TrainingPlan fallbackPlan(Report report) {
        return new ReportDtos.TrainingPlan(weakestPriority(report), 7, fallbackFocusAreas(report), fallbackDays(report),
                fallbackBanks(report), fallbackDrills(report), fallbackCriteria(report), "rule");
    }

    private List<String> fallbackFocusAreas(Report report) {
        String weakest = weakestDimension(report);
        return switch (weakest) {
            case "专业能力" -> List.of("补齐 Java / MySQL / Spring 核心知识", "每道题回答必须覆盖原理、场景、边界和例子", "用题库进行专项强化");
            case "表达能力" -> List.of("使用 STAR / 背景-行动-结果 结构回答", "练习两分钟项目复盘", "减少口头禅和跳跃式表达");
            case "逻辑思维" -> List.of("训练问题拆解和推理链路", "用算法题和系统设计题练习边界分析", "回答前先给结论再展开依据");
            default -> List.of("强化追问下的临场反应", "练习不知道时的拆解表达", "模拟压力追问并复盘调整");
        };
    }

    private List<ReportDtos.TrainingDay> fallbackDays(Report report) {
        String weakest = weakestDimension(report);
        return List.of(
                day(1, "定位短板", "复盘本次报告，整理最低分维度的 5 个具体问题", "选 10 道相关题目做口头回答", "记录每题回答是否包含结论、原因和例子"),
                day(2, weakest + "专项训练", "完成 20 道专项题", "每题控制在 2 分钟内回答", "用录音回听并标记表达断点"),
                day(3, "项目表达重构", "准备一个项目的背景、职责、难点、结果", "用 STAR 结构输出 2 版回答", "补充数据结果和个人贡献"),
                day(4, "追问强化", "针对 5 个回答继续自问 2 层为什么", "练习边界、异常和取舍说明", "把不会的问题转化为分析过程"),
                day(5, "综合模拟", "完成一场 30 分钟模拟面试", "重点观察最低分维度是否提升", "记录 3 个仍然卡住的问题"),
                day(6, "查漏补缺", "补齐前一天卡住的问题", "整理一页常用回答模板", "再完成 10 道同类题"),
                day(7, "复测与总结", "重新进行一次模拟面试", "对比本次报告与上次报告", "沉淀下一轮训练目标"));
    }

    private ReportDtos.TrainingDay day(int day, String title, String... tasks) {
        return new ReportDtos.TrainingDay(day, title, List.of(tasks));
    }

    private List<String> fallbackBanks(Report report) {
        return switch (weakestDimension(report)) {
            case "专业能力" -> List.of("Java 核心基础题库", "MySQL 数据库题库", "Spring 与微服务题库");
            case "表达能力" -> List.of("HR 综合素质题库", "项目复盘表达训练", "STAR 行为面试训练");
            case "逻辑思维" -> List.of("算法与数据结构题库", "系统设计场景题", "数据库性能优化题");
            default -> List.of("压力追问模拟", "项目深挖模拟", "综合素质面试");
        };
    }

    private List<String> fallbackDrills(Report report) {
        return List.of("温和型面试官完成基础复盘", "大厂技术面模式完成技术深挖", "压迫型面试官完成抗压追问");
    }

    private List<String> fallbackCriteria(Report report) {
        return List.of("最低分维度提升 8 分以上", "每个核心问题能在 2 分钟内结构化回答", "至少完成 2 次完整模拟面试并生成报告");
    }

    private String weakestPriority(Report report) {
        return "当前最需要优先提升：" + weakestDimension(report) + "。建议先完成 7 天专项训练，再进行一次完整模拟复测。";
    }

    private String weakestDimension(Report report) {
        BigDecimal min = report.getProfessionalScore();
        String label = "专业能力";
        if (report.getExpressionScore().compareTo(min) < 0) { min = report.getExpressionScore(); label = "表达能力"; }
        if (report.getLogicScore().compareTo(min) < 0) { min = report.getLogicScore(); label = "逻辑思维"; }
        if (report.getAdaptabilityScore().compareTo(min) < 0) label = "应变能力";
        return label;
    }

    private String text(JsonNode node, String field, String fallback) {
        String value = node.path(field).asText("");
        return value.isBlank() ? fallback : value;
    }

    private Integer integer(JsonNode node, String field, Integer fallback) {
        return node.path(field).canConvertToInt() ? node.path(field).asInt() : fallback;
    }

    private List<String> texts(JsonNode node, List<String> fallback) {
        if (!node.isArray() || node.isEmpty()) return fallback;
        List<String> values = new java.util.ArrayList<>();
        node.forEach(item -> {
            String value = item.asText("");
            if (!value.isBlank()) values.add(value);
        });
        return values.isEmpty() ? fallback : values;
    }

    private List<ReportDtos.TrainingDay> days(JsonNode node, List<ReportDtos.TrainingDay> fallback) {
        if (!node.isArray() || node.isEmpty()) return fallback;
        List<ReportDtos.TrainingDay> values = new java.util.ArrayList<>();
        node.forEach(item -> values.add(new ReportDtos.TrainingDay(item.path("day").asInt(values.size() + 1),
                text(item, "title", "专项训练"), texts(item.path("tasks"), List.of("完成专项练习并复盘")))));
        return values.isEmpty() ? fallback : values;
    }
}
