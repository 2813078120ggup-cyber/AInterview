package com.tyut.aiinterview.security;

import io.jsonwebtoken.JwtException;
import com.tyut.aiinterview.observability.OperationAuditService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {
    private static final Logger log = LoggerFactory.getLogger(JwtAuthenticationFilter.class);
    private final JwtTokenService tokenService;
    private final UserDetailsService userDetailsService;
    private final OperationAuditService auditService;

    public JwtAuthenticationFilter(JwtTokenService tokenService, UserDetailsService userDetailsService,
                                   OperationAuditService auditService) {
        this.tokenService = tokenService;
        this.userDetailsService = userDetailsService;
        this.auditService = auditService;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String header = request.getHeader("Authorization");
        if (header != null && header.startsWith("Bearer ")) {
            try {
                JwtTokenService.ParsedToken parsed = tokenService.parse(header.substring(7));
                if (parsed.securityVersion() == null) {
                    reject(parsed.userId(), "AUTH_JWT_LEGACY_REJECTED", "拒绝缺少 securityVersion 的旧访问令牌");
                    filterChain.doFilter(request, response);
                    return;
                }
                if (parsed.sessionId() == null || parsed.sessionId().isBlank()) {
                    reject(parsed.userId(), "AUTH_JWT_REJECTED", "拒绝缺少会话标识的访问令牌");
                    filterChain.doFilter(request, response);
                    return;
                }
                LoginUser user = (LoginUser) userDetailsService.loadUserByUsername(String.valueOf(parsed.userId()));
                if (!user.isEnabled()) {
                    reject(user.getId(), "AUTH_DISABLED_ACCOUNT_REJECTED", "拒绝已停用账号或企业成员的访问令牌");
                    filterChain.doFilter(request, response);
                    return;
                }
                if (!parsed.securityVersion().equals(user.getSecurityVersion())) {
                    reject(user.getId(), "AUTH_SECURITY_VERSION_REJECTED", "访问令牌安全版本已失效");
                    filterChain.doFilter(request, response);
                    return;
                }
                user = user.withSessionId(parsed.sessionId());
                UsernamePasswordAuthenticationToken authentication =
                        new UsernamePasswordAuthenticationToken(user, null, user.getAuthorities());
                authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                SecurityContextHolder.getContext().setAuthentication(authentication);
            } catch (JwtException | IllegalArgumentException | UsernameNotFoundException ignored) {
                reject(null, "AUTH_JWT_REJECTED", "访问令牌无效、过期或无法加载账号");
            } catch (RuntimeException ignored) {
                reject(null, "AUTH_JWT_REJECTED", "访问令牌认证失败");
            }
        }
        filterChain.doFilter(request, response);
    }

    private void reject(Long userId, String action, String summary) {
        SecurityContextHolder.clearContext();
        try {
            auditService.denied("AUTHENTICATION", action, "USER", userId, null, summary);
        } catch (RuntimeException exception) {
            log.warn("Authentication rejection audit unavailable: action={}", action);
        }
    }
}
