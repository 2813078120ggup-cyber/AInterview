package com.tyut.aiinterview.user;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.tyut.aiinterview.common.BusinessException;
import com.tyut.aiinterview.domain.Role;
import com.tyut.aiinterview.domain.UserAccount;
import com.tyut.aiinterview.domain.UserRole;
import com.tyut.aiinterview.mapper.RoleMapper;
import com.tyut.aiinterview.mapper.UserMapper;
import com.tyut.aiinterview.mapper.UserRoleMapper;
import com.tyut.aiinterview.observability.OperationAuditService;
import com.tyut.aiinterview.recruitment.CompanyAccessService;
import com.tyut.aiinterview.security.CurrentUser;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
public class CompanyTeamService {
    private static final Set<String> TEAM_ROLE_CODES = Set.of(
            "COMPANY_ADMIN", "COMPANY_RECRUITER", "COMPANY_INTERVIEWER");

    private final UserMapper userMapper;
    private final UserRoleMapper userRoleMapper;
    private final RoleMapper roleMapper;
    private final PasswordEncoder passwordEncoder;
    private final CurrentUser currentUser;
    private final CompanyAccessService companyAccess;
    private final OperationAuditService auditService;

    public CompanyTeamService(UserMapper userMapper, UserRoleMapper userRoleMapper, RoleMapper roleMapper,
                              PasswordEncoder passwordEncoder, CurrentUser currentUser,
                              CompanyAccessService companyAccess) {
        this(userMapper, userRoleMapper, roleMapper, passwordEncoder, currentUser, companyAccess, null);
    }

    @Autowired
    public CompanyTeamService(UserMapper userMapper, UserRoleMapper userRoleMapper, RoleMapper roleMapper,
                              PasswordEncoder passwordEncoder, CurrentUser currentUser,
                              CompanyAccessService companyAccess, OperationAuditService auditService) {
        this.userMapper = userMapper;
        this.userRoleMapper = userRoleMapper;
        this.roleMapper = roleMapper;
        this.passwordEncoder = passwordEncoder;
        this.currentUser = currentUser;
        this.companyAccess = companyAccess;
        this.auditService = auditService;
    }

    public List<CompanyTeamDtos.TeamMemberView> list() {
        Long companyId = companyAccess.requirePermission("company:read");
        return userMapper.selectList(new LambdaQueryWrapper<UserAccount>()
                        .eq(UserAccount::getCompanyId, companyId)
                        .orderByAsc(UserAccount::getRealName).orderByAsc(UserAccount::getId))
                .stream().map(this::toView).toList();
    }

    @Transactional
    public CompanyTeamDtos.TeamMemberView create(CompanyTeamDtos.TeamCreateRequest request) {
        Long companyId = companyAccess.requirePermission("company:team:manage");
        if (userMapper.exists(new LambdaQueryWrapper<UserAccount>()
                .eq(UserAccount::getUsername, request.username().trim()))) {
            throw BusinessException.badRequest("用户名已存在");
        }
        RoleSelection selection = resolveRoles(request.roleCodes());
        UserAccount user = new UserAccount();
        user.setUsername(request.username().trim());
        user.setPasswordHash(passwordEncoder.encode(request.password()));
        user.setRealName(request.realName().trim());
        user.setEmail(normalizeOptional(request.email()));
        user.setPhone(normalizeOptional(request.phone()));
        user.setCompanyId(companyId);
        user.setStatus(1);
        userMapper.insert(user);
        replaceRoles(user.getId(), selection.roles());
        audit("TEAM_MEMBER_CREATED", user.getId(), companyId, "创建企业成员并分配 " + selection.roles().size() + " 个角色");
        return toView(user);
    }

    @Transactional
    public CompanyTeamDtos.TeamMemberView updateRoles(Long userId, CompanyTeamDtos.TeamRoleRequest request) {
        Long companyId = companyAccess.requirePermission("company:team:manage");
        UserAccount user = requireMember(companyId, userId);
        RoleSelection selection = resolveRoles(request.roleCodes());
        ensureAdminRemains(companyId, user, selection.roleIds(), null);
        replaceRoles(user.getId(), selection.roles());
        audit("TEAM_MEMBER_ROLES_UPDATED", user.getId(), companyId, "更新企业成员角色，共 " + selection.roles().size() + " 个");
        return toView(user);
    }

    @Transactional
    public void updateStatus(Long userId, CompanyTeamDtos.TeamStatusRequest request) {
        Long companyId = companyAccess.requirePermission("company:team:manage");
        if (request.status() == 0 && userId.equals(currentUser.id())) {
            throw BusinessException.badRequest("不能停用当前登录账号");
        }
        UserAccount user = requireMember(companyId, userId);
        if (request.status() == 0) ensureAdminRemains(companyId, user, null, 0);
        if (!Integer.valueOf(request.status()).equals(user.getStatus())) {
            user.setStatus(request.status());
            user.incrementSecurityVersion();
            userMapper.updateById(user);
        }
        audit("TEAM_MEMBER_STATUS_UPDATED", user.getId(), companyId,
                "企业成员状态变更为 " + (request.status() == 1 ? "启用" : "停用"));
    }

    private UserAccount requireMember(Long companyId, Long userId) {
        UserAccount user = userMapper.selectById(userId);
        if (user == null || !companyId.equals(user.getCompanyId())) {
            throw BusinessException.notFound("企业成员不存在");
        }
        return user;
    }

    private RoleSelection resolveRoles(List<String> roleCodes) {
        List<String> normalized = roleCodes.stream().map(this::normalizeRoleCode).distinct().toList();
        if (normalized.stream().anyMatch(code -> !TEAM_ROLE_CODES.contains(code))) {
            throw BusinessException.badRequest("只能分配企业团队角色");
        }
        List<Role> roles = roleMapper.selectList(new LambdaQueryWrapper<Role>()
                .in(Role::getRoleCode, normalized).eq(Role::getStatus, 1));
        if (roles.size() != normalized.size()) throw BusinessException.badRequest("角色不存在或已停用");
        Map<String, Role> byCode = roles.stream().collect(Collectors.toMap(Role::getRoleCode, Function.identity()));
        List<Role> ordered = normalized.stream().map(byCode::get).toList();
        return new RoleSelection(ordered, ordered.stream().map(Role::getId).collect(Collectors.toSet()));
    }

    private void replaceRoles(Long userId, List<Role> roles) {
        userRoleMapper.delete(new LambdaQueryWrapper<UserRole>().eq(UserRole::getUserId, userId));
        for (Role role : roles) {
            UserRole relation = new UserRole();
            relation.setUserId(userId);
            relation.setRoleId(role.getId());
            relation.setAssignedBy(currentUser.id());
            relation.setAssignedAt(LocalDateTime.now());
            userRoleMapper.insert(relation);
        }
    }

    /** Locks all active company members so two concurrent changes cannot remove the last admin. */
    private void ensureAdminRemains(Long companyId, UserAccount target, Set<Long> replacementRoleIds,
                                    Integer replacementStatus) {
        List<UserAccount> activeMembers = userMapper.selectList(new LambdaQueryWrapper<UserAccount>()
                .eq(UserAccount::getCompanyId, companyId).eq(UserAccount::getStatus, 1).last("FOR UPDATE"));
        if (activeMembers.isEmpty()) return;
        Long adminRoleId = roleMapper.selectOne(new LambdaQueryWrapper<Role>()
                .eq(Role::getRoleCode, "COMPANY_ADMIN").eq(Role::getStatus, 1)).getId();
        Set<Long> activeIds = activeMembers.stream().map(UserAccount::getId).collect(Collectors.toSet());
        Set<Long> activeAdminIds = userRoleMapper.selectList(new LambdaQueryWrapper<UserRole>()
                        .eq(UserRole::getRoleId, adminRoleId).in(UserRole::getUserId, activeIds))
                .stream().map(UserRole::getUserId).collect(Collectors.toSet());
        if (!activeAdminIds.contains(target.getId())) return;
        boolean remainsActive = replacementStatus == null || replacementStatus == 1;
        boolean remainsAdmin = replacementRoleIds == null || replacementRoleIds.contains(adminRoleId);
        if ((!remainsActive || !remainsAdmin) && activeAdminIds.size() <= 1) {
            throw BusinessException.badRequest("不能移除或停用企业最后一个可用管理员");
        }
    }

    private CompanyTeamDtos.TeamMemberView toView(UserAccount user) {
        List<Long> roleIds = userRoleMapper.selectList(new LambdaQueryWrapper<UserRole>()
                        .eq(UserRole::getUserId, user.getId()))
                .stream().map(UserRole::getRoleId).toList();
        List<String> roles = roleIds.isEmpty() ? List.of() : roleMapper.selectBatchIds(roleIds).stream()
                .map(Role::getRoleCode).sorted().toList();
        return new CompanyTeamDtos.TeamMemberView(user.getId(), user.getUsername(), user.getRealName(),
                user.getEmail(), user.getPhone(), user.getStatus(), roles, user.getCreatedAt());
    }

    private String normalizeRoleCode(String value) {
        return value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
    }

    private static String normalizeOptional(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    private void audit(String action, Long userId, Long companyId, String summary) {
        if (auditService != null) auditService.success("COMPANY_TEAM", action, "USER", userId, companyId, summary);
    }

    private record RoleSelection(List<Role> roles, Set<Long> roleIds) {}
}
