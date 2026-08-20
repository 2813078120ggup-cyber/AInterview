package com.tyut.aiinterview.ai;

public record AiGenerationContext(Long taskId, Long interviewId, Long freeInterviewSessionId,
                                  String generationType, Long createdBy, Long companyId,
                                  boolean recruitmentGoverned, boolean governanceEvaluation) {
    public AiGenerationContext(Long taskId, Long interviewId, Long freeInterviewSessionId,
                               String generationType, Long createdBy) {
        this(taskId, interviewId, freeInterviewSessionId, generationType, createdBy, null, false, false);
    }

    public static AiGenerationContext standalone(String generationType) {
        return new AiGenerationContext(null, null, null, generationType, null, null, false, false);
    }

    public static AiGenerationContext recruitment(Long taskId, Long interviewId, String generationType,
                                                  Long createdBy, Long companyId) {
        return new AiGenerationContext(taskId, interviewId, null, generationType, createdBy, companyId, true, false);
    }

    public static AiGenerationContext evaluation(String generationType, Long createdBy) {
        return new AiGenerationContext(null, null, null, generationType, createdBy, null, true, true);
    }
}
