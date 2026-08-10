package com.tyut.aiinterview.domain;

import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Data;

@Data
@TableName("learning_resource_version")
public class LearningResourceVersion {
    private Long id;
    private Long resourceId;
    private Integer versionNo;
    private Long mediaId;
    private String originalName;
    private Long fileSize;
    private String checksumSha256;
    private Integer pageCount;
    private Long createdBy;
    private LocalDateTime createdAt;
}
