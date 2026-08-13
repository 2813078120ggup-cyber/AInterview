package com.tyut.aiinterview.interview;

public record InterviewLifecycleEvent(Long interviewId, Phase phase, Long operatorId) {
    public enum Phase {
        STARTED,
        ENDED
    }
}
