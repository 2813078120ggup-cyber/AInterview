package com.tyut.aiinterview.utils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class TokenHashUtilsTest {
    @Test
    void generatesOpaqueTokensAndStableHashes() {
        String first = TokenHashUtils.generateOpaqueToken();
        String second = TokenHashUtils.generateOpaqueToken();

        assertNotEquals(first, second);
        assertTrue(first.length() >= 64);
        assertEquals(TokenHashUtils.sha256(first), TokenHashUtils.sha256(first));
        assertEquals(64, TokenHashUtils.sha256(first).length());
    }
}
