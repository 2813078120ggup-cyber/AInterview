package com.tyut.aiinterview.security;

import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.tyut.aiinterview.observability.OperationAuditService;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.List;
import javax.crypto.SecretKey;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;

class JwtAuthenticationFilterTest {
    private static final String SECRET = "a-strong-random-secret-with-at-least-32-characters";

    private final JwtProperties properties = new JwtProperties();
    private final JwtTokenService tokenService;
    private final UserDetailsService userDetailsService = mock(UserDetailsService.class);
    private final OperationAuditService auditService = mock(OperationAuditService.class);
    private final FilterChain chain = mock(FilterChain.class);

    JwtAuthenticationFilterTest() {
        properties.setJwtSecret(SECRET);
        tokenService = new JwtTokenService(properties);
    }

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void acceptsNormalJwtWithMatchingSecurityVersion() throws Exception {
        LoginUser user = user(true, 0);
        when(userDetailsService.loadUserByUsername("7")).thenReturn(user);

        doFilter(tokenService.createToken(7L, "candidate", 0, "session-a"));

        assertTrue(SecurityContextHolder.getContext().getAuthentication().isAuthenticated());
        LoginUser authenticated = (LoginUser) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        assertEquals("session-a", authenticated.getSessionId());
        verify(chain).doFilter(any(), any());
        verify(auditService, never()).denied(any(), any(), any(), any(), any(), any());
    }

    @Test
    void rejectsExpiredJwtWithoutThrowing() throws Exception {
        doFilter(signedToken(builder().expiration(new Date(System.currentTimeMillis() - 1000L))
                .claim("securityVersion", 0).claim("sessionId", "session-a")));

        assertNull(SecurityContextHolder.getContext().getAuthentication());
        verify(auditService).denied(eq("AUTHENTICATION"), eq("AUTH_JWT_REJECTED"), eq("USER"), eq(null), eq(null), any());
    }

    @Test
    void rejectsLegacyJwtWithoutSecurityVersion() throws Exception {
        doFilter(signedToken(builder().expiration(new Date(System.currentTimeMillis() + 60_000L))
                .claim("sessionId", "session-a")));

        assertNull(SecurityContextHolder.getContext().getAuthentication());
        verify(userDetailsService, never()).loadUserByUsername(any());
        verify(auditService).denied(eq("AUTHENTICATION"), eq("AUTH_JWT_LEGACY_REJECTED"), eq("USER"), eq(7L), eq(null), any());
    }

    @Test
    void rejectsSecurityVersionMismatch() throws Exception {
        when(userDetailsService.loadUserByUsername("7")).thenReturn(user(true, 2));

        doFilter(tokenService.createToken(7L, "candidate", 1, "session-a"));

        assertNull(SecurityContextHolder.getContext().getAuthentication());
        verify(auditService).denied(eq("AUTHENTICATION"), eq("AUTH_SECURITY_VERSION_REJECTED"), eq("USER"), eq(7L), eq(null), any());
    }

    @Test
    void rejectsDisabledUserAndDisabledCompanyMember() throws Exception {
        when(userDetailsService.loadUserByUsername("7")).thenReturn(user(false, 0));

        doFilter(tokenService.createToken(7L, "company-member", 0, "session-a"));

        assertNull(SecurityContextHolder.getContext().getAuthentication());
        verify(auditService).denied(eq("AUTHENTICATION"), eq("AUTH_DISABLED_ACCOUNT_REJECTED"), eq("USER"), eq(7L), eq(null), any());
    }

    @Test
    void userLookupFailureDoesNotBecomeServerError() throws Exception {
        when(userDetailsService.loadUserByUsername("7")).thenThrow(new UsernameNotFoundException("missing"));

        doFilter(tokenService.createToken(7L, "missing", 0, "session-a"));

        assertNull(SecurityContextHolder.getContext().getAuthentication());
        verify(chain).doFilter(any(), any());
    }

    private void doFilter(String token) throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer " + token);
        new JwtAuthenticationFilter(tokenService, userDetailsService, auditService)
                .doFilter(request, new MockHttpServletResponse(), chain);
    }

    private LoginUser user(boolean enabled, int securityVersion) {
        return new LoginUser(7L, "candidate", "encoded", enabled, List.of("CANDIDATE"), null,
                List.of(), securityVersion);
    }

    private io.jsonwebtoken.JwtBuilder builder() {
        return Jwts.builder().subject("7").issuedAt(new Date());
    }

    private String signedToken(io.jsonwebtoken.JwtBuilder builder) {
        return builder.signWith(Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8))).compact();
    }
}
