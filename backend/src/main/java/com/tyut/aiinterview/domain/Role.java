package com.tyut.aiinterview.domain;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

@Data
@TableName("role")
public class Role {
    private Long id;
    private String roleCode;
    private String roleName;
    private String description;
    private Integer status;
}
