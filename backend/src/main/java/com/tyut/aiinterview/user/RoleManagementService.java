package com.tyut.aiinterview.user;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.tyut.aiinterview.common.BusinessException;
import com.tyut.aiinterview.domain.Permission;
import com.tyut.aiinterview.domain.Role;
import com.tyut.aiinterview.domain.RolePermission;
import com.tyut.aiinterview.mapper.AdminUserMapper;
import com.tyut.aiinterview.mapper.PermissionMapper;
import com.tyut.aiinterview.mapper.RoleMapper;
import com.tyut.aiinterview.mapper.RolePermissionMapper;
import com.tyut.aiinterview.observability.OperationAuditService;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RoleManagementService {
    private static final Pattern ROLE_CODE = Pattern.compile("^[A-Z][A-Z0-9_]{1,31}$");
    private static final Set<String> SYSTEM_ROLE_CODES = Set.of(
            "ADMIN", "CANDIDATE", "INTERVIEWER", "HR", "COMPANY_ADMIN", "COMPANY_RECRUITER", "COMPANY_INTERVIEWER");

    private final RoleMapper roleMapper;
    private final PermissionMapper permissionMapper;
    private final RolePermissionMapper rolePermissionMapper;
    private final AdminUserMapper adminUserMapper;
    private final OperationAuditService auditService;

    public RoleManagementService(RoleMapper roleMapper, PermissionMapper permissionMapper, RolePermissionMapper rolePermissionMapper) {
        this(roleMapper, permissionMapper, rolePermissionMapper, null, null);
    }

    public RoleManagementService(RoleMapper roleMapper, PermissionMapper permissionMapper, RolePermissionMapper rolePermissionMapper,
                                 OperationAuditService auditService) {
        this(roleMapper, permissionMapper, rolePermissionMapper, null, auditService);
    }

    @Autowired
    public RoleManagementService(RoleMapper roleMapper, PermissionMapper permissionMapper, RolePermissionMapper rolePermissionMapper,
                                 AdminUserMapper adminUserMapper, OperationAuditService auditService) {
        this.roleMapper = roleMapper;
        this.permissionMapper = permissionMapper;
        this.rolePermissionMapper = rolePermissionMapper;
        this.adminUserMapper = adminUserMapper;
        this.auditService = auditService;
    }

    public List<RoleDtos.RoleVO> roles() {
        return roleMapper.selectList(new LambdaQueryWrapper<Role>().orderByAsc(Role::getRoleCode)).stream().map(this::toVO).toList();
    }

    public List<RoleDtos.PermissionVO> permissions() {
        return permissionMapper.selectList(new LambdaQueryWrapper<Permission>().orderByAsc(Permission::getResourceType).orderByAsc(Permission::getPermissionCode)).stream()
                .map(item -> new RoleDtos.PermissionVO(item.getId(), item.getPermissionCode(), item.getPermissionName(), item.getResourceType(), item.getDescription())).toList();
    }

    @Transactional
    public RoleDtos.RoleVO create(RoleDtos.RoleRequest request) {
        String code = normalizeCode(request.roleCode());
        validateCode(code);
        if (SYSTEM_ROLE_CODES.contains(code)) {
            auditDenied("ROLE_CREATED", null, "系统角色代码受保护");
            throw BusinessException.forbidden("系统角色代码不可创建或覆盖");
        }
        if (roleMapper.selectOne(new LambdaQueryWrapper<Role>().eq(Role::getRoleCode, code)) != null) throw BusinessException.conflict("角色代码已存在");
        validateStatus(request.status());
        Role role = new Role();
        role.setRoleCode(code);
        role.setRoleName(request.roleName().trim());
        role.setDescription(request.description());
        role.setStatus(request.status());
        role.setVersion(0);
        roleMapper.insert(role);
        audit("ROLE_CREATED", role.getId(), "创建自定义角色");
        return toVO(role);
    }

    @Transactional
    public RoleDtos.RoleVO update(Long id, RoleDtos.RoleRequest request) {
        Role role = requireRole(id);
        String code = normalizeCode(request.roleCode());
        validateCode(code);
        validateStatus(request.status());
        if (!code.equals(role.getRoleCode())) {
            auditDenied("ROLE_UPDATED", id, "角色代码是稳定标识，不允许修改");
            throw BusinessException.forbidden("角色代码不可修改");
        }
        if (SYSTEM_ROLE_CODES.contains(role.getRoleCode())) {
            auditDenied("ROLE_UPDATED", id, "系统角色受保护");
            throw BusinessException.forbidden("系统角色不可任意修改");
        }
        int expectedVersion = expectedVersion(role, request.version());
        role.setRoleName(request.roleName().trim());
        role.setDescription(request.description());
        role.setStatus(request.status());
        role.setVersion(expectedVersion);
        if (roleMapper.updateWithVersion(role) != 1) {
            auditDenied("ROLE_UPDATED", id, "角色版本冲突");
            throw BusinessException.conflict("角色已被其他管理员修改，请刷新后重试");
        }
        role.setVersion(expectedVersion + 1);
        audit("ROLE_UPDATED", id, "更新自定义角色");
        return toVO(role);
    }

    @Transactional
    public RoleDtos.RoleVO assignPermissions(Long id, RoleDtos.AssignPermissionsRequest request) {
        Role role = requireRole(id);
        List<Long> permissionIds = request.permissionIds().stream().distinct().sorted().toList();
        List<Permission> permissions = permissionMapper.selectBatchIds(permissionIds);
        if (permissions.size() != permissionIds.size()) throw BusinessException.badRequest("存在无效权限");
        List<Long> currentIds = rolePermissionMapper.selectList(new LambdaQueryWrapper<RolePermission>().eq(RolePermission::getRoleId, id)).stream()
                .map(RolePermission::getPermissionId).distinct().sorted().toList();
        if (currentIds.equals(permissionIds)) return toVO(role);
        long affectedUsers = adminUserMapper == null ? 0 : adminUserMapper.countUsersByRoleId(id);
        if (affectedUsers > 0 && !Boolean.TRUE.equals(request.confirmImpact())) {
            auditDenied("ROLE_PERMISSIONS_UPDATED", id, "权限变更影响 " + affectedUsers + " 个用户，未确认");
            throw BusinessException.conflict("该角色当前影响 " + affectedUsers + " 个用户，请确认后再保存");
        }
        int expectedVersion = expectedVersion(role, request.version());
        if (roleMapper.bumpVersion(id, expectedVersion) != 1) {
            auditDenied("ROLE_PERMISSIONS_UPDATED", id, "角色版本冲突");
            throw BusinessException.conflict("角色已被其他管理员修改，请刷新后重试");
        }
        rolePermissionMapper.delete(new LambdaQueryWrapper<RolePermission>().eq(RolePermission::getRoleId, id));
        for (Long permissionId : permissionIds) {
            RolePermission relation = new RolePermission();
            relation.setRoleId(id);
            relation.setPermissionId(permissionId);
            rolePermissionMapper.insert(relation);
        }
        role.setVersion(expectedVersion + 1);
        audit("ROLE_PERMISSIONS_UPDATED", id, "更新角色权限矩阵");
        return toVO(role);
    }

    private int expectedVersion(Role role, Integer requested) { return requested == null ? role.getVersion() == null ? 0 : role.getVersion() : requested; }

    private Role requireRole(Long id) {
        Role role = roleMapper.selectById(id);
        if (role == null) throw BusinessException.notFound("角色不存在");
        return role;
    }

    private RoleDtos.RoleVO toVO(Role role) {
        List<Long> permissionIds = rolePermissionMapper.selectList(new LambdaQueryWrapper<RolePermission>().eq(RolePermission::getRoleId, role.getId())).stream()
                .map(RolePermission::getPermissionId).distinct().sorted().toList();
        long affected = adminUserMapper == null ? 0 : adminUserMapper.countUsersByRoleId(role.getId());
        return new RoleDtos.RoleVO(role.getId(), role.getRoleCode(), role.getRoleName(), role.getDescription(), role.getStatus(), permissionIds,
                SYSTEM_ROLE_CODES.contains(role.getRoleCode()), affected, role.getVersion() == null ? 0 : role.getVersion());
    }

    private void validateCode(String code) {
        if (!ROLE_CODE.matcher(code).matches()) throw BusinessException.badRequest("角色代码只能使用大写字母、数字和下划线");
    }

    private void validateStatus(Integer status) {
        if (status == null || (status != 0 && status != 1)) throw BusinessException.badRequest("角色状态不合法");
    }

    private String normalizeCode(String code) { return code == null ? "" : code.trim().toUpperCase(Locale.ROOT); }

    private void audit(String action, Long roleId, String summary) {
        if (auditService != null) auditService.success("AUTHORIZATION", action, "ROLE", roleId, null, summary);
    }

    private void auditDenied(String action, Long roleId, String summary) {
        if (auditService != null) auditService.denied("AUTHORIZATION", action, "ROLE", roleId, null, summary);
    }
}
