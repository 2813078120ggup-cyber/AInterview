package com.tyut.aiinterview.governance;

public class AiGovernanceException extends IllegalStateException {
    private final String reasonCode;

    public AiGovernanceException(String reasonCode, String message) {
        super(message);
        this.reasonCode = reasonCode;
    }

    public String reasonCode() {
        return reasonCode;
    }
}
