package com.tyut.aiinterview.user;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.tyut.aiinterview.common.BusinessException;
import com.tyut.aiinterview.common.PageResult;
import com.tyut.aiinterview.domain.Company;
import com.tyut.aiinterview.domain.Role;
import com.tyut.aiinterview.domain.UserAccount;
import com.tyut.aiinterview.domain.UserRole;
import com.tyut.aiinterview.mapper.AdminUserMapper;
import com.tyut.aiinterview.mapper.AdminUserRow;
import com.tyut.aiinterview.mapper.CompanyMapper;
import com.tyut.aiinterview.mapper.RoleMapper;
import com.tyut.aiinterview.mapper.UserMapper;
import com.tyut.aiinterview.mapper.UserRoleMapper;
import com.tyut.aiinterview.observability.OperationAuditService;
import com.tyut.aiinterview.security.CurrentUser;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
public class UserManagementService {
    private static final String ADMIN_ROLE = "ADMIN";
    private static final Set<String> COMPANY_ROLES = Set.of("COMPANY_ADMIN", "COMPANY_RECRUITER", "COMPANY_INTERVIEWER");

    private final UserMapper userMapper;
    private final UserRoleMapper userRoleMapper;
    private final RoleMapper roleMapper;
    private final PasswordEncoder passwordEncoder;
    private final CurrentUser currentUser;
    private final CompanyMapper companyMapper;
    private final AdminUserMapper adminUserMapper;
    private final OperationAuditService auditService;

    public UserManagementService(UserMapper userMapper, UserRoleMapper userRoleMapper, RoleMapper roleMapper,
                                 PasswordEncoder passwordEncoder, CurrentUser currentUser) {
        this(userMapper, userRoleMapper, roleMapper, passwordEncoder, currentUser, null, null, null);
    }

    public UserManagementService(UserMapper userMapper, UserRoleMapper userRoleMapper, RoleMapper roleMapper,
                                 PasswordEncoder passwordEncoder, CurrentUser currentUser,
                                 OperationAuditService auditService) {
        this(userMapper, userRoleMapper, roleMapper, passwordEncoder, currentUser, null, null, auditService);
    }

    @Autowired
    public UserManagementService(UserMapper userMapper, UserRoleMapper userRoleMapper, RoleMapper roleMapper,
                                 PasswordEncoder passwordEncoder, CurrentUser currentUser, CompanyMapper companyMapper,
                                 AdminUserMapper adminUserMapper, OperationAuditService auditService) {
        this.userMapper = userMapper;
        this.userRoleMapper = userRoleMapper;
        this.roleMapper = roleMapper;
        this.passwordEncoder = passwordEncoder;
        this.currentUser = currentUser;
        this.companyMapper = companyMapper;
        this.adminUserMapper = adminUserMapper;
        this.auditService = auditService;
    }

    public PageResult<UserDtos.UserVO> page(UserDtos.UserQuery query) {
        UserDtos.UserQuery normalized = query == null ? new UserDtos.UserQuery(1L, 20L, null, null) : query;
        long pageNo = normalized.pageNo() == null ? 1 : Math.max(1, normalized.pageNo());
        long pageSize = normalized.pageSize() == null ? 20 : Math.min(100, Math.max(1, normalized.pageSize()));
        LocalDateTime createdFrom = parseDate(normalized.createdFrom(), false);
        LocalDateTime createdToExclusive = parseDate(normalized.createdTo(), true);
        if (adminUserMapper == null) {
            LambdaQueryWrapper<UserAccount> wrapper = new LambdaQueryWrapper<UserAccount>().orderByDesc(UserAccount::getCreatedAt);
            if (StringUtils.hasText(normalized.keyword())) wrapper.and(item -> item.like(UserAccount::getUsername, normalized.keyword()).or().like(UserAccount::getRealName, normalized.keyword()));
            if (normalized.status() != null) wrapper.eq(UserAccount::getStatus, normalized.status());
            var result = userMapper.selectPage(new com.baomidou.mybatisplus.extension.plugins.pagination.Page<>(pageNo, pageSize), wrapper);
            return PageResult.of(result.getRecords().stream().map(this::toVO).toList(), result.getTotal(), pageNo, pageSize);
        }
        String roleCode = StringUtils.hasText(normalized.roleCode()) ? normalized.roleCode().trim().toUpperCase(Locale.ROOT) : null;
        long offset = (pageNo - 1) * pageSize;
        List<AdminUserRow> rows = adminUserMapper.selectPage(normalized.keyword(), roleCode, normalized.companyId(), normalized.status(),
                createdFrom, createdToExclusive, offset, pageSize);
        long total = adminUserMapper.count(normalized.keyword(), roleCode, normalized.companyId(), normalized.status(), createdFrom, createdToExclusive);
        return PageResult.of(rows.stream().map(this::toVO).toList(), total, pageNo, pageSize);
    }

    public UserDtos.UserVO detail(Long userId) {
        if (adminUserMapper == null) return toVO(requireUser(userId));
        AdminUserRow row = adminUserMapper.selectById(userId);
        if (row == null) throw BusinessException.notFound("用户不存在");
        return toVO(row);
    }

    public List<UserDtos.UserOption> candidates(String keyword) {
        if (adminUserMapper != null) {
            return adminUserMapper.selectCandidates(StringUtils.hasText(keyword) ? keyword.trim() : null).stream()
                    .map(item -> new UserDtos.UserOption(item.getId(), item.getUsername(), item.getRealName())).toList();
        }
        List<Long> ids = userRoleMapper.selectList(new LambdaQueryWrapper<UserRole>().eq(UserRole::getRoleId, 2L)).stream().map(UserRole::getUserId).toList();
        if (ids.isEmpty()) return List.of();
        LambdaQueryWrapper<UserAccount> wrapper = new LambdaQueryWrapper<UserAccount>().in(UserAccount::getId, ids).eq(UserAccount::getStatus, 1).orderByAsc(UserAccount::getRealName);
        if (StringUtils.hasText(keyword)) wrapper.and(item -> item.like(UserAccount::getRealName, keyword).or().like(UserAccount::getUsername, keyword));
        return userMapper.selectList(wrapper).stream().map(item -> new UserDtos.UserOption(item.getId(), item.getUsername(), item.getRealName())).toList();
    }

    @Transactional
    public UserDtos.UserVO create(UserDtos.CreateUserRequest request) {
        if (userMapper.exists(new LambdaQueryWrapper<UserAccount>().eq(UserAccount::getUsername, request.username()))) throw BusinessException.badRequest("用户名已存在");
        List<Role> roles = validateRoleIds(request.roleIds());
        validateCompanyBinding(request.companyId(), roles);
        UserAccount user = new UserAccount();
        user.setUsername(request.username().trim());
        user.setPasswordHash(passwordEncoder.encode(request.password()));
        user.setRealName(request.realName().trim());
        user.setEmail(normalizeOptional(request.email()));
        user.setPhone(normalizeOptional(request.phone()));
        user.setCompanyId(request.companyId());
        user.setStatus(1);
        userMapper.insert(user);
        replaceRoles(user.getId(), request.roleIds());
        audit("USER_CREATED", user.getId(), user.getCompanyId(), "创建用户并分配角色");
        return detailOrFallback(user);
    }

    @Transactional
    public void updateStatus(Long userId, UserDtos.UpdateStatusRequest request) {
        if (request.status() != 0 && request.status() != 1) throw BusinessException.badRequest("用户状态不合法");
        if (userId.equals(currentUser.id()) && request.status() == 0) throw BusinessException.badRequest("不能停用当前登录账号");
        UserAccount user = requireUser(userId);
        List<String> roles = roleCodes(userId);
        if (request.status() == 0 && roles.contains(ADMIN_ROLE) && activeAdminCountForUpdate() <= 1) {
            auditDenied("USER_STATUS_UPDATED", userId, user.getCompanyId(), "禁止停用最后一个超级管理员");
            throw BusinessException.conflict("不能停用最后一个超级管理员");
        }
        boolean changed = !request.status().equals(user.getStatus());
        if (changed) {
            user.setStatus(request.status());
            user.incrementSecurityVersion();
            userMapper.updateById(user);
        }
        String operation = request.status() == 0 ? "管理员停用账号" : "管理员恢复账号";
        audit("USER_STATUS_UPDATED", user.getId(), user.getCompanyId(),
                changed ? operation : operation + "（状态未变化）");
    }

    @Transactional
    public UserDtos.UserVO assignRoles(Long userId, UserDtos.AssignRolesRequest request) {
        UserAccount user = requireUser(userId);
        List<Role> roles = validateRoleIds(request.roleIds());
        validateCompanyBinding(user.getCompanyId(), roles);
        List<String> currentRoles = roleCodes(userId);
        boolean removesAdmin = currentRoles.contains(ADMIN_ROLE) && roles.stream().noneMatch(item -> ADMIN_ROLE.equals(item.getRoleCode()));
        if (removesAdmin && activeAdminCountForUpdate() <= 1) {
            auditDenied("USER_ROLES_UPDATED", userId, user.getCompanyId(), "禁止移除最后一个超级管理员角色");
            throw BusinessException.conflict("不能移除最后一个超级管理员角色");
        }
        List<Long> currentIds = userRoleMapper.selectList(new LambdaQueryWrapper<UserRole>().eq(UserRole::getUserId, userId)).stream().map(UserRole::getRoleId).sorted().toList();
        List<Long> nextIds = request.roleIds().stream().distinct().sorted().toList();
        if (!currentIds.equals(nextIds)) replaceRoles(userId, nextIds);
        audit("USER_ROLES_UPDATED", user.getId(), user.getCompanyId(), "更新用户角色");
        return detailOrFallback(user);
    }

    private UserDtos.UserVO detailOrFallback(UserAccount user) {
        if (adminUserMapper != null) {
            AdminUserRow row = adminUserMapper.selectById(user.getId());
            if (row != null) return toVO(row);
        }
        return toVO(user);
    }

    private void replaceRoles(Long userId, List<Long> roleIds) {
        userRoleMapper.delete(new LambdaQueryWrapper<UserRole>().eq(UserRole::getUserId, userId));
        for (Long roleId : roleIds.stream().distinct().toList()) {
            UserRole relation = new UserRole();
            relation.setUserId(userId);
            relation.setRoleId(roleId);
            relation.setAssignedBy(currentUser.id());
            relation.setAssignedAt(LocalDateTime.now());
            userRoleMapper.insert(relation);
        }
    }

    private List<Role> validateRoleIds(List<Long> roleIds) {
        if (roleIds == null || roleIds.isEmpty()) throw BusinessException.badRequest("至少选择一个角色");
        List<Long> distinctIds = roleIds.stream().distinct().toList();
        List<Role> roles = roleMapper.selectBatchIds(distinctIds);
        if (roles.size() != distinctIds.size() || roles.stream().anyMatch(role -> role.getStatus() != 1)) throw BusinessException.badRequest("角色不存在或已停用");
        return roles;
    }

    private void validateCompanyBinding(Long companyId, List<Role> roles) {
        boolean hasCompanyRole = roles.stream().anyMatch(item -> COMPANY_ROLES.contains(item.getRoleCode()));
        if (companyId == null && hasCompanyRole) throw BusinessException.badRequest("企业角色必须绑定企业");
        if (companyId != null && roles.stream().anyMatch(item -> !COMPANY_ROLES.contains(item.getRoleCode()))) throw BusinessException.badRequest("企业成员只能分配企业角色");
        if (companyId != null) {
            if (companyMapper == null) throw BusinessException.badRequest("企业绑定不可用");
            Company company = companyMapper.selectById(companyId);
            if (company == null || company.getStatus() != null && company.getStatus() != 1) throw BusinessException.badRequest("企业不存在或已停用");
        }
    }

    private long countActiveAdmins() {
        return adminUserMapper == null ? Long.MAX_VALUE : adminUserMapper.countActiveUsersByRoleCode(ADMIN_ROLE);
    }

    private long activeAdminCountForUpdate() {
        if (adminUserMapper == null) return Long.MAX_VALUE;
        List<Long> lockedIds = adminUserMapper.lockActiveAdminIds();
        return lockedIds == null || lockedIds.isEmpty() ? countActiveAdmins() : lockedIds.size();
    }

    private List<String> roleCodes(Long userId) {
        List<Long> roleIds = userRoleMapper.selectList(new LambdaQueryWrapper<UserRole>().eq(UserRole::getUserId, userId)).stream().map(UserRole::getRoleId).toList();
        return roleIds.isEmpty() ? List.of() : roleMapper.selectBatchIds(roleIds).stream().map(Role::getRoleCode).toList();
    }

    private UserAccount requireUser(Long userId) {
        UserAccount user = userMapper.selectById(userId);
        if (user == null) throw BusinessException.notFound("用户不存在");
        return user;
    }

    private static String normalizeOptional(String value) { return StringUtils.hasText(value) ? value.trim() : null; }

    private LocalDateTime parseDate(String value, boolean endExclusive) {
        if (!StringUtils.hasText(value)) return null;
        try {
            LocalDate date = LocalDate.parse(value.trim());
            return (endExclusive ? date.plusDays(1) : date).atStartOfDay();
        } catch (DateTimeParseException exception) {
            throw BusinessException.badRequest("创建时间筛选格式应为 YYYY-MM-DD");
        }
    }

    private void audit(String action, Long userId, Long companyId, String summary) {
        if (auditService != null) auditService.success("USER_MANAGEMENT", action, "USER", userId, companyId, summary);
    }

    private void auditDenied(String action, Long userId, Long companyId, String summary) {
        if (auditService != null) auditService.denied("USER_MANAGEMENT", action, "USER", userId, companyId, summary);
    }

    private UserDtos.UserVO toVO(AdminUserRow row) {
        List<Long> roleIds = userRoleMapper.selectList(new LambdaQueryWrapper<UserRole>().eq(UserRole::getUserId, row.getId())).stream().map(UserRole::getRoleId).sorted().toList();
        List<String> roles = roleIds.isEmpty() ? List.of() : roleMapper.selectBatchIds(roleIds).stream().map(Role::getRoleCode).sorted().toList();
        return new UserDtos.UserVO(row.getId(), row.getUsername(), row.getRealName(), row.getEmail(), row.getPhone(), row.getAvatarUrl(),
                row.getCompanyId(), row.getCompanyName(), row.getStatus(), roles, roleIds, row.getLastLoginAt(), row.getCreatedAt(), row.getUpdatedAt());
    }

    private UserDtos.UserVO toVO(UserAccount user) {
        List<Long> roleIds = userRoleMapper.selectList(new LambdaQueryWrapper<UserRole>().eq(UserRole::getUserId, user.getId())).stream().map(UserRole::getRoleId).sorted().toList();
        List<String> roles = roleIds.isEmpty() ? List.of() : roleMapper.selectBatchIds(roleIds).stream().map(Role::getRoleCode).sorted().toList();
        String companyName = null;
        if (user.getCompanyId() != null && companyMapper != null) {
            Company company = companyMapper.selectById(user.getCompanyId());
            companyName = company == null ? null : company.getName();
        }
        return new UserDtos.UserVO(user.getId(), user.getUsername(), user.getRealName(), user.getEmail(), user.getPhone(), user.getAvatarUrl(),
                user.getCompanyId(), companyName, user.getStatus(), roles, roleIds, user.getLastLoginAt(), user.getCreatedAt(), user.getUpdatedAt());
    }
}
