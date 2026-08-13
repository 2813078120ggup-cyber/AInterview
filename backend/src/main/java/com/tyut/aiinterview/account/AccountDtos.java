package com.tyut.aiinterview.account;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.tyut.aiinterview.security.AccountCredentialPolicy;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import java.time.LocalDateTime;
import java.util.List;

public final class AccountDtos {
    private AccountDtos() {}

    public record AccountProfile(
            Long id,
            String username,
            String realName,
            String accountType,
            Integer accountStatus,
            boolean avatarAvailable,
            String email,
            String emailMasked,
            boolean emailVerified,
            String phone,
            String phoneMasked,
            boolean phoneVerified,
            List<String> availableLoginMethods,
            LocalDateTime lastLoginAt,
            LocalDateTime createdAt,
            Integer version) {
    }

    /**
     * Deliberately contains no userId, role, status or company fields. Unknown
     * JSON properties are ignored so adding protected fields to a client payload
     * can never turn into an account-management capability.
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record UpdateProfileRequest(
            @NotBlank(message = "姓名不能为空") String realName,
            @NotNull(message = "版本不能为空") Integer version) {
    }

    public record ChangeCodeRequest(@NotBlank(message = "联系方式不能为空") String target) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record ChangeContactRequest(
            @NotBlank(message = "联系方式不能为空") String target,
            @NotBlank(message = "验证码不能为空") String verificationCode,
            @NotBlank(message = "当前密码不能为空") String currentPassword,
            @NotBlank(message = "刷新令牌不能为空") String refreshToken,
            @NotNull(message = "版本不能为空") Integer version) {
    }

    public record ChangeCodeResponse(long cooldownSeconds, long expiresInSeconds) {
    }

    public record ContactChangeResponse(AccountProfile profile, String accessToken, String refreshToken) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record ChangePasswordRequest(
            @NotBlank(message = "当前密码不能为空") String currentPassword,
            @NotBlank(message = "新密码不能为空")
            @Pattern(regexp = AccountCredentialPolicy.PASSWORD_REGEX,
                    message = AccountCredentialPolicy.PASSWORD_MESSAGE) String newPassword,
            @NotBlank(message = "刷新令牌不能为空") String refreshToken) {
    }

    public record ChangePasswordResponse(String accessToken, String refreshToken, String sessionBehavior) {
    }

    public record AccountSession(
            String sessionId,
            boolean current,
            String deviceType,
            String browser,
            String operatingSystem,
            String maskedIp,
            LocalDateTime createdAt,
            LocalDateTime lastActiveAt,
            LocalDateTime expiresAt) {
    }
}
