package com.tyut.aiinterview.domain;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Data;

@Data
@TableName("question_category")
public class QuestionCategory {
    private Long id;
    private Long parentId;
    private String categoryCode;
    private String name;
    private Integer sortOrder;
    private Integer status;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    @TableLogic(value = "null", delval = "now()")
    @TableField("deleted_at")
    private LocalDateTime deletedAt;
}
