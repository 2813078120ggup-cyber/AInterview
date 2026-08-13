package com.tyut.aiinterview.algorithmworker.internal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.tyut.aiinterview.algorithmworker.AlgorithmJudgeWorkerService;
import com.tyut.aiinterview.algorithmworker.config.AlgorithmJudgeProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

class AlgorithmJudgeInternalControllerTest {
    private AlgorithmJudgeWorkerService workerService;
    private AlgorithmJudgeInternalController controller;

    @BeforeEach
    void setUp() {
        workerService = mock(AlgorithmJudgeWorkerService.class);
        AlgorithmJudgeProperties properties = new AlgorithmJudgeProperties();
        properties.setInternalToken("worker-secret");
        controller = new AlgorithmJudgeInternalController(workerService, properties);
    }

    @Test
    void rejectsWrongInternalToken() {
        ResponseEntity<AlgorithmJudgeWorkerService.RunResponse> response = controller.run(
                "wrong", new AlgorithmJudgeInternalController.RunRequest("class Main {}", "", 1000, 128));

        assertEquals(HttpStatus.UNAUTHORIZED, response.getStatusCode());
    }

    @Test
    void delegatesAuthorizedRunToWorkerService() {
        AlgorithmJudgeWorkerService.RunResponse expected = new AlgorithmJudgeWorkerService.RunResponse(
                "ACCEPTED", "42", null, 12L, 4096L);
        when(workerService.run(anyString(), anyString(), anyInt(), anyInt())).thenReturn(expected);

        ResponseEntity<AlgorithmJudgeWorkerService.RunResponse> response = controller.run(
                "worker-secret", new AlgorithmJudgeInternalController.RunRequest("class Main {}", "", 1000, 128));

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(expected, response.getBody());
        verify(workerService).run("class Main {}", "", 1000, 128);
    }
}
