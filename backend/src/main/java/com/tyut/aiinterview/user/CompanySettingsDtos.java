package com.tyut.aiinterview.user;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.LocalDateTime;

public final class CompanySettingsDtos {
    private CompanySettingsDtos() {}

    public record SettingsView(Long id, String companyCode, String name, String shortName, String logoUrl,
                               String industry, String companySize, String city, String description,
                               String websiteUrl, String recruitmentContactName, String recruitmentContactEmail,
                               String recruitmentContactPhone, LocalDateTime updatedAt) {}

    public record UpdateRequest(
            @NotBlank @Size(max = 160) String name,
            @Size(max = 80) String shortName,
            @Size(max = 512) String logoUrl,
            @Size(max = 96) String industry,
            @Size(max = 48) String companySize,
            @Size(max = 96) String city,
            @Size(max = 4000) String description,
            @Size(max = 512) String websiteUrl,
            @Size(max = 80) String recruitmentContactName,
            @Email @Size(max = 160) String recruitmentContactEmail,
            @Pattern(regexp = "^$|^1\\d{10}$", message = "手机号格式不正确") @Size(max = 32) String recruitmentContactPhone) {}
}
