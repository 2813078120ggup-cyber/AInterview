package com.tyut.aiinterview.domain;

import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Data;

@Data
@TableName("permission")
public class Permission {
    private Long id;
    private String permissionCode;
    private String permissionName;
    private String resourceType;
    private String description;
    private LocalDateTime createdAt;
}
