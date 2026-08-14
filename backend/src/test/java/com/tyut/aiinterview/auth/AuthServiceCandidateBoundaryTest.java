package com.tyut.aiinterview.auth;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.tyut.aiinterview.domain.Role;
import com.tyut.aiinterview.domain.UserAccount;
import com.tyut.aiinterview.domain.UserRole;
import com.tyut.aiinterview.mapper.CompanyMapper;
import com.tyut.aiinterview.mapper.PermissionMapper;
import com.tyut.aiinterview.mapper.RoleMapper;
import com.tyut.aiinterview.mapper.RolePermissionMapper;
import com.tyut.aiinterview.mapper.UserMapper;
import com.tyut.aiinterview.mapper.UserRoleMapper;
import com.tyut.aiinterview.security.JwtTokenService;
import com.tyut.aiinterview.security.RefreshTokenService;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.password.PasswordEncoder;

class AuthServiceCandidateBoundaryTest {
    @Test
    void profileFailsClosedWhenLegacyAccountHasCandidateAndAdminRoles() {
        UserMapper userMapper = mock(UserMapper.class);
        RoleMapper roleMapper = mock(RoleMapper.class);
        UserRoleMapper userRoleMapper = mock(UserRoleMapper.class);
        UserAccount user = new UserAccount();
        user.setId(10L);
        user.setUsername("legacy_mixed");
        user.setRealName("历史混合账号");
        user.setCompanyId(100L);
        UserRole candidateAssignment = roleAssignment(10L, 2L);
        UserRole adminAssignment = roleAssignment(10L, 1L);
        Role candidate = role(2L, "CANDIDATE");
        Role admin = role(1L, "ADMIN");

        when(userMapper.selectById(10L)).thenReturn(user);
        when(userRoleMapper.selectList(any())).thenReturn(List.of(candidateAssignment, adminAssignment));
        when(roleMapper.selectBatchIds(List.of(2L, 1L))).thenReturn(List.of(candidate, admin));

        AuthService service = new AuthService(userMapper, mock(CompanyMapper.class), roleMapper, userRoleMapper,
                mock(PermissionMapper.class), mock(RolePermissionMapper.class), mock(PasswordEncoder.class),
                mock(JwtTokenService.class), mock(RefreshTokenService.class), mock(VerificationCodeService.class));

        AuthDtos.UserProfile profile = service.profileOf(10L);

        assertEquals(List.of("CANDIDATE"), profile.roles());
        assertNull(profile.companyId());
    }

    private static UserRole roleAssignment(Long userId, Long roleId) {
        UserRole assignment = new UserRole();
        assignment.setUserId(userId);
        assignment.setRoleId(roleId);
        return assignment;
    }

    private static Role role(Long id, String code) {
        Role role = new Role();
        role.setId(id);
        role.setRoleCode(code);
        return role;
    }
}
