package com.tyut.aiinterview.domain;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Data;

@Data
@TableName("user")
public class UserAccount {
    private Long id;
    private String username;
    @TableField("password_hash") private String passwordHash;
    private String realName;
    private String email;
    private String phone;
    private String avatarUrl;
    private Integer status;
    private LocalDateTime lastLoginAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    // The schema stores a soft-delete timestamp: active rows have NULL here.
    @TableLogic(value = "null", delval = "now()")
    private LocalDateTime deletedAt;
}
