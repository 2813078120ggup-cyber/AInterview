package com.tyut.aiinterview.account;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.tyut.aiinterview.common.PageResult;
import com.tyut.aiinterview.domain.OperationAuditLog;
import com.tyut.aiinterview.domain.UserAccount;
import com.tyut.aiinterview.mapper.OperationAuditLogMapper;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class AccountSecurityEventService {
    private static final long DEFAULT_PAGE_SIZE = 15;
    private static final long MAX_PAGE_SIZE = 50;

    private static final Set<String> AUTH_ACTIONS = Set.of(
            "AUTH_LOGIN_SUCCESS",
            "AUTH_LOGIN_FAILED",
            "AUTH_LOGIN_DISABLED",
            "AUTH_LOGIN_COMPANY_DISABLED",
            "AUTH_LOGIN_CODE_FAILED",
            "AUTH_SESSION_CREATED",
            "AUTH_LOGOUT_SUCCESS",
            "AUTH_LOGOUT_REJECTED",
            "PASSWORD_RESET_SUCCESS",
            "PASSWORD_RESET_REJECTED");
    private static final Set<String> ACCOUNT_ACTIONS = Set.of(
            "PASSWORD_CHANGED",
            "CONTACT_CHANGED",
            "AVATAR_UPDATED",
            "AVATAR_UPLOADED",
            "AVATAR_REPLACED",
            "AVATAR_DELETED",
            "PROFILE_UPDATED",
            "SESSION_REVOKE",
            "OTHER_SESSIONS_REVOKE");

    private final OperationAuditLogMapper auditLogMapper;
    private final AccountService accountService;

    public AccountSecurityEventService(OperationAuditLogMapper auditLogMapper, AccountService accountService) {
        this.auditLogMapper = auditLogMapper;
        this.accountService = accountService;
    }

    public PageResult<AccountSecurityEventDtos.SecurityEvent> page(AccountSecurityEventDtos.Query query) {
        UserAccount user = accountService.requireCurrentUser();
        long pageNo = query == null || query.pageNo() == null ? 1 : Math.max(1, query.pageNo());
        long pageSize = query == null || query.pageSize() == null
                ? DEFAULT_PAGE_SIZE : Math.min(MAX_PAGE_SIZE, Math.max(1, query.pageSize()));

        String userId = String.valueOf(user.getId());
        QueryWrapper<OperationAuditLog> wrapper = new QueryWrapper<OperationAuditLog>()
                .and(events -> events
                        .and(auth -> auth.eq("module", "AUTHENTICATION")
                                .in("action", AUTH_ACTIONS)
                                .eq("resource_type", "USER")
                                .eq("resource_id", userId))
                        .or(account -> account.eq("module", "ACCOUNT")
                                .in("action", ACCOUNT_ACTIONS)
                                .and(scope -> scope.eq("actor_id", user.getId())
                                        .or(target -> target.eq("resource_type", "USER")
                                                .eq("resource_id", userId))))
                        .or(admin -> admin.eq("module", "USER_MANAGEMENT")
                                .eq("action", "USER_STATUS_UPDATED")
                                .eq("resource_type", "USER")
                                .eq("resource_id", userId)))
                .orderByDesc("created_at", "id");

        Page<OperationAuditLog> result = auditLogMapper.selectPage(new Page<>(pageNo, pageSize), wrapper);
        List<AccountSecurityEventDtos.SecurityEvent> records = result.getRecords().stream()
                .map(this::toSecurityEvent)
                .toList();
        return PageResult.of(records, result.getTotal(), pageNo, pageSize);
    }

    private AccountSecurityEventDtos.SecurityEvent toSecurityEvent(OperationAuditLog log) {
        EventPresentation presentation = presentation(log);
        return new AccountSecurityEventDtos.SecurityEvent(
                presentation.eventType(),
                normalizeResult(log.getResult()),
                summary(log, presentation),
                maskIp(log.getIpAddress()),
                summarizeDevice(log.getUserAgent()),
                log.getCreatedAt());
    }

    private EventPresentation presentation(OperationAuditLog log) {
        String action = safe(log.getAction());
        String storedSummary = safe(log.getSummary());
        return switch (action) {
            case "AUTH_LOGIN_SUCCESS" -> storedSummary.contains("验证码")
                    ? new EventPresentation("VERIFICATION_CODE_LOGIN", "验证码登录")
                    : new EventPresentation("PASSWORD_LOGIN", "密码登录");
            case "AUTH_LOGIN_CODE_FAILED" -> new EventPresentation("VERIFICATION_CODE_LOGIN", "验证码登录");
            case "AUTH_LOGIN_FAILED", "AUTH_LOGIN_DISABLED", "AUTH_LOGIN_COMPANY_DISABLED" ->
                    storedSummary.contains("验证码")
                            ? new EventPresentation("VERIFICATION_CODE_LOGIN", "验证码登录")
                            : new EventPresentation("PASSWORD_LOGIN", "密码登录");
            case "AUTH_SESSION_CREATED" -> new EventPresentation("NEW_SESSION", "新会话创建");
            case "AUTH_LOGOUT_SUCCESS", "AUTH_LOGOUT_REJECTED" ->
                    new EventPresentation("SESSION_REVOKED", "当前设备退出");
            case "PASSWORD_CHANGED" -> new EventPresentation("PASSWORD_CHANGED", "登录密码修改");
            case "PASSWORD_RESET_SUCCESS", "PASSWORD_RESET_REJECTED" ->
                    new EventPresentation("PASSWORD_RESET", "登录密码重置");
            case "CONTACT_CHANGED" -> storedSummary.contains("手机号")
                    ? new EventPresentation("PHONE_CHANGED", "手机号修改")
                    : new EventPresentation("EMAIL_CHANGED", "邮箱修改");
            case "AVATAR_UPLOADED", "AVATAR_REPLACED", "AVATAR_DELETED", "AVATAR_UPDATED" ->
                    new EventPresentation("AVATAR_CHANGED", "头像修改");
            case "PROFILE_UPDATED" -> new EventPresentation("PROFILE_CHANGED", "基本资料修改");
            case "SESSION_REVOKE" -> new EventPresentation("SESSION_REVOKED", "指定设备退出");
            case "OTHER_SESSIONS_REVOKE" -> new EventPresentation("OTHER_SESSIONS_REVOKED", "其他设备退出");
            case "USER_STATUS_UPDATED" -> new EventPresentation("ACCOUNT_STATUS_CHANGED", "账号状态变更");
            default -> new EventPresentation("SECURITY_EVENT", "账户安全操作");
        };
    }

    private String summary(OperationAuditLog log, EventPresentation presentation) {
        String result = normalizeResult(log.getResult());
        String action = safe(log.getAction());
        String storedSummary = safe(log.getSummary());
        if ("SUCCESS".equals(result)) {
            return switch (action) {
                case "AUTH_LOGIN_SUCCESS" -> presentation.label() + "成功";
                case "AUTH_SESSION_CREATED" -> "创建了新的登录会话";
                case "AUTH_LOGOUT_SUCCESS" -> "已退出当前登录设备";
                case "PASSWORD_CHANGED" -> "登录密码已修改";
                case "PASSWORD_RESET_SUCCESS" -> "登录密码已重置";
                case "CONTACT_CHANGED" -> presentation.eventType().equals("PHONE_CHANGED")
                        ? "手机号已修改" : "邮箱已修改";
                case "AVATAR_UPLOADED" -> "头像已上传";
                case "AVATAR_REPLACED" -> "头像已替换";
                case "AVATAR_DELETED" -> "头像已删除";
                case "AVATAR_UPDATED" -> "头像修改请求已处理";
                case "PROFILE_UPDATED" -> "基本资料已修改";
                case "SESSION_REVOKE" -> "已退出指定登录设备";
                case "OTHER_SESSIONS_REVOKE" -> "已退出其他登录设备";
                case "USER_STATUS_UPDATED" -> storedSummary.contains("停用")
                        ? "管理员已停用账号"
                        : storedSummary.contains("恢复") ? "管理员已恢复账号" : "管理员已调整账号状态";
                default -> presentation.label() + "已完成";
            };
        }
        return presentation.label() + ("DENIED".equals(result) ? "被拒绝" : "失败");
    }

    private String normalizeResult(String result) {
        String normalized = safe(result).toUpperCase(Locale.ROOT);
        return switch (normalized) {
            case "SUCCESS", "FAILURE", "DENIED" -> normalized;
            default -> "FAILURE";
        };
    }

    private String maskIp(String rawIp) {
        if (!StringUtils.hasText(rawIp)) return null;
        String ip = rawIp.trim();
        if (ip.contains(".")) {
            String[] parts = ip.split("\\.");
            if (parts.length == 4) return parts[0] + "." + parts[1] + "." + parts[2] + ".*";
        }
        if (ip.contains(":")) {
            String[] parts = ip.split(":");
            List<String> visible = new ArrayList<>();
            for (String part : parts) {
                if (!part.isBlank()) visible.add(part);
                if (visible.size() == 3) break;
            }
            if (!visible.isEmpty()) return String.join(":", visible) + ":*";
        }
        return "***";
    }

    private String summarizeDevice(String rawUserAgent) {
        if (!StringUtils.hasText(rawUserAgent)) return null;
        String lower = rawUserAgent.toLowerCase(Locale.ROOT);
        String browser = lower.contains("edg/") ? "Edge"
                : lower.contains("opr/") || lower.contains("opera") ? "Opera"
                : lower.contains("firefox/") ? "Firefox"
                : lower.contains("chrome/") || lower.contains("crios/") ? "Chrome"
                : lower.contains("safari/") ? "Safari" : "未知浏览器";
        String operatingSystem = lower.contains("windows") ? "Windows"
                : lower.contains("iphone") || lower.contains("ipad") || lower.contains("cpu os") ? "iOS"
                : lower.contains("android") ? "Android"
                : lower.contains("mac os") || lower.contains("macintosh") ? "macOS"
                : lower.contains("cros") ? "ChromeOS"
                : lower.contains("linux") ? "Linux" : "未知系统";
        String device = lower.contains("ipad") || lower.contains("tablet") ? "平板设备"
                : lower.contains("mobile") || lower.contains("iphone") || lower.contains("android") ? "移动设备"
                : "桌面设备";
        return browser + " · " + operatingSystem + " · " + device;
    }

    private String safe(String value) {
        return value == null ? "" : value.trim();
    }

    private record EventPresentation(String eventType, String label) {
    }
}
