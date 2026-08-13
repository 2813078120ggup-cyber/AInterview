package com.tyut.aiinterview.mapper;

import java.time.LocalDateTime;
import lombok.Data;

/** A bounded, server-side projection for the administrator user list. */
@Data
public class AdminUserRow {
    private Long id;
    private String username;
    private String realName;
    private String email;
    private String phone;
    private String avatarUrl;
    private Long companyId;
    private String companyName;
    private Integer status;
    private LocalDateTime lastLoginAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
