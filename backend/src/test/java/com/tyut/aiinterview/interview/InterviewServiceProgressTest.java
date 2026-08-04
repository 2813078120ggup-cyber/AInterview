package com.tyut.aiinterview.interview;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tyut.aiinterview.common.BusinessException;
import com.tyut.aiinterview.domain.AiTask;
import com.tyut.aiinterview.domain.Interview;
import com.tyut.aiinterview.domain.InterviewAnswer;
import com.tyut.aiinterview.domain.InterviewQuestion;
import com.tyut.aiinterview.mapper.AiTaskMapper;
import com.tyut.aiinterview.mapper.InterviewAnswerMapper;
import com.tyut.aiinterview.mapper.InterviewMapper;
import com.tyut.aiinterview.mapper.InterviewQuestionMapper;
import com.tyut.aiinterview.mapper.JobPositionMapper;
import com.tyut.aiinterview.mapper.QuestionBankMapper;
import com.tyut.aiinterview.mapper.QuestionMapper;
import com.tyut.aiinterview.mapper.UserMapper;
import com.tyut.aiinterview.mapper.UserRoleMapper;
import com.tyut.aiinterview.security.CurrentUser;
import com.tyut.aiinterview.recording.InterviewRecordingService;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;

class InterviewServiceProgressTest {
    @Test
    void derivesCountdownFollowUpsAndRecoverableTaskFromServerState() {
        InterviewMapper interviewMapper = mock(InterviewMapper.class);
        AiTaskMapper taskMapper = mock(AiTaskMapper.class);
        InterviewQuestionMapper questionMapper = mock(InterviewQuestionMapper.class);
        InterviewAnswerMapper answerMapper = mock(InterviewAnswerMapper.class);
        CurrentUser currentUser = mock(CurrentUser.class);
        InterviewService service = new InterviewService(interviewMapper, taskMapper, questionMapper, answerMapper,
                mock(QuestionMapper.class), mock(QuestionBankMapper.class), mock(UserMapper.class),
                mock(UserRoleMapper.class), mock(JobPositionMapper.class), currentUser,
                mock(AiEvaluationGateway.class), mock(InterviewRecordingService.class), new ObjectMapper(), 15);

        Interview interview = new Interview();
        interview.setId(11L);
        interview.setCandidateId(7L);
        interview.setInterviewerId(8L);
        interview.setStatus(Interview.IN_PROGRESS);
        interview.setDuration(60);
        interview.setStartedAt(LocalDateTime.now().minusSeconds(120));
        interview.setActiveQuestionIndex(0);
        when(currentUser.id()).thenReturn(7L);
        when(interviewMapper.selectById(11L)).thenReturn(interview);

        InterviewQuestion question = new InterviewQuestion();
        question.setId(21L);
        question.setInterviewId(11L);
        question.setSequenceNo(1);
        question.setQuestionSnapshot("{\"content\":\"什么是 volatile？\",\"questionType\":\"subjective\"}");
        when(questionMapper.selectList(any())).thenReturn(List.of(question));

        InterviewAnswer answer = new InterviewAnswer();
        answer.setId(31L);
        answer.setInterviewQuestionId(21L);
        answer.setAnswerData("[{\"role\":\"assistant\",\"content\":\"如何保证可见性？\",\"kind\":\"follow-up\"}]");
        when(answerMapper.selectOne(any())).thenReturn(answer);

        AiTask task = new AiTask();
        task.setId(41L);
        task.setAnswerId(31L);
        task.setTaskType("FOLLOW_UP");
        task.setStatus("SUCCESS");
        task.setDedupeKey("simulation-follow-up:21:1");
        when(taskMapper.selectOne(any())).thenReturn(task);

        InterviewDtos.ProgressView result = service.progress(11L);

        assertEquals(0, result.activeQuestionIndex());
        assertTrue(result.remainingSeconds() >= 3478 && result.remainingSeconds() <= 3480);
        assertEquals(1, result.followUpCount());
        assertEquals(3, result.followUpLimit());
        assertEquals(41L, result.activeTaskId());
        assertEquals(1, result.activeTaskSequence());
    }

    @Test
    void recordedInterviewRejectsSkippingQuestions() {
        InterviewMapper interviewMapper = mock(InterviewMapper.class);
        InterviewQuestionMapper questionMapper = mock(InterviewQuestionMapper.class);
        InterviewAnswerMapper answerMapper = mock(InterviewAnswerMapper.class);
        InterviewRecordingService recordingService = mock(InterviewRecordingService.class);
        CurrentUser currentUser = mock(CurrentUser.class);
        InterviewService service = service(interviewMapper, mock(AiTaskMapper.class), questionMapper,
                answerMapper, currentUser, recordingService);
        Interview interview = inProgressInterview();
        when(currentUser.id()).thenReturn(7L);
        when(interviewMapper.selectById(11L)).thenReturn(interview);
        when(questionMapper.selectCount(any())).thenReturn(3L);
        when(recordingService.requiresSequentialMode(11L)).thenReturn(true);

        BusinessException exception = assertThrows(BusinessException.class,
                () -> service.updateProgress(11L, new InterviewDtos.ProgressRequest(2)));

        assertEquals("录制面试只能完成当前题后按顺序进入下一题", exception.getMessage());
    }

    @Test
    void recordedInterviewRejectsNextQuestionUntilCurrentAnswerIsSaved() {
        InterviewMapper interviewMapper = mock(InterviewMapper.class);
        InterviewQuestionMapper questionMapper = mock(InterviewQuestionMapper.class);
        InterviewAnswerMapper answerMapper = mock(InterviewAnswerMapper.class);
        InterviewRecordingService recordingService = mock(InterviewRecordingService.class);
        CurrentUser currentUser = mock(CurrentUser.class);
        InterviewService service = service(interviewMapper, mock(AiTaskMapper.class), questionMapper,
                answerMapper, currentUser, recordingService);
        Interview interview = inProgressInterview();
        InterviewQuestion currentQuestion = new InterviewQuestion();
        currentQuestion.setId(21L);
        currentQuestion.setInterviewId(11L);
        when(currentUser.id()).thenReturn(7L);
        when(interviewMapper.selectById(11L)).thenReturn(interview);
        when(questionMapper.selectCount(any())).thenReturn(2L);
        when(questionMapper.selectOne(any())).thenReturn(currentQuestion);
        when(answerMapper.exists(any())).thenReturn(false);
        when(recordingService.requiresSequentialMode(11L)).thenReturn(true);

        BusinessException exception = assertThrows(BusinessException.class,
                () -> service.updateProgress(11L, new InterviewDtos.ProgressRequest(1)));

        assertEquals("请先完成并保存当前题目，再进入下一题", exception.getMessage());
    }

    private InterviewService service(InterviewMapper interviewMapper, AiTaskMapper taskMapper,
                                     InterviewQuestionMapper questionMapper, InterviewAnswerMapper answerMapper,
                                     CurrentUser currentUser, InterviewRecordingService recordingService) {
        return new InterviewService(interviewMapper, taskMapper, questionMapper, answerMapper,
                mock(QuestionMapper.class), mock(QuestionBankMapper.class), mock(UserMapper.class),
                mock(UserRoleMapper.class), mock(JobPositionMapper.class), currentUser,
                mock(AiEvaluationGateway.class), recordingService, new ObjectMapper(), 15);
    }

    private Interview inProgressInterview() {
        Interview interview = new Interview();
        interview.setId(11L);
        interview.setCandidateId(7L);
        interview.setInterviewerId(8L);
        interview.setStatus(Interview.IN_PROGRESS);
        interview.setDuration(60);
        interview.setStartedAt(LocalDateTime.now());
        interview.setActiveQuestionIndex(0);
        return interview;
    }
}
