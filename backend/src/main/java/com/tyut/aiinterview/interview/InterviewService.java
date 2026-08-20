package com.tyut.aiinterview.interview;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tyut.aiinterview.ai.AiTaskService;
import com.tyut.aiinterview.ai.SimulationFollowUpPolicy;
import com.tyut.aiinterview.common.BusinessException;
import com.tyut.aiinterview.common.PageResult;
import com.tyut.aiinterview.domain.AiTask;
import com.tyut.aiinterview.domain.Interview;
import com.tyut.aiinterview.domain.InterviewAnswer;
import com.tyut.aiinterview.domain.InterviewQuestion;
import com.tyut.aiinterview.domain.Question;
import com.tyut.aiinterview.domain.QuestionBank;
import com.tyut.aiinterview.domain.UserAccount;
import com.tyut.aiinterview.domain.UserRole;
import com.tyut.aiinterview.mapper.InterviewAnswerMapper;
import com.tyut.aiinterview.mapper.AiTaskMapper;
import com.tyut.aiinterview.mapper.InterviewMapper;
import com.tyut.aiinterview.mapper.InterviewQuestionMapper;
import com.tyut.aiinterview.mapper.JobPositionMapper;
import com.tyut.aiinterview.mapper.QuestionMapper;
import com.tyut.aiinterview.mapper.QuestionBankMapper;
import com.tyut.aiinterview.mapper.UserMapper;
import com.tyut.aiinterview.mapper.UserRoleMapper;
import com.tyut.aiinterview.recruitment.CompanyAccessService;
import com.tyut.aiinterview.observability.OperationAuditService;
import com.tyut.aiinterview.security.CurrentUser;
import com.tyut.aiinterview.recording.InterviewRecordingService;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class InterviewService {
    private static final Set<String> INTERVIEW_TYPES = Set.of("tech", "hr", "comprehensive", "ai");

    private final InterviewMapper interviewMapper;
    private final AiTaskMapper aiTaskMapper;
    private final InterviewQuestionMapper interviewQuestionMapper;
    private final InterviewAnswerMapper answerMapper;
    private final QuestionMapper questionMapper;
    private final QuestionBankMapper questionBankMapper;
    private final UserMapper userMapper;
    private final UserRoleMapper userRoleMapper;
    private final JobPositionMapper positionMapper;
    private final CurrentUser currentUser;
    private final AiEvaluationGateway aiEvaluationGateway;
    private final InterviewRecordingService recordingService;
    private final ObjectMapper objectMapper;
    private final SimulationFollowUpPolicy followUpPolicy;
    private final int startWindowMinutes;
    private final CompanyAccessService companyAccess;
    private ApplicationEventPublisher eventPublisher;
    private OperationAuditService auditService;

    public InterviewService(InterviewMapper interviewMapper, AiTaskMapper aiTaskMapper,
                            InterviewQuestionMapper interviewQuestionMapper,
                            InterviewAnswerMapper answerMapper, QuestionMapper questionMapper, QuestionBankMapper questionBankMapper, UserMapper userMapper,
                             UserRoleMapper userRoleMapper, JobPositionMapper positionMapper,
                             CurrentUser currentUser, AiEvaluationGateway aiEvaluationGateway,
                             InterviewRecordingService recordingService, ObjectMapper objectMapper,
                             @Value("${app.interview.start-window-minutes:15}") int startWindowMinutes,
                             CompanyAccessService companyAccess) {
        this.interviewMapper = interviewMapper;
        this.aiTaskMapper = aiTaskMapper;
        this.interviewQuestionMapper = interviewQuestionMapper;
        this.answerMapper = answerMapper;
        this.questionMapper = questionMapper;
        this.questionBankMapper = questionBankMapper;
        this.userMapper = userMapper;
        this.userRoleMapper = userRoleMapper;
        this.positionMapper = positionMapper;
        this.currentUser = currentUser;
        this.aiEvaluationGateway = aiEvaluationGateway;
        this.recordingService = recordingService;
        this.objectMapper = objectMapper;
        this.followUpPolicy = new SimulationFollowUpPolicy(objectMapper);
        this.startWindowMinutes = startWindowMinutes;
        this.companyAccess = companyAccess;
    }

    @Transactional
    public Interview create(InterviewDtos.CreateRequest request) {
        requireManager();
        return createScheduled(request);
    }

    @Transactional
    public Interview createRecruitment(Long positionId, Long candidateId, InterviewDtos.CreateRequest request) {
        companyAccess.requirePermission("interview:create");
        companyAccess.requirePosition(positionId);
        if (!positionId.equals(request.positionId()) || !candidateId.equals(request.candidateId())) {
            throw BusinessException.badRequest("AI 面试关联对象与当前申请不一致");
        }
        ensurePublicQuestionBank(request.questionBankId());
        return createScheduled(request);
    }

    private Interview createScheduled(InterviewDtos.CreateRequest request) {
        validateType(request.type());
        String interviewerStyle = normalizeInterviewerStyle(request.interviewerStyle());
        ensureActiveCandidate(request.candidateId());
        if (request.positionId() != null && positionMapper.selectById(request.positionId()) == null) {
            throw BusinessException.badRequest("关联岗位不存在");
        }
        List<Question> questions = resolveQuestions(request);
        Interview interview = new Interview();
        interview.setPositionId(request.positionId());
        interview.setTitle(request.title());
        interview.setCandidateId(request.candidateId());
        interview.setInterviewerId(currentUser.id());
        interview.setScheduledAt(request.scheduledAt());
        interview.setDuration(request.duration());
        interview.setStatus(Interview.PENDING);
        interview.setType(request.type());
        interview.setMeetingUrl(request.meetingUrl());
        interview.setRemark(mergeRemark(request.remark(), interviewerStyle));
        interview.setCreatedBy(currentUser.id());
        interviewMapper.insert(interview);
        for (int index = 0; index < questions.size(); index++) {
            Question question = questions.get(index);
            InterviewQuestion selected = new InterviewQuestion();
            selected.setInterviewId(interview.getId());
            selected.setQuestionId(question.getId());
            selected.setSequenceNo(index + 1);
            selected.setMaxScore(question.getScore());
            selected.setQuestionSnapshot(snapshot(question));
            selected.setSource("bank");
            interviewQuestionMapper.insert(selected);
        }
        return interview;
    }

    private void ensurePublicQuestionBank(Long questionBankId) {
        QuestionBank bank = questionBankMapper.selectById(questionBankId);
        if (bank == null || bank.getStatus() != 1 || bank.getVisibility() == null || bank.getVisibility() != 2) {
            throw BusinessException.badRequest("企业只能使用已发布的公开题库");
        }
    }

    @Autowired
    public void setEventPublisher(ApplicationEventPublisher eventPublisher) {
        this.eventPublisher = eventPublisher;
    }

    @Autowired
    public void setAuditService(OperationAuditService auditService) {
        this.auditService = auditService;
    }

    public List<InterviewDtos.PracticeBankView> practiceBanks() {
        return questionBankMapper.selectList(new LambdaQueryWrapper<QuestionBank>()
                        .eq(QuestionBank::getStatus, 1).eq(QuestionBank::getVisibility, 2).orderByAsc(QuestionBank::getName))
                .stream().map(bank -> new InterviewDtos.PracticeBankView(bank.getId(), bank.getName(), bank.getDescription(),
                        questionMapper.selectCount(new LambdaQueryWrapper<Question>().eq(Question::getBankId, bank.getId()).eq(Question::getStatus, 1))))
                .filter(bank -> bank.questionCount() > 0).toList();
    }

    @Transactional
    public Interview createPractice(InterviewDtos.PracticeRequest request) {
        if (!currentUser.hasRole("CANDIDATE")) throw BusinessException.forbidden("仅候选人可创建模拟练习");
        String interviewerStyle = normalizeInterviewerStyle(request.interviewerStyle());
        QuestionBank bank = questionBankMapper.selectById(request.questionBankId());
        if (bank == null || bank.getStatus() != 1 || bank.getVisibility() != 2) throw BusinessException.notFound("练习题库不存在或未公开");
        List<Question> questions = questionMapper.selectList(new LambdaQueryWrapper<Question>().eq(Question::getBankId, bank.getId())
                .eq(Question::getStatus, 1).last("ORDER BY RAND() LIMIT " + request.questionCount()));
        if (questions.size() < request.questionCount()) throw BusinessException.badRequest("题库可用题目不足，请减少抽题数量");
        LocalDateTime now = LocalDateTime.now();
        Interview practice = new Interview();
        practice.setTitle(bank.getName() + " · 模拟练习"); practice.setCandidateId(currentUser.id()); practice.setInterviewerId(currentUser.id());
        practice.setScheduledAt(now); practice.setStartedAt(now); practice.setDuration(request.duration()); practice.setStatus(Interview.IN_PROGRESS);
        practice.setActiveQuestionIndex(0); practice.setProgressUpdatedAt(now);
        practice.setType("ai"); practice.setRemark(mergeRemark("candidate-practice", interviewerStyle)); practice.setCreatedBy(currentUser.id()); interviewMapper.insert(practice);
        for (int index = 0; index < questions.size(); index++) {
            Question question = questions.get(index); InterviewQuestion selected = new InterviewQuestion();
            selected.setInterviewId(practice.getId()); selected.setQuestionId(question.getId()); selected.setSequenceNo(index + 1);
            selected.setMaxScore(question.getScore()); selected.setQuestionSnapshot(snapshot(question)); selected.setSource("bank"); interviewQuestionMapper.insert(selected);
        }
        return practice;
    }

    public List<Interview> mine() {
        Long userId = currentUser.id();
        LambdaQueryWrapper<Interview> wrapper = new LambdaQueryWrapper<Interview>().orderByDesc(Interview::getScheduledAt);
        if (isManager()) return interviewMapper.selectList(wrapper);
        return interviewMapper.selectList(wrapper.and(item -> item.eq(Interview::getCandidateId, userId)
                .or().eq(Interview::getInterviewerId, userId)));
    }

    public PageResult<Interview> page(InterviewDtos.InterviewQuery query) {
        long pageNo = query.pageNo() == null ? 1 : Math.max(1, query.pageNo());
        long pageSize = query.pageSize() == null ? 20 : Math.min(100, Math.max(1, query.pageSize()));
        LambdaQueryWrapper<Interview> wrapper = new LambdaQueryWrapper<Interview>().orderByDesc(Interview::getScheduledAt);
        if (query.status() != null) {
            if (query.status() < Interview.PENDING || query.status() > Interview.FAILED) throw BusinessException.badRequest("面试状态不合法");
            wrapper.eq(Interview::getStatus, query.status());
        }
        if (query.positionId() != null) wrapper.eq(Interview::getPositionId, query.positionId());
        if (query.scheduledFrom() != null) wrapper.ge(Interview::getScheduledAt, query.scheduledFrom());
        if (query.scheduledTo() != null) wrapper.le(Interview::getScheduledAt, query.scheduledTo());
        if (query.scheduledFrom() != null && query.scheduledTo() != null && query.scheduledFrom().isAfter(query.scheduledTo())) {
            throw BusinessException.badRequest("排期开始时间不能晚于结束时间");
        }
        if (isManager()) {
            if (query.candidateId() != null) wrapper.eq(Interview::getCandidateId, query.candidateId());
            if (query.interviewerId() != null) wrapper.eq(Interview::getInterviewerId, query.interviewerId());
        } else {
            Long userId = currentUser.id();
            wrapper.and(item -> item.eq(Interview::getCandidateId, userId).or().eq(Interview::getInterviewerId, userId));
        }
        Page<Interview> result = interviewMapper.selectPage(new Page<>(pageNo, pageSize), wrapper);
        return PageResult.of(result.getRecords(), result.getTotal(), pageNo, pageSize);
    }

    public Interview detail(Long id) {
        Interview interview = requireInterview(id);
        requireParticipant(interview);
        return interview;
    }

    @Transactional
    public Interview reschedule(Long id, InterviewDtos.RescheduleRequest request) {
        requireManager();
        Interview interview = requireInterview(id);
        requireStatus(interview, Interview.PENDING, "仅待开始面试可改期");
        interview.setScheduledAt(request.scheduledAt());
        interview.setDuration(request.duration());
        interviewMapper.updateById(interview);
        return interview;
    }

    @Transactional
    public void cancel(Long id, String reason) {
        requireManager();
        Interview interview = requireInterview(id);
        requireStatus(interview, Interview.PENDING, "仅待开始面试可取消");
        interview.setStatus(Interview.CANCELLED);
        if (reason != null && !reason.isBlank()) interview.setRemark(reason);
        if (interviewMapper.update(interview, new LambdaQueryWrapper<Interview>().eq(Interview::getId, id)
                .eq(Interview::getStatus, Interview.PENDING)) == 0) {
            throw BusinessException.badRequest("面试状态已变更，请刷新后重试");
        }
    }

    @Transactional
    public Interview pass(Long id) {
        requireManager();
        Interview interview = requireInterview(id);
        if (interview.getStatus() == Interview.CANCELLED) {
            throw BusinessException.badRequest("已取消面试不能标记通过");
        }
        if (interview.getStatus() == Interview.PASSED) {
            return interview;
        }
        if (interview.getStatus() == Interview.PENDING || interview.getStatus() == Interview.IN_PROGRESS || interview.getStatus() == Interview.REPORT_GENERATING) {
            throw BusinessException.badRequest("仅已结束或已出报告的面试可标记通过");
        }
        interview.setStatus(Interview.PASSED);
        if (interview.getEndedAt() == null) {
            interview.setEndedAt(LocalDateTime.now());
        }
        interviewMapper.updateById(interview);
        return interview;
    }

    @Transactional
    public void delete(Long id) {
        requireManager();
        Interview interview = requireInterview(id);
        interviewMapper.deleteById(interview.getId());
    }

    @Transactional
    public Interview start(Long id) {
        Interview interview = requireInterview(id);
        requireCandidateOwner(interview, "仅指定候选人可开始 AI 面试");
        requireStatus(interview, Interview.PENDING, "当前状态不允许开始");
        LocalDateTime now = LocalDateTime.now();
        if (now.isBefore(interview.getScheduledAt().minusMinutes(startWindowMinutes))
                || now.isAfter(interview.getScheduledAt().plusMinutes(interview.getDuration()))) {
            throw BusinessException.badRequest("当前不在允许开始的时间窗口内");
        }
        if (!interviewQuestionMapper.exists(new LambdaQueryWrapper<InterviewQuestion>().eq(InterviewQuestion::getInterviewId, id))) {
            throw BusinessException.badRequest("面试尚未关联题目");
        }
        interview.setStatus(Interview.IN_PROGRESS);
        interview.setStartedAt(now);
        interview.setActiveQuestionIndex(0);
        interview.setProgressUpdatedAt(now);
        if (interviewMapper.update(interview, new LambdaQueryWrapper<Interview>().eq(Interview::getId, id)
                .eq(Interview::getStatus, Interview.PENDING)) == 0) {
            throw BusinessException.badRequest("面试状态已变更，请刷新后重试");
        }
        if (auditService != null) auditService.success("INTERVIEW", "INTERVIEW_CANCELLED", "INTERVIEW", id, null,
                "取消 AI 面试");
        if (eventPublisher != null) {
            eventPublisher.publishEvent(new InterviewLifecycleEvent(id, InterviewLifecycleEvent.Phase.STARTED, currentUser.id()));
        }
        if (auditService != null) auditService.success("INTERVIEW", "INTERVIEW_STARTED", "INTERVIEW", id, null,
                "开始 AI 面试");
        return interview;
    }

    @Transactional
    public InterviewDtos.EndResponse end(Long id) {
        Interview interview = requireInterview(id);
        requireCandidateOwner(interview, "仅指定候选人可结束 AI 面试");
        requireStatus(interview, Interview.IN_PROGRESS, "当前状态不允许结束");
        interview.setStatus(Interview.REPORT_GENERATING);
        interview.setEndedAt(LocalDateTime.now());
        if (interviewMapper.update(interview, new LambdaQueryWrapper<Interview>().eq(Interview::getId, id)
                .eq(Interview::getStatus, Interview.IN_PROGRESS)) == 0) {
            throw BusinessException.badRequest("面试状态已变更，请刷新后重试");
        }
        recordingService.completeForInterview(id);
        AiTask task = aiEvaluationGateway.enqueue(interview);
        if (eventPublisher != null) {
            eventPublisher.publishEvent(new InterviewLifecycleEvent(id, InterviewLifecycleEvent.Phase.ENDED, currentUser.id()));
        }
        if (auditService != null) auditService.success("INTERVIEW", "INTERVIEW_ENDED", "INTERVIEW", id, null,
                "结束 AI 面试并排队生成报告");
        return new InterviewDtos.EndResponse(interview, task.getId(), task.getStatus());
    }

    public List<InterviewDtos.QuestionView> questions(Long interviewId) {
        Interview interview = requireInterview(interviewId);
        requireParticipant(interview);
        return interviewQuestionMapper.selectList(new LambdaQueryWrapper<InterviewQuestion>().eq(InterviewQuestion::getInterviewId, interviewId)
                        .orderByAsc(InterviewQuestion::getSequenceNo)).stream()
                .map(this::toQuestionView).toList();
    }

    public List<InterviewDtos.AnswerView> answers(Long interviewId) {
        Interview interview = requireInterview(interviewId);
        requireParticipant(interview);
        List<Long> questionIds = interviewQuestionMapper.selectList(new LambdaQueryWrapper<InterviewQuestion>()
                        .eq(InterviewQuestion::getInterviewId, interviewId))
                .stream().map(InterviewQuestion::getId).toList();
        if (questionIds.isEmpty()) return List.of();
        return answerMapper.selectList(new LambdaQueryWrapper<InterviewAnswer>().in(InterviewAnswer::getInterviewQuestionId, questionIds))
                .stream().map(answer -> new InterviewDtos.AnswerView(answer.getInterviewQuestionId(), answer.getAnswerContent(),
                        answer.getAnswerData(), answer.getAudioUrl(), answer.getDurationSeconds(), answer.getAnsweredAt())).toList();
    }

    public InterviewDtos.ProgressView progress(Long interviewId) {
        Interview interview = requireInterview(interviewId);
        requireParticipant(interview);
        return progressView(interview);
    }

    @Transactional
    public InterviewDtos.ProgressView updateProgress(Long interviewId, InterviewDtos.ProgressRequest request) {
        Interview interview = requireInterview(interviewId);
        requireCandidateOwner(interview, "仅指定候选人可更新面试进度");
        requireStatus(interview, Interview.IN_PROGRESS, "仅进行中的面试可更新进度");
        long questionCount = interviewQuestionMapper.selectCount(new LambdaQueryWrapper<InterviewQuestion>()
                .eq(InterviewQuestion::getInterviewId, interviewId));
        if (questionCount == 0 || request.activeQuestionIndex() >= questionCount) {
            throw BusinessException.badRequest("当前题目序号超出面试题目范围");
        }
        int currentIndex = interview.getActiveQuestionIndex() == null ? 0 : interview.getActiveQuestionIndex();
        if (recordingService.requiresSequentialMode(interviewId) && request.activeQuestionIndex() != currentIndex) {
            if (request.activeQuestionIndex() != currentIndex + 1) {
                throw BusinessException.badRequest("录制面试只能完成当前题后按顺序进入下一题");
            }
            InterviewQuestion currentQuestion = interviewQuestionMapper.selectOne(new LambdaQueryWrapper<InterviewQuestion>()
                    .eq(InterviewQuestion::getInterviewId, interviewId)
                    .eq(InterviewQuestion::getSequenceNo, currentIndex + 1).last("LIMIT 1"));
            boolean answered = currentQuestion != null && answerMapper.exists(new LambdaQueryWrapper<InterviewAnswer>()
                    .eq(InterviewAnswer::getInterviewQuestionId, currentQuestion.getId()));
            if (!answered) throw BusinessException.badRequest("请先完成并保存当前题目，再进入下一题");
        }
        LocalDateTime now = LocalDateTime.now();
        interview.setActiveQuestionIndex(request.activeQuestionIndex());
        interview.setProgressUpdatedAt(now);
        interviewMapper.updateById(interview);
        return progressView(interview);
    }

    private InterviewDtos.ProgressView progressView(Interview interview) {
        List<InterviewQuestion> questions = interviewQuestionMapper.selectList(new LambdaQueryWrapper<InterviewQuestion>()
                .eq(InterviewQuestion::getInterviewId, interview.getId())
                .orderByAsc(InterviewQuestion::getSequenceNo));
        int questionCount = questions.size();
        int activeIndex = Math.max(0, interview.getActiveQuestionIndex() == null ? 0 : interview.getActiveQuestionIndex());
        if (questionCount > 0) activeIndex = Math.min(activeIndex, questionCount - 1);

        int followUpCount = 0;
        AiTask activeTask = null;
        Integer activeTaskSequence = null;
        if (questionCount > 0) {
            InterviewQuestion activeQuestion = questions.get(activeIndex);
            InterviewAnswer answer = answerMapper.selectOne(new LambdaQueryWrapper<InterviewAnswer>()
                    .eq(InterviewAnswer::getInterviewQuestionId, activeQuestion.getId()).last("LIMIT 1"));
            if (answer != null) {
                followUpCount = followUpPolicy.inspect(answer.getAnswerData(), toQuestionView(activeQuestion).content()).followUpCount();
                activeTask = aiTaskMapper.selectOne(new LambdaQueryWrapper<AiTask>()
                        .eq(AiTask::getAnswerId, answer.getId())
                        .eq(AiTask::getTaskType, AiTaskService.FOLLOW_UP)
                        .orderByDesc(AiTask::getId).last("LIMIT 1"));
                activeTaskSequence = followUpSequence(activeTask);
            }
        }
        return new InterviewDtos.ProgressView(activeIndex, remainingSeconds(interview), followUpCount,
                SimulationFollowUpPolicy.MAX_FOLLOW_UPS, activeTask == null ? null : activeTask.getId(),
                activeTask == null ? null : activeTask.getStatus(), activeTaskSequence, interview.getProgressUpdatedAt());
    }

    private long remainingSeconds(Interview interview) {
        long total = Math.max(0, interview.getDuration() == null ? 0L : interview.getDuration() * 60L);
        if (interview.getStartedAt() == null) return total;
        long elapsed = Math.max(0, ChronoUnit.SECONDS.between(interview.getStartedAt(), LocalDateTime.now()));
        return Math.max(0, total - elapsed);
    }

    private Integer followUpSequence(AiTask task) {
        if (task == null || task.getDedupeKey() == null) return null;
        String prefix = "simulation-follow-up:";
        if (!task.getDedupeKey().startsWith(prefix)) return null;
        int separator = task.getDedupeKey().lastIndexOf(':');
        try {
            return Integer.parseInt(task.getDedupeKey().substring(separator + 1));
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    @Transactional
    public InterviewAnswer submitAnswer(Long interviewId, Long interviewQuestionId, InterviewDtos.AnswerRequest request) {
        Interview interview = requireInterview(interviewId);
        requireCandidateOwner(interview, "仅指定候选人可提交作答");
        requireStatus(interview, Interview.IN_PROGRESS, "面试未进行中，不能提交作答");
        if (!hasText(request.answerContent()) && !hasText(request.answerData()) && !hasText(request.audioUrl())) {
            throw BusinessException.badRequest("作答内容、结构化作答和音频地址不能同时为空");
        }
        validateJson(request.answerData(), "结构化作答");
        InterviewQuestion selected = interviewQuestionMapper.selectById(interviewQuestionId);
        if (selected == null || !selected.getInterviewId().equals(interviewId)) throw BusinessException.notFound("面试题目不存在");
        InterviewAnswer answer = answerMapper.selectOne(new LambdaQueryWrapper<InterviewAnswer>()
                .eq(InterviewAnswer::getInterviewQuestionId, interviewQuestionId));
        if (answer == null) {
            answer = new InterviewAnswer();
            answer.setInterviewQuestionId(interviewQuestionId);
        }
        answer.setAnswerContent(request.answerContent());
        answer.setAnswerData(request.answerData());
        answer.setAudioUrl(request.audioUrl());
        answer.setDurationSeconds(request.durationSeconds());
        answer.setAnsweredAt(LocalDateTime.now());
        if (answer.getId() == null) answerMapper.insert(answer); else answerMapper.updateById(answer);
        return answer;
    }

    private InterviewDtos.QuestionView toQuestionView(InterviewQuestion selected) {
        try {
            JsonNode snapshot = objectMapper.readTree(selected.getQuestionSnapshot());
            if (snapshot != null && snapshot.isObject()) {
                Question question = questionMapper.selectById(selected.getQuestionId());
                return new InterviewDtos.QuestionView(selected.getId(), selected.getQuestionId(), selected.getSequenceNo(), selected.getMaxScore(),
                        snapshot.path("content").asText(), nullableText(snapshot, "options"), snapshot.path("questionType").asText(),
                        firstNonBlank(nullableText(snapshot, "correctAnswer"), question == null ? null : question.getCorrectAnswer()),
                        firstNonBlank(nullableText(snapshot, "answerTemplate"), question == null ? null : question.getAnswerTemplate()),
                        firstNonBlank(nullableText(snapshot, "explanation"), question == null ? null : question.getExplanation()));
            }
            if (snapshot != null && snapshot.isTextual()) {
                Question question = questionMapper.selectById(selected.getQuestionId());
                return new InterviewDtos.QuestionView(selected.getId(), selected.getQuestionId(), selected.getSequenceNo(), selected.getMaxScore(),
                        snapshot.asText(), question == null ? null : question.getOptions(), question == null ? null : question.getQuestionType(),
                        question == null ? null : question.getCorrectAnswer(),
                        question == null ? null : question.getAnswerTemplate(),
                        question == null ? null : question.getExplanation());
            }
        } catch (JsonProcessingException ignored) {
            // Legacy rows can contain non-JSON snapshots; the current question is used as a compatibility fallback.
        }
        Question question = questionMapper.selectById(selected.getQuestionId());
        if (question == null) throw BusinessException.notFound("面试题目快照不存在");
        return new InterviewDtos.QuestionView(selected.getId(), selected.getQuestionId(), selected.getSequenceNo(), selected.getMaxScore(),
                question.getContent(), question.getOptions(), question.getQuestionType(),
                question.getCorrectAnswer(), question.getAnswerTemplate(), question.getExplanation());
    }

    private String snapshot(Question question) {
        try {
            return objectMapper.writeValueAsString(new QuestionSnapshot(question.getContent(), question.getOptions(), question.getQuestionType(),
                    question.getDifficulty(), question.getScore(), question.getCorrectAnswer(), question.getAnswerTemplate(), question.getExplanation()));
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("无法创建面试题目快照", exception);
        }
    }

    private List<Question> resolveQuestions(InterviewDtos.CreateRequest request) {
        boolean hasSelectedQuestions = request.questionIds() != null && !request.questionIds().isEmpty();
        boolean hasQuestionBank = request.questionBankId() != null;
        if (hasSelectedQuestions == hasQuestionBank) {
            throw BusinessException.badRequest("请选择题目或一个题库，且两者不能同时选择");
        }
        if (hasSelectedQuestions) {
            List<Long> ids = request.questionIds().stream().distinct().toList();
            List<Question> questions = ids.stream().map(questionMapper::selectById).toList();
            if (questions.size() != ids.size() || questions.stream().anyMatch(question -> question == null || question.getStatus() != 1)) {
                throw BusinessException.badRequest("所选题目不存在或未发布");
            }
            return questions;
        }
        QuestionBank bank = questionBankMapper.selectById(request.questionBankId());
        if (bank == null || bank.getStatus() != 1) throw BusinessException.badRequest("所选题库不存在或未发布");
        int count = request.questionCount() == null ? 5 : request.questionCount();
        List<Question> questions = questionMapper.selectList(new LambdaQueryWrapper<Question>()
                .eq(Question::getBankId, bank.getId()).eq(Question::getStatus, 1).last("ORDER BY RAND() LIMIT " + count));
        if (questions.size() < count) throw BusinessException.badRequest("所选题库的已发布题目不足 " + count + " 道");
        return questions;
    }

    private void ensureActiveCandidate(Long userId) {
        UserAccount user = userMapper.selectById(userId);
        if (user == null || user.getStatus() != 1) throw BusinessException.badRequest("候选人不存在或已禁用");
        boolean candidate = userRoleMapper.exists(new LambdaQueryWrapper<UserRole>()
                .eq(UserRole::getUserId, userId)
                .eq(UserRole::getRoleId, 2L));
        if (!candidate) throw BusinessException.badRequest("用户不是候选人");
    }

    private void validateType(String type) {
        if (!INTERVIEW_TYPES.contains(type)) throw BusinessException.badRequest("面试类型不合法");
    }

    private String normalizeInterviewerStyle(String style) {
        if (style == null || style.isBlank()) return "big-tech";
        String normalized = style.trim();
        Set<String> allowed = Set.of("gentle", "pressure", "big-tech", "hr", "project-deep", "campus-basic");
        if (!allowed.contains(normalized)) throw BusinessException.badRequest("AI 面试官风格不合法");
        return normalized;
    }

    private String mergeRemark(String remark, String interviewerStyle) {
        String base = remark == null ? "" : remark.trim();
        String marker = "interviewerStyle=" + interviewerStyle;
        if (base.isBlank()) return marker;
        if (base.contains("interviewerStyle=")) return base.replaceAll("interviewerStyle=[a-zA-Z0-9-]+", marker);
        return base + ";" + marker;
    }

    private void validateJson(String value, String fieldName) {
        if (!hasText(value)) return;
        try {
            objectMapper.readTree(value);
        } catch (JsonProcessingException exception) {
            throw BusinessException.badRequest(fieldName + "必须是有效 JSON");
        }
    }

    private String nullableText(JsonNode node, String name) {
        return node.hasNonNull(name) ? node.path(name).asText() : null;
    }

    private String firstNonBlank(String... values) {
        if (values == null) return null;
        for (String value : values) {
            if (value != null && !value.isBlank()) return value;
        }
        return null;
    }

    private Interview requireInterview(Long id) {
        Interview interview = interviewMapper.selectById(id);
        if (interview == null) throw BusinessException.notFound("面试不存在");
        return interview;
    }

    private void requireStatus(Interview interview, int status, String message) {
        if (interview.getStatus() != status) throw BusinessException.badRequest(message);
    }

    private void requireManager() {
        if (!isManager()) throw BusinessException.forbidden("仅管理员可执行此操作");
    }

    private void requireCandidateOwner(Interview interview, String message) {
        if (!currentUser.hasRole("CANDIDATE") || currentUser.hasRole("ADMIN") || currentUser.hasCompanyRole()
                || !Objects.equals(currentUser.id(), interview.getCandidateId())) {
            throw BusinessException.forbidden(message);
        }
    }

    private boolean isManager() {
        return currentUser.hasRole("ADMIN");
    }

    private void requireParticipant(Interview interview) {
        Long userId = currentUser.id();
        if (currentUser.hasCompanyRole()) {
            companyAccess.requireAuthorizedInterview(interview.getId());
            return;
        }
        if (!(userId.equals(interview.getCandidateId()) || userId.equals(interview.getInterviewerId()) || isManager())) {
            throw BusinessException.forbidden("无权查看该面试");
        }
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private record QuestionSnapshot(String content, String options, String questionType, Integer difficulty, BigDecimal score,
                                    String correctAnswer, String answerTemplate, String explanation) {
    }
}
