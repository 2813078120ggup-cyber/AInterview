package com.tyut.aiinterview.user;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.tyut.aiinterview.common.BusinessException;
import com.tyut.aiinterview.common.PageResult;
import com.tyut.aiinterview.domain.Role;
import com.tyut.aiinterview.domain.UserAccount;
import com.tyut.aiinterview.domain.UserRole;
import com.tyut.aiinterview.mapper.RoleMapper;
import com.tyut.aiinterview.mapper.UserMapper;
import com.tyut.aiinterview.mapper.UserRoleMapper;
import com.tyut.aiinterview.security.CurrentUser;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
public class UserManagementService {
    private final UserMapper userMapper;
    private final UserRoleMapper userRoleMapper;
    private final RoleMapper roleMapper;
    private final PasswordEncoder passwordEncoder;
    private final CurrentUser currentUser;

    public UserManagementService(UserMapper userMapper, UserRoleMapper userRoleMapper, RoleMapper roleMapper,
            PasswordEncoder passwordEncoder, CurrentUser currentUser) {
        this.userMapper = userMapper; this.userRoleMapper = userRoleMapper; this.roleMapper = roleMapper;
        this.passwordEncoder = passwordEncoder; this.currentUser = currentUser;
    }

    public PageResult<UserDtos.UserVO> page(UserDtos.UserQuery query) {
        long pageNo = query.pageNo() == null ? 1 : Math.max(1, query.pageNo());
        long pageSize = query.pageSize() == null ? 20 : Math.min(100, Math.max(1, query.pageSize()));
        LambdaQueryWrapper<UserAccount> wrapper = new LambdaQueryWrapper<UserAccount>().orderByDesc(UserAccount::getCreatedAt);
        if (query.keyword() != null && !query.keyword().isBlank()) wrapper.and(item -> item.like(UserAccount::getUsername, query.keyword()).or().like(UserAccount::getRealName, query.keyword()));
        if (query.status() != null) wrapper.eq(UserAccount::getStatus, query.status());
        Page<UserAccount> result = userMapper.selectPage(new Page<>(pageNo, pageSize), wrapper);
        return PageResult.of(result.getRecords().stream().map(this::toVO).toList(), result.getTotal(), pageNo, pageSize);
    }

    public List<UserDtos.UserOption> candidates(String keyword) {
        List<Long> ids = userRoleMapper.selectList(new LambdaQueryWrapper<UserRole>().eq(UserRole::getRoleId, 2L)).stream().map(UserRole::getUserId).toList();
        if (ids.isEmpty()) return List.of();
        LambdaQueryWrapper<UserAccount> wrapper = new LambdaQueryWrapper<UserAccount>().in(UserAccount::getId, ids).eq(UserAccount::getStatus, 1).orderByAsc(UserAccount::getRealName);
        if (keyword != null && !keyword.isBlank()) wrapper.and(item -> item.like(UserAccount::getRealName, keyword).or().like(UserAccount::getUsername, keyword));
        return userMapper.selectList(wrapper).stream().map(item -> new UserDtos.UserOption(item.getId(), item.getUsername(), item.getRealName())).toList();
    }

    @Transactional
    public UserDtos.UserVO create(UserDtos.CreateUserRequest request) {
        if (userMapper.exists(new LambdaQueryWrapper<UserAccount>().eq(UserAccount::getUsername, request.username()))) throw BusinessException.badRequest("用户名已存在");
        validateRoleIds(request.roleIds());
        UserAccount user = new UserAccount();
        user.setUsername(request.username()); user.setPasswordHash(passwordEncoder.encode(request.password())); user.setRealName(request.realName());
        user.setEmail(normalizeOptional(request.email())); user.setPhone(normalizeOptional(request.phone())); user.setStatus(1); userMapper.insert(user);
        replaceRoles(user.getId(), request.roleIds());
        return toVO(user);
    }

    @Transactional
    public void updateStatus(Long userId, UserDtos.UpdateStatusRequest request) {
        if (request.status() != 0 && request.status() != 1) throw BusinessException.badRequest("用户状态不合法");
        if (userId.equals(currentUser.id()) && request.status() == 0) throw BusinessException.badRequest("不能停用当前登录账号");
        UserAccount user = requireUser(userId); user.setStatus(request.status()); userMapper.updateById(user);
    }

    @Transactional
    public UserDtos.UserVO assignRoles(Long userId, UserDtos.AssignRolesRequest request) {
        UserAccount user = requireUser(userId); validateRoleIds(request.roleIds()); replaceRoles(userId, request.roleIds()); return toVO(user);
    }

    private void replaceRoles(Long userId, List<Long> roleIds) {
        userRoleMapper.delete(new LambdaQueryWrapper<UserRole>().eq(UserRole::getUserId, userId));
        for (Long roleId : roleIds.stream().distinct().toList()) {
            UserRole relation = new UserRole(); relation.setUserId(userId); relation.setRoleId(roleId); relation.setAssignedBy(currentUser.id()); relation.setAssignedAt(LocalDateTime.now()); userRoleMapper.insert(relation);
        }
    }

    private void validateRoleIds(List<Long> roleIds) {
        List<Long> distinctIds = roleIds.stream().distinct().toList();
        List<Role> roles = roleMapper.selectBatchIds(distinctIds);
        if (roles.size() != distinctIds.size() || roles.stream().anyMatch(role -> role.getStatus() != 1)) throw BusinessException.badRequest("角色不存在或已停用");
    }

    private UserAccount requireUser(Long userId) { UserAccount user = userMapper.selectById(userId); if (user == null) throw BusinessException.notFound("用户不存在"); return user; }
    private static String normalizeOptional(String value) { return StringUtils.hasText(value) ? value.trim() : null; }
    private UserDtos.UserVO toVO(UserAccount user) {
        List<Long> roleIds = userRoleMapper.selectList(new LambdaQueryWrapper<UserRole>().eq(UserRole::getUserId, user.getId())).stream().map(UserRole::getRoleId).toList();
        List<String> roles = roleIds.isEmpty() ? List.of() : roleMapper.selectBatchIds(roleIds).stream().map(Role::getRoleCode).toList();
        return new UserDtos.UserVO(user.getId(), user.getUsername(), user.getRealName(), user.getEmail(), user.getPhone(), user.getAvatarUrl(), user.getStatus(), roles, user.getLastLoginAt(), user.getCreatedAt());
    }
}
