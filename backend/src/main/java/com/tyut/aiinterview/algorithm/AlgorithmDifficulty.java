package com.tyut.aiinterview.algorithm;

public enum AlgorithmDifficulty {
    EASY("简单"),
    MEDIUM("中等"),
    HARD("困难");

    private final String label;

    AlgorithmDifficulty(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}
