package com.tyut.aiinterview.algorithm;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.tyut.aiinterview.common.BusinessException;
import com.tyut.aiinterview.domain.AlgorithmProblem;
import com.tyut.aiinterview.domain.AlgorithmSubmission;
import com.tyut.aiinterview.mapper.AlgorithmCaseResultMapper;
import com.tyut.aiinterview.mapper.AlgorithmProblemMapper;
import com.tyut.aiinterview.mapper.AlgorithmSubmissionMapper;
import com.tyut.aiinterview.mapper.AlgorithmTestCaseMapper;
import com.tyut.aiinterview.security.CurrentUser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class AlgorithmSubmissionServiceTest {
    private AlgorithmProblemMapper problemMapper;
    private AlgorithmSubmissionMapper submissionMapper;
    private AlgorithmJudgeTaskService taskService;
    private AlgorithmSubmissionService service;

    @BeforeEach
    void setUp() {
        submissionMapper = mock(AlgorithmSubmissionMapper.class);
        problemMapper = mock(AlgorithmProblemMapper.class);
        taskService = mock(AlgorithmJudgeTaskService.class);
        AlgorithmProblem problem = new AlgorithmProblem();
        problem.setId(7L);
        problem.setStatus(1);
        when(problemMapper.selectById(7L)).thenReturn(problem);
        doAnswer(invocation -> {
            invocation.getArgument(0, AlgorithmSubmission.class).setId(302L);
            return 1;
        }).when(submissionMapper).insert(any(AlgorithmSubmission.class));
        service = new AlgorithmSubmissionService(
                submissionMapper,
                problemMapper,
                mock(AlgorithmCaseResultMapper.class),
                mock(AlgorithmTestCaseMapper.class),
                mock(AlgorithmJudgeService.class),
                taskService,
                mock(CurrentUser.class));
    }

    @Test
    void createsQueuedSubmissionAndPublishesItsId() {
        Long submissionId = service.submit(11L,
                new AlgorithmDtos.SubmitRequest(7L, null, "public class Main {}"));

        assertEquals(302L, submissionId);
        ArgumentCaptor<AlgorithmSubmission> inserted = ArgumentCaptor.forClass(AlgorithmSubmission.class);
        verify(submissionMapper).insert(inserted.capture());
        assertEquals("SUBMIT", inserted.getValue().getSubmitType());
        assertEquals("QUEUED", inserted.getValue().getStatus());
        assertEquals("JAVA17", inserted.getValue().getLanguage());
        verify(taskService).publish(302L);
    }

    @Test
    void rejectsDisabledProblemWithoutCreatingTask() {
        when(problemMapper.selectById(7L)).thenReturn(null);

        assertThrows(BusinessException.class, () -> service.submit(11L,
                new AlgorithmDtos.SubmitRequest(7L, "JAVA17", "public class Main {}")));

        verify(submissionMapper, never()).insert(any(AlgorithmSubmission.class));
        verify(taskService, never()).publish(any());
    }
}
