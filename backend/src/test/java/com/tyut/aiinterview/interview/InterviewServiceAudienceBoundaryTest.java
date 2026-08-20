package com.tyut.aiinterview.interview;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tyut.aiinterview.common.BusinessException;
import com.tyut.aiinterview.domain.Interview;
import com.tyut.aiinterview.mapper.AiTaskMapper;
import com.tyut.aiinterview.mapper.InterviewAnswerMapper;
import com.tyut.aiinterview.mapper.InterviewMapper;
import com.tyut.aiinterview.mapper.InterviewQuestionMapper;
import com.tyut.aiinterview.mapper.JobPositionMapper;
import com.tyut.aiinterview.mapper.QuestionBankMapper;
import com.tyut.aiinterview.mapper.QuestionMapper;
import com.tyut.aiinterview.mapper.UserMapper;
import com.tyut.aiinterview.mapper.UserRoleMapper;
import com.tyut.aiinterview.recording.InterviewRecordingService;
import com.tyut.aiinterview.recruitment.CompanyAccessService;
import com.tyut.aiinterview.security.CurrentUser;
import java.lang.reflect.Method;
import java.time.LocalDateTime;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.security.access.prepost.PreAuthorize;

class InterviewServiceAudienceBoundaryTest {
    private InterviewMapper interviewMapper;
    private AiTaskMapper aiTaskMapper;
    private InterviewQuestionMapper interviewQuestionMapper;
    private InterviewAnswerMapper answerMapper;
    private CurrentUser currentUser;
    private InterviewRecordingService recordingService;
    private AiEvaluationGateway aiEvaluationGateway;

    @BeforeEach
    void setUp() {
        interviewMapper = mock(InterviewMapper.class);
        aiTaskMapper = mock(AiTaskMapper.class);
        interviewQuestionMapper = mock(InterviewQuestionMapper.class);
        answerMapper = mock(InterviewAnswerMapper.class);
        currentUser = mock(CurrentUser.class);
        recordingService = mock(InterviewRecordingService.class);
        aiEvaluationGateway = mock(AiEvaluationGateway.class);
    }

    @Test
    void adminCannotStartEndUpdateProgressOrSubmitAnswerEvenWhenIdMatchesCandidate() {
        Interview interview = interview(Interview.PENDING);
        when(interviewMapper.selectById(11L)).thenReturn(interview);
        when(currentUser.id()).thenReturn(7L);
        when(currentUser.hasRole("CANDIDATE")).thenReturn(true);
        when(currentUser.hasRole("ADMIN")).thenReturn(true);
        InterviewService service = service();

        assertForbidden(() -> service.start(11L));

        interview.setStatus(Interview.IN_PROGRESS);
        assertForbidden(() -> service.end(11L));
        assertForbidden(() -> service.updateProgress(11L, new InterviewDtos.ProgressRequest(0)));
        assertForbidden(() -> service.submitAnswer(11L, 21L,
                new InterviewDtos.AnswerRequest("管理员不应代答", null, null, 10)));
    }

    @Test
    void anotherCandidateCannotWriteToSomeoneElsesInterview() {
        Interview interview = interview(Interview.PENDING);
        when(interviewMapper.selectById(11L)).thenReturn(interview);
        when(currentUser.id()).thenReturn(8L);
        when(currentUser.hasRole("CANDIDATE")).thenReturn(true);
        InterviewService service = service();

        assertForbidden(() -> service.start(11L));

        interview.setStatus(Interview.IN_PROGRESS);
        assertForbidden(() -> service.end(11L));
        assertForbidden(() -> service.updateProgress(11L, new InterviewDtos.ProgressRequest(0)));
        assertForbidden(() -> service.submitAnswer(11L, 21L,
                new InterviewDtos.AnswerRequest("其他候选人不应代答", null, null, 10)));
    }

    @Test
    void companyUserCannotWriteToCandidateInterview() {
        Interview interview = interview(Interview.PENDING);
        when(interviewMapper.selectById(11L)).thenReturn(interview);
        when(currentUser.id()).thenReturn(7L);
        when(currentUser.hasRole("COMPANY_INTERVIEWER")).thenReturn(true);
        InterviewService service = service();

        assertForbidden(() -> service.start(11L));
        interview.setStatus(Interview.IN_PROGRESS);
        assertForbidden(() -> service.end(11L));
        assertForbidden(() -> service.updateProgress(11L, new InterviewDtos.ProgressRequest(0)));
        assertForbidden(() -> service.submitAnswer(11L, 21L,
                new InterviewDtos.AnswerRequest("企业用户不应代答", null, null, 10)));
    }

    @Test
    void assignedCandidateWithCandidateRoleCanStartInterview() {
        Interview interview = interview(Interview.PENDING);
        when(interviewMapper.selectById(11L)).thenReturn(interview);
        when(interviewQuestionMapper.exists(any())).thenReturn(true);
        when(interviewMapper.update(any(), any())).thenReturn(1);
        when(currentUser.id()).thenReturn(7L);
        when(currentUser.hasRole("CANDIDATE")).thenReturn(true);

        Interview result = service().start(11L);

        assertEquals(Interview.IN_PROGRESS, result.getStatus());
        assertEquals(7L, result.getCandidateId());
    }

    @ParameterizedTest
    @MethodSource("candidateWriteMethods")
    void candidateWriteEndpointsRequireCandidateRole(String methodName, Class<?>[] parameterTypes) throws Exception {
        Method method = InterviewController.class.getDeclaredMethod(methodName, parameterTypes);
        PreAuthorize preAuthorize = method.getAnnotation(PreAuthorize.class);

        assertEquals("hasRole('CANDIDATE')", preAuthorize.value());
    }

    private static Stream<Arguments> candidateWriteMethods() {
        return Stream.of(
                Arguments.of("start", new Class<?>[]{Long.class}),
                Arguments.of("end", new Class<?>[]{Long.class}),
                Arguments.of("updateProgress", new Class<?>[]{Long.class, InterviewDtos.ProgressRequest.class}),
                Arguments.of("answer", new Class<?>[]{Long.class, Long.class, InterviewDtos.AnswerRequest.class}));
    }

    private InterviewService service() {
        return new InterviewService(interviewMapper, aiTaskMapper, interviewQuestionMapper, answerMapper,
                mock(QuestionMapper.class), mock(QuestionBankMapper.class), mock(UserMapper.class),
                mock(UserRoleMapper.class), mock(JobPositionMapper.class), currentUser,
                aiEvaluationGateway, recordingService, new ObjectMapper(), 15,
                mock(CompanyAccessService.class));
    }

    private Interview interview(int status) {
        Interview interview = new Interview();
        interview.setId(11L);
        interview.setCandidateId(7L);
        interview.setInterviewerId(9L);
        interview.setStatus(status);
        interview.setDuration(60);
        interview.setScheduledAt(LocalDateTime.now());
        interview.setActiveQuestionIndex(0);
        return interview;
    }

    private void assertForbidden(org.junit.jupiter.api.function.Executable executable) {
        BusinessException exception = assertThrows(BusinessException.class, executable);
        assertEquals(40300, exception.getCode());
    }
}
