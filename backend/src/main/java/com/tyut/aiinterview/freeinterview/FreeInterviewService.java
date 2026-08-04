package com.tyut.aiinterview.freeinterview;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tyut.aiinterview.ai.AiTaskService;
import com.tyut.aiinterview.common.BusinessException;
import com.tyut.aiinterview.domain.AiTask;
import com.tyut.aiinterview.domain.FreeInterviewSession;
import com.tyut.aiinterview.domain.FreeInterviewTurn;
import com.tyut.aiinterview.mapper.AiTaskMapper;
import com.tyut.aiinterview.mapper.FreeInterviewSessionMapper;
import com.tyut.aiinterview.mapper.FreeInterviewTurnMapper;
import com.tyut.aiinterview.security.CurrentUser;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

@Service
public class FreeInterviewService {
    public static final int TOTAL_TURNS = 10;

    private final FreeInterviewSessionMapper sessionMapper;
    private final FreeInterviewTurnMapper turnMapper;
    private final AiTaskMapper taskMapper;
    private final ResumeTextExtractor extractor;
    private final AiTaskService taskService;
    private final ObjectMapper objectMapper;
    private final CurrentUser currentUser;

    public FreeInterviewService(FreeInterviewSessionMapper sessionMapper, FreeInterviewTurnMapper turnMapper,
                                AiTaskMapper taskMapper, ResumeTextExtractor extractor, AiTaskService taskService,
                                ObjectMapper objectMapper, CurrentUser currentUser) {
        this.sessionMapper = sessionMapper;
        this.turnMapper = turnMapper;
        this.taskMapper = taskMapper;
        this.extractor = extractor;
        this.taskService = taskService;
        this.objectMapper = objectMapper;
        this.currentUser = currentUser;
    }

    @Transactional
    public FreeInterviewDtos.SessionView create(MultipartFile resume, String targetRole) {
        FreeInterviewSession session = new FreeInterviewSession();
        session.setCandidateId(currentUser.id());
        session.setResumeFilename(resume.getOriginalFilename());
        session.setTargetRole(blankToNull(targetRole));
        session.setResumeText(extractor.extract(resume));
        session.setResumeSummary("{}");
        session.setStatus(FreeInterviewSession.ANALYZING);
        sessionMapper.insert(session);
        AiTask task = taskService.enqueueFreeInterviewAnalysis(session.getId());
        return view(session, List.of(), task);
    }

    @Transactional
    public FreeInterviewDtos.TurnResult submitTurn(Long id, FreeInterviewDtos.SubmitTurnRequest request) {
        FreeInterviewSession session = owned(id);
        if (!FreeInterviewSession.INTERVIEWING.equals(session.getStatus())) {
            throw BusinessException.badRequest("当前自由面试尚未进入可答题状态");
        }

        FreeInterviewTurn existing = turnMapper.selectOne(new LambdaQueryWrapper<FreeInterviewTurn>()
                .eq(FreeInterviewTurn::getSessionId, id)
                .eq(FreeInterviewTurn::getSubmissionKey, request.submissionId().trim())
                .last("LIMIT 1"));
        if (existing != null) return existingSubmission(session, existing);

        List<FreeInterviewTurn> previousTurns = turns(id);
        int turnNo = previousTurns.size() + 1;
        if (turnNo > TOTAL_TURNS) throw BusinessException.badRequest("本次自由面试已完成 10 轮");

        String expectedQuestion = expectedQuestion(session, previousTurns);
        if (expectedQuestion == null || expectedQuestion.isBlank()) {
            throw BusinessException.badRequest("上一轮追问仍在生成，请稍后重试");
        }
        String submittedQuestion = request.question().trim();
        if (!expectedQuestion.equals(submittedQuestion)) {
            throw BusinessException.badRequest("当前问题已更新，请刷新页面后继续作答");
        }

        FreeInterviewTurn turn = new FreeInterviewTurn();
        turn.setSessionId(id);
        turn.setTurnNo(turnNo);
        turn.setSubmissionKey(request.submissionId().trim());
        turn.setQuestion(expectedQuestion);
        turn.setAnswer(request.answer().trim());
        try {
            turnMapper.insert(turn);
        } catch (DuplicateKeyException exception) {
            FreeInterviewTurn duplicate = turnMapper.selectOne(new LambdaQueryWrapper<FreeInterviewTurn>()
                    .eq(FreeInterviewTurn::getSessionId, id)
                    .eq(FreeInterviewTurn::getSubmissionKey, request.submissionId().trim())
                    .last("LIMIT 1"));
            if (duplicate != null) return existingSubmission(session, duplicate);
            throw exception;
        }

        AiTask task;
        if (turnNo < TOTAL_TURNS) {
            task = taskService.enqueueFreeInterviewFollowUp(id, turn.getId());
        } else {
            session.setStatus(FreeInterviewSession.REPORT_GENERATING);
            sessionMapper.updateById(session);
            task = taskService.enqueueFreeInterviewReport(id);
        }
        List<FreeInterviewTurn> savedTurns = turns(id);
        return new FreeInterviewDtos.TurnResult(view(session, savedTurns, task), null, task.getId());
    }

    @Transactional
    public FreeInterviewDtos.TaskResult requestReport(Long id) {
        FreeInterviewSession session = owned(id);
        List<FreeInterviewTurn> turns = turns(id);
        if (turns.size() < TOTAL_TURNS) throw BusinessException.badRequest("完成 10 轮问答后才能生成报告");
        if (FreeInterviewSession.REPORT_READY.equals(session.getStatus())) {
            return new FreeInterviewDtos.TaskResult(view(session, turns, null), null);
        }
        session.setStatus(FreeInterviewSession.REPORT_GENERATING);
        sessionMapper.updateById(session);
        AiTask task = taskService.enqueueFreeInterviewReport(id);
        return new FreeInterviewDtos.TaskResult(view(session, turns, task), task.getId());
    }

    public FreeInterviewDtos.DetailView detail(Long id) {
        FreeInterviewSession session = owned(id);
        List<FreeInterviewTurn> turns = turns(id);
        return new FreeInterviewDtos.DetailView(view(session, turns, null), turns.stream()
                .map(turn -> new FreeInterviewDtos.TurnView(turn.getTurnNo(), turn.getQuestion(), turn.getAnswer(),
                        turn.getNextQuestion(), turn.getCreatedAt())).toList());
    }

    public List<FreeInterviewDtos.HistoryView> history() {
        List<FreeInterviewSession> sessions = sessionMapper.selectList(new LambdaQueryWrapper<FreeInterviewSession>()
                .eq(FreeInterviewSession::getCandidateId, currentUser.id())
                .orderByDesc(FreeInterviewSession::getUpdatedAt)
                .orderByDesc(FreeInterviewSession::getId));
        if (sessions.isEmpty()) return List.of();

        List<Long> sessionIds = sessions.stream().map(FreeInterviewSession::getId).toList();
        List<FreeInterviewTurn> savedTurns = turnMapper.selectList(new LambdaQueryWrapper<FreeInterviewTurn>()
                .select(FreeInterviewTurn::getSessionId)
                .in(FreeInterviewTurn::getSessionId, sessionIds));
        Map<Long, Integer> turnCounts = new HashMap<>();
        savedTurns.forEach(turn -> turnCounts.merge(turn.getSessionId(), 1, Integer::sum));

        return sessions.stream().map(session -> new FreeInterviewDtos.HistoryView(
                session.getId(), session.getResumeFilename(), session.getTargetRole(), session.getStatus(),
                turnCounts.getOrDefault(session.getId(), 0), session.getTotalScore(), session.getCreatedAt(),
                session.getUpdatedAt(), session.getCompletedAt())).toList();
    }

    private FreeInterviewDtos.TurnResult existingSubmission(FreeInterviewSession session, FreeInterviewTurn turn) {
        AiTask task = findTask(turn.getTurnNo() < TOTAL_TURNS
                ? "free-follow-up:" + turn.getId()
                : "free-report:" + session.getId());
        List<FreeInterviewTurn> turns = turns(session.getId());
        return new FreeInterviewDtos.TurnResult(view(session, turns, task), turn.getNextQuestion(), task == null ? null : task.getId());
    }

    private FreeInterviewSession owned(Long id) {
        FreeInterviewSession session = sessionMapper.selectById(id);
        if (session == null) throw BusinessException.notFound("自由面试不存在");
        if (!currentUser.id().equals(session.getCandidateId())) throw BusinessException.forbidden("无权访问该自由面试");
        return session;
    }

    private List<FreeInterviewTurn> turns(Long id) {
        return turnMapper.selectList(new LambdaQueryWrapper<FreeInterviewTurn>()
                .eq(FreeInterviewTurn::getSessionId, id).orderByAsc(FreeInterviewTurn::getTurnNo));
    }

    private FreeInterviewDtos.SessionView view(FreeInterviewSession item, List<FreeInterviewTurn> turns, AiTask suppliedTask) {
        AiTask activeTask = suppliedTask == null ? activeTask(item, turns) : suppliedTask;
        return new FreeInterviewDtos.SessionView(item.getId(), item.getResumeFilename(), item.getTargetRole(), item.getResumeSummary(),
                item.getStatus(), turns.size(), openingPrompt(item),
                FreeInterviewSession.REPORT_READY.equals(item.getStatus()) ? report(item) : null, item.getCreatedAt(),
                activeTask == null ? null : activeTask.getId(), activeTask == null ? null : activeTask.getTaskType(),
                activeTask == null ? null : activeTask.getStatus());
    }

    private AiTask activeTask(FreeInterviewSession session, List<FreeInterviewTurn> turns) {
        if (FreeInterviewSession.ANALYZING.equals(session.getStatus())
                || (FreeInterviewSession.FAILED.equals(session.getStatus()) && turns.isEmpty())) {
            return findTask("free-analysis:" + session.getId());
        }
        if (turns.size() >= TOTAL_TURNS && (FreeInterviewSession.REPORT_GENERATING.equals(session.getStatus())
                || FreeInterviewSession.FAILED.equals(session.getStatus()))) {
            return findTask("free-report:" + session.getId());
        }
        if (!turns.isEmpty()) {
            FreeInterviewTurn last = turns.get(turns.size() - 1);
            if (last.getTurnNo() < TOTAL_TURNS && (last.getNextQuestion() == null || last.getNextQuestion().isBlank())) {
                return findTask("free-follow-up:" + last.getId());
            }
        }
        return null;
    }

    private AiTask findTask(String dedupeKey) {
        return taskMapper.selectOne(new LambdaQueryWrapper<AiTask>().eq(AiTask::getDedupeKey, dedupeKey).last("LIMIT 1"));
    }

    private String expectedQuestion(FreeInterviewSession session, List<FreeInterviewTurn> turns) {
        if (turns.isEmpty()) return openingPrompt(session);
        return blankToNull(turns.get(turns.size() - 1).getNextQuestion());
    }

    private String openingPrompt(FreeInterviewSession item) {
        try {
            String question = objectMapper.readTree(item.getResumeSummary()).path("openingQuestion").asText().trim();
            if (!question.isBlank()) return question;
        } catch (JsonProcessingException ignored) {
            // Older sessions may contain plain text; use the stable fallback.
        }
        return "请结合你的简历，选择一个最有代表性的项目，说明你的职责、技术难点和最终结果。";
    }

    private FreeInterviewDtos.ReportView report(FreeInterviewSession item) {
        return new FreeInterviewDtos.ReportView(item.getTotalScore(), item.getProfessionalScore(), item.getExpressionScore(),
                item.getLogicScore(), item.getAdaptabilityScore(), item.getSummary(), item.getStrengths(),
                item.getWeaknesses(), item.getImprovementSuggestions());
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
