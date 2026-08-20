package com.tyut.aiinterview.auth;

import com.tyut.aiinterview.security.AccountCredentialPolicy;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.List;

public final class AuthDtos {
    private AuthDtos() {}

    public record RegisterRequest(
            @NotBlank @Pattern(regexp = AccountCredentialPolicy.USERNAME_REGEX, message = AccountCredentialPolicy.USERNAME_MESSAGE) String username,
            @NotBlank @Pattern(regexp = AccountCredentialPolicy.PASSWORD_REGEX, message = AccountCredentialPolicy.PASSWORD_MESSAGE) String password,
            @NotBlank @Size(max = 64) String realName,
            @Email String email,
            @NotBlank @Pattern(regexp = "^1\\d{10}$", message = "手机号格式不正确") String phone,
            @NotBlank String verificationCode) {}

    /**
     * Public bootstrap registration for an enterprise tenant and its first HR administrator.
     * The phone is always verified by the registration-code flow before the transaction creates
     * either row.  Company identifiers are deliberately not accepted from the client; the service
     * generates a tenant code after validation.
     */
    public record CompanyRegisterRequest(
            @NotBlank @Pattern(regexp = AccountCredentialPolicy.USERNAME_REGEX,
                    message = AccountCredentialPolicy.USERNAME_MESSAGE) String username,
            @NotBlank @Pattern(regexp = AccountCredentialPolicy.PASSWORD_REGEX,
                    message = AccountCredentialPolicy.PASSWORD_MESSAGE) String password,
            @NotBlank @Size(max = 64) String realName,
            @Email @Size(max = 128) String email,
            @NotBlank @Pattern(regexp = "^1\\d{10}$", message = "手机号格式不正确") String phone,
            @NotBlank @Pattern(regexp = "^\\d{6}$", message = "验证码格式不正确") String verificationCode,
            @NotBlank @Size(max = 160) String companyName,
            @Size(max = 80) String shortName,
            @NotBlank @Size(max = 96) String industry,
            @NotBlank @Size(max = 48) String companySize,
            @NotBlank @Size(max = 96) String city,
            @Pattern(regexp = "^$|https?://[^\\s]{1,504}$", message = "企业网站地址不正确")
            @Size(max = 512) String websiteUrl,
            @Size(max = 2000) String description,
            @Size(max = 64) String legalRepresentative,
            @Pattern(regexp = "^$|[A-Za-z0-9\\u4e00-\\u9fa5-]{5,64}$", message = "统一社会信用代码格式不正确")
            String businessLicenseNo) {}

    public record CompanyRegisterResponse(Long companyId, String companyCode, UserProfile admin) {}

    public record SendVerificationCodeRequest(
            @NotBlank @Pattern(regexp = "^1\\d{10}$") String phone,
            @Email String email,
            @NotBlank(message = "请输入图形验证码") String captchaChallengeId,
            @NotBlank(message = "请输入图形验证码") String captchaCode) {
        public SendVerificationCodeRequest(String phone, String email) {
            this(phone, email, null, null);
        }

        /** Compatibility constructor for callers that used the former slider proof token. */
        public SendVerificationCodeRequest(String phone, String email, String legacyCaptchaToken) {
            this(phone, email, null, legacyCaptchaToken);
        }
    }

    public record LoginRequest(@NotBlank String username, @NotBlank String password,
                               @NotBlank(message = "请输入图形验证码") String captchaChallengeId,
                               @NotBlank(message = "请输入图形验证码") String captchaCode) {
        public LoginRequest(String username, String password) {
            this(username, password, null, null);
        }

        /** Compatibility constructor for callers that used the former slider proof token. */
        public LoginRequest(String username, String password, String legacyCaptchaToken) {
            this(username, password, null, legacyCaptchaToken);
        }
    }

    public record SendLoginCodeRequest(String channel, @NotBlank String target,
                                       @NotBlank(message = "请输入图形验证码") String captchaChallengeId,
                                       @NotBlank(message = "请输入图形验证码") String captchaCode) {
        public SendLoginCodeRequest(String channel, String target) {
            this(channel, target, null, null);
        }

        /** Compatibility constructor for callers that used the former slider proof token. */
        public SendLoginCodeRequest(String channel, String target, String legacyCaptchaToken) {
            this(channel, target, null, legacyCaptchaToken);
        }
    }

    public record CodeLoginRequest(String channel, @NotBlank String target, @NotBlank String verificationCode) {}

    public record RefreshRequest(@NotBlank String refreshToken) {}

    public record LogoutRequest(@NotBlank String refreshToken) {}

    public record PasswordResetCodeRequest(
            @NotBlank String channel,
            @NotBlank String target,
            @NotBlank(message = "请输入图形验证码") String captchaChallengeId,
            @NotBlank(message = "请输入图形验证码") String captchaCode) {
        public PasswordResetCodeRequest(String channel, String target) {
            this(channel, target, null, null);
        }
    }

    public record PasswordResetVerifyRequest(
            @NotBlank String channel,
            @NotBlank String target,
            @NotBlank(message = "验证码不能为空") String verificationCode) {}

    public record PasswordResetCompleteRequest(
            @NotBlank(message = "账户验证已失效，请重新验证") String resetToken,
            @NotBlank(message = "新密码不能为空")
            @Pattern(regexp = AccountCredentialPolicy.PASSWORD_REGEX,
                    message = AccountCredentialPolicy.PASSWORD_MESSAGE) String newPassword) {}

    public record PasswordResetRequest(
            @NotBlank String channel,
            @NotBlank String target,
            @NotBlank(message = "验证码不能为空") String verificationCode,
            @NotBlank(message = "新密码不能为空")
            @Pattern(regexp = AccountCredentialPolicy.PASSWORD_REGEX,
                    message = AccountCredentialPolicy.PASSWORD_MESSAGE) String newPassword) {}

    public record PasswordResetCodeResponse(boolean accepted, long cooldownSeconds, long expiresInSeconds,
                                            String message) {}

    public record PasswordResetVerifyResponse(String resetToken, long expiresInSeconds) {}

    public record PasswordResetResponse(String sessionBehavior) {}

    public record CaptchaChallengeRequest(@NotBlank String purpose) {}

    public record CaptchaChallengeResponse(String challengeId, String imageDataUrl, long expiresInSeconds) {}

    public record LoginResponse(String token, String refreshToken, UserProfile user) {}

    public record UserProfile(Long id, String username, String realName, List<String> roles, Long companyId) {}
}
