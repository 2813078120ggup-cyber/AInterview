package com.tyut.aiinterview.security;

import java.util.Collection;
import java.util.List;
import lombok.Getter;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

@Getter
public class LoginUser implements UserDetails {
    private final Long id;
    private final String username;
    private final String password;
    private final boolean enabled;
    private final List<String> roles;

    public LoginUser(Long id, String username, String password, boolean enabled, List<String> roles) {
        this.id = id;
        this.username = username;
        this.password = password;
        this.enabled = enabled;
        this.roles = roles;
    }

    public boolean hasRole(String role) { return roles.contains(role); }
    @Override public Collection<? extends GrantedAuthority> getAuthorities() {
        return roles.stream().map(role -> new SimpleGrantedAuthority("ROLE_" + role)).toList();
    }
    @Override public String getPassword() { return password; }
    @Override public String getUsername() { return username; }
    @Override public boolean isEnabled() { return enabled; }
}
