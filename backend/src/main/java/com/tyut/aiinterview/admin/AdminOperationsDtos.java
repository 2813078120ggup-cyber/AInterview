package com.tyut.aiinterview.admin;

import java.time.LocalDateTime;
import java.util.List;

public final class AdminOperationsDtos {
    private AdminOperationsDtos() {
    }

    public record Summary(LocalDateTime generatedAt, boolean degraded, String metricsPath,
                          String metricsLabel, List<Component> components) {
    }

    public record Component(String code, String label, String state, String stateLabel,
                            String summary, String recommendation) {
    }
}
