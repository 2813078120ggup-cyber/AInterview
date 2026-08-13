package com.tyut.aiinterview.algorithmworker.config;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class WorkerSecurityConfigTest {
    @TempDir
    Path tempDir;

    @Test
    void keepsEmptyTokenCompatibleForPrivateLocalMode() {
        assertTrue(WorkerSecurityConfig.tokenMatches("", null));
        assertTrue(WorkerSecurityConfig.tokenMatches(null, ""));
    }

    @Test
    void requiresConfiguredTokenWhenStrictModeIsEnabled() {
        assertFalse(WorkerSecurityConfig.tokenMatches("", null, true));
        assertFalse(WorkerSecurityConfig.tokenMatches("secret", null, true));
        assertFalse(WorkerSecurityConfig.tokenMatches("secret", "wrong", true));
        assertTrue(WorkerSecurityConfig.tokenMatches("secret", "secret", true));
    }

    @Test
    void resolvesTokenFromSecretFile() throws IOException {
        Path tokenFile = tempDir.resolve("algorithm-judge-internal-token");
        Files.writeString(tokenFile, "worker-file-token\n");
        AlgorithmJudgeProperties properties = new AlgorithmJudgeProperties();
        properties.setInternalTokenFile(tokenFile.toString());

        assertEquals("worker-file-token", properties.resolveInternalToken());
    }

    @Test
    void rejectsStrictModeWithoutEnvironmentOrSecretFile() {
        AlgorithmJudgeProperties properties = new AlgorithmJudgeProperties();
        properties.setRequireInternalToken(true);
        properties.setInternalTokenFile(tempDir.resolve("missing-token").toString());

        assertThrows(IllegalStateException.class,
                () -> new WorkerSecurityConfig().validateConfiguration(properties));
    }
}
