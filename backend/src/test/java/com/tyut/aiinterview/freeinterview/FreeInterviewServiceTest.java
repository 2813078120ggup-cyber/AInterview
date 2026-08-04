package com.tyut.aiinterview.freeinterview;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tyut.aiinterview.ai.AiTaskService;
import com.tyut.aiinterview.domain.AiTask;
import com.tyut.aiinterview.domain.FreeInterviewSession;
import com.tyut.aiinterview.domain.FreeInterviewTurn;
import com.tyut.aiinterview.mapper.AiTaskMapper;
import com.tyut.aiinterview.mapper.FreeInterviewSessionMapper;
import com.tyut.aiinterview.mapper.FreeInterviewTurnMapper;
import com.tyut.aiinterview.security.CurrentUser;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.springframework.web.multipart.MultipartFile;

class FreeInterviewServiceTest {
    private final FreeInterviewSessionMapper sessionMapper = mock(FreeInterviewSessionMapper.class);
    private final FreeInterviewTurnMapper turnMapper = mock(FreeInterviewTurnMapper.class);
    private final AiTaskMapper taskMapper = mock(AiTaskMapper.class);
    private final ResumeTextExtractor extractor = mock(ResumeTextExtractor.class);
    private final AiTaskService taskService = mock(AiTaskService.class);
    private final CurrentUser currentUser = mock(CurrentUser.class);
    private FreeInterviewService service;

    @BeforeEach
    void setUp() {
        service = new FreeInterviewService(sessionMapper, turnMapper, taskMapper, extractor, taskService,
                new ObjectMapper(), currentUser);
    }

    @Test
    void createPersistsResumeBeforeEnqueuingAnalysis() {
        MultipartFile resume = mock(MultipartFile.class);
        when(resume.getOriginalFilename()).thenReturn("resume.pdf");
        when(extractor.extract(resume)).thenReturn("resume text");
        when(currentUser.id()).thenReturn(7L);
        when(sessionMapper.insert(any(FreeInterviewSession.class))).thenAnswer(invocation -> {
            FreeInterviewSession session = invocation.getArgument(0);
            session.setId(21L);
            return 1;
        });
        AiTask task = task(31L, AiTaskService.FREE_INTERVIEW_ANALYSIS);
        when(taskService.enqueueFreeInterviewAnalysis(21L)).thenReturn(task);

        FreeInterviewDtos.SessionView result = service.create(resume, "Java 后端开发工程师");

        assertEquals(21L, result.id());
        assertEquals(FreeInterviewSession.ANALYZING, result.status());
        assertEquals(31L, result.activeTaskId());
        InOrder order = inOrder(sessionMapper, taskService);
        order.verify(sessionMapper).insert(any(FreeInterviewSession.class));
        order.verify(taskService).enqueueFreeInterviewAnalysis(21L);
    }

    @Test
    void submitPersistsAnswerBeforeEnqueuingFollowUp() {
        FreeInterviewSession session = new FreeInterviewSession();
        session.setId(21L);
        session.setCandidateId(7L);
        session.setResumeSummary("{}");
        session.setStatus(FreeInterviewSession.INTERVIEWING);
        when(currentUser.id()).thenReturn(7L);
        when(sessionMapper.selectById(21L)).thenReturn(session);
        when(turnMapper.selectOne(any())).thenReturn(null);

        AtomicReference<FreeInterviewTurn> saved = new AtomicReference<>();
        when(turnMapper.selectList(any())).thenAnswer(invocation -> saved.get() == null ? List.of() : List.of(saved.get()));
        when(turnMapper.insert(any(FreeInterviewTurn.class))).thenAnswer(invocation -> {
            FreeInterviewTurn turn = invocation.getArgument(0);
            turn.setId(41L);
            saved.set(turn);
            return 1;
        });
        AiTask task = task(51L, AiTaskService.FREE_INTERVIEW_FOLLOW_UP);
        when(taskService.enqueueFreeInterviewFollowUp(21L, 41L)).thenReturn(task);
        String question = "请结合你的简历，选择一个最有代表性的项目，说明你的职责、技术难点和最终结果。";

        FreeInterviewDtos.TurnResult result = service.submitTurn(21L,
                new FreeInterviewDtos.SubmitTurnRequest("submission-1", question, "我负责后端接口和部署。"));

        assertEquals(1, result.session().completedTurns());
        assertEquals(51L, result.taskId());
        assertNull(result.nextQuestion());
        assertEquals("submission-1", saved.get().getSubmissionKey());
        InOrder order = inOrder(turnMapper, taskService);
        order.verify(turnMapper).insert(any(FreeInterviewTurn.class));
        order.verify(taskService).enqueueFreeInterviewFollowUp(21L, 41L);
        verify(turnMapper).selectOne(any());
    }

    @Test
    void historyReturnsOnlyCurrentCandidatesSessionsWithTurnCounts() {
        when(currentUser.id()).thenReturn(7L);
        FreeInterviewSession session = new FreeInterviewSession();
        session.setId(21L);
        session.setCandidateId(7L);
        session.setResumeFilename("resume.pdf");
        session.setTargetRole("Java 后端开发工程师");
        session.setStatus(FreeInterviewSession.INTERVIEWING);
        session.setCreatedAt(LocalDateTime.of(2026, 7, 28, 10, 0));
        session.setUpdatedAt(LocalDateTime.of(2026, 7, 28, 10, 30));
        when(sessionMapper.selectList(any())).thenReturn(List.of(session));

        FreeInterviewTurn first = new FreeInterviewTurn();
        first.setSessionId(21L);
        FreeInterviewTurn second = new FreeInterviewTurn();
        second.setSessionId(21L);
        when(turnMapper.selectList(any())).thenReturn(List.of(first, second));

        List<FreeInterviewDtos.HistoryView> result = service.history();

        assertEquals(1, result.size());
        assertEquals(21L, result.get(0).id());
        assertEquals(2, result.get(0).completedTurns());
        assertEquals("Java 后端开发工程师", result.get(0).targetRole());
        verify(sessionMapper).selectList(any());
        verify(turnMapper).selectList(any());
    }

    private AiTask task(Long id, String type) {
        AiTask task = new AiTask();
        task.setId(id);
        task.setTaskType(type);
        task.setStatus("PENDING");
        return task;
    }
}
