package com.tyut.aiinterview.recruitment;

public record ResumeParseCompletedEvent(Long resumeId, Integer version, boolean success, String error) {
}
