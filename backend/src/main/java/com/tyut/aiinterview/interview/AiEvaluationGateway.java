package com.tyut.aiinterview.interview;

import com.tyut.aiinterview.domain.Interview;
import com.tyut.aiinterview.domain.AiTask;

public interface AiEvaluationGateway {
    AiTask enqueue(Interview interview);
}
