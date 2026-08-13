package com.tyut.aiinterview.user;

import com.tyut.aiinterview.security.AccountCredentialPolicy;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.LocalDateTime;
import java.util.List;

public final class CompanyTeamDtos {
    private CompanyTeamDtos() {}

    public record TeamCreateRequest(
            @NotBlank @Pattern(regexp = AccountCredentialPolicy.USERNAME_REGEX, message = AccountCredentialPolicy.USERNAME_MESSAGE)
            String username,
            @NotBlank @Pattern(regexp = AccountCredentialPolicy.PASSWORD_REGEX, message = AccountCredentialPolicy.PASSWORD_MESSAGE)
            String password,
            @NotBlank @Size(max = 64) String realName,
            @Email String email,
            @NotBlank @Pattern(regexp = "^1\\d{10}$", message = "手机号格式不正确") String phone,
            @NotEmpty List<@NotBlank @Size(max = 32) String> roleCodes) {}

    public record TeamRoleRequest(
            @NotEmpty List<@NotBlank @Size(max = 32) String> roleCodes) {}

    public record TeamStatusRequest(@NotNull @Min(0) @Max(1) Integer status) {}

    public record TeamMemberView(Long id, String username, String realName, String email, String phone,
                                 Integer status, List<String> roles, LocalDateTime createdAt) {}
}
