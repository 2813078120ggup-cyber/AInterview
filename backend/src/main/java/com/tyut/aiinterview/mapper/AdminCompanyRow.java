package com.tyut.aiinterview.mapper;

import java.time.LocalDateTime;
import lombok.Data;

@Data
public class AdminCompanyRow {
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
    private Long recruitingPositionCount;
    private Long applicationCount;
    private Long memberCount;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
