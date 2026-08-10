package com.tyut.aiinterview.domain;

import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Data;

@Data
@TableName("learning_resource_annotation")
public class LearningResourceAnnotation {
    public static final String PRIVATE = "PRIVATE";
    public static final String ADMIN_VISIBLE = "ADMIN_VISIBLE";
    public static final String PUBLIC = "PUBLIC";

    private Long id;
    private String publicId;
    private Long resourceId;
    private Long versionId;
    private Long ownerUserId;
    private Integer pageIndex;
    private String annotationType;
    private String anchorType;
    private String geometryJson;
    private String selectedText;
    private String noteContent;
    private String styleJson;
    private String visibility;
    private Integer version;
    @TableLogic(value = "null", delval = "now()")
    private LocalDateTime deletedAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
