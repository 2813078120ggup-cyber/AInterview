package com.tyut.aiinterview.auth;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.tyut.aiinterview.common.BusinessException;
import com.tyut.aiinterview.domain.Company;
import com.tyut.aiinterview.domain.Permission;
import com.tyut.aiinterview.domain.Role;
import com.tyut.aiinterview.domain.RolePermission;
import com.tyut.aiinterview.domain.UserAccount;
import com.tyut.aiinterview.domain.UserRole;
import com.tyut.aiinterview.mapper.PermissionMapper;
import com.tyut.aiinterview.mapper.CompanyMapper;
import com.tyut.aiinterview.mapper.RoleMapper;
import com.tyut.aiinterview.mapper.RolePermissionMapper;
import com.tyut.aiinterview.mapper.UserMapper;
import com.tyut.aiinterview.mapper.UserRoleMapper;
import com.tyut.aiinterview.observability.OperationAuditService;
import com.tyut.aiinterview.security.JwtTokenService;
import com.tyut.aiinterview.security.LoginUser;
import com.tyut.aiinterview.security.RefreshTokenService;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
public class AuthService implements UserDetailsService {
    private final UserMapper userMapper;
    private final CompanyMapper companyMapper;
    private final RoleMapper roleMapper;
    private final UserRoleMapper userRoleMapper;
    private final PermissionMapper permissionMapper;
    private final RolePermissionMapper rolePermissionMapper;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenService tokenService;
    private final RefreshTokenService refreshTokenService;
    private final VerificationCodeService verificationCodeService;
    private final OperationAuditService auditService;

    public AuthService(UserMapper userMapper, CompanyMapper companyMapper, RoleMapper roleMapper, UserRoleMapper userRoleMapper,
            PermissionMapper permissionMapper, RolePermissionMapper rolePermissionMapper,
            PasswordEncoder passwordEncoder, JwtTokenService tokenService, RefreshTokenService refreshTokenService,
            VerificationCodeService verificationCodeService) {
        this(userMapper, companyMapper, roleMapper, userRoleMapper, permissionMapper, rolePermissionMapper,
                passwordEncoder, tokenService, refreshTokenService, verificationCodeService, null);
    }

    @org.springframework.beans.factory.annotation.Autowired
    public AuthService(UserMapper userMapper, CompanyMapper companyMapper, RoleMapper roleMapper, UserRoleMapper userRoleMapper,
            PermissionMapper permissionMapper, RolePermissionMapper rolePermissionMapper,
            PasswordEncoder passwordEncoder, JwtTokenService tokenService, RefreshTokenService refreshTokenService,
            VerificationCodeService verificationCodeService, OperationAuditService auditService) {
        this.userMapper = userMapper;
        this.companyMapper = companyMapper;
        this.roleMapper = roleMapper;
        this.userRoleMapper = userRoleMapper;
        this.permissionMapper = permissionMapper;
        this.rolePermissionMapper = rolePermissionMapper;
        this.passwordEncoder = passwordEncoder;
        this.tokenService = tokenService;
        this.refreshTokenService = refreshTokenService;
        this.verificationCodeService = verificationCodeService;
        this.auditService = auditService;
    }

    @Transactional
    public AuthDtos.UserProfile register(AuthDtos.RegisterRequest request) {
        if (userMapper.exists(new LambdaQueryWrapper<UserAccount>().eq(UserAccount::getUsername, request.username()))) {
            throw BusinessException.badRequest("用户名已存在");
        }
        verificationCodeService.verifyRegisterCode(request.phone(), request.verificationCode());
        UserAccount user = new UserAccount();
        user.setUsername(request.username());
        user.setPasswordHash(passwordEncoder.encode(request.password()));
        user.setRealName(request.realName());
        user.setEmail(normalizeOptional(request.email()));
        user.setPhone(normalizeOptional(request.phone()));
        user.setPhoneVerifiedAt(LocalDateTime.now());
        user.setStatus(1);
        userMapper.insert(user);
        Role candidateRole = roleMapper.selectOne(new LambdaQueryWrapper<Role>().eq(Role::getRoleCode, "CANDIDATE"));
        if (candidateRole == null) {
            throw new IllegalStateException("缺少 CANDIDATE 初始角色，请先执行 docs/database/seed_v1.sql");
        }
        UserRole userRole = new UserRole();
        userRole.setUserId(user.getId());
        userRole.setRoleId(candidateRole.getId());
        userRole.setAssignedAt(LocalDateTime.now());
        userRoleMapper.insert(userRole);
        return profile(user, List.of("CANDIDATE"));
    }

    @Transactional
    public AuthDtos.LoginResponse login(AuthDtos.LoginRequest request, String clientIp, String userAgent) {
        UserAccount user = userMapper.selectOne(new LambdaQueryWrapper<UserAccount>().eq(UserAccount::getUsername, request.username()));
        if (user != null && user.getStatus() != 1) {
            auditFailure("AUTH_LOGIN_DISABLED", user.getId(), user.getCompanyId(), "停用账号密码登录被拒绝");
            throw BusinessException.forbidden("用户名或密码错误");
        }
        if (user == null || !passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            auditFailure("AUTH_LOGIN_FAILED", user == null ? null : user.getId(),
                    user == null ? null : user.getCompanyId(), "密码登录失败");
            throw BusinessException.forbidden("用户名或密码错误");
        }
        try {
            requireCompanyActive(user);
        } catch (BusinessException exception) {
            auditFailure("AUTH_LOGIN_COMPANY_DISABLED", user.getId(), user.getCompanyId(), "企业账号登录被拒绝");
            throw exception;
        }
        user.setLastLoginAt(LocalDateTime.now());
        userMapper.updateById(user);
        RefreshTokenService.IssuedToken refreshToken = refreshTokenService.issue(user.getId(), clientIp, userAgent);
        auditSuccess("AUTH_LOGIN_SUCCESS", user.getId(), user.getCompanyId(), "密码登录成功");
        auditSuccess("AUTH_SESSION_CREATED", user.getId(), user.getCompanyId(), "创建新的密码登录会话");
        return loginResponse(user, refreshToken);
    }

    public void sendLoginCode(AuthDtos.SendLoginCodeRequest request) {
        requireRegisteredVerificationAccount(request.channel(), request.target());
        verificationCodeService.sendLoginCode(request);
    }

    @Transactional
    public AuthDtos.LoginResponse loginWithCode(AuthDtos.CodeLoginRequest request, String clientIp, String userAgent) {
        UserAccount user;
        try {
            user = requireRegisteredVerificationAccount(request.channel(), request.target());
        } catch (BusinessException exception) {
            auditFailure("AUTH_LOGIN_CODE_FAILED", null, null, "验证码登录失败");
            throw exception;
        }
        if (user.getStatus() != 1) {
            auditFailure("AUTH_LOGIN_DISABLED", user.getId(), user.getCompanyId(), "验证码登录被停用账号拒绝");
            throw BusinessException.forbidden("账号已被停用");
        }
        try {
            requireCompanyActive(user);
            verificationCodeService.verifyLoginCode(request.channel(), request.target(), request.verificationCode());
        } catch (BusinessException exception) {
            auditFailure("AUTH_LOGIN_CODE_FAILED", user.getId(), user.getCompanyId(), "验证码登录失败");
            throw exception;
        }
        user.setLastLoginAt(LocalDateTime.now());
        userMapper.updateById(user);
        RefreshTokenService.IssuedToken refreshToken = refreshTokenService.issue(user.getId(), clientIp, userAgent);
        auditSuccess("AUTH_LOGIN_SUCCESS", user.getId(), user.getCompanyId(), "验证码登录成功");
        auditSuccess("AUTH_SESSION_CREATED", user.getId(), user.getCompanyId(), "创建新的验证码登录会话");
        return loginResponse(user, refreshToken);
    }

    @Transactional
    public AuthDtos.LoginResponse refresh(AuthDtos.RefreshRequest request, String clientIp, String userAgent) {
        try {
            RefreshTokenService.IssuedToken refreshToken = refreshTokenService.rotate(request.refreshToken(), clientIp, userAgent,
                    userId -> isAccountEnabled(userMapper.selectById(userId)));
            UserAccount user = userMapper.selectById(refreshToken.userId());
            if (user == null || !isAccountEnabled(user)) throw BusinessException.forbidden("用户不存在或已被禁用");
            auditSuccess("AUTH_REFRESH_SUCCESS", user.getId(), user.getCompanyId(), "访问令牌刷新成功");
            return loginResponse(user, refreshToken);
        } catch (BusinessException exception) {
            auditFailure("AUTH_REFRESH_REJECTED", null, null, "访问令牌刷新被拒绝");
            throw exception;
        }
    }

    public void logout(AuthDtos.LogoutRequest request, Long userId) {
        RefreshTokenService.RevokeOutcome outcome = refreshTokenService.revoke(request.refreshToken(), userId);
        if (outcome.ownerMatched()) {
            auditSuccess("AUTH_LOGOUT_SUCCESS", userId, null, "退出登录完成");
        } else {
            auditFailure("AUTH_LOGOUT_REJECTED", null, null, "退出登录令牌与当前账号不匹配或已失效");
        }
    }

    public AuthDtos.UserProfile profileOf(Long userId) {
        UserAccount user = userMapper.selectById(userId);
        if (user == null) throw BusinessException.notFound("用户不存在");
        return profile(user, rolesOf(userId));
    }

    @Override
    public LoginUser loadUserByUsername(String identifier) throws UsernameNotFoundException {
        UserAccount user = identifier.matches("\\d+")
                ? userMapper.selectById(Long.valueOf(identifier))
                : userMapper.selectOne(new LambdaQueryWrapper<UserAccount>().eq(UserAccount::getUsername, identifier));
        if (user == null) throw new UsernameNotFoundException("用户不存在");
        return new LoginUser(user.getId(), user.getUsername(), user.getPasswordHash(), isAccountEnabled(user),
                rolesOf(user.getId()), user.getCompanyId(), permissionsOf(user.getId()), user.getSecurityVersion());
    }

    private List<String> rolesOf(Long userId) {
        List<Long> roleIds = roleIdsOf(userId);
        if (roleIds.isEmpty()) return List.of();
        return roleMapper.selectBatchIds(roleIds).stream().map(Role::getRoleCode).toList();
    }

    private List<String> permissionsOf(Long userId) {
        List<Long> roleIds = roleIdsOf(userId);
        if (roleIds.isEmpty()) return List.of();
        List<Long> permissionIds = rolePermissionMapper.selectList(new LambdaQueryWrapper<RolePermission>()
                        .in(RolePermission::getRoleId, roleIds))
                .stream().map(RolePermission::getPermissionId).distinct().toList();
        if (permissionIds.isEmpty()) return List.of();
        return permissionMapper.selectBatchIds(permissionIds).stream()
                .map(Permission::getPermissionCode).distinct().toList();
    }

    private List<Long> roleIdsOf(Long userId) {
        return userRoleMapper.selectList(new LambdaQueryWrapper<UserRole>().eq(UserRole::getUserId, userId))
                .stream().map(UserRole::getRoleId).distinct().toList();
    }

    private AuthDtos.UserProfile profile(UserAccount user, List<String> roles) {
        return new AuthDtos.UserProfile(user.getId(), user.getUsername(), user.getRealName(), roles, user.getCompanyId());
    }

    private AuthDtos.LoginResponse loginResponse(UserAccount user, RefreshTokenService.IssuedToken refreshToken) {
        List<String> roles = rolesOf(user.getId());
        return new AuthDtos.LoginResponse(tokenService.createToken(user.getId(), user.getUsername(),
                user.getSecurityVersion(), refreshToken.sessionId()), refreshToken.plainToken(), profile(user, roles));
    }

    private static String normalizeOptional(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    private void requireCompanyActive(UserAccount user) {
        if (user.getCompanyId() == null) return;
        Company company = companyMapper.selectById(user.getCompanyId());
        if (company == null || !Integer.valueOf(1).equals(company.getStatus())) {
            throw BusinessException.forbidden("所属企业已停用，暂不能登录企业工作区");
        }
    }

    private boolean isAccountEnabled(UserAccount user) {
        if (user == null || user.getStatus() != 1) return false;
        if (user.getCompanyId() == null) return true;
        Company company = companyMapper.selectById(user.getCompanyId());
        return company != null && Integer.valueOf(1).equals(company.getStatus());
    }

    private void auditSuccess(String action, Long userId, Long companyId, String summary) {
        if (auditService != null) auditService.success("AUTHENTICATION", action, "USER", userId, companyId, summary);
    }

    private void auditFailure(String action, Long userId, Long companyId, String summary) {
        if (auditService != null) auditService.failure("AUTHENTICATION", action, "USER", userId, companyId, summary);
    }

    private UserAccount requireRegisteredVerificationAccount(String channel, String target) {
        String normalizedChannel = channel == null ? "" : channel.trim().toLowerCase();
        String normalizedTarget = normalizeOptional(target);
        if (normalizedTarget == null || (!"sms".equals(normalizedChannel) && !"email".equals(normalizedChannel))) {
            throw BusinessException.badRequest("验证码登录方式不正确");
        }
        LambdaQueryWrapper<UserAccount> query = new LambdaQueryWrapper<>();
        if ("sms".equals(normalizedChannel)) {
            query.eq(UserAccount::getPhone, normalizedTarget).isNotNull(UserAccount::getPhoneVerifiedAt);
        } else {
            query.eq(UserAccount::getEmail, normalizedTarget).isNotNull(UserAccount::getEmailVerifiedAt);
        }
        UserAccount user = userMapper.selectOne(query);
        if (user == null) throw BusinessException.notFound("该账号还未注册，请先注册");
        return user;
    }

}
