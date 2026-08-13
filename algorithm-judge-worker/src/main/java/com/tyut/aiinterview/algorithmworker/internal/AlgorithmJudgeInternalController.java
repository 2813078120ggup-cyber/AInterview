package com.tyut.aiinterview.algorithmworker.internal;

import com.tyut.aiinterview.algorithmworker.AlgorithmJudgeWorkerService;
import com.tyut.aiinterview.algorithmworker.config.AlgorithmJudgeProperties;
import com.tyut.aiinterview.algorithmworker.config.WorkerSecurityConfig;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Private API called only by backend-core through the Compose network. */
@RestController
@RequestMapping("/internal/algorithm-judge")
public class AlgorithmJudgeInternalController {
    private final AlgorithmJudgeWorkerService workerService;
    private final AlgorithmJudgeProperties properties;

    public AlgorithmJudgeInternalController(AlgorithmJudgeWorkerService workerService,
                                            AlgorithmJudgeProperties properties) {
        this.workerService = workerService;
        this.properties = properties;
    }

    @PostMapping("/run")
    public ResponseEntity<AlgorithmJudgeWorkerService.RunResponse> run(
            @RequestHeader(value = "X-Internal-Token", required = false) String token,
            @RequestBody RunRequest request) {
        if (!WorkerSecurityConfig.tokenMatches(properties.resolveInternalToken(), token,
                properties.isRequireInternalToken())) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        if (request == null) {
            return ResponseEntity.badRequest().build();
        }
        return ResponseEntity.ok(workerService.run(request.sourceCode(), request.input(),
                request.timeLimitMs(), request.memoryLimitMb()));
    }

    public record RunRequest(String sourceCode, String input, int timeLimitMs, int memoryLimitMb) {}
}
