package com.tyut.aiinterview.admin;

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

public final class AdminCompanyDtos {
    private AdminCompanyDtos() {}

    public record Query(Long pageNo, Long pageSize, String keyword, Integer status) {}

    public record CreateRequest(
            @NotBlank @Pattern(regexp = "[A-Za-z0-9][A-Za-z0-9_-]{1,63}", message = "企业编码须为 2–64 位字母、数字、下划线或短横线")
            String companyCode,
            @NotBlank @Size(max = 160) String name,
            @Size(max = 80) String shortName,
            @Size(max = 512) String logoUrl,
            @Size(max = 96) String industry,
            @Size(max = 48) String companySize,
            @Size(max = 96) String city,
            @Size(max = 10000) String description,
            @Size(max = 512) String websiteUrl,
            @Size(max = 80) String recruitmentContactName,
            @Email @Size(max = 160) String recruitmentContactEmail,
            @Pattern(regexp = "^$|^1\\d{10}$", message = "手机号格式不正确") @Size(max = 32) String recruitmentContactPhone) {}

    public record UpdateRequest(
            @NotBlank @Size(max = 160) String name,
            @Size(max = 80) String shortName,
            @Size(max = 512) String logoUrl,
            @Size(max = 96) String industry,
            @Size(max = 48) String companySize,
            @Size(max = 96) String city,
            @Size(max = 10000) String description,
            @Size(max = 512) String websiteUrl,
            @Size(max = 80) String recruitmentContactName,
            @Email @Size(max = 160) String recruitmentContactEmail,
            @Pattern(regexp = "^$|^1\\d{10}$", message = "手机号格式不正确") @Size(max = 32) String recruitmentContactPhone) {}

    public record StatusRequest(@NotNull @Min(0) @Max(1) Integer status, Boolean confirm) {}

    public record MemberCreateRequest(
            @NotBlank @Pattern(regexp = AccountCredentialPolicy.USERNAME_REGEX, message = AccountCredentialPolicy.USERNAME_MESSAGE)
            String username,
            @NotBlank @Pattern(regexp = AccountCredentialPolicy.PASSWORD_REGEX, message = AccountCredentialPolicy.PASSWORD_MESSAGE)
            String password,
            @NotBlank @Size(max = 64) String realName,
            @Email @Size(max = 160) String email,
            @NotBlank @Pattern(regexp = "^1\\d{10}$", message = "手机号格式不正确") String phone,
            @NotEmpty List<@NotBlank @Size(max = 32) String> roleCodes) {}

    public record CompanyView(
            Long id,
            String companyCode,
            String name,
            String shortName,
            String logoUrl,
            String industry,
            String companySize,
            String city,
            String description,
            String websiteUrl,
            String recruitmentContactName,
            String recruitmentContactEmail,
            String recruitmentContactPhone,
            Integer status,
            Long recruitingPositionCount,
            Long applicationCount,
            Long memberCount,
            LocalDateTime createdAt,
            LocalDateTime updatedAt) {}

    public record CompanyDetailView(CompanyView company, Overview overview) {}

    public record Overview(Long recruitingPositionCount, Long applicationCount, Long memberCount,
                           Long inProgressInterviewCount) {}

    public record MemberView(Long id, String username, String realName, String email, String phone,
                             Integer status, List<String> roles, LocalDateTime lastLoginAt,
                             LocalDateTime createdAt) {}
}
