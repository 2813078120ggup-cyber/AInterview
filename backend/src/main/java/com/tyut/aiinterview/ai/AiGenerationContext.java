package com.tyut.aiinterview.ai;

public record AiGenerationContext(Long taskId, Long interviewId, Long freeInterviewSessionId,
                                  String generationType, Long createdBy) {
    public static AiGenerationContext standalone(String generationType) {
        return new AiGenerationContext(null, null, null, generationType, null);
    }
}
