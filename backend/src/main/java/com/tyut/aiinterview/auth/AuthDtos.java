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

    public record SendVerificationCodeRequest(
            @NotBlank @Pattern(regexp = "^1\\d{10}$") String phone,
            @Email String email) {}

    public record LoginRequest(@NotBlank String username, @NotBlank String password) {}

    public record SendLoginCodeRequest(@NotBlank String channel, @NotBlank String target) {}

    public record CodeLoginRequest(@NotBlank String channel, @NotBlank String target, @NotBlank String verificationCode) {}

    public record RefreshRequest(@NotBlank String refreshToken) {}

    public record LogoutRequest(@NotBlank String refreshToken) {}

    public record LoginResponse(String token, String refreshToken, UserProfile user) {}

    public record UserProfile(Long id, String username, String realName, List<String> roles) {}
}
