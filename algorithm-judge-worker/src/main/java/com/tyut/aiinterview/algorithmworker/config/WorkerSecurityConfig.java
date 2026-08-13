package com.tyut.aiinterview.algorithmworker.config;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;

/**
 * Marker configuration for the private Worker endpoint.
 * Authentication is checked in the controller so the endpoint stays outside
 * the candidate JWT security domain and can be called by the backend only.
 */
@Configuration
public class WorkerSecurityConfig {
    @Autowired
    void validateConfiguration(AlgorithmJudgeProperties properties) {
        if (properties.isRequireInternalToken() && properties.resolveInternalToken().isBlank()) {
            throw new IllegalStateException("ALGORITHM_JUDGE_INTERNAL_TOKEN or its secret file must be configured");
        }
    }

    public static boolean tokenMatches(String configured, String provided) {
        return tokenMatches(configured, provided, false);
    }

    public static boolean tokenMatches(String configured, String provided, boolean required) {
        if (configured == null || configured.isBlank()) return !required;
        if (provided == null || provided.isBlank()) return false;
        return MessageDigest.isEqual(configured.getBytes(StandardCharsets.UTF_8),
                provided.getBytes(StandardCharsets.UTF_8));
    }
}
