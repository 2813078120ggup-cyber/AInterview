package com.tyut.aiinterview.auth;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.tyut.aiinterview.common.BusinessException;
import com.tyut.aiinterview.domain.Role;
import com.tyut.aiinterview.domain.UserAccount;
import com.tyut.aiinterview.domain.UserRole;
import com.tyut.aiinterview.mapper.RoleMapper;
import com.tyut.aiinterview.mapper.UserMapper;
import com.tyut.aiinterview.mapper.UserRoleMapper;
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
    private final RoleMapper roleMapper;
    private final UserRoleMapper userRoleMapper;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenService tokenService;
    private final RefreshTokenService refreshTokenService;
    private final VerificationCodeService verificationCodeService;

    public AuthService(UserMapper userMapper, RoleMapper roleMapper, UserRoleMapper userRoleMapper,
            PasswordEncoder passwordEncoder, JwtTokenService tokenService, RefreshTokenService refreshTokenService,
            VerificationCodeService verificationCodeService) {
        this.userMapper = userMapper;
        this.roleMapper = roleMapper;
        this.userRoleMapper = userRoleMapper;
        this.passwordEncoder = passwordEncoder;
        this.tokenService = tokenService;
        this.refreshTokenService = refreshTokenService;
        this.verificationCodeService = verificationCodeService;
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
        if (user == null || user.getStatus() != 1 || !passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            throw BusinessException.forbidden("用户名或密码错误");
        }
        user.setLastLoginAt(LocalDateTime.now());
        userMapper.updateById(user);
        return loginResponse(user, refreshTokenService.issue(user.getId(), clientIp, userAgent).plainToken());
    }

    public void sendLoginCode(AuthDtos.SendLoginCodeRequest request) {
        requireRegisteredVerificationAccount(request.channel(), request.target());
        verificationCodeService.sendLoginCode(request);
    }

    @Transactional
    public AuthDtos.LoginResponse loginWithCode(AuthDtos.CodeLoginRequest request, String clientIp, String userAgent) {
        UserAccount user = requireRegisteredVerificationAccount(request.channel(), request.target());
        if (user.getStatus() != 1) throw BusinessException.forbidden("账号已被停用");
        verificationCodeService.verifyLoginCode(request.channel(), request.target(), request.verificationCode());
        user.setLastLoginAt(LocalDateTime.now());
        userMapper.updateById(user);
        return loginResponse(user, refreshTokenService.issue(user.getId(), clientIp, userAgent).plainToken());
    }

    @Transactional
    public AuthDtos.LoginResponse refresh(AuthDtos.RefreshRequest request, String clientIp, String userAgent) {
        RefreshTokenService.IssuedToken refreshToken = refreshTokenService.rotate(request.refreshToken(), clientIp, userAgent);
        UserAccount user = userMapper.selectById(refreshToken.userId());
        if (user == null || user.getStatus() != 1) throw BusinessException.forbidden("用户不存在或已被禁用");
        return loginResponse(user, refreshToken.plainToken());
    }

    public void logout(AuthDtos.LogoutRequest request, Long userId) {
        refreshTokenService.revoke(request.refreshToken(), userId);
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
        return new LoginUser(user.getId(), user.getUsername(), user.getPasswordHash(), user.getStatus() == 1, rolesOf(user.getId()));
    }

    private List<String> rolesOf(Long userId) {
        List<Long> roleIds = userRoleMapper.selectList(new LambdaQueryWrapper<UserRole>().eq(UserRole::getUserId, userId))
                .stream().map(UserRole::getRoleId).toList();
        if (roleIds.isEmpty()) return List.of();
        return roleMapper.selectBatchIds(roleIds).stream().map(Role::getRoleCode).toList();
    }

    private AuthDtos.UserProfile profile(UserAccount user, List<String> roles) {
        return new AuthDtos.UserProfile(user.getId(), user.getUsername(), user.getRealName(), roles);
    }

    private AuthDtos.LoginResponse loginResponse(UserAccount user, String refreshToken) {
        List<String> roles = rolesOf(user.getId());
        return new AuthDtos.LoginResponse(tokenService.createToken(user.getId(), user.getUsername()), refreshToken, profile(user, roles));
    }

    private static String normalizeOptional(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    private UserAccount requireRegisteredVerificationAccount(String channel, String target) {
        String normalizedChannel = channel == null ? "" : channel.trim().toLowerCase();
        String normalizedTarget = normalizeOptional(target);
        if (normalizedTarget == null || (!"sms".equals(normalizedChannel) && !"email".equals(normalizedChannel))) {
            throw BusinessException.badRequest("验证码登录方式不正确");
        }
        UserAccount user = userMapper.selectOne(new LambdaQueryWrapper<UserAccount>()
                .eq("sms".equals(normalizedChannel), UserAccount::getPhone, normalizedTarget)
                .eq("email".equals(normalizedChannel), UserAccount::getEmail, normalizedTarget));
        if (user == null) throw BusinessException.notFound("该账号还未注册，请先注册");
        return user;
    }

}
