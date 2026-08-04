package com.tyut.aiinterview.domain;

import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.Data;

@Data
@TableName("question")
public class Question {
    private Long id;
    private Long bankId;
    private String questionType;
    private Integer difficulty;
    private String content;
    private String options;
    private String correctAnswer;
    private String answerTemplate;
    private String explanation;
    private String tags;
    private BigDecimal score;
    private String source;
    private Integer sortOrder;
    private Integer status;
    private Long createdBy;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    @TableLogic(value = "null", delval = "now()")
    @TableField("deleted_at")
    private LocalDateTime deletedAt;
}
