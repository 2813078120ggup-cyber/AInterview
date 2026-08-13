package com.tyut.aiinterview.ai;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tyut.aiinterview.common.BusinessException;
import com.tyut.aiinterview.domain.AiTask;
import com.tyut.aiinterview.domain.Evaluation;
import com.tyut.aiinterview.domain.FreeInterviewSession;
import com.tyut.aiinterview.domain.FreeInterviewTurn;
import com.tyut.aiinterview.domain.Interview;
import com.tyut.aiinterview.domain.InterviewAnswer;
import com.tyut.aiinterview.domain.InterviewQuestion;
import com.tyut.aiinterview.domain.Question;
import com.tyut.aiinterview.domain.Report;
import com.tyut.aiinterview.mapper.AiTaskMapper;
import com.tyut.aiinterview.mapper.EvaluationMapper;
import com.tyut.aiinterview.mapper.FreeInterviewSessionMapper;
import com.tyut.aiinterview.mapper.FreeInterviewTurnMapper;
import com.tyut.aiinterview.mapper.InterviewAnswerMapper;
import com.tyut.aiinterview.mapper.InterviewMapper;
import com.tyut.aiinterview.mapper.InterviewQuestionMapper;
import com.tyut.aiinterview.mapper.QuestionMapper;
import com.tyut.aiinterview.mapper.ReportMapper;
import com.tyut.aiinterview.observability.OperationAuditService;
import com.tyut.aiinterview.prompt.PromptCatalog;
import com.tyut.aiinterview.prompt.PromptTemplateService;
import com.tyut.aiinterview.recruitment.RecruitmentResumeAnalysisService;
import com.tyut.aiinterview.recruitment.RecruitmentJobMatchService;
import com.tyut.aiinterview.recruitment.CompanyAccessService;
import com.tyut.aiinterview.security.CurrentUser;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.Executor;
import java.util.function.Function;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AiTaskService {
    public static final String FOLLOW_UP = "FOLLOW_UP";
    public static final String OPENING = "OPENING";
    public static final String AUTO_EVALUATION = "AUTO_EVALUATION";
    public static final String FREE_INTERVIEW_ANALYSIS = "FREE_ANALYSIS";
    public static final String FREE_INTERVIEW_FOLLOW_UP = "FREE_FOLLOW_UP";
    public static final String FREE_INTERVIEW_REPORT = "FREE_REPORT";
    public static final String RESUME_PARSE = "RESUME_PARSE";
    public static final String JOB_MATCH = "JOB_MATCH";

    private final AiTaskMapper taskMapper;
    private final InterviewMapper interviewMapper;
    private final InterviewQuestionMapper interviewQuestionMapper;
    private final InterviewAnswerMapper answerMapper;
    private final QuestionMapper questionMapper;
    private final EvaluationMapper evaluationMapper;
    private final ReportMapper reportMapper;
    private final FreeInterviewSessionMapper freeSessionMapper;
    private final FreeInterviewTurnMapper freeTurnMapper;
    private final DeepSeekGateway deepSeekGateway;
    private final PromptTemplateService promptTemplates;
    private final ChoiceAnswerScorer choiceAnswerScorer;
    private final ObjectMapper objectMapper;
    private final SimulationFollowUpPolicy simulationFollowUpPolicy;
    private final FollowUpQualityGuard followUpQualityGuard;
    private final CurrentUser currentUser;
    private final Executor aiTaskWorkerExecutor;
    private final Executor reportScoringExecutor;
    private final RecruitmentResumeAnalysisService recruitmentResumeAnalysisService;
    private final int taskFetchLimit;
    private RecruitmentJobMatchService recruitmentJobMatchService;
    private CompanyAccessService companyAccessService;
    private OperationAuditService operationAuditService;
    @Value("${app.ai-task.scheduler-enabled:true}")
    private boolean schedulerEnabled = true;

    public AiTaskService(AiTaskMapper taskMapper, InterviewMapper interviewMapper, InterviewQuestionMapper interviewQuestionMapper,
                         InterviewAnswerMapper answerMapper, QuestionMapper questionMapper, EvaluationMapper evaluationMapper,
                         ReportMapper reportMapper, FreeInterviewSessionMapper freeSessionMapper,
                         FreeInterviewTurnMapper freeTurnMapper, DeepSeekGateway deepSeekGateway,
                         PromptTemplateService promptTemplates, ChoiceAnswerScorer choiceAnswerScorer,
                         ObjectMapper objectMapper, CurrentUser currentUser,
                         RecruitmentResumeAnalysisService recruitmentResumeAnalysisService,
                         @Qualifier("aiTaskWorkerExecutor") Executor aiTaskWorkerExecutor,
                         @Qualifier("reportScoringExecutor") Executor reportScoringExecutor,
                         @Value("${app.ai-task.fetch-limit:12}") int taskFetchLimit) {
        this.taskMapper = taskMapper;
        this.interviewMapper = interviewMapper;
        this.interviewQuestionMapper = interviewQuestionMapper;
        this.answerMapper = answerMapper;
        this.questionMapper = questionMapper;
        this.evaluationMapper = evaluationMapper;
        this.reportMapper = reportMapper;
        this.freeSessionMapper = freeSessionMapper;
        this.freeTurnMapper = freeTurnMapper;
        this.deepSeekGateway = deepSeekGateway;
        this.promptTemplates = promptTemplates;
        this.choiceAnswerScorer = choiceAnswerScorer;
        this.objectMapper = objectMapper;
        this.simulationFollowUpPolicy = new SimulationFollowUpPolicy(objectMapper);
        this.followUpQualityGuard = new FollowUpQualityGuard();
        this.currentUser = currentUser;
        this.recruitmentResumeAnalysisService = recruitmentResumeAnalysisService;
        this.aiTaskWorkerExecutor = aiTaskWorkerExecutor;
        this.reportScoringExecutor = reportScoringExecutor;
        this.taskFetchLimit = Math.max(1, Math.min(50, taskFetchLimit));
    }

    @Transactional
    public AiTask requestFollowUp(Long interviewId, Long interviewQuestionId, String answer) {
        Interview interview = requireInterview(interviewId);
        requireParticipant(interview);
        if (interview.getStatus() != Interview.IN_PROGRESS) throw BusinessException.badRequest("仅进行中的面试可生成追问");
        InterviewQuestion interviewQuestion = interviewQuestionMapper.selectById(interviewQuestionId);
        if (interviewQuestion == null || !interviewId.equals(interviewQuestion.getInterviewId())) {
            throw BusinessException.notFound("面试题目不存在");
        }
        if (isChoiceQuestion(interviewQuestion)) {
            throw BusinessException.badRequest("选择题提交后将直接进入下一题，不生成 AI 追问");
        }
        InterviewAnswer savedAnswer = answerMapper.selectOne(new LambdaQueryWrapper<InterviewAnswer>()
                .eq(InterviewAnswer::getInterviewQuestionId, interviewQuestionId).last("LIMIT 1"));
        if (savedAnswer == null) throw BusinessException.badRequest("请先保存当前题目的回答，再生成追问");
        String storedQuestion = questionContent(interviewQuestion);
        if (storedQuestion.isBlank()) throw BusinessException.badRequest("当前面试题目缺少题目内容");
        SimulationFollowUpPolicy.State state = simulationFollowUpPolicy.inspect(savedAnswer.getAnswerData(), storedQuestion);
        if (state.limitReached()) throw BusinessException.badRequest("本题已达到 3 次追问上限，请进入下一题");
        String latestAnswer = firstNonBlank(state.latestCandidateAnswer(), answer);
        String dedupeKey = "simulation-follow-up:" + interviewQuestionId + ":" + state.nextSequence();
        return enqueue(interviewId, savedAnswer.getId(), FOLLOW_UP, dedupeKey,
                json("answer", latestAnswer, "question", storedQuestion, "interviewerStyle", interviewerStyle(interview),
                        "conversationContext", state.conversationContext(),
                        "previousFollowUps", String.join("\u001E", state.previousFollowUps())));
    }

    @Transactional
    public AiTask requestOpening(Long interviewId) {
        Interview interview = requireInterview(interviewId);
        requireCandidate(interview);
        if (interview.getStatus() != Interview.IN_PROGRESS) throw BusinessException.badRequest("仅进行中的面试可生成 AI 开场问题");
        InterviewQuestion first = interviewQuestionMapper.selectOne(new LambdaQueryWrapper<InterviewQuestion>()
                .eq(InterviewQuestion::getInterviewId, interviewId).orderByAsc(InterviewQuestion::getSequenceNo).last("LIMIT 1"));
        if (first == null) throw BusinessException.badRequest("本场面试尚未配置题目");
        String question = questionContent(first);
        if (question.isBlank()) throw new IllegalStateException("面试首题快照缺少内容");
        return enqueue(interviewId, null, OPENING, "opening:" + interviewId, json("question", question, "interviewerStyle", interviewerStyle(interview)));
    }

    /** Called only after the interview status has been atomically changed to report-generating. */
    @Transactional
    public AiTask enqueueAutomaticEvaluation(Interview interview) {
        if (interview.getStatus() != Interview.REPORT_GENERATING) {
            throw new IllegalArgumentException("仅已结束面试可创建自动评分任务");
        }
        return enqueue(interview.getId(), null, AUTO_EVALUATION, "evaluation:" + interview.getId(),
                json("interviewId", interview.getId()));
    }

    @Transactional
    public AiTask enqueueFreeInterviewAnalysis(Long sessionId) {
        return enqueue(null, null, FREE_INTERVIEW_ANALYSIS, "free-analysis:" + sessionId,
                json("sessionId", sessionId), 2);
    }

    @Transactional
    public AiTask enqueueFreeInterviewFollowUp(Long sessionId, Long turnId) {
        return enqueue(null, null, FREE_INTERVIEW_FOLLOW_UP, "free-follow-up:" + turnId,
                json("sessionId", sessionId, "turnId", turnId), 1);
    }

    @Transactional
    public AiTask enqueueFreeInterviewReport(Long sessionId) {
        return enqueue(null, null, FREE_INTERVIEW_REPORT, "free-report:" + sessionId,
                json("sessionId", sessionId), 3);
    }

    @Transactional
    public AiTask enqueueResumeAnalysis(Long resumeId, Long analysisId, int analysisVersion) {
        return enqueue(null, null, RESUME_PARSE, "resume-parse:" + resumeId + ":" + analysisVersion,
                json("resumeId", resumeId, "analysisId", analysisId, "analysisVersion", analysisVersion), 3);
    }

    public AiTask enqueueJobMatch(Long applicationId, Long positionId, Long resumeId, int resumeVersion,
                                  int evaluationVersion, Long createdBy) {
        return enqueue(null, null, JOB_MATCH, "job-match:" + applicationId + ":" + evaluationVersion,
                json("applicationId", applicationId, "positionId", positionId, "resumeId", resumeId,
                        "resumeVersion", resumeVersion, "evaluationVersion", evaluationVersion), 3, createdBy);
    }

    @Transactional
    public AiTask retryAutomaticEvaluation(Long interviewId) {
        Interview interview = requireInterview(interviewId);
        if (currentUser.hasRole("ADMIN")) {
            // Platform operations are already protected by the admin controller
            // and may retry a failed technical report task without being an
            // interview participant.
        } else if (currentUser.hasCompanyRole() && companyAccessService != null) {
            companyAccessService.requireAuthorizedInterview(interviewId);
        } else {
            requireParticipant(interview);
        }
        if (interview.getStatus() == Interview.REPORT_READY) {
            return latestEvaluationTask(interviewId);
        }
        if (interview.getStatus() != Interview.COMPLETED && interview.getStatus() != Interview.REPORT_GENERATING && interview.getStatus() != Interview.FAILED) {
            throw BusinessException.badRequest("当前面试状态不允许重新生成报告");
        }
        if (interview.getStatus() != Interview.REPORT_GENERATING) {
            interview.setStatus(Interview.REPORT_GENERATING);
            if (interview.getEndedAt() == null) interview.setEndedAt(LocalDateTime.now());
            interviewMapper.updateById(interview);
        }
        return enqueueAutomaticEvaluation(interview);
    }

    @Transactional
    public AiTask regenerateAutomaticEvaluation(Long interviewId) {
        Interview interview = requireInterview(interviewId);
        if (!currentUser.hasRole("ADMIN")) throw BusinessException.forbidden("仅管理员可重新评分已生成的报告");
        if (interview.getStatus() != Interview.REPORT_READY && interview.getStatus() != Interview.COMPLETED
                && interview.getStatus() != Interview.FAILED) {
            throw BusinessException.badRequest("当前面试状态不允许重新评分");
        }
        interview.setStatus(Interview.REPORT_GENERATING);
        if (interview.getEndedAt() == null) interview.setEndedAt(LocalDateTime.now());
        interviewMapper.updateById(interview);

        String payload = json("interviewId", interviewId);
        AiTask existing = taskMapper.selectOne(new LambdaQueryWrapper<AiTask>()
                .eq(AiTask::getDedupeKey, "evaluation:" + interviewId).last("LIMIT 1"));
        if (existing == null) return enqueueAutomaticEvaluation(interview);
        resetTask(existing, payload, 3);
        return existing;
    }

    /**
     * Platform operations may retry only recruitment technical tasks. The
     * existing dedupe key and payload are reused, so repeated requests never
     * create a second task or advance a business decision.
     */
    @Transactional
    public AiTask retryAdminRecruitmentTask(Long taskId) {
        if (!currentUser.hasRole("ADMIN")) throw BusinessException.forbidden("仅超级管理员可重试招聘技术任务");
        AiTask task = taskMapper.selectById(taskId);
        if (task == null) throw BusinessException.notFound("AI 任务不存在");
        if (!Set.of(JOB_MATCH, AUTO_EVALUATION).contains(task.getTaskType())) {
            throw BusinessException.badRequest("该任务类型不支持招聘运营重试");
        }
        if (!"FAILED".equals(task.getStatus())) return task;
        if (!isTechnicalRecruitmentFailure(task)) {
            throw BusinessException.badRequest("该任务属于业务数据问题，不支持平台自动重试");
        }
        if (AUTO_EVALUATION.equals(task.getTaskType())) {
            return retryAutomaticEvaluation(task.getInterviewId());
        }
        resetTask(task, task.getInputPayload(), task.getMaxAttempts(), currentUser.id());
        return task;
    }

    /**
     * Platform AI operations retry an existing failed task in place. The
     * dedupe key is never replaced, so repeated requests cannot create a
     * second task or duplicate a business result.
     */
    @Transactional
    public AiTask retryAdminAiTask(Long taskId) {
        if (!currentUser.hasRole("ADMIN")) throw BusinessException.forbidden("仅超级管理员可重试 AI 任务");
        AiTask task = taskMapper.selectById(taskId);
        if (task == null) throw BusinessException.notFound("AI 任务不存在");
        if (!Set.of(FOLLOW_UP, OPENING, AUTO_EVALUATION, FREE_INTERVIEW_ANALYSIS,
                FREE_INTERVIEW_FOLLOW_UP, FREE_INTERVIEW_REPORT, RESUME_PARSE, JOB_MATCH)
                .contains(task.getTaskType())) {
            throw BusinessException.badRequest("该任务类型不支持平台受控重试");
        }
        if (!"FAILED".equals(task.getStatus())) return task;
        if (!isTechnicalRecruitmentFailure(task)) {
            throw BusinessException.badRequest("该任务属于业务数据问题，不支持平台自动重试");
        }
        if (AUTO_EVALUATION.equals(task.getTaskType())) return retryAutomaticEvaluation(task.getInterviewId());
        resetTask(task, task.getInputPayload(), task.getMaxAttempts(), currentUser.id());
        return task;
    }

    private boolean isTechnicalRecruitmentFailure(AiTask task) {
        String message = task.getErrorMessage() == null ? "" : task.getErrorMessage().toLowerCase();
        return !(message.contains("尚未完成解析") || message.contains("不存在")
                || message.contains("未配置题目") || message.contains("缺少岗位或简历")
                || message.contains("不能自动评分") || message.contains("任务参数损坏")
                || message.contains("尚未进入报告生成中") || message.contains("面试未配置题目")
                || message.contains("当前面试状态不允许") || message.contains("缺少面试"));
    }

    public AiTask get(Long id) {
        AiTask task = taskMapper.selectById(id);
        if (task == null) throw BusinessException.notFound("AI 任务不存在");
        if (!(currentUser.id().equals(task.getCreatedBy()) || currentUser.hasRole("ADMIN"))) throw BusinessException.forbidden("无权查看 AI 任务");
        return task;
    }

    public AiTask latestEvaluationTask(Long interviewId) {
        Interview interview = requireInterview(interviewId);
        requireParticipant(interview);
        AiTask task = taskMapper.selectOne(new LambdaQueryWrapper<AiTask>()
                .eq(AiTask::getInterviewId, interviewId)
                .eq(AiTask::getTaskType, AUTO_EVALUATION)
                .orderByDesc(AiTask::getId)
                .last("LIMIT 1"));
        if (task == null) throw BusinessException.notFound("AI 评测任务不存在");
        return task;
    }

    @Scheduled(fixedDelayString = "${app.ai-task.poll-interval-ms:1000}")
    public void processPendingTasks() {
        if (!schedulerEnabled) return;
        List<AiTask> tasks = taskMapper.selectList(new LambdaQueryWrapper<AiTask>().eq(AiTask::getStatus, "PENDING")
                .le(AiTask::getScheduledAt, LocalDateTime.now())
                .last("ORDER BY CASE WHEN task_type = 'AUTO_EVALUATION' THEN 0 "
                        + "WHEN task_type = 'FREE_REPORT' THEN 1 ELSE 2 END, id ASC LIMIT " + taskFetchLimit));
        for (AiTask task : tasks) {
            AiTask claimed = claim(task.getId());
            if (claimed != null) {
                aiTaskWorkerExecutor.execute(() -> executeClaimed(claimed));
            }
        }
    }

    public void process(Long id) {
        AiTask claimed = claim(id);
        if (claimed != null) executeClaimed(claimed);
    }

    @Autowired
    public void setOperationAuditService(OperationAuditService operationAuditService) {
        this.operationAuditService = operationAuditService;
    }

    private AiTask claim(Long id) {
        AiTask task = taskMapper.selectById(id);
        if (task == null || !"PENDING".equals(task.getStatus())) return null;
        task.setStatus("RUNNING");
        task.setAttempts(task.getAttempts() + 1);
        task.setStartedAt(LocalDateTime.now());
        if (taskMapper.update(task, new LambdaQueryWrapper<AiTask>()
                .eq(AiTask::getId, id).eq(AiTask::getStatus, "PENDING")) == 0) return null;
        return task;
    }

    private void executeClaimed(AiTask task) {
        try {
            String output = switch (task.getTaskType()) {
                case FOLLOW_UP -> followUp(task);
                case OPENING -> opening(task);
                case AUTO_EVALUATION -> evaluateInterview(task);
                case FREE_INTERVIEW_ANALYSIS -> analyzeFreeInterview(task);
                case FREE_INTERVIEW_FOLLOW_UP -> freeInterviewFollowUp(task);
                case FREE_INTERVIEW_REPORT -> freeInterviewReport(task);
                case RESUME_PARSE -> recruitmentResumeAnalysisService.process(task);
                case JOB_MATCH -> recruitmentJobMatchService.process(task);
                default -> throw new IllegalArgumentException("不支持的 AI 任务类型：" + task.getTaskType());
            };
            task.setStatus("SUCCESS");
            task.setOutputPayload(output);
            task.setFinishedAt(LocalDateTime.now());
            task.setErrorMessage(null);
            taskMapper.updateById(task);
            if (AUTO_EVALUATION.equals(task.getTaskType())) {
                markInterviewReportReady(task.getInterviewId());
                if (operationAuditService != null) operationAuditService.success("REPORT", "REPORT_GENERATED",
                        "REPORT", task.getInterviewId(), null, "AI 面试报告生成完成");
            }
        } catch (RuntimeException exception) {
            if (FREE_INTERVIEW_FOLLOW_UP.equals(task.getTaskType())) {
                completeFreeInterviewFollowUpWithFallback(task, exception);
                return;
            }
            task.setStatus(task.getAttempts() < task.getMaxAttempts() && retryable(exception) ? "PENDING" : "FAILED");
            task.setScheduledAt(LocalDateTime.now().plusSeconds(30));
            task.setErrorMessage(truncate(exception.getMessage()));
            if ("FAILED".equals(task.getStatus())) {
                task.setFinishedAt(LocalDateTime.now());
                if (AUTO_EVALUATION.equals(task.getTaskType())) {
                    markInterviewEvaluationFailed(task.getInterviewId());
                    if (operationAuditService != null) operationAuditService.failure("REPORT", "REPORT_GENERATION_FAILED",
                            "REPORT", task.getInterviewId(), null, "AI 面试报告生成失败");
                }
                if (FREE_INTERVIEW_ANALYSIS.equals(task.getTaskType()) || FREE_INTERVIEW_REPORT.equals(task.getTaskType())) {
                    markFreeInterviewFailed(task);
                }
            }
            taskMapper.updateById(task);
        }
    }

    private String followUp(AiTask task) {
        JsonNode input = tree(task.getInputPayload());
        List<String> previousFollowUps = splitFollowUps(input.path("previousFollowUps").asText());
        String conversation = firstNonBlank(input.path("conversationContext").asText(),
                "候选人：" + input.path("answer").asText());
        DeepSeekGateway.Generated<String> generated = deepSeekGateway.followUp(input.path("question").asText(),
                conversation, input.path("interviewerStyle").asText("big-tech"),
                context(task, null, "FOLLOW_UP"));
        String rejection = followUpQualityGuard.rejectionReason(generated.content(), previousFollowUps);
        if (rejection.isBlank()) {
            return json("followUp", generated.content().trim(), "generationRequestId", generated.requestId(),
                    "qualityRetry", false, "qualityFallback", false);
        }

        String retryContext = conversation + "\n\n上一次生成内容：" + generated.content()
                + "\n未通过原因：" + rejection + "。请换一个尚未问过的角度，只输出一个追问。";
        DeepSeekGateway.Generated<String> retry = deepSeekGateway.followUp(input.path("question").asText(),
                retryContext, input.path("interviewerStyle").asText("big-tech"),
                context(task, null, "FOLLOW_UP_QUALITY_RETRY"));
        String retryRejection = followUpQualityGuard.rejectionReason(retry.content(), previousFollowUps);
        if (retryRejection.isBlank()) {
            return json("followUp", retry.content().trim(), "generationRequestId", retry.requestId(),
                    "qualityRetry", true, "qualityFallback", false);
        }
        return json("followUp", followUpQualityGuard.fallback(previousFollowUps),
                "generationRequestId", retry.requestId(), "qualityRetry", true, "qualityFallback", true);
    }

    private List<String> splitFollowUps(String value) {
        if (value == null || value.isBlank()) return List.of();
        return List.of(value.split("\\u001E", -1)).stream().filter(item -> !item.isBlank()).toList();
    }

    private String opening(AiTask task) {
        JsonNode input = tree(task.getInputPayload());
        DeepSeekGateway.Generated<String> generated = deepSeekGateway.openingQuestion(input.path("question").asText(),
                input.path("interviewerStyle").asText("big-tech"), context(task, null, "OPENING"));
        return json("question", generated.content(), "generationRequestId", generated.requestId());
    }

    private String analyzeFreeInterview(AiTask task) {
        Long sessionId = tree(task.getInputPayload()).path("sessionId").asLong();
        FreeInterviewSession session = requireFreeSession(sessionId);
        if (FreeInterviewSession.INTERVIEWING.equals(session.getStatus()) && session.getResumeSummary() != null
                && !session.getResumeSummary().isBlank() && !"{}".equals(session.getResumeSummary().trim())) {
            return json("sessionId", sessionId, "openingQuestion", freeOpeningQuestion(session));
        }
        JsonNode result = deepSeekGateway.analyzeResume(session.getResumeText(), session.getTargetRole(),
                context(task, sessionId, "RESUME_ANALYSIS")).content();
        session.setResumeSummary(write(result));
        session.setStatus(FreeInterviewSession.INTERVIEWING);
        freeSessionMapper.updateById(session);
        return json("sessionId", sessionId, "openingQuestion", freeOpeningQuestion(session));
    }

    private String freeInterviewFollowUp(AiTask task) {
        JsonNode input = tree(task.getInputPayload());
        Long sessionId = input.path("sessionId").asLong();
        Long turnId = input.path("turnId").asLong();
        FreeInterviewSession session = requireFreeSession(sessionId);
        FreeInterviewTurn turn = requireFreeTurn(sessionId, turnId);
        if (turn.getNextQuestion() != null && !turn.getNextQuestion().isBlank()) {
            return json("sessionId", sessionId, "turnId", turnId, "nextQuestion", turn.getNextQuestion());
        }
        List<FreeInterviewTurn> turns = freeTurns(sessionId);
        DeepSeekGateway.Generated<String> generated = deepSeekGateway.generateFreeInterviewFollowUp(
                session.getResumeSummary(), freeTranscript(turns), turn.getTurnNo() + 1,
                context(task, sessionId, "FREE_FOLLOW_UP"));
        String nextQuestion = generated.content();
        if (nextQuestion == null || nextQuestion.isBlank()) throw new IllegalStateException("DeepSeek 未返回有效追问");
        turn.setNextQuestion(nextQuestion.trim());
        freeTurnMapper.updateById(turn);
        return json("sessionId", sessionId, "turnId", turnId, "nextQuestion", turn.getNextQuestion());
    }

    private String freeInterviewReport(AiTask task) {
        Long sessionId = tree(task.getInputPayload()).path("sessionId").asLong();
        FreeInterviewSession session = requireFreeSession(sessionId);
        if (FreeInterviewSession.REPORT_READY.equals(session.getStatus())) {
            return json("sessionId", sessionId, "reportReady", true);
        }
        List<FreeInterviewTurn> turns = freeTurns(sessionId);
        if (turns.size() < 10) throw new IllegalStateException("自由面试不足 10 轮，无法生成报告");
        JsonNode result = deepSeekGateway.generateFreeInterviewReport(session.getResumeSummary(), freeTranscript(turns),
                context(task, sessionId, "FREE_REPORT")).content();
        session.setTotalScore(score(result, "totalScore"));
        session.setProfessionalScore(score(result, "professionalScore"));
        session.setExpressionScore(score(result, "expressionScore"));
        session.setLogicScore(score(result, "logicScore"));
        session.setAdaptabilityScore(score(result, "adaptabilityScore"));
        session.setSummary(requiredText(result, "summary", 3000));
        session.setStrengths(requiredText(result, "strengths", 3000));
        session.setWeaknesses(requiredText(result, "weaknesses", 3000));
        session.setImprovementSuggestions(requiredText(result, "improvementSuggestions", 3000));
        session.setStatus(FreeInterviewSession.REPORT_READY);
        session.setCompletedAt(LocalDateTime.now());
        freeSessionMapper.updateById(session);
        return json("sessionId", sessionId, "reportReady", true);
    }

    private String evaluateInterview(AiTask task) {
        Interview interview = requireInterview(task.getInterviewId());
        if (interview.getStatus() != Interview.REPORT_GENERATING) throw new IllegalStateException("面试尚未进入报告生成中，不能自动评分");
        List<InterviewQuestion> interviewQuestions = interviewQuestionMapper.selectList(new LambdaQueryWrapper<InterviewQuestion>()
                .eq(InterviewQuestion::getInterviewId, interview.getId()).orderByAsc(InterviewQuestion::getSequenceNo));
        if (interviewQuestions.isEmpty()) throw new IllegalStateException("面试未配置题目，无法生成评测");

        int scoringPromptVersion = promptTemplates.activeVersionNo(PromptCatalog.ANSWER_EVALUATION);
        int reportPromptVersion = promptTemplates.activeVersionNo(PromptCatalog.SIMULATION_REPORT);

        Map<Long, InterviewAnswer> answersByQuestion = new HashMap<>();
        answerMapper.selectList(new LambdaQueryWrapper<InterviewAnswer>()
                        .in(InterviewAnswer::getInterviewQuestionId,
                                interviewQuestions.stream().map(InterviewQuestion::getId).toList()))
                .forEach(answer -> answersByQuestion.put(answer.getInterviewQuestionId(), answer));

        List<Long> sourceQuestionIds = interviewQuestions.stream().map(InterviewQuestion::getQuestionId)
                .filter(id -> id != null).distinct().toList();
        Map<Long, Question> sourceQuestions = new HashMap<>();
        if (!sourceQuestionIds.isEmpty()) {
            questionMapper.selectBatchIds(sourceQuestionIds)
                    .forEach(question -> sourceQuestions.put(question.getId(), question));
        }

        List<EvaluationInput> inputs = interviewQuestions.stream().map(interviewQuestion -> {
            Question sourceQuestion = sourceQuestions.get(interviewQuestion.getQuestionId());
            InterviewAnswer answer = answersByQuestion.get(interviewQuestion.getId());
            String question = questionContent(interviewQuestion);
            String reference = sourceQuestion == null ? "" : joinReference(sourceQuestion);
            String candidateAnswer = answer == null ? ""
                    : firstNonBlank(answer.getAnswerContent(), answer.getTranscript(), answer.getAnswerData());
            String questionType = questionType(interviewQuestion, sourceQuestion);
            return new EvaluationInput(interviewQuestion, sourceQuestion, answer, question, reference,
                    candidateAnswer, questionType, isChoiceQuestion(questionType));
        }).toList();

        Map<Long, CompletableFuture<JsonNode>> semanticEvaluations = new HashMap<>();
        for (EvaluationInput input : inputs) {
            if (input.choiceQuestion()) continue;
            semanticEvaluations.put(input.interviewQuestion().getId(), CompletableFuture.supplyAsync(
                    () -> deepSeekGateway.evaluateAnswer(input.question(), input.reference(), input.candidateAnswer(),
                            context(task, null, "ANSWER_EVALUATION"), scoringPromptVersion).content(),
                    reportScoringExecutor));
        }
        awaitSemanticEvaluations(semanticEvaluations.values().stream().toList());

        List<EvaluationContext> contexts = new ArrayList<>();
        List<Evaluation> evaluations = new ArrayList<>();
        for (EvaluationInput input : inputs) {
            Evaluation evaluation;
            if (input.choiceQuestion()) {
                ChoiceAnswerScorer.Result result = choiceAnswerScorer.score(input.questionType(),
                        questionField(input.interviewQuestion(), "correctAnswer",
                                input.sourceQuestion() == null ? null : input.sourceQuestion().getCorrectAnswer()),
                        input.answer() == null ? null : input.answer().getAnswerData(), input.candidateAnswer(),
                        questionField(input.interviewQuestion(), "explanation",
                                input.sourceQuestion() == null ? null : input.sourceQuestion().getExplanation()));
                evaluation = upsertChoiceEvaluation(input.interviewQuestion().getId(), result);
            } else {
                JsonNode result = semanticEvaluations.get(input.interviewQuestion().getId()).join();
                evaluation = upsertAiEvaluation(input.interviewQuestion().getId(), result,
                        input.candidateAnswer(), false);
            }
            evaluations.add(evaluation);
            contexts.add(new EvaluationContext(input.interviewQuestion().getSequenceNo(), input.questionType(),
                    input.choiceQuestion() ? "objective_rule" : "ai_semantic", input.question(), input.candidateAnswer(),
                    evaluation.getProfessionalScore(), evaluation.getExpressionScore(), evaluation.getLogicScore(),
                    evaluation.getAdaptabilityScore(), evaluation.getOverallScore(), evaluation.getComment()));
        }

        ReportEvaluationContext reportContext = new ReportEvaluationContext(
                "objective_rule 表示客观选择题已由系统按标准答案判分；候选人只需选择选项，不得因答案短或没有解释而扣分。ai_semantic 表示简答题，可评价内容深度、表达、逻辑和应变。",
                interviewQuestions.size(), contexts);
        JsonNode narrative = deepSeekGateway.generateReport(write(reportContext),
                context(task, null, "SIMULATION_REPORT"), reportPromptVersion).content();
        Report report = upsertReport(interview, task, evaluations, narrative, scoringPromptVersion, reportPromptVersion);
        return json("reportId", report.getId(), "evaluationCount", evaluations.size(),
                "scoringPromptVersion", scoringPromptVersion, "reportPromptVersion", reportPromptVersion);
    }

    private void awaitSemanticEvaluations(List<CompletableFuture<JsonNode>> futures) {
        if (futures.isEmpty()) return;
        try {
            CompletableFuture.allOf(futures.toArray(CompletableFuture[]::new)).join();
        } catch (CompletionException exception) {
            Throwable cause = exception.getCause();
            if (cause instanceof RuntimeException runtimeException) throw runtimeException;
            throw new IllegalStateException("并行评分执行失败", cause);
        }
    }

    private Evaluation upsertAiEvaluation(Long interviewQuestionId, JsonNode result, String candidateAnswer, boolean choiceQuestion) {
        Evaluation evaluation = automaticEvaluation(interviewQuestionId);
        BigDecimal professional = calibratedScore(score(result, "professionalScore"), candidateAnswer, choiceQuestion);
        BigDecimal expression = calibratedScore(score(result, "expressionScore"), candidateAnswer, choiceQuestion);
        BigDecimal logic = calibratedScore(score(result, "logicScore"), candidateAnswer, choiceQuestion);
        BigDecimal adaptability = calibratedScore(score(result, "adaptabilityScore"), candidateAnswer, choiceQuestion);
        evaluation.setProfessionalScore(professional);
        evaluation.setExpressionScore(expression);
        evaluation.setLogicScore(logic);
        evaluation.setAdaptabilityScore(adaptability);
        evaluation.setOverallScore(calibratedOverall(score(result, "overallScore"), candidateAnswer, choiceQuestion,
                professional, expression, logic, adaptability));
        evaluation.setComment(requiredText(result, "comment", 2000));
        evaluation.setStatus(1);
        evaluation.setConfirmedBy(null);
        if (evaluation.getId() == null) evaluationMapper.insert(evaluation); else evaluationMapper.updateById(evaluation);
        return evaluation;
    }

    private Evaluation upsertChoiceEvaluation(Long interviewQuestionId, ChoiceAnswerScorer.Result result) {
        Evaluation evaluation = automaticEvaluation(interviewQuestionId);
        evaluation.setProfessionalScore(result.score());
        evaluation.setExpressionScore(result.score());
        evaluation.setLogicScore(result.score());
        evaluation.setAdaptabilityScore(result.score());
        evaluation.setOverallScore(result.score());
        evaluation.setComment(result.comment());
        evaluation.setStatus(1);
        evaluation.setConfirmedBy(null);
        if (evaluation.getId() == null) evaluationMapper.insert(evaluation); else evaluationMapper.updateById(evaluation);
        return evaluation;
    }

    private Evaluation automaticEvaluation(Long interviewQuestionId) {
        Evaluation evaluation = evaluationMapper.selectOne(new LambdaQueryWrapper<Evaluation>()
                .eq(Evaluation::getInterviewQuestionId, interviewQuestionId).eq(Evaluation::getSource, "ai").last("LIMIT 1"));
        if (evaluation == null) {
            evaluation = new Evaluation();
            evaluation.setInterviewQuestionId(interviewQuestionId);
            evaluation.setSource("ai");
            evaluation.setEvaluatorId(null);
        }
        return evaluation;
    }

    private Report upsertReport(Interview interview, AiTask task, List<Evaluation> evaluations, JsonNode narrative,
                                int scoringPromptVersion, int reportPromptVersion) {
        Report report = reportMapper.selectOne(new LambdaQueryWrapper<Report>().eq(Report::getInterviewId, interview.getId()));
        if (report == null) {
            report = new Report();
            report.setInterviewId(interview.getId());
        }
        report.setProfessionalScore(average(evaluations, Evaluation::getProfessionalScore));
        report.setExpressionScore(average(evaluations, Evaluation::getExpressionScore));
        report.setLogicScore(average(evaluations, Evaluation::getLogicScore));
        report.setAdaptabilityScore(average(evaluations, Evaluation::getAdaptabilityScore));
        report.setTotalScore(reportTotalScore(evaluations));
        report.setSummary(requiredText(narrative, "summary", 3000));
        report.setStrengths(requiredText(narrative, "strengths", 3000));
        report.setWeaknesses(requiredText(narrative, "weaknesses", 3000));
        report.setImprovementSuggestions(requiredText(narrative, "improvementSuggestions", 3000));
        report.setGenerationMethod("ai");
        report.setScoringPromptCode(PromptCatalog.ANSWER_EVALUATION);
        report.setScoringPromptVersionNo(scoringPromptVersion);
        report.setReportPromptCode(PromptCatalog.SIMULATION_REPORT);
        report.setReportPromptVersionNo(reportPromptVersion);
        report.setGeneratedBy(task.getCreatedBy());
        // Recruitment reports are an internal HR review artifact first. The
        // candidate-facing publication is an explicit, separately authorized
        // action on the company application endpoint.
        report.setStatus(0);
        report.setPublishedAt(null);
        if (report.getId() == null) reportMapper.insert(report); else reportMapper.updateById(report);
        return report;
    }

    private AiGenerationContext context(AiTask task, Long freeInterviewSessionId, String generationType) {
        return new AiGenerationContext(task.getId(), task.getInterviewId(), freeInterviewSessionId,
                generationType, task.getCreatedBy());
    }

    private void markInterviewReportReady(Long interviewId) {
        if (interviewId == null) return;
        Interview interview = interviewMapper.selectById(interviewId);
        if (interview == null || interview.getStatus() != Interview.REPORT_GENERATING) return;
        interview.setStatus(Interview.REPORT_READY);
        interviewMapper.updateById(interview);
    }

    private void markInterviewEvaluationFailed(Long interviewId) {
        if (interviewId == null) return;
        Interview interview = interviewMapper.selectById(interviewId);
        if (interview == null || interview.getStatus() != Interview.REPORT_GENERATING) return;
        interview.setStatus(Interview.COMPLETED);
        interviewMapper.updateById(interview);
    }

    private void completeFreeInterviewFollowUpWithFallback(AiTask task, RuntimeException exception) {
        try {
            JsonNode input = tree(task.getInputPayload());
            Long sessionId = input.path("sessionId").asLong();
            Long turnId = input.path("turnId").asLong();
            FreeInterviewTurn turn = requireFreeTurn(sessionId, turnId);
            String fallback = freeFallbackQuestion(turn.getTurnNo() + 1);
            turn.setNextQuestion(fallback);
            freeTurnMapper.updateById(turn);
            task.setStatus("SUCCESS");
            task.setOutputPayload(json("sessionId", sessionId, "turnId", turnId,
                    "nextQuestion", fallback, "fallback", true));
            task.setErrorMessage("模型追问不可用，已使用稳定追问：" + truncate(exception.getMessage()));
            task.setFinishedAt(LocalDateTime.now());
            taskMapper.updateById(task);
        } catch (RuntimeException fallbackException) {
            task.setStatus("FAILED");
            task.setErrorMessage(truncate(fallbackException.getMessage()));
            task.setFinishedAt(LocalDateTime.now());
            taskMapper.updateById(task);
        }
    }

    private void markFreeInterviewFailed(AiTask task) {
        Long sessionId = tree(task.getInputPayload()).path("sessionId").asLong();
        FreeInterviewSession session = freeSessionMapper.selectById(sessionId);
        if (session == null || FreeInterviewSession.REPORT_READY.equals(session.getStatus())) return;
        session.setStatus(FreeInterviewSession.FAILED);
        freeSessionMapper.updateById(session);
    }

    private AiTask enqueue(Long interviewId, Long answerId, String type, String dedupeKey, String payload) {
        return enqueue(interviewId, answerId, type, dedupeKey, payload, 3);
    }

    private AiTask enqueue(Long interviewId, Long answerId, String type, String dedupeKey, String payload, int maxAttempts) {
        return enqueue(interviewId, answerId, type, dedupeKey, payload, maxAttempts, currentUser.id());
    }

    private AiTask enqueue(Long interviewId, Long answerId, String type, String dedupeKey, String payload,
                           int maxAttempts, Long createdBy) {
        if (dedupeKey != null) {
            AiTask existing = taskMapper.selectOne(new LambdaQueryWrapper<AiTask>().eq(AiTask::getDedupeKey, dedupeKey));
            if (existing != null) {
                if ("FAILED".equals(existing.getStatus())) {
                    resetTask(existing, payload, maxAttempts, createdBy);
                }
                return existing;
            }
        }
        AiTask task = new AiTask();
        task.setInterviewId(interviewId);
        task.setAnswerId(answerId);
        task.setTaskType(type);
        task.setDedupeKey(dedupeKey);
        task.setStatus("PENDING");
        task.setAttempts(0);
        task.setMaxAttempts(maxAttempts);
        task.setScheduledAt(LocalDateTime.now());
        task.setInputPayload(payload);
        task.setCreatedBy(createdBy);
        taskMapper.insert(task);
        return task;
    }

    private void resetTask(AiTask task, String payload, int maxAttempts) {
        resetTask(task, payload, maxAttempts, currentUser.id());
    }

    private void resetTask(AiTask task, String payload, int maxAttempts, Long createdBy) {
        task.setStatus("PENDING");
        task.setAttempts(0);
        task.setScheduledAt(LocalDateTime.now());
        task.setStartedAt(null);
        task.setFinishedAt(null);
        task.setInputPayload(payload);
        task.setOutputPayload(null);
        task.setErrorMessage(null);
        task.setCreatedBy(createdBy);
        task.setMaxAttempts(maxAttempts);
        taskMapper.updateById(task);
    }

    @Autowired
    public void setRecruitmentJobMatchService(RecruitmentJobMatchService recruitmentJobMatchService) {
        this.recruitmentJobMatchService = recruitmentJobMatchService;
    }

    @Autowired
    public void setCompanyAccessService(CompanyAccessService companyAccessService) {
        this.companyAccessService = companyAccessService;
    }

    private FreeInterviewSession requireFreeSession(Long id) {
        FreeInterviewSession session = freeSessionMapper.selectById(id);
        if (session == null) throw new IllegalStateException("自由面试会话不存在：" + id);
        return session;
    }

    private FreeInterviewTurn requireFreeTurn(Long sessionId, Long turnId) {
        FreeInterviewTurn turn = freeTurnMapper.selectById(turnId);
        if (turn == null || !sessionId.equals(turn.getSessionId())) {
            throw new IllegalStateException("自由面试轮次不存在：" + turnId);
        }
        return turn;
    }

    private List<FreeInterviewTurn> freeTurns(Long sessionId) {
        return freeTurnMapper.selectList(new LambdaQueryWrapper<FreeInterviewTurn>()
                .eq(FreeInterviewTurn::getSessionId, sessionId).orderByAsc(FreeInterviewTurn::getTurnNo));
    }

    private String freeTranscript(List<FreeInterviewTurn> turns) {
        return turns.stream()
                .map(turn -> "第" + turn.getTurnNo() + "轮\n问题：" + turn.getQuestion() + "\n回答：" + turn.getAnswer())
                .reduce("", (left, right) -> left + "\n\n" + right);
    }

    private String freeOpeningQuestion(FreeInterviewSession session) {
        String question = tree(session.getResumeSummary()).path("openingQuestion").asText().trim();
        return question.isBlank() ? "请结合你的简历，选择一个最有代表性的项目，说明你的职责、技术难点和最终结果。" : question;
    }

    private String freeFallbackQuestion(int nextTurn) {
        return switch (nextTurn) {
            case 2 -> "你刚才的回答中，哪一部分最能体现你的个人贡献？请给出一个具体事实。";
            case 3 -> "针对你提到的实现方案，当时为什么选择它，而不是其他方案？";
            case 4 -> "这个方案遇到过哪些异常或边界情况？你是怎么处理的？";
            case 5 -> "如果业务量扩大十倍，你会优先优化哪个环节？为什么？";
            case 6 -> "你如何验证这项工作的最终效果？请给出可量化的指标。";
            case 7 -> "在这段经历中，你遇到过什么协作分歧？最后是如何解决的？";
            case 8 -> "回看这段经历，你认为当时最大的技术不足是什么？";
            case 9 -> "如果重新实现一次，你会做哪项关键调整？请说明原因。";
            default -> "结合目标岗位，这段经历证明了你的哪项核心能力？你还存在哪些差距？";
        };
    }

    private String questionContent(InterviewQuestion question) {
        String content = tree(question.getQuestionSnapshot()).path("content").asText();
        return content == null ? "" : content.trim();
    }

    private boolean isChoiceQuestion(InterviewQuestion question) {
        Question source = question.getQuestionId() == null ? null : questionMapper.selectById(question.getQuestionId());
        return isChoiceQuestion(questionType(question, source));
    }

    private boolean isChoiceQuestion(String questionType) {
        return List.of("single_choice", "multiple_choice", "true_false").contains(questionType);
    }

    private String questionType(InterviewQuestion question, Question source) {
        return questionField(question, "questionType", source == null ? null : source.getQuestionType());
    }

    private String questionField(InterviewQuestion question, String field, String fallback) {
        JsonNode value = tree(question.getQuestionSnapshot()).get(field);
        if (value == null || value.isNull()) return nullToEmpty(fallback);
        if (value.isTextual()) return firstNonBlank(value.asText(), fallback);
        return firstNonBlank(value.toString(), fallback);
    }

    private String joinReference(Question question) {
        return String.join("\n", List.of("参考答案：" + nullToEmpty(question.getCorrectAnswer()),
                "答题要点：" + nullToEmpty(question.getAnswerTemplate()), "解析：" + nullToEmpty(question.getExplanation()))).trim();
    }

    private BigDecimal score(JsonNode result, String field) {
        JsonNode value = result.get(field);
        if (value == null || !value.isNumber()) throw new IllegalStateException("DeepSeek 评分结果缺少数值字段：" + field);
        BigDecimal score = value.decimalValue().setScale(2, RoundingMode.HALF_UP);
        if (score.compareTo(BigDecimal.ZERO) < 0 || score.compareTo(BigDecimal.valueOf(100)) > 0) {
            throw new IllegalStateException("DeepSeek 评分超出 0-100 范围：" + field);
        }
        return score;
    }

    private BigDecimal calibratedScore(BigDecimal rawScore, String answer, boolean choiceQuestion) {
        BigDecimal calibrated;
        if (rawScore.compareTo(BigDecimal.valueOf(40)) <= 0) {
            calibrated = rawScore.multiply(BigDecimal.valueOf(0.95));
        } else if (rawScore.compareTo(BigDecimal.valueOf(70)) <= 0) {
            calibrated = rawScore.multiply(BigDecimal.valueOf(0.90)).add(BigDecimal.valueOf(2));
        } else if (rawScore.compareTo(BigDecimal.valueOf(85)) <= 0) {
            calibrated = rawScore.multiply(BigDecimal.valueOf(0.84)).add(BigDecimal.valueOf(5));
        } else {
            calibrated = BigDecimal.valueOf(76).add(rawScore.subtract(BigDecimal.valueOf(85)).multiply(BigDecimal.valueOf(0.45)));
        }
        return applyAnswerEvidenceCap(calibrated, answer, choiceQuestion);
    }

    private BigDecimal calibratedOverall(BigDecimal rawOverall, String answer, boolean choiceQuestion, BigDecimal professional,
                                         BigDecimal expression, BigDecimal logic, BigDecimal adaptability) {
        BigDecimal calibrated = calibratedScore(rawOverall, answer, choiceQuestion);
        BigDecimal weightedByDimensions = weightedScore(professional, expression, logic, adaptability);
        BigDecimal evidenceCeiling = weightedByDimensions.add(BigDecimal.valueOf(3));
        if (calibrated.compareTo(evidenceCeiling) > 0) {
            calibrated = evidenceCeiling;
        }
        return applyAnswerEvidenceCap(calibrated, answer, choiceQuestion);
    }

    private BigDecimal reportTotalScore(List<Evaluation> evaluations) {
        BigDecimal professional = average(evaluations, Evaluation::getProfessionalScore);
        BigDecimal expression = average(evaluations, Evaluation::getExpressionScore);
        BigDecimal logic = average(evaluations, Evaluation::getLogicScore);
        BigDecimal adaptability = average(evaluations, Evaluation::getAdaptabilityScore);
        BigDecimal overall = average(evaluations, Evaluation::getOverallScore);
        return overall.multiply(BigDecimal.valueOf(0.65))
                .add(weightedScore(professional, expression, logic, adaptability).multiply(BigDecimal.valueOf(0.35)))
                .setScale(2, RoundingMode.HALF_UP);
    }

    private BigDecimal weightedScore(BigDecimal professional, BigDecimal expression, BigDecimal logic, BigDecimal adaptability) {
        return professional.multiply(BigDecimal.valueOf(0.45))
                .add(logic.multiply(BigDecimal.valueOf(0.25)))
                .add(expression.multiply(BigDecimal.valueOf(0.20)))
                .add(adaptability.multiply(BigDecimal.valueOf(0.10)))
                .setScale(2, RoundingMode.HALF_UP);
    }

    private BigDecimal applyAnswerEvidenceCap(BigDecimal score, String answer, boolean choiceQuestion) {
        int length = normalizedLength(answer);
        BigDecimal capped = score;
        if (length == 0) {
            capped = min(capped, BigDecimal.valueOf(10));
        } else if (isNoKnowledgeAnswer(answer)) {
            capped = min(capped, BigDecimal.valueOf(30));
        } else if (!choiceQuestion) {
            if (length < 10) {
                capped = min(capped, BigDecimal.valueOf(35));
            } else if (length < 30) {
                capped = min(capped, BigDecimal.valueOf(50));
            } else if (length < 80) {
                capped = min(capped, BigDecimal.valueOf(65));
            }
        }
        return capped.max(BigDecimal.ZERO).min(BigDecimal.valueOf(100)).setScale(2, RoundingMode.HALF_UP);
    }

    private BigDecimal min(BigDecimal left, BigDecimal right) {
        return left.compareTo(right) <= 0 ? left : right;
    }

    private int normalizedLength(String answer) {
        if (answer == null) return 0;
        return answer.replaceAll("\\s+", "").length();
    }

    private boolean isNoKnowledgeAnswer(String answer) {
        if (answer == null) return false;
        String normalized = answer.replaceAll("\\s+", "").toLowerCase();
        return normalized.contains("不知道") || normalized.contains("不会") || normalized.contains("不清楚")
                || normalized.contains("不懂") || normalized.contains("没思路") || normalized.equals("no")
                || normalized.equals("none");
    }

    private String requiredText(JsonNode result, String field, int maxLength) {
        String value = result.path(field).asText().trim();
        if (value.isBlank()) throw new IllegalStateException("DeepSeek 结果缺少文本字段：" + field);
        return value.substring(0, Math.min(maxLength, value.length()));
    }

    private BigDecimal average(List<Evaluation> records, Function<Evaluation, BigDecimal> getter) {
        return records.stream().map(getter).reduce(BigDecimal.ZERO, BigDecimal::add)
                .divide(BigDecimal.valueOf(records.size()), 2, RoundingMode.HALF_UP);
    }

    private Interview requireInterview(Long id) {
        Interview item = interviewMapper.selectById(id);
        if (item == null) throw BusinessException.notFound("面试不存在");
        return item;
    }

    private void requireCandidate(Interview item) {
        if (!currentUser.id().equals(item.getCandidateId())) throw BusinessException.forbidden("仅候选人可生成本场 AI 开场问题");
    }

    private void requireParticipant(Interview item) {
        Long id = currentUser.id();
        if (!(id.equals(item.getCandidateId()) || id.equals(item.getInterviewerId()) || currentUser.hasRole("ADMIN"))) {
            throw BusinessException.forbidden("无权操作该面试");
        }
    }

    private JsonNode tree(String value) {
        try {
            return objectMapper.readTree(value == null ? "{}" : value);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("AI 任务参数损坏", exception);
        }
    }

    private String json(Object... values) {
        try {
            var node = objectMapper.createObjectNode();
            for (int i = 0; i < values.length; i += 2) node.put(String.valueOf(values[i]), String.valueOf(values[i + 1]));
            return objectMapper.writeValueAsString(node);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException(exception);
        }
    }

    private String write(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("无法序列化 AI 评测上下文", exception);
        }
    }

    private String firstNonBlank(String... values) {
        for (String value : values) if (value != null && !value.isBlank()) return value;
        return "";
    }

    private String nullToEmpty(String value) { return value == null ? "" : value; }

    private String interviewerStyle(Interview interview) {
        String remark = interview.getRemark();
        if (remark == null) return "big-tech";
        int index = remark.indexOf("interviewerStyle=");
        if (index < 0) return "big-tech";
        String value = remark.substring(index + "interviewerStyle=".length()).split("[;\\s,|]", 2)[0].trim();
        return value.isBlank() ? "big-tech" : value;
    }
    private boolean retryable(RuntimeException exception) {
        String message = exception.getMessage() == null ? "" : exception.getMessage();
        return !(message.contains("未配置 DEEPSEEK_API_KEY")
                || message.contains("未找到可用的大模型配置")
                || message.contains("HTTP 401") || message.contains("HTTP 403"));
    }
    private String truncate(String value) { return value == null ? "未知错误" : value.substring(0, Math.min(1000, value.length())); }

    private record EvaluationInput(InterviewQuestion interviewQuestion, Question sourceQuestion,
                                   InterviewAnswer answer, String question, String reference,
                                   String candidateAnswer, String questionType, boolean choiceQuestion) {}

    private record EvaluationContext(Integer sequenceNo, String questionType, String scoringMethod,
                                     String question, String answer, BigDecimal professionalScore,
                                     BigDecimal expressionScore, BigDecimal logicScore, BigDecimal adaptabilityScore,
                                     BigDecimal overallScore, String comment) {}

    private record ReportEvaluationContext(String scoringPolicy, int questionCount,
                                           List<EvaluationContext> evaluations) {}
}
