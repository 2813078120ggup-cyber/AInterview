package com.tyut.aiinterview.account;

import com.tyut.aiinterview.common.BusinessException;
import com.tyut.aiinterview.domain.Company;
import com.tyut.aiinterview.domain.MediaFile;
import com.tyut.aiinterview.domain.Role;
import com.tyut.aiinterview.domain.UserAccount;
import com.tyut.aiinterview.domain.UserRole;
import com.tyut.aiinterview.mapper.CompanyMapper;
import com.tyut.aiinterview.mapper.MediaFileMapper;
import com.tyut.aiinterview.mapper.RoleMapper;
import com.tyut.aiinterview.mapper.UserMapper;
import com.tyut.aiinterview.mapper.UserRoleMapper;
import com.tyut.aiinterview.observability.OperationAuditService;
import com.tyut.aiinterview.security.CurrentUser;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
public class AccountService {
    private static final Set<String> COMPANY_ROLE_CODES = Set.of(
            "COMPANY_ADMIN", "COMPANY_RECRUITER", "COMPANY_INTERVIEWER", "HR", "INTERVIEWER");

    private final UserMapper userMapper;
    private final UserRoleMapper userRoleMapper;
    private final RoleMapper roleMapper;
    private final CompanyMapper companyMapper;
    private final MediaFileMapper mediaFileMapper;
    private final CurrentUser currentUser;
    private final OperationAuditService auditService;

    public AccountService(UserMapper userMapper, UserRoleMapper userRoleMapper, RoleMapper roleMapper,
                          CompanyMapper companyMapper, MediaFileMapper mediaFileMapper,
                          CurrentUser currentUser, OperationAuditService auditService) {
        this.userMapper = userMapper;
        this.userRoleMapper = userRoleMapper;
        this.roleMapper = roleMapper;
        this.companyMapper = companyMapper;
        this.mediaFileMapper = mediaFileMapper;
        this.currentUser = currentUser;
        this.auditService = auditService;
    }

    public AccountDtos.AccountProfile profile() {
        return toProfile(requireActiveUser(currentUser.id()));
    }

    UserAccount requireCurrentUser() {
        return requireActiveUser(currentUser.id());
    }

    @Transactional
    public AccountDtos.AccountProfile updateProfile(AccountDtos.UpdateProfileRequest request) {
        Long userId = currentUser.id();
        UserAccount current = requireActiveUser(userId);
        String realName = normalizeRealName(request.realName());
        int expectedVersion = request.version();
        int currentVersion = current.getVersion() == null ? 0 : current.getVersion();
        if (expectedVersion != currentVersion) {
            auditDenied(userId, current.getCompanyId(), "本人账户资料版本冲突");
            throw BusinessException.conflict("账户资料已被其他请求更新，请刷新后重试");
        }

        int updated = userMapper.updateProfileWithVersion(userId, realName, expectedVersion);
        if (updated != 1) {
            UserAccount latest = userMapper.selectById(userId);
            if (latest == null || !isActive(latest)) {
                auditDenied(userId, current.getCompanyId(), "停用账号更新本人账户资料被拒绝");
                throw BusinessException.forbidden("账号已停用");
            }
            auditDenied(userId, latest.getCompanyId(), "本人账户资料版本冲突");
            throw BusinessException.conflict("账户资料已被其他请求更新，请刷新后重试");
        }

        UserAccount latest = userMapper.selectById(userId);
        if (latest == null || !isActive(latest)) {
            throw BusinessException.forbidden("账号已停用");
        }
        auditService.success("ACCOUNT", "PROFILE_UPDATED", "USER", userId, latest.getCompanyId(),
                "更新本人账户资料");
        return toProfile(latest);
    }

    private UserAccount requireActiveUser(Long userId) {
        UserAccount user = userMapper.selectById(userId);
        if (user == null) throw BusinessException.notFound("用户不存在");
        if (!isActive(user)) throw BusinessException.forbidden("账号已停用");
        return user;
    }

    private boolean isActive(UserAccount user) {
        if (user == null || !Integer.valueOf(1).equals(user.getStatus())) return false;
        if (user.getCompanyId() == null) return true;
        Company company = companyMapper.selectById(user.getCompanyId());
        return company != null && Integer.valueOf(1).equals(company.getStatus());
    }

    private AccountDtos.AccountProfile toProfile(UserAccount user) {
        String email = normalizeOptional(user.getEmail());
        String phone = normalizeOptional(user.getPhone());
        List<String> roleCodes = roleCodes(user.getId());
        return new AccountDtos.AccountProfile(
                user.getId(),
                user.getUsername(),
                user.getRealName(),
                accountType(roleCodes),
                user.getStatus(),
                avatarAvailable(user.getAvatarMediaId()),
                email,
                maskEmail(email),
                email != null && user.getEmailVerifiedAt() != null,
                phone,
                maskPhone(phone),
                phone != null && user.getPhoneVerifiedAt() != null,
                availableLoginMethods(user, email, phone),
                user.getLastLoginAt(),
                user.getCreatedAt(),
                user.getVersion() == null ? 0 : user.getVersion());
    }

    private List<String> roleCodes(Long userId) {
        List<Long> roleIds = userRoleMapper.selectList(
                        new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<UserRole>()
                                .eq(UserRole::getUserId, userId))
                .stream().map(UserRole::getRoleId).distinct().toList();
        if (roleIds.isEmpty()) return List.of();
        return roleMapper.selectBatchIds(roleIds).stream()
                .map(Role::getRoleCode).filter(StringUtils::hasText).map(String::trim).toList();
    }

    private String accountType(List<String> roleCodes) {
        if (roleCodes.contains("ADMIN")) return "ADMIN";
        if (roleCodes.contains("CANDIDATE")) return "CANDIDATE";
        if (roleCodes.stream().anyMatch(COMPANY_ROLE_CODES::contains)) return "COMPANY";
        return "USER";
    }

    private boolean avatarAvailable(Long avatarMediaId) {
        if (avatarMediaId == null) return false;
        MediaFile media = mediaFileMapper.selectById(avatarMediaId);
        return media != null && Objects.equals(media.getStatus(), MediaFile.AVAILABLE)
                && "image".equalsIgnoreCase(media.getMediaType());
    }

    private List<String> availableLoginMethods(UserAccount user, String email, String phone) {
        java.util.ArrayList<String> methods = new java.util.ArrayList<>();
        if (StringUtils.hasText(user.getPasswordHash())) methods.add("PASSWORD");
        if (phone != null && user.getPhoneVerifiedAt() != null) methods.add("SMS");
        if (email != null && user.getEmailVerifiedAt() != null) methods.add("EMAIL");
        return List.copyOf(methods);
    }

    private String normalizeRealName(String value) {
        String normalized = value == null ? "" : value.trim();
        if (normalized.isEmpty()) throw BusinessException.badRequest("姓名不能全为空白");
        if (normalized.length() > 64) throw BusinessException.badRequest("姓名不能超过 64 个字符");
        return normalized;
    }

    private static String normalizeOptional(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    private String maskEmail(String email) {
        if (!StringUtils.hasText(email)) return null;
        int at = email.indexOf('@');
        if (at <= 0 || at == email.length() - 1) return "***";
        String local = email.substring(0, at);
        String visible = local.length() == 1 ? local : local.substring(0, 1);
        return visible + "***" + email.substring(at);
    }

    private String maskPhone(String phone) {
        if (!StringUtils.hasText(phone)) return null;
        if (phone.length() <= 7) return "***";
        return phone.substring(0, 3) + "****" + phone.substring(phone.length() - 4);
    }

    private void auditDenied(Long userId, Long companyId, String summary) {
        auditService.denied("ACCOUNT", "PROFILE_UPDATED", "USER", userId, companyId, summary);
    }
}
