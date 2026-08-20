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
import com.tyut.aiinterview.security.AccountCredentialPolicy;
import com.tyut.aiinterview.security.JwtTokenService;
import com.tyut.aiinterview.security.LoginUser;
import com.tyut.aiinterview.security.RefreshTokenService;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import org.springframework.dao.DuplicateKeyException;
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

    /**
     * Atomically bootstraps a new enterprise tenant and its first COMPANY_ADMIN.
     *
     * <p>The phone verification is consumed before any row is written.  The company is inserted
     * first with a null creator because the creator foreign key points back to the user; after the
     * user and role rows exist, the company is linked to that first administrator in the same
     * transaction.  A failed role lookup or audit write therefore rolls the whole bootstrap back.
     */
    @Transactional
    public AuthDtos.CompanyRegisterResponse registerCompany(AuthDtos.CompanyRegisterRequest request) {
        try {
            return registerCompanyInternal(request);
        } catch (RuntimeException exception) {
            // Keep the business failure visible to the caller even if the audit store is
            // temporarily unavailable.  The enclosing transaction still rolls back all rows.
            if (auditService != null) {
                try {
                    auditService.failure("AUTHENTICATION", "COMPANY_REGISTER_FAILED", "COMPANY", null, null,
                            "公开企业注册失败");
                } catch (RuntimeException ignored) {
                    // Do not turn a validation/conflict response into a generic 500.
                }
            }
            throw exception;
        }
    }

    private AuthDtos.CompanyRegisterResponse registerCompanyInternal(AuthDtos.CompanyRegisterRequest request) {
        String username = requiredText(request.username(), "用户名不能为空");
        if (!username.matches(AccountCredentialPolicy.USERNAME_REGEX)) {
            throw BusinessException.badRequest(AccountCredentialPolicy.USERNAME_MESSAGE);
        }
        String password = requiredText(request.password(), "密码不能为空");
        if (!password.matches(AccountCredentialPolicy.PASSWORD_REGEX)) {
            throw BusinessException.badRequest(AccountCredentialPolicy.PASSWORD_MESSAGE);
        }
        String realName = boundedText(request.realName(), "姓名不能为空", 64);
        String phone = VerificationCodeService.normalizePhone(request.phone());
        String verificationCode = requiredText(request.verificationCode(), "验证码不能为空");
        String email = normalizeOptionalEmail(request.email());
        String companyName = boundedText(request.companyName(), "企业名称不能为空", 160);
        String shortName = boundedOptional(request.shortName(), "企业简称", 80);
        String industry = boundedText(request.industry(), "所属行业不能为空", 96);
        String companySize = boundedText(request.companySize(), "企业规模不能为空", 48);
        String city = boundedText(request.city(), "所在城市不能为空", 96);
        String websiteUrl = normalizeWebsite(request.websiteUrl());
        String description = boundedOptional(request.description(), "企业简介", 2000);
        String legalRepresentative = boundedOptional(request.legalRepresentative(), "法定代表人", 64);
        String businessLicenseNo = boundedOptional(request.businessLicenseNo(), "统一社会信用代码", 64);
        if (businessLicenseNo != null && !businessLicenseNo.matches("^[A-Za-z0-9\\u4e00-\\u9fa5-]{5,64}$")) {
            throw BusinessException.badRequest("统一社会信用代码格式不正确");
        }
        if (!verificationCode.matches("^\\d{6}$")) {
            throw BusinessException.badRequest("验证码格式不正确");
        }

        if (userMapper.exists(new LambdaQueryWrapper<UserAccount>().eq(UserAccount::getUsername, username))) {
            throw BusinessException.conflict("用户名已存在");
        }
        if (email != null && userMapper.exists(new LambdaQueryWrapper<UserAccount>().eq(UserAccount::getEmail, email))) {
            throw BusinessException.conflict("邮箱已被注册");
        }
        if (userMapper.exists(new LambdaQueryWrapper<UserAccount>().eq(UserAccount::getPhone, phone))) {
            throw BusinessException.conflict("手机号已被注册");
        }
        if (businessLicenseNo != null && companyMapper.exists(
                new LambdaQueryWrapper<Company>().eq(Company::getBusinessLicenseNo, businessLicenseNo))) {
            throw BusinessException.conflict("统一社会信用代码已登记");
        }

        verificationCodeService.verifyRegisterCode(phone, verificationCode);
        Role adminRole = roleMapper.selectOne(new LambdaQueryWrapper<Role>()
                .eq(Role::getRoleCode, "COMPANY_ADMIN").eq(Role::getStatus, 1));
        if (adminRole == null) {
            throw new IllegalStateException("缺少 COMPANY_ADMIN 初始角色，请先执行角色初始化迁移");
        }

        Company company = new Company();
        company.setCompanyCode(generateCompanyCode());
        company.setBusinessLicenseNo(businessLicenseNo);
        company.setName(companyName);
        company.setShortName(shortName);
        company.setIndustry(industry);
        company.setCompanySize(companySize);
        company.setCity(city);
        company.setDescription(description);
        company.setWebsiteUrl(websiteUrl);
        company.setLegalRepresentative(legalRepresentative);
        company.setRecruitmentContactName(realName);
        company.setRecruitmentContactEmail(email);
        company.setRecruitmentContactPhone(phone);
        company.setStatus(1);

        try {
            companyMapper.insert(company);

            UserAccount admin = new UserAccount();
            admin.setUsername(username);
            admin.setPasswordHash(passwordEncoder.encode(password));
            admin.setRealName(realName);
            admin.setEmail(email);
            admin.setPhone(phone);
            admin.setPhoneVerifiedAt(LocalDateTime.now());
            admin.setCompanyId(company.getId());
            admin.setStatus(1);
            userMapper.insert(admin);

            UserRole userRole = new UserRole();
            userRole.setUserId(admin.getId());
            userRole.setRoleId(adminRole.getId());
            // The bootstrap administrator is a valid FK assignee and gives the role assignment
            // an explicit audit trail without inventing a platform actor.
            userRole.setAssignedBy(admin.getId());
            userRole.setAssignedAt(LocalDateTime.now());
            userRoleMapper.insert(userRole);

            company.setCreatedBy(admin.getId());
            companyMapper.updateById(company);
            if (auditService != null) {
                auditService.success("AUTHENTICATION", "COMPANY_REGISTER_SUCCESS", "COMPANY",
                        company.getId(), company.getId(), "公开企业注册并创建首个企业管理员");
            }
            return new AuthDtos.CompanyRegisterResponse(company.getId(), company.getCompanyCode(),
                    profile(admin, List.of("COMPANY_ADMIN")));
        } catch (DuplicateKeyException exception) {
            throw BusinessException.conflict("账号或企业登记信息已存在");
        }
    }

    @Transactional
    public AuthDtos.LoginResponse login(AuthDtos.LoginRequest request, String clientIp, String userAgent) {
        UserAccount user = findLoginUser(request.username());
        if (user != null && user.getStatus() != 1) {
            auditFailure("AUTH_LOGIN_DISABLED", user.getId(), user.getCompanyId(), "停用账号密码登录被拒绝");
            throw BusinessException.forbidden("用户名、手机号、邮箱或密码错误");
        }
        if (user == null || !passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            auditFailure("AUTH_LOGIN_FAILED", user == null ? null : user.getId(),
                    user == null ? null : user.getCompanyId(), "密码登录失败");
            throw BusinessException.forbidden("用户名、手机号、邮箱或密码错误");
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
        requireRegisteredVerificationAccount(request.target());
        verificationCodeService.sendLoginCode(request);
    }

    @Transactional
    public AuthDtos.LoginResponse loginWithCode(AuthDtos.CodeLoginRequest request, String clientIp, String userAgent) {
        UserAccount user;
        try {
            user = requireRegisteredVerificationAccount(request.target());
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
            verificationCodeService.verifyLoginCode(null, request.target(), request.verificationCode());
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
        List<String> effectiveRoles = LoginUser.effectiveRoles(roles);
        Long effectiveCompanyId = effectiveRoles.contains("CANDIDATE") ? null : user.getCompanyId();
        return new AuthDtos.UserProfile(user.getId(), user.getUsername(), user.getRealName(), effectiveRoles, effectiveCompanyId);
    }

    private AuthDtos.LoginResponse loginResponse(UserAccount user, RefreshTokenService.IssuedToken refreshToken) {
        List<String> roles = rolesOf(user.getId());
        return new AuthDtos.LoginResponse(tokenService.createToken(user.getId(), user.getUsername(),
                user.getSecurityVersion(), refreshToken.sessionId()), refreshToken.plainToken(), profile(user, roles));
    }

    private static String normalizeOptional(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    private static String normalizeOptionalEmail(String value) {
        if (!StringUtils.hasText(value)) return null;
        String normalized = VerificationCodeService.normalizeEmail(value);
        if (normalized.length() > 128 || hasControlCharacter(normalized)) {
            throw BusinessException.badRequest("邮箱格式不正确");
        }
        return normalized;
    }

    private static String requiredText(String value, String message) {
        if (!StringUtils.hasText(value)) throw BusinessException.badRequest(message);
        return value.trim();
    }

    private static String boundedText(String value, String message, int maxLength) {
        String normalized = requiredText(value, message);
        if (normalized.length() > maxLength || hasControlCharacter(normalized)) {
            throw BusinessException.badRequest(message.replace("不能为空", "格式不正确"));
        }
        return normalized;
    }

    private static String boundedOptional(String value, String field, int maxLength) {
        if (!StringUtils.hasText(value)) return null;
        String normalized = value.trim();
        if (normalized.length() > maxLength || hasControlCharacter(normalized)) {
            throw BusinessException.badRequest(field + "格式不正确");
        }
        return normalized;
    }

    private static String normalizeWebsite(String value) {
        String normalized = boundedOptional(value, "企业网站", 512);
        if (normalized == null) return null;
        try {
            java.net.URI uri = java.net.URI.create(normalized);
            String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase(Locale.ROOT);
            if (!("http".equals(scheme) || "https".equals(scheme)) || !StringUtils.hasText(uri.getHost())) {
                throw new IllegalArgumentException("invalid website");
            }
            return uri.toString();
        } catch (IllegalArgumentException exception) {
            throw BusinessException.badRequest("企业网站地址不正确");
        }
    }

    private static boolean hasControlCharacter(String value) {
        return value.chars().anyMatch(Character::isISOControl);
    }

    private String generateCompanyCode() {
        String code;
        do {
            code = "ENT-" + UUID.randomUUID().toString().replace("-", "")
                    .substring(0, 20).toUpperCase(Locale.ROOT);
        } while (companyMapper.exists(new LambdaQueryWrapper<Company>().eq(Company::getCompanyCode, code)));
        return code;
    }

    private UserAccount findLoginUser(String identifier) {
        String normalized = normalizeOptional(identifier);
        if (normalized == null) return null;
        UserAccount user = null;
        if (normalized.contains("@")) {
            user = userMapper.selectOne(new LambdaQueryWrapper<UserAccount>().eq(UserAccount::getEmail, normalized));
        } else if (normalized.matches("^1\\d{10}$")) {
            user = userMapper.selectOne(new LambdaQueryWrapper<UserAccount>().eq(UserAccount::getPhone, normalized));
        }
        return user != null ? user
                : userMapper.selectOne(new LambdaQueryWrapper<UserAccount>().eq(UserAccount::getUsername, normalized));
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

    private UserAccount requireRegisteredVerificationAccount(String target) {
        String normalizedTarget = normalizeOptional(target);
        if (normalizedTarget == null) throw BusinessException.badRequest("手机号或邮箱格式不正确");
        String normalizedChannel;
        if (normalizedTarget.matches("^1\\d{10}$")) {
            normalizedChannel = "sms";
        } else if (normalizedTarget.matches("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$")) {
            normalizedChannel = "email";
            normalizedTarget = VerificationCodeService.normalizeEmail(normalizedTarget);
        } else {
            throw BusinessException.badRequest("手机号或邮箱格式不正确");
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
