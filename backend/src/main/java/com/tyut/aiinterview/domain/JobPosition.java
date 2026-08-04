package com.tyut.aiinterview.domain;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Data;

@Data
@TableName("job_position")
public class JobPosition {
    private Long id;
    private String positionCode;
    private String name;
    private String department;
    private String description;
    private String competencyModel;
    private Integer status;
    private Long createdBy;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    @TableLogic(value = "null", delval = "now()")
    @TableField("deleted_at")
    private LocalDateTime deletedAt;
}
