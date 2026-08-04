package com.tyut.aiinterview.user;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.tyut.aiinterview.common.BusinessException;
import com.tyut.aiinterview.domain.Permission;
import com.tyut.aiinterview.domain.Role;
import com.tyut.aiinterview.domain.RolePermission;
import com.tyut.aiinterview.mapper.PermissionMapper;
import com.tyut.aiinterview.mapper.RoleMapper;
import com.tyut.aiinterview.mapper.RolePermissionMapper;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RoleManagementService {
    private final RoleMapper roleMapper;
    private final PermissionMapper permissionMapper;
    private final RolePermissionMapper rolePermissionMapper;
    public RoleManagementService(RoleMapper roleMapper, PermissionMapper permissionMapper, RolePermissionMapper rolePermissionMapper) { this.roleMapper = roleMapper; this.permissionMapper = permissionMapper; this.rolePermissionMapper = rolePermissionMapper; }

    public List<RoleDtos.RoleVO> roles() { return roleMapper.selectList(new LambdaQueryWrapper<Role>().orderByAsc(Role::getRoleCode)).stream().map(this::toVO).toList(); }
    public List<RoleDtos.PermissionVO> permissions() { return permissionMapper.selectList(new LambdaQueryWrapper<Permission>().orderByAsc(Permission::getPermissionCode)).stream().map(item -> new RoleDtos.PermissionVO(item.getId(), item.getPermissionCode(), item.getPermissionName(), item.getResourceType(), item.getDescription())).toList(); }

    @Transactional
    public RoleDtos.RoleVO create(RoleDtos.RoleRequest request) {
        throw BusinessException.badRequest("系统角色固定为管理员和候选人，不支持新增角色");
    }

    @Transactional
    public RoleDtos.RoleVO update(Long id, RoleDtos.RoleRequest request) {
        throw BusinessException.badRequest("系统角色固定为管理员和候选人，不支持修改角色");
    }

    @Transactional
    public RoleDtos.RoleVO assignPermissions(Long id, RoleDtos.AssignPermissionsRequest request) {
        throw BusinessException.badRequest("系统角色权限由平台固定维护，不支持在线调整");
    }

    private Role requireRole(Long id) { Role role = roleMapper.selectById(id); if (role == null) throw BusinessException.notFound("角色不存在"); return role; }
    private RoleDtos.RoleVO toVO(Role role) { List<Long> permissionIds = rolePermissionMapper.selectList(new LambdaQueryWrapper<RolePermission>().eq(RolePermission::getRoleId, role.getId())).stream().map(RolePermission::getPermissionId).toList(); return new RoleDtos.RoleVO(role.getId(), role.getRoleCode(), role.getRoleName(), role.getDescription(), role.getStatus(), permissionIds); }
}
