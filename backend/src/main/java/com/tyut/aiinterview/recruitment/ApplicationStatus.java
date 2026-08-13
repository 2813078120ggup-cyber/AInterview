package com.tyut.aiinterview.recruitment;

import com.tyut.aiinterview.common.BusinessException;
import java.util.Locale;

public enum ApplicationStatus {
    SUBMITTED("已投递", false),
    AI_INTERVIEW_PENDING("待 AI 面试", false),
    AI_INTERVIEWING("AI 面试中", false),
    UNDER_REVIEW("企业评估中", false),
    OFFLINE_INTERVIEW("线下面试", false),
    REJECTED("未通过", true),
    HIRED("已录用", true);

    private final String label;
    private final boolean terminal;

    ApplicationStatus(String label, boolean terminal) {
        this.label = label;
        this.terminal = terminal;
    }

    public String label() {
        return label;
    }

    public boolean terminal() {
        return terminal;
    }

    public static ApplicationStatus parse(String value) {
        try {
            return value == null ? null : valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            throw BusinessException.badRequest("申请状态不合法");
        }
    }
}
