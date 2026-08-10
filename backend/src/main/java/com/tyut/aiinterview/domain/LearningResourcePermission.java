package com.tyut.aiinterview.domain;

import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Data;

@Data
@TableName("learning_resource_permission")
public class LearningResourcePermission {
    public static final String USER = "USER";
    public static final String ROLE = "ROLE";

    private Long id;
    private Long resourceId;
    private String subjectType;
    private String subjectId;
    private Integer canView;
    private Integer canAnnotate;
    private LocalDateTime expiresAt;
    private Long grantedBy;
    private LocalDateTime createdAt;
}
