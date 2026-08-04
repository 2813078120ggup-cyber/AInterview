package com.tyut.aiinterview.interview;

import com.tyut.aiinterview.ai.AiTaskService;
import com.tyut.aiinterview.domain.AiTask;
import com.tyut.aiinterview.domain.Interview;
import org.springframework.stereotype.Component;

@Component
public class AiTaskEvaluationGateway implements AiEvaluationGateway {
    private final AiTaskService aiTaskService;

    public AiTaskEvaluationGateway(AiTaskService aiTaskService) {
        this.aiTaskService = aiTaskService;
    }

    @Override
    public AiTask enqueue(Interview interview) {
        return aiTaskService.enqueueAutomaticEvaluation(interview);
    }
}
