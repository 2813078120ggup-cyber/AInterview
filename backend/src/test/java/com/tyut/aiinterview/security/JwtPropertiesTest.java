package com.tyut.aiinterview.security;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class JwtPropertiesTest {
    @Test
    void rejectsDefaultPlaceholder() {
        JwtProperties properties = new JwtProperties();
        properties.setJwtSecret(JwtProperties.DEFAULT_JWT_SECRET);
        assertThrows(IllegalStateException.class, properties::validateStrong);
    }

    @Test
    void rejectsExamplePlaceholder() {
        JwtProperties properties = new JwtProperties();
        properties.setJwtSecret(JwtProperties.EXAMPLE_JWT_SECRET);
        assertThrows(IllegalStateException.class, properties::validateStrong);
    }

    @Test
    void rejectsMissingAndShortSecrets() {
        assertThrows(IllegalStateException.class, new JwtProperties()::validateStrong);
        JwtProperties shortSecret = new JwtProperties();
        shortSecret.setJwtSecret("too-short");
        assertThrows(IllegalStateException.class, shortSecret::validateStrong);
    }

    @Test
    void acceptsStrongSecret() {
        JwtProperties properties = new JwtProperties();
        properties.setJwtSecret("a-strong-random-secret-with-at-least-32-characters");
        assertDoesNotThrow(properties::validateStrong);
    }

    @Test
    void acceptsWeakSecretWhenExplicitlyOptedOut() {
        JwtProperties properties = new JwtProperties();
        properties.setJwtSecret(JwtProperties.DEFAULT_JWT_SECRET);
        properties.setRequireStrongJwtSecret(false);
        assertDoesNotThrow(properties::validateStrong);
    }
}
