package com.tyut.aiinterview.domain;

import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Data;

@Data
@TableName("user_role")
public class UserRole {
    private Long userId;
    private Long roleId;
    private Long assignedBy;
    private LocalDateTime assignedAt;
}
