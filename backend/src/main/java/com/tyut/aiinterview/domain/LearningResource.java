package com.tyut.aiinterview.domain;

import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Data;

@Data
@TableName("learning_resource")
public class LearningResource {
    public static final String DRAFT = "DRAFT";
    public static final String PUBLISHED = "PUBLISHED";
    public static final String OFFLINE = "OFFLINE";

    private Long id;
    private String publicId;
    private String title;
    private String description;
    private String status;
    private Integer allowDownload;
    private Long currentVersionId;
    private Long createdBy;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    @TableLogic(value = "null", delval = "now()")
    private LocalDateTime deletedAt;
}
