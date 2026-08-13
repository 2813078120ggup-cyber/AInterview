package com.tyut.aiinterview.algorithm;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.tyut.aiinterview.algorithm.config.AlgorithmJudgeProperties;
import com.tyut.aiinterview.common.BusinessException;
import com.tyut.aiinterview.domain.AlgorithmProblem;
import com.tyut.aiinterview.domain.AlgorithmSubmission;
import com.tyut.aiinterview.mapper.AlgorithmProblemMapper;
import com.tyut.aiinterview.mapper.AlgorithmSubmissionMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class AlgorithmJudgeServiceTest {
    private AlgorithmProblemMapper problemMapper;
    private AlgorithmSubmissionMapper submissionMapper;
    private AlgorithmJudgeWorkerClient workerClient;
    private AlgorithmJudgeService service;

    @BeforeEach
    void setUp() {
        problemMapper = mock(AlgorithmProblemMapper.class);
        submissionMapper = mock(AlgorithmSubmissionMapper.class);
        workerClient = mock(AlgorithmJudgeWorkerClient.class);
        AlgorithmJudgeProperties properties = new AlgorithmJudgeProperties();
        properties.setSourceLimitChars(1000);
        service = new AlgorithmJudgeService(problemMapper, submissionMapper, properties, workerClient);

        AlgorithmProblem problem = new AlgorithmProblem();
        problem.setId(7L);
        problem.setStatus(1);
        problem.setTimeLimitMs(3000);
        problem.setMemoryLimitMb(128);
        when(problemMapper.selectById(7L)).thenReturn(problem);
        doAnswer(invocation -> {
            invocation.getArgument(0, AlgorithmSubmission.class).setId(301L);
            return 1;
        }).when(submissionMapper).insert(any(AlgorithmSubmission.class));
    }

    @Test
    void persistsAcceptedRunResultReturnedByWorker() {
        when(workerClient.run("public class Main {}", "42", 3000, 128))
                .thenReturn(new AlgorithmJudgeWorkerClient.RunResult(null, "ACCEPTED", "42", null, 16, 4096));

        AlgorithmDtos.RunResponse response = service.run(11L,
                new AlgorithmDtos.RunRequest(7L, null, "public class Main {}", "42"));

        assertEquals(301L, response.submissionId());
        assertEquals("ACCEPTED", response.status());
        assertEquals("42", response.output());
        assertEquals(16L, response.executionTimeMs());
        ArgumentCaptor<AlgorithmSubmission> updated = ArgumentCaptor.forClass(AlgorithmSubmission.class);
        verify(submissionMapper).updateById(updated.capture());
        assertEquals("RUN", updated.getValue().getSubmitType());
        assertEquals("ACCEPTED", updated.getValue().getStatus());
        assertEquals(11L, updated.getValue().getUserId());
    }

    @Test
    void convertsWorkerFailureToSystemErrorAndPersistsIt() {
        when(workerClient.run("public class Main {}", "", 3000, 128))
                .thenReturn(new AlgorithmJudgeWorkerClient.RunResult(
                        "ConnectException: refused", "SYSTEM_ERROR", "", "连接 Worker 失败", 0, 0));

        AlgorithmDtos.RunResponse response = service.run(11L,
                new AlgorithmDtos.RunRequest(7L, "JAVA17", "public class Main {}", ""));

        assertEquals("SYSTEM_ERROR", response.status());
        assertEquals("ConnectException: refused", response.errorMessage());
        ArgumentCaptor<AlgorithmSubmission> updated = ArgumentCaptor.forClass(AlgorithmSubmission.class);
        verify(submissionMapper).updateById(updated.capture());
        assertEquals("SYSTEM_ERROR", updated.getValue().getStatus());
        assertEquals("ConnectException: refused", updated.getValue().getRuntimeMessage());
    }

    @Test
    void rejectsWorkerResponseWithoutStatus() {
        when(workerClient.run("public class Main {}", "", 3000, 128))
                .thenReturn(new AlgorithmJudgeWorkerClient.RunResult(null, null, "", null, 0, 0));

        AlgorithmDtos.RunResponse response = service.run(11L,
                new AlgorithmDtos.RunRequest(7L, "JAVA17", "public class Main {}", ""));

        assertEquals("SYSTEM_ERROR", response.status());
        assertEquals("判题 Worker 返回无效状态", response.errorMessage());
    }

    @Test
    void validatesSourceBeforeCreatingSubmission() {
        assertThrows(BusinessException.class, () -> service.run(11L,
                new AlgorithmDtos.RunRequest(7L, "JAVA17", "class NotMain {}", "")));

        verify(submissionMapper, never()).insert(any(AlgorithmSubmission.class));
        verify(workerClient, never()).run(any(String.class), any(String.class), anyInt(), anyInt());
    }
}
