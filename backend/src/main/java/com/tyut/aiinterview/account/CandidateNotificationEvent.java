package com.tyut.aiinterview.account;

import java.util.Locale;
import org.springframework.util.StringUtils;

public enum CandidateNotificationEvent {
    APPLICATION_STATUS_CHANGED("申请进度", "申请阶段、录用或未通过等状态发生变化。", "求职进展", true, false, false, false),
    INTERVIEW_CREATED("面试安排", "收到新的 AI 面试或线下面试安排。", "面试安排", true, true, false, false),
    INTERVIEW_RESCHEDULED("面试改期", "面试时间或安排发生变化。", "面试安排", true, true, false, false),
    INTERVIEW_CANCELLED("面试取消", "已经安排的面试被取消。", "面试安排", true, true, false, false),
    INTERVIEW_REMINDER("面试提醒", "面试开始前的时间提醒。", "面试安排", true, true, true, false),
    REPORT_PUBLISHED("报告发布", "新的面试评测报告可以查看。", "评测报告", true, true, false, false),
    ACCOUNT_SECURITY("账户安全", "密码、联系方式和登录安全发生重要变化。", "账户安全", true, true, false, true),
    PLATFORM_ANNOUNCEMENT("平台公告", "产品更新、维护与平台运营公告。", "平台消息", false, false, false, false);

    private final String label;
    private final String description;
    private final String group;
    private final boolean siteForced;
    private final boolean defaultEmail;
    private final boolean defaultSms;
    private final boolean emailForced;

    CandidateNotificationEvent(String label, String description, String group, boolean siteForced,
                               boolean defaultEmail, boolean defaultSms, boolean emailForced) {
        this.label = label;
        this.description = description;
        this.group = group;
        this.siteForced = siteForced;
        this.defaultEmail = defaultEmail;
        this.defaultSms = defaultSms;
        this.emailForced = emailForced;
    }

    public String label() { return label; }
    public String description() { return description; }
    public String group() { return group; }
    public boolean siteForced() { return siteForced; }
    public boolean defaultSite() { return true; }
    public boolean defaultEmail() { return defaultEmail; }
    public boolean defaultSms() { return defaultSms; }
    public boolean emailForced() { return emailForced; }

    public static CandidateNotificationEvent parse(String value) {
        if (!StringUtils.hasText(value)) return null;
        try {
            return valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }
}
