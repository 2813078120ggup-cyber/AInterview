package com.tyut.aiinterview.report;

import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class ReportServiceTest {
    @Test
    void warnsWhenInterviewContainsTooFewQuestions() {
        String warning = ReportService.reliabilityWarning(1);

        assertTrue(warning.contains("仅包含 1 道题"));
        assertTrue(warning.contains("参考性有限"));
        assertNull(ReportService.reliabilityWarning(5));
    }
}
