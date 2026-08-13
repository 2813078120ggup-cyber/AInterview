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
    private Long companyId;
    private String name;
    private String department;
    private Integer salaryMin;
    private Integer salaryMax;
    private String city;
    private String experienceRequirement;
    private String educationRequirement;
    private String jobType;
    private String description;
    private String requirements;
    private String skillTags;
    private String competencyModel;
    private Integer status;
    private String recruitmentStatus;
    private LocalDateTime publishedAt;
    private LocalDateTime expiresAt;
    private Long createdBy;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    @TableLogic(value = "null", delval = "now()")
    @TableField("deleted_at")
    private LocalDateTime deletedAt;
}
