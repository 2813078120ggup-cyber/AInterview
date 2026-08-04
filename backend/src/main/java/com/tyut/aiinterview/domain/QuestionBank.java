package com.tyut.aiinterview.domain;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Data;

@Data
@TableName("question_bank")
public class QuestionBank {
    private Long id;
    private Long categoryId;
    private Long positionId;
    private String bankCode;
    private String name;
    private String description;
    private Integer visibility;
    private Integer status;
    private Long createdBy;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    @TableLogic(value = "null", delval = "now()")
    @TableField("deleted_at")
    private LocalDateTime deletedAt;
}
