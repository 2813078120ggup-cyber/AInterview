package com.tyut.aiinterview.account;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.tyut.aiinterview.common.BusinessException;
import com.tyut.aiinterview.domain.RefreshToken;
import com.tyut.aiinterview.domain.UserAccount;
import com.tyut.aiinterview.mapper.RefreshTokenMapper;
import com.tyut.aiinterview.observability.OperationAuditService;
import com.tyut.aiinterview.security.CurrentUser;
import com.tyut.aiinterview.security.RefreshTokenService;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
public class AccountSessionService {
    private static final String SESSION_REVOKED = "SESSION_REVOKED";
    private static final String OTHER_SESSIONS_REVOKED = "OTHER_SESSIONS_REVOKED";

    private final RefreshTokenMapper refreshTokenMapper;
    private final RefreshTokenService refreshTokenService;
    private final AccountService accountService;
    private final CurrentUser currentUser;
    private final OperationAuditService auditService;

    public AccountSessionService(RefreshTokenMapper refreshTokenMapper, RefreshTokenService refreshTokenService,
                                 AccountService accountService, CurrentUser currentUser,
                                 OperationAuditService auditService) {
        this.refreshTokenMapper = refreshTokenMapper;
        this.refreshTokenService = refreshTokenService;
        this.accountService = accountService;
        this.currentUser = currentUser;
        this.auditService = auditService;
    }

    public List<AccountDtos.AccountSession> sessions() {
        UserAccount user = accountService.requireCurrentUser();
        String currentSessionId = requireCurrentSessionId();
        List<RefreshToken> tokens = refreshTokenMapper.selectList(new LambdaQueryWrapper<RefreshToken>()
                .eq(RefreshToken::getUserId, user.getId())
                .orderByAsc(RefreshToken::getCreatedAt, RefreshToken::getId));
        LocalDateTime now = LocalDateTime.now();
        Map<String, SessionAggregate> grouped = new LinkedHashMap<>();
        for (RefreshToken token : tokens) {
            if (!StringUtils.hasText(token.getSessionId())) continue;
            grouped.computeIfAbsent(token.getSessionId(), SessionAggregate::new).accept(token, now);
        }
        return grouped.values().stream()
                .filter(SessionAggregate::active)
                .map(value -> value.toDto(currentSessionId))
                .sorted(Comparator.comparing(AccountDtos.AccountSession::current).reversed()
                        .thenComparing(AccountDtos.AccountSession::lastActiveAt,
                                Comparator.nullsLast(Comparator.reverseOrder())))
                .toList();
    }

    @Transactional
    public void revoke(String sessionId) {
        UserAccount user = accountService.requireCurrentUser();
        String normalized = normalizeSessionId(sessionId);
        if (refreshTokenMapper.countUserSession(user.getId(), normalized) == 0) {
            auditService.denied("ACCOUNT", "SESSION_REVOKE", "SESSION", null, user.getCompanyId(),
                    "撤销登录设备被拒绝：会话不存在或不属于当前用户");
            throw BusinessException.notFound("登录会话不存在");
        }
        refreshTokenMapper.revokeUserSession(user.getId(), normalized, SESSION_REVOKED);
        auditService.success("ACCOUNT", "SESSION_REVOKE", "SESSION", normalized, user.getCompanyId(),
                "撤销本人登录设备会话");
    }

    @Transactional
    public void revokeOthers() {
        UserAccount user = accountService.requireCurrentUser();
        String currentSessionId = requireCurrentSessionId();
        int revoked = refreshTokenService.revokeOtherSessions(user.getId(), currentSessionId,
                OTHER_SESSIONS_REVOKED);
        auditService.success("ACCOUNT", "OTHER_SESSIONS_REVOKE", "USER", user.getId(), user.getCompanyId(),
                revoked > 0 ? "撤销本人其他登录设备会话" : "本人没有可撤销的其他登录设备会话");
    }

    private String requireCurrentSessionId() {
        String sessionId = currentUser.sessionId();
        if (!StringUtils.hasText(sessionId)) throw BusinessException.forbidden("当前登录会话无效");
        return sessionId.trim();
    }

    private String normalizeSessionId(String sessionId) {
        if (!StringUtils.hasText(sessionId) || sessionId.length() > 64) {
            throw BusinessException.notFound("登录会话不存在");
        }
        return sessionId.trim();
    }

    private static final class SessionAggregate {
        private final String sessionId;
        private final List<RefreshToken> activeTokens = new ArrayList<>();
        private LocalDateTime createdAt;
        private LocalDateTime lastActiveAt;

        private SessionAggregate(String sessionId) {
            this.sessionId = sessionId;
        }

        private void accept(RefreshToken token, LocalDateTime now) {
            createdAt = earlier(createdAt, token.getCreatedAt());
            lastActiveAt = later(lastActiveAt, later(token.getCreatedAt(), token.getLastUsedAt()));
            if (token.getRevokedAt() == null && token.getExpiresAt() != null && token.getExpiresAt().isAfter(now)) {
                activeTokens.add(token);
            }
        }

        private boolean active() {
            return !activeTokens.isEmpty();
        }

        private AccountDtos.AccountSession toDto(String currentSessionId) {
            RefreshToken latest = activeTokens.stream().max(Comparator
                    .comparing(RefreshToken::getCreatedAt, Comparator.nullsFirst(Comparator.naturalOrder()))
                    .thenComparing(RefreshToken::getId, Comparator.nullsFirst(Comparator.naturalOrder())))
                    .orElseThrow();
            LocalDateTime expiresAt = activeTokens.stream().map(RefreshToken::getExpiresAt)
                    .max(Comparator.naturalOrder()).orElse(latest.getExpiresAt());
            UserAgentSummary agent = UserAgentSummary.parse(latest.getUserAgent());
            return new AccountDtos.AccountSession(sessionId, sessionId.equals(currentSessionId),
                    agent.deviceType(), agent.browser(), agent.operatingSystem(), maskIp(latest.getClientIp()),
                    createdAt, lastActiveAt, expiresAt);
        }
    }

    private record UserAgentSummary(String deviceType, String browser, String operatingSystem) {
        private static UserAgentSummary parse(String raw) {
            String userAgent = raw == null ? "" : raw;
            String lower = userAgent.toLowerCase(Locale.ROOT);
            String device = lower.contains("ipad") || lower.contains("tablet") ? "TABLET"
                    : lower.contains("mobile") || lower.contains("iphone") || lower.contains("android") ? "MOBILE"
                    : userAgent.isBlank() ? "UNKNOWN" : "DESKTOP";
            String browser = lower.contains("edg/") ? "Edge"
                    : lower.contains("opr/") || lower.contains("opera") ? "Opera"
                    : lower.contains("firefox/") ? "Firefox"
                    : lower.contains("chrome/") || lower.contains("crios/") ? "Chrome"
                    : lower.contains("safari/") ? "Safari" : "未知浏览器";
            String os = lower.contains("windows") ? "Windows"
                    : lower.contains("iphone") || lower.contains("ipad") || lower.contains("cpu os") ? "iOS"
                    : lower.contains("android") ? "Android"
                    : lower.contains("mac os") || lower.contains("macintosh") ? "macOS"
                    : lower.contains("cros") ? "ChromeOS"
                    : lower.contains("linux") ? "Linux" : "未知系统";
            return new UserAgentSummary(device, browser, os);
        }
    }

    private static String maskIp(String rawIp) {
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

    private static LocalDateTime earlier(LocalDateTime left, LocalDateTime right) {
        if (left == null) return right;
        if (right == null) return left;
        return left.isBefore(right) ? left : right;
    }

    private static LocalDateTime later(LocalDateTime left, LocalDateTime right) {
        if (left == null) return right;
        if (right == null) return left;
        return left.isAfter(right) ? left : right;
    }
}
