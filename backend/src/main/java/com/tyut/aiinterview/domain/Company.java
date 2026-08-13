package com.tyut.aiinterview.domain;

import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Data;

@Data
@TableName("company")
public class Company {
    private Long id;
    private String companyCode;
    private String name;
    private String shortName;
    private String logoUrl;
    private String industry;
    private String companySize;
    private String city;
    private String description;
    private String websiteUrl;
    private String recruitmentContactName;
    private String recruitmentContactEmail;
    private String recruitmentContactPhone;
    private Integer status;
    private Long createdBy;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    @TableLogic(value = "null", delval = "now()")
    private LocalDateTime deletedAt;
}
