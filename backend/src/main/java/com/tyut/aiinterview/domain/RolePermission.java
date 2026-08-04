package com.tyut.aiinterview.domain;

import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Data;

@Data
@TableName("role_permission")
public class RolePermission {
    private Long roleId;
    private Long permissionId;
    private LocalDateTime assignedAt;
}
