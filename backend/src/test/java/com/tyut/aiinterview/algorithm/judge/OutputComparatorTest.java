package com.tyut.aiinterview.algorithm.judge;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class OutputComparatorTest {
    @Test
    void matchesIgnoringTrailingWhitespaceAndEmptyLines() {
        assertTrue(OutputComparator.matches("0 1\n", "0 1"));
        assertTrue(OutputComparator.matches("hello   \nworld\n\n", "hello\nworld"));
        assertTrue(OutputComparator.matches("", "\n\n"));
    }

    @Test
    void detectsDifferentOutput() {
        assertFalse(OutputComparator.matches("0 2", "0 1"));
        assertFalse(OutputComparator.matches("hello", "world"));
        assertFalse(OutputComparator.matches("1", "1 1"));
    }

    @Test
    void handlesWindowsLineEndings() {
        assertTrue(OutputComparator.matches("a\r\nb\r\n", "a\nb"));
    }
}
