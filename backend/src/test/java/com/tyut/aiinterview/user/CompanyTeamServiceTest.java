package com.tyut.aiinterview.user;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.tyut.aiinterview.common.BusinessException;
import com.tyut.aiinterview.domain.Role;
import com.tyut.aiinterview.domain.UserAccount;
import com.tyut.aiinterview.domain.UserRole;
import com.tyut.aiinterview.mapper.RoleMapper;
import com.tyut.aiinterview.mapper.UserMapper;
import com.tyut.aiinterview.mapper.UserRoleMapper;
import com.tyut.aiinterview.recruitment.CompanyAccessService;
import com.tyut.aiinterview.security.CurrentUser;
import com.tyut.aiinterview.security.LoginUser;
import com.tyut.aiinterview.security.RefreshTokenService;
import java.util.List;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.apache.ibatis.session.Configuration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;

class CompanyTeamServiceTest {
    private final UserMapper userMapper = mock(UserMapper.class);
    private final UserRoleMapper userRoleMapper = mock(UserRoleMapper.class);
    private final RoleMapper roleMapper = mock(RoleMapper.class);
    private final PasswordEncoder passwordEncoder = mock(PasswordEncoder.class);
    private final CurrentUser currentUser = mock(CurrentUser.class);
    private final CompanyAccessService companyAccess = mock(CompanyAccessService.class);
    private final RefreshTokenService refreshTokenService = mock(RefreshTokenService.class);
    private final CompanyTeamService service = new CompanyTeamService(userMapper, userRoleMapper, roleMapper,
            passwordEncoder, currentUser, companyAccess, null, refreshTokenService);

    @BeforeEach
    void initializeMyBatisPlusLambdaMetadata() {
        Configuration configuration = new Configuration();
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(configuration, "test");
        for (Class<?> entityType : List.of(Role.class, UserAccount.class, UserRole.class)) {
            if (TableInfoHelper.getTableInfo(entityType) == null) TableInfoHelper.initTableInfo(assistant, entityType);
        }
    }

    @Test
    void memberListIsReadableWithCompanyReadButMutationsRemainSeparate() {
        when(companyAccess.requirePermission("company:read")).thenReturn(100L);
        UserAccount member = member(20L, 100L, 1);
        member.setUsername("recruiter");
        member.setRealName("招聘专员");
        when(userMapper.selectList(any())).thenReturn(List.of(member));
        when(userRoleMapper.selectList(any())).thenReturn(List.of());

        List<CompanyTeamDtos.TeamMemberView> result = service.list();

        assertEquals(1, result.size());
        assertEquals("招聘专员", result.get(0).realName());
        verify(companyAccess).requirePermission("company:read");
    }

    @Test
    void permissionCombinationsKeepRecruiterAndInterviewerBoundaries() {
        LoginUser admin = new LoginUser(1L, "admin", "", true, List.of("COMPANY_ADMIN"), 100L);
        LoginUser recruiter = new LoginUser(2L, "recruiter", "", true,
                List.of("COMPANY_RECRUITER"), 100L,
                List.of("company:read", "recruitment:position:write", "application:review", "interview:create"));
        LoginUser interviewer = new LoginUser(3L, "interviewer", "", true,
                List.of("COMPANY_INTERVIEWER"), 100L,
                List.of("company:read", "application:read", "interview:read", "interview:review"));

        assertTrue(admin.hasPermission("company:team:manage"));
        assertTrue(recruiter.hasPermission("interview:create"));
        assertFalse(recruiter.hasPermission("company:team:manage"));
        assertTrue(interviewer.hasPermission("interview:review"));
        assertFalse(interviewer.hasPermission("recruitment:position:write"));
        assertFalse(interviewer.hasPermission("report:read"));
    }

    @Test
    void cannotManageMemberFromAnotherCompany() {
        when(companyAccess.requirePermission("company:team:manage")).thenReturn(100L);
        UserAccount otherCompanyMember = member(20L, 200L, 1);
        when(userMapper.selectById(20L)).thenReturn(otherCompanyMember);

        BusinessException exception = assertThrows(BusinessException.class,
                () -> service.updateStatus(20L, new CompanyTeamDtos.TeamStatusRequest(0)));

        assertTrue(exception.getStatus() == HttpStatus.NOT_FOUND);
        verify(userMapper, never()).updateById(any(UserAccount.class));
    }

    @Test
    void cannotDisableTheLastActiveCompanyAdmin() {
        when(companyAccess.requirePermission("company:team:manage")).thenReturn(100L);
        when(currentUser.id()).thenReturn(88L);
        UserAccount admin = member(20L, 100L, 1);
        when(userMapper.selectById(20L)).thenReturn(admin);
        when(userMapper.selectList(any())).thenReturn(List.of(admin));
        when(roleMapper.selectOne(any())).thenReturn(role(10L, "COMPANY_ADMIN"));
        when(userRoleMapper.selectList(any())).thenReturn(List.of(userRole(20L, 10L)));

        BusinessException exception = assertThrows(BusinessException.class,
                () -> service.updateStatus(20L, new CompanyTeamDtos.TeamStatusRequest(0)));

        assertTrue(exception.getStatus() == HttpStatus.BAD_REQUEST);
        verify(userMapper, never()).updateById(any(UserAccount.class));
    }

    @Test
    void cannotRemoveTheLastActiveCompanyAdminRole() {
        when(companyAccess.requirePermission("company:team:manage")).thenReturn(100L);
        UserAccount admin = member(20L, 100L, 1);
        when(userMapper.selectById(20L)).thenReturn(admin);
        when(userMapper.selectList(any())).thenReturn(List.of(admin));
        when(roleMapper.selectOne(any())).thenReturn(role(10L, "COMPANY_ADMIN"));
        when(userRoleMapper.selectList(any())).thenReturn(List.of(userRole(20L, 10L)));
        when(roleMapper.selectList(any())).thenReturn(List.of(role(11L, "COMPANY_RECRUITER")));

        BusinessException exception = assertThrows(BusinessException.class,
                () -> service.updateRoles(20L, new CompanyTeamDtos.TeamRoleRequest(List.of("COMPANY_RECRUITER"))));

        assertTrue(exception.getStatus() == HttpStatus.BAD_REQUEST);
        verify(userRoleMapper, never()).delete(any());
    }

    @Test
    void changedTeamRolesIncrementSecurityVersion() {
        when(companyAccess.requirePermission("company:team:manage")).thenReturn(100L);
        when(currentUser.id()).thenReturn(88L);
        UserAccount recruiter = member(21L, 100L, 1);
        recruiter.setSecurityVersion(2);
        when(userMapper.selectById(21L)).thenReturn(recruiter);
        when(userMapper.selectList(any())).thenReturn(List.of());
        when(userRoleMapper.selectList(any())).thenReturn(List.of(userRole(21L, 11L)));
        when(roleMapper.selectList(any())).thenReturn(List.of(role(12L, "COMPANY_INTERVIEWER")));
        when(roleMapper.selectBatchIds(any())).thenReturn(List.of(role(11L, "COMPANY_RECRUITER")));

        service.updateRoles(21L,
                new CompanyTeamDtos.TeamRoleRequest(List.of("COMPANY_INTERVIEWER")));

        assertEquals(3, recruiter.getSecurityVersion());
        verify(userMapper).updateById(recruiter);
        verify(refreshTokenService).revokeAllSessions(21L, "ROLES_CHANGED");
    }

    private UserAccount member(Long id, Long companyId, int status) {
        UserAccount user = new UserAccount();
        user.setId(id);
        user.setCompanyId(companyId);
        user.setStatus(status);
        return user;
    }

    private Role role(Long id, String code) {
        Role role = new Role();
        role.setId(id);
        role.setRoleCode(code);
        role.setStatus(1);
        return role;
    }

    private UserRole userRole(Long userId, Long roleId) {
        UserRole relation = new UserRole();
        relation.setUserId(userId);
        relation.setRoleId(roleId);
        return relation;
    }
}
