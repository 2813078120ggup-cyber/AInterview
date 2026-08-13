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

    public record UserQuery(Long pageNo, Long pageSize, String keyword, Integer status,
                            String roleCode, Long companyId, String createdFrom, String createdTo) {
        public UserQuery(Long pageNo, Long pageSize, String keyword, Integer status) {
            this(pageNo, pageSize, keyword, status, null, null, null, null);
        }
    }

    public record CreateUserRequest(
            @NotBlank @Pattern(regexp = AccountCredentialPolicy.USERNAME_REGEX, message = AccountCredentialPolicy.USERNAME_MESSAGE) String username,
            @NotBlank @Pattern(regexp = AccountCredentialPolicy.PASSWORD_REGEX, message = AccountCredentialPolicy.PASSWORD_MESSAGE) String password,
            @NotBlank @Size(max = 64) String realName,
            @Email String email,
            @NotBlank @Pattern(regexp = "^1\\d{10}$", message = "手机号格式不正确") String phone,
            Long companyId,
            @NotEmpty List<Long> roleIds) {
        public CreateUserRequest(String username, String password, String realName, String email, String phone,
                                 List<Long> roleIds) {
            this(username, password, realName, email, phone, null, roleIds);
        }
    }

    public record UpdateStatusRequest(@NotNull Integer status) {}

    public record AssignRolesRequest(@NotEmpty List<Long> roleIds, Integer version) {
        public AssignRolesRequest(List<Long> roleIds) {
            this(roleIds, null);
        }
    }

    public record UserVO(Long id, String username, String realName, String email, String phone, String avatarUrl,
                         Long companyId, String companyName, Integer status, List<String> roles, List<Long> roleIds,
                         LocalDateTime lastLoginAt, LocalDateTime createdAt, LocalDateTime updatedAt) {
        public UserVO(Long id, String username, String realName, String email, String phone, String avatarUrl,
                      Integer status, List<String> roles, LocalDateTime lastLoginAt, LocalDateTime createdAt) {
            this(id, username, realName, email, phone, avatarUrl, null, null, status, roles, List.of(), lastLoginAt, createdAt, null);
        }
    }

    public record UserOption(Long id, String username, String realName) {}
}
