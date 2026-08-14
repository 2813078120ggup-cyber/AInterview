package com.tyut.aiinterview.security;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import lombok.Getter;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

@Getter
public class LoginUser implements UserDetails {
    private static final String CANDIDATE_ROLE = "CANDIDATE";
    private static final Set<String> COMPANY_ADMIN_PERMISSIONS = Set.of(
            "company:read", "company:write", "company:team:manage",
            "recruitment:position:read", "recruitment:position:write", "recruitment:position:publish",
            "application:read", "application:review", "application:export",
            "interview:read", "interview:create", "interview:review",
            "report:read", "analytics:read");

    private final Long id;
    private final String username;
    private final String password;
    private final boolean enabled;
    private final List<String> roles;
    private final Long companyId;
    private final List<String> permissions;
    private final Integer securityVersion;
    private final String sessionId;

    public LoginUser(Long id, String username, String password, boolean enabled, List<String> roles, Long companyId) {
        this(id, username, password, enabled, roles, companyId, List.of(), 0);
    }

    public LoginUser(Long id, String username, String password, boolean enabled, List<String> roles, Long companyId,
                     List<String> permissions) {
        this(id, username, password, enabled, roles, companyId, permissions, 0);
    }

    public LoginUser(Long id, String username, String password, boolean enabled, List<String> roles, Long companyId,
                     List<String> permissions, Integer securityVersion) {
        this(id, username, password, enabled, roles, companyId, permissions, securityVersion, null);
    }

    private LoginUser(Long id, String username, String password, boolean enabled, List<String> roles, Long companyId,
                      List<String> permissions, Integer securityVersion, String sessionId) {
        List<String> effectiveRoles = effectiveRoles(roles);
        this.id = id;
        this.username = username;
        this.password = password;
        this.enabled = enabled;
        this.roles = effectiveRoles;
        this.companyId = effectiveRoles.contains(CANDIDATE_ROLE) ? null : companyId;
        this.permissions = effectiveRoles.contains(CANDIDATE_ROLE) || permissions == null
                ? List.of()
                : List.copyOf(permissions);
        this.securityVersion = securityVersion == null ? 0 : securityVersion;
        this.sessionId = sessionId;
    }

    public static List<String> effectiveRoles(List<String> assignedRoles) {
        if (assignedRoles == null || assignedRoles.isEmpty()) return List.of();
        if (assignedRoles.contains(CANDIDATE_ROLE)) return List.of(CANDIDATE_ROLE);
        return List.copyOf(assignedRoles);
    }

    public LoginUser withSessionId(String authenticatedSessionId) {
        return new LoginUser(id, username, password, enabled, roles, companyId, permissions,
                securityVersion, authenticatedSessionId);
    }

    public boolean hasRole(String role) { return roles.contains(role); }
    public boolean hasAnyRole(String... expectedRoles) {
        if (expectedRoles == null) return false;
        for (String role : expectedRoles) if (hasRole(role)) return true;
        return false;
    }
    public boolean isCompanyUser() {
        return hasAnyRole("COMPANY_ADMIN", "COMPANY_RECRUITER", "COMPANY_INTERVIEWER");
    }
    public boolean hasPermission(String permission) {
        if (hasRole(CANDIDATE_ROLE)) return false;
        return hasRole("COMPANY_ADMIN") && COMPANY_ADMIN_PERMISSIONS.contains(permission)
                || permissions.contains(permission);
    }
    @Override public Collection<? extends GrantedAuthority> getAuthorities() {
        Set<String> authorityCodes = new LinkedHashSet<>();
        roles.forEach(role -> authorityCodes.add("ROLE_" + role));
        if (!hasRole(CANDIDATE_ROLE)) {
            permissions.forEach(permission -> authorityCodes.add("PERM_" + permission));
            if (hasRole("COMPANY_ADMIN")) {
                COMPANY_ADMIN_PERMISSIONS.forEach(permission -> authorityCodes.add("PERM_" + permission));
            }
        }
        return authorityCodes.stream().map(SimpleGrantedAuthority::new).toList();
    }
    @Override public String getPassword() { return password; }
    @Override public String getUsername() { return username; }
    @Override public boolean isEnabled() { return enabled; }
}
