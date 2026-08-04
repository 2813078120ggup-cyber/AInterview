package com.tyut.aiinterview.user;

import com.tyut.aiinterview.security.AccountCredentialPolicy;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.LocalDateTime;
import java.util.List;

public final class UserDtos {
    private UserDtos() {}

    public record UserQuery(Long pageNo, Long pageSize, String keyword, Integer status) {}
    public record CreateUserRequest(@NotBlank @Pattern(regexp = AccountCredentialPolicy.USERNAME_REGEX, message = AccountCredentialPolicy.USERNAME_MESSAGE) String username,
                                    @NotBlank @Pattern(regexp = AccountCredentialPolicy.PASSWORD_REGEX, message = AccountCredentialPolicy.PASSWORD_MESSAGE) String password,
                                    @NotBlank @Size(max = 64) String realName,
                                    @Email String email,
                                    @NotBlank @Pattern(regexp = "^1\\d{10}$", message = "手机号格式不正确") String phone,
                                    @NotEmpty List<Long> roleIds) {}
    public record UpdateStatusRequest(@NotNull Integer status) {}
    public record AssignRolesRequest(@NotEmpty List<Long> roleIds) {}
    public record UserVO(Long id, String username, String realName, String email, String phone, String avatarUrl,
                         Integer status, List<String> roles, LocalDateTime lastLoginAt, LocalDateTime createdAt) {}
    public record UserOption(Long id, String username, String realName) {}
}
