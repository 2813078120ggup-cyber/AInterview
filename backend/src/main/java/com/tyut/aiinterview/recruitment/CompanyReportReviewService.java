package com.tyut.aiinterview.recruitment;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tyut.aiinterview.ai.AiTaskService;
import com.tyut.aiinterview.common.BusinessException;
import com.tyut.aiinterview.domain.AiTask;
import com.tyut.aiinterview.domain.Evaluation;
import com.tyut.aiinterview.domain.Interview;
import com.tyut.aiinterview.domain.InterviewAnswer;
import com.tyut.aiinterview.domain.InterviewQuestion;
import com.tyut.aiinterview.domain.JobApplication;
import com.tyut.aiinterview.domain.Report;
import com.tyut.aiinterview.mapper.AiTaskMapper;
import com.tyut.aiinterview.mapper.EvaluationMapper;
import com.tyut.aiinterview.mapper.InterviewAnswerMapper;
import com.tyut.aiinterview.mapper.InterviewQuestionMapper;
import com.tyut.aiinterview.mapper.ReportMapper;
import com.tyut.aiinterview.recording.InterviewRecordingService;
import com.tyut.aiinterview.recording.RecordingDtos;
import com.tyut.aiinterview.observability.OperationAuditService;
import com.tyut.aiinterview.notification.SiteNotificationService;
import com.tyut.aiinterview.report.ReportDtos;
import com.tyut.aiinterview.report.ReportService;
import com.tyut.aiinterview.security.CurrentUser;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

/**
 * Company-only report review facade. It deliberately returns a structured
 * allowlist instead of exposing report generation payloads or generic admin
 * report objects.
 */
@Service
public class CompanyReportReviewService {
    private final CompanyAccessService companyAccess;
    private final ReportMapper reportMapper;
    private final InterviewQuestionMapper questionMapper;
    private final InterviewAnswerMapper answerMapper;
    private final EvaluationMapper evaluationMapper;
    private final AiTaskMapper taskMapper;
    private final InterviewRecordingService recordingService;
    private final AiTaskService aiTaskService;
    private final CurrentUser currentUser;
    private final ObjectMapper objectMapper;
    private final OperationAuditService auditService;
    private final SiteNotificationService notificationService;

    public CompanyReportReviewService(CompanyAccessService companyAccess, ReportMapper reportMapper,
                                      InterviewQuestionMapper questionMapper, InterviewAnswerMapper answerMapper,
                                      EvaluationMapper evaluationMapper, AiTaskMapper taskMapper,
                                      InterviewRecordingService recordingService, AiTaskService aiTaskService,
                                      CurrentUser currentUser, ObjectMapper objectMapper) {
        this(companyAccess, reportMapper, questionMapper, answerMapper, evaluationMapper, taskMapper,
                recordingService, aiTaskService, currentUser, objectMapper, null, null);
    }

    @Autowired
    public CompanyReportReviewService(CompanyAccessService companyAccess, ReportMapper reportMapper,
                                      InterviewQuestionMapper questionMapper, InterviewAnswerMapper answerMapper,
                                      EvaluationMapper evaluationMapper, AiTaskMapper taskMapper,
                                      InterviewRecordingService recordingService, AiTaskService aiTaskService,
                                      CurrentUser currentUser, ObjectMapper objectMapper,
                                      OperationAuditService auditService,
                                      SiteNotificationService notificationService) {
        this.companyAccess = companyAccess;
        this.reportMapper = reportMapper;
        this.questionMapper = questionMapper;
        this.answerMapper = answerMapper;
        this.evaluationMapper = evaluationMapper;
        this.taskMapper = taskMapper;
        this.recordingService = recordingService;
        this.aiTaskService = aiTaskService;
        this.currentUser = currentUser;
        this.objectMapper = objectMapper;
        this.auditService = auditService;
        this.notificationService = notificationService;
    }

    public ReportDtos.CompanyReportDetail companyDetail(Long applicationId) {
        companyAccess.requireAnyPermission("interview:review", "report:read");
        JobApplication application = companyAccess.requireApplication(applicationId);
        if (application.getInterviewId() == null) return notAvailable(applicationId);

        Interview interview = companyAccess.requireInterviewForApplication(application);
        Report report = findReport(interview.getId());
        AiTask task = latestEvaluationTask(interview.getId());
        List<InterviewQuestion> questions = questions(interview.getId());
        RecordingDtos.RecordingView recording = recordingService.companyView(interview.getId());
        return toDetail(applicationId, interview, report, task, questions, recording);
    }

    @Transactional
    public ReportDtos.CompanyReportDetail retry(Long applicationId) {
        companyAccess.requirePermission("interview:review");
        JobApplication application = companyAccess.requireApplication(applicationId);
        Interview interview = companyAccess.requireInterviewForApplication(application);
        aiTaskService.retryAutomaticEvaluation(interview.getId());
        if (auditService != null) auditService.success("REPORT", "REPORT_GENERATION_RETRIED", "REPORT",
                interview.getId(), application.getCompanyId(), "重试面试报告生成");
        return companyDetail(applicationId);
    }

    @Transactional
    public ReportDtos.CompanyReportDetail publish(Long applicationId) {
        companyAccess.requirePermission("interview:review");
        JobApplication application = companyAccess.requireApplication(applicationId);
        Interview interview = companyAccess.requireInterviewForApplication(application);
        Report report = findReport(interview.getId());
        if (report == null) throw BusinessException.notFound("报告尚未生成");
        if (!Integer.valueOf(1).equals(report.getStatus())) {
            report.setHumanReviewRequired(1);
            report.setHumanReviewStatus("APPROVED");
            report.setHumanReviewDecision("PUBLISH");
            report.setHumanReviewNote("已由企业复核并发布给候选人");
            report.setHumanReviewedBy(currentUser.id());
            report.setHumanReviewedAt(LocalDateTime.now());
            report.setStatus(1);
            report.setPublishedAt(LocalDateTime.now());
            reportMapper.updateById(report);
            if (auditService != null) auditService.success("REPORT", "REPORT_PUBLISHED", "REPORT", report.getId(),
                    application.getCompanyId(), "企业内部发布面试报告给候选人");
            if (notificationService != null) {
                notificationService.create(interview.getCandidateId(), "REPORT_PUBLISHED", "面试报告已发布",
                        "你的面试评测报告已发布，可以前往评测报告查看。", "REPORT", report.getId(),
                        "report-published-" + report.getId());
            }
        }
        return companyDetail(applicationId);
    }

    private ReportDtos.CompanyReportDetail notAvailable(Long applicationId) {
        return new ReportDtos.CompanyReportDetail(applicationId, null, null, null, null, null, null, null,
                null, null, null, null, null, null, null, 0, null,
                "NOT_AVAILABLE", null, null, "当前申请尚未关联 AI 面试。", false, List.of(), null,
                false, "NOT_REQUIRED", null, null, null, null);
    }

    private ReportDtos.CompanyReportDetail toDetail(Long applicationId, Interview interview, Report report,
                                                     AiTask task, List<InterviewQuestion> questions,
                                                     RecordingDtos.RecordingView recording) {
        String reportStatus = reportStatus(interview, report, task);
        boolean canRetry = currentUser.hasPermission("interview:review")
                && ("FAILED".equals(reportStatus) || (report == null && task == null
                && (interview.getStatus() == Interview.COMPLETED
                || interview.getStatus() == Interview.REPORT_GENERATING
                || interview.getStatus() == Interview.FAILED)));
        String taskMessage = task == null ? null : switch (task.getStatus()) {
            case "FAILED" -> "AI 报告生成失败，请重试。";
            case "RUNNING" -> "AI 报告正在生成，请稍候。";
            case "PENDING" -> "AI 报告已排队，等待生成。";
            default -> null;
        };
        List<ReportDtos.CompanyQuestionReview> reviews = questionReviews(interview.getId(), questions);
        return new ReportDtos.CompanyReportDetail(applicationId,
                report == null ? null : report.getId(), interview.getId(),
                report == null ? null : report.getTotalScore(),
                report == null ? null : report.getProfessionalScore(),
                report == null ? null : report.getExpressionScore(),
                report == null ? null : report.getLogicScore(),
                report == null ? null : report.getAdaptabilityScore(),
                report == null ? null : report.getSummary(),
                report == null ? null : report.getStrengths(),
                report == null ? null : report.getWeaknesses(),
                report == null ? null : report.getImprovementSuggestions(),
                report == null ? null : report.getStatus(),
                report == null ? null : report.getGeneratedAt(),
                report == null ? null : report.getPublishedAt(), questions.size(),
                ReportService.reliabilityWarning(questions.size()), reportStatus,
                task == null ? null : task.getStatus(), task == null ? null : task.getAttempts(),
                taskMessage, canRetry, reviews, recording,
                report != null && Integer.valueOf(1).equals(report.getHumanReviewRequired()),
                report == null ? null : report.getHumanReviewStatus(),
                report == null ? null : report.getHumanReviewDecision(),
                report == null ? null : report.getHumanReviewNote(),
                report == null ? null : report.getHumanReviewedBy(),
                report == null ? null : report.getHumanReviewedAt());
    }

    private List<InterviewQuestion> questions(Long interviewId) {
        return questionMapper.selectList(new LambdaQueryWrapper<InterviewQuestion>()
                .eq(InterviewQuestion::getInterviewId, interviewId)
                .orderByAsc(InterviewQuestion::getSequenceNo)
                .orderByAsc(InterviewQuestion::getId));
    }

    private List<ReportDtos.CompanyQuestionReview> questionReviews(Long interviewId,
                                                                    List<InterviewQuestion> questions) {
        if (questions.isEmpty()) return List.of();
        List<Long> questionIds = questions.stream().map(InterviewQuestion::getId).toList();
        List<InterviewAnswer> answers = answerMapper.selectList(new LambdaQueryWrapper<InterviewAnswer>()
                .in(InterviewAnswer::getInterviewQuestionId, questionIds));
        Map<Long, InterviewAnswer> answerByQuestion = answers.stream()
                .collect(java.util.stream.Collectors.toMap(InterviewAnswer::getInterviewQuestionId,
                        item -> item, (left, right) -> right));
        Map<Long, Evaluation> evaluationByQuestion = new LinkedHashMap<>();
        evaluationMapper.selectList(new LambdaQueryWrapper<Evaluation>()
                        .in(Evaluation::getInterviewQuestionId, questionIds)
                        .orderByDesc(Evaluation::getId))
                .forEach(item -> evaluationByQuestion.putIfAbsent(item.getInterviewQuestionId(), item));
        Map<Long, List<String>> followUps = followUps(interviewId, answers);
        return questions.stream().map(question -> {
            InterviewAnswer answer = answerByQuestion.get(question.getId());
            Evaluation evaluation = evaluationByQuestion.get(question.getId());
            return new ReportDtos.CompanyQuestionReview(question.getId(), question.getSequenceNo(),
                    questionText(question), questionType(question), readableAnswer(answer),
                    answer == null ? null : answer.getAnsweredAt(),
                    followUps.getOrDefault(question.getId(), List.of()), evaluationView(evaluation));
        }).toList();
    }

    private Map<Long, List<String>> followUps(Long interviewId, List<InterviewAnswer> answers) {
        Map<Long, Long> questionByAnswer = answers.stream().filter(item -> item.getId() != null)
                .collect(java.util.stream.Collectors.toMap(InterviewAnswer::getId,
                        InterviewAnswer::getInterviewQuestionId, (left, right) -> left));
        Map<Long, List<String>> result = new HashMap<>();
        List<AiTask> tasks = taskMapper.selectList(new LambdaQueryWrapper<AiTask>()
                .eq(AiTask::getInterviewId, interviewId)
                .eq(AiTask::getTaskType, AiTaskService.FOLLOW_UP)
                .eq(AiTask::getStatus, "SUCCESS")
                .orderByAsc(AiTask::getId));
        for (AiTask task : tasks) {
            Long questionId = questionByAnswer.get(task.getAnswerId());
            String followUp = followUpText(task.getOutputPayload());
            if (questionId != null && followUp != null) {
                result.computeIfAbsent(questionId, ignored -> new ArrayList<>()).add(followUp);
            }
        }
        return result;
    }

    private ReportDtos.CompanyEvaluationView evaluationView(Evaluation evaluation) {
        if (evaluation == null) return null;
        return new ReportDtos.CompanyEvaluationView(evaluation.getProfessionalScore(),
                evaluation.getExpressionScore(), evaluation.getLogicScore(),
                evaluation.getAdaptabilityScore(), evaluation.getOverallScore(),
                evaluation.getComment(), evaluation.getSource(), evaluation.getStatus());
    }

    private String questionText(InterviewQuestion question) {
        JsonNode node = parse(question.getQuestionSnapshot());
        if (node == null) return "面试题目";
        return node.isTextual() ? node.asText() : text(node, "content", "面试题目");
    }

    private String questionType(InterviewQuestion question) {
        JsonNode node = parse(question.getQuestionSnapshot());
        return node == null || node.isTextual() ? null : text(node, "questionType", null);
    }

    private String readableAnswer(InterviewAnswer answer) {
        if (answer == null) return "未作答";
        if (hasText(answer.getAnswerContent())) return answer.getAnswerContent().trim();
        if (hasText(answer.getTranscript())) return answer.getTranscript().trim();
        JsonNode node = parse(answer.getAnswerData());
        if (node == null) return "已提交作答";
        for (String field : List.of("selected", "selectedOption", "value", "answer", "text", "content")) {
            String value = readableNode(node.get(field));
            if (hasText(value)) return value;
        }
        return "已提交结构化作答";
    }

    private String followUpText(String payload) {
        JsonNode node = parse(payload);
        return node == null ? null : trimToNull(text(node, "followUp", null));
    }

    private String readableNode(JsonNode node) {
        if (node == null || node.isNull()) return null;
        if (node.isArray()) {
            List<String> values = new ArrayList<>();
            node.forEach(item -> { if (item.isValueNode()) values.add(item.asText()); });
            return values.isEmpty() ? null : String.join("、", values);
        }
        return node.isValueNode() ? node.asText() : null;
    }

    private JsonNode parse(String value) {
        if (!hasText(value)) return null;
        try {
            return objectMapper.readTree(value);
        } catch (Exception ignored) {
            return null;
        }
    }

    private String text(JsonNode node, String field, String fallback) {
        JsonNode value = node == null ? null : node.get(field);
        return value == null || value.isNull() || !value.isValueNode() ? fallback : value.asText(fallback);
    }

    private String reportStatus(Interview interview, Report report, AiTask task) {
        if (report != null) return Integer.valueOf(1).equals(report.getStatus()) ? "PUBLISHED" : "READY";
        if (task != null) {
            if ("FAILED".equals(task.getStatus())) return "FAILED";
            if ("RUNNING".equals(task.getStatus())) return "RUNNING";
            if ("PENDING".equals(task.getStatus())) return "PENDING";
        }
        if (interview.getStatus() == Interview.FAILED) return "FAILED";
        if (interview.getStatus() == Interview.REPORT_GENERATING) return "PENDING";
        return "NOT_AVAILABLE";
    }

    private Report findReport(Long interviewId) {
        return reportMapper.selectOne(new LambdaQueryWrapper<Report>()
                .eq(Report::getInterviewId, interviewId).last("LIMIT 1"));
    }

    private AiTask latestEvaluationTask(Long interviewId) {
        return taskMapper.selectOne(new LambdaQueryWrapper<AiTask>()
                .eq(AiTask::getInterviewId, interviewId)
                .eq(AiTask::getTaskType, AiTaskService.AUTO_EVALUATION)
                .orderByDesc(AiTask::getId).last("LIMIT 1"));
    }

    private boolean hasText(String value) { return value != null && !value.isBlank(); }
    private String trimToNull(String value) { return hasText(value) ? value.trim() : null; }
}
