package com.tyut.aiinterview.security;

import com.tyut.aiinterview.common.BusinessException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

@Component
public class CurrentUser {
    public LoginUser require() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !(authentication.getPrincipal() instanceof LoginUser loginUser)) {
            throw BusinessException.forbidden("登录已失效");
        }
        return loginUser;
    }

    public Long id() { return require().getId(); }
    public boolean hasRole(String role) { return require().hasRole(role); }
}
