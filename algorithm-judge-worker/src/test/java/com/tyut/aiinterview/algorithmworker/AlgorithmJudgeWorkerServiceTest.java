package com.tyut.aiinterview.algorithmworker;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.tyut.aiinterview.algorithmworker.config.AlgorithmJudgeProperties;
import com.tyut.aiinterview.algorithmworker.domain.AlgorithmProblem;
import com.tyut.aiinterview.algorithmworker.domain.AlgorithmSubmission;
import com.tyut.aiinterview.algorithmworker.domain.AlgorithmTestCase;
import com.tyut.aiinterview.algorithmworker.judge.DockerJavaSandbox;
import com.tyut.aiinterview.algorithmworker.mapper.AlgorithmProblemMapper;
import com.tyut.aiinterview.algorithmworker.mapper.AlgorithmSubmissionMapper;
import com.tyut.aiinterview.algorithmworker.mapper.AlgorithmTestCaseMapper;
import com.tyut.aiinterview.algorithmworker.observability.AlgorithmJudgeMetrics;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class AlgorithmJudgeWorkerServiceTest {
    private DockerJavaSandbox sandbox;
    private AlgorithmJudgeProperties properties;
    private AlgorithmProblemMapper problemMapper;
    private AlgorithmSubmissionMapper submissionMapper;
    private AlgorithmTestCaseMapper testCaseMapper;
    private AlgorithmJudgeResultPersistenceService resultPersistenceService;
    private AlgorithmJudgeWorkerService service;

    @BeforeEach
    void setUp() {
        sandbox = mock(DockerJavaSandbox.class);
        properties = new AlgorithmJudgeProperties();
        properties.setSourceLimitChars(100);
        problemMapper = mock(AlgorithmProblemMapper.class);
        submissionMapper = mock(AlgorithmSubmissionMapper.class);
        testCaseMapper = mock(AlgorithmTestCaseMapper.class);
        resultPersistenceService = mock(AlgorithmJudgeResultPersistenceService.class);
        service = new AlgorithmJudgeWorkerService(
                problemMapper,
                submissionMapper,
                testCaseMapper,
                properties,
                sandbox,
                resultPersistenceService,
                mock(AlgorithmJudgeMetrics.class));
    }

    @Test
    void mapsAcceptedSandboxResultToInternalResponse() {
        DockerJavaSandbox.SandboxResult result = new DockerJavaSandbox.SandboxResult(
                null, "ACCEPTED", null, 1, 1, 18, 4096,
                List.of(new DockerJavaSandbox.CaseResult("ACCEPTED", "42", "", 18, 4096)));
        when(sandbox.execute(anyString(), anyList(), eq(1000), eq(128), eq(false))).thenReturn(result);

        AlgorithmJudgeWorkerService.RunResponse response = service.run("class Main {}", "", 1000, 128);

        assertEquals("ACCEPTED", response.status());
        assertEquals("42", response.output());
        assertNull(response.errorMessage());
        assertEquals(18L, response.executionTimeMs());
        assertEquals(4096L, response.memoryUsageKb());
    }

    @Test
    void returnsCompileMessageForCompileFailure() {
        DockerJavaSandbox.SandboxResult result = new DockerJavaSandbox.SandboxResult(
                null, "COMPILE_ERROR", "javac failed", 0, 1, 0, 0, List.of());
        when(sandbox.execute(anyString(), anyList(), anyInt(), anyInt(), eq(false))).thenReturn(result);

        AlgorithmJudgeWorkerService.RunResponse response = service.run("class Main {}", "", 1000, 128);

        assertEquals("COMPILE_ERROR", response.status());
        assertEquals("javac failed", response.errorMessage());
    }

    @Test
    void rejectsInvalidSourceBeforeStartingSandbox() {
        AlgorithmJudgeWorkerService.RunResponse blank = service.run(" ", "", 1000, 128);
        AlgorithmJudgeWorkerService.RunResponse oversized = service.run("x".repeat(101), "", 1000, 128);

        assertEquals("SYSTEM_ERROR", blank.status());
        assertEquals("代码不能为空", blank.errorMessage());
        assertEquals("SYSTEM_ERROR", oversized.status());
        assertEquals("代码长度超过限制", oversized.errorMessage());
        verify(sandbox, never()).execute(anyString(), anyList(), anyInt(), anyInt(), any(Boolean.class));
    }

    @Test
    void doesNotReexecuteFinishedSubmission() {
        AlgorithmSubmission submission = submission("ACCEPTED");
        when(submissionMapper.selectById(42L)).thenReturn(submission);

        service.judgeSubmission(42L);

        verify(submissionMapper, never()).claimQueued(any(Long.class), anyString(), any());
        verify(sandbox, never()).execute(anyString(), anyList(), anyInt(), anyInt(), any(Boolean.class));
    }

    @Test
    void skipsWhenAnotherWorkerWinsAtomicClaim() {
        when(submissionMapper.selectById(42L)).thenReturn(submission("QUEUED"));
        when(submissionMapper.claimQueued(eq(42L), eq("COMPILING"), any())).thenReturn(0);

        service.judgeSubmission(42L);

        verify(problemMapper, never()).selectById(any(Long.class));
        verify(sandbox, never()).execute(anyString(), anyList(), anyInt(), anyInt(), any(Boolean.class));
    }

    @Test
    void recoversStaleClaimOnlyWhenExplicitlyRequested() {
        AlgorithmSubmission submission = submission("COMPILING");
        when(submissionMapper.selectById(42L)).thenReturn(submission);
        when(submissionMapper.reclaimStale(eq(42L), eq("COMPILING"), any(), any())).thenReturn(1);
        AlgorithmProblem problem = new AlgorithmProblem();
        problem.setId(7L);
        problem.setStatus(1);
        problem.setTimeLimitMs(1000);
        problem.setMemoryLimitMb(128);
        when(problemMapper.selectById(7L)).thenReturn(problem);
        AlgorithmTestCase testCase = new AlgorithmTestCase();
        testCase.setId(8L);
        testCase.setProblemId(7L);
        testCase.setEnabled(1);
        testCase.setCaseType("SAMPLE");
        testCase.setExpectedOutput("42");
        when(testCaseMapper.selectList(any())).thenReturn(List.of(testCase));
        when(sandbox.execute(anyString(), anyList(), eq(1000), eq(128), eq(true)))
                .thenReturn(new DockerJavaSandbox.SandboxResult(
                        null, "ACCEPTED", null, 1, 1, 2, 64,
                        List.of(new DockerJavaSandbox.CaseResult("ACCEPTED", "42", "", 2, 64))));

        service.judgeSubmission(42L, true);

        verify(submissionMapper).reclaimStale(eq(42L), eq("COMPILING"), any(), any());
        verify(sandbox).execute(anyString(), anyList(), eq(1000), eq(128), eq(true));
        verify(resultPersistenceService).finish(any(), eq("ACCEPTED"), eq(1), eq(1), eq(0),
                eq(2L), eq(null), any(), any());
    }

    private static AlgorithmSubmission submission(String status) {
        AlgorithmSubmission submission = new AlgorithmSubmission();
        submission.setId(42L);
        submission.setUserId(1L);
        submission.setProblemId(7L);
        submission.setSubmitType("SUBMIT");
        submission.setStatus(status);
        submission.setSourceCode("class Main {}");
        return submission;
    }
}
