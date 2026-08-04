package com.tyut.aiinterview.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Data;

@Data
@TableName("ai_prompt_activation_log")
public class AiPromptActivationLog {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String promptCode;
    private Integer fromVersionNo;
    private Integer toVersionNo;
    private String action;
    private String note;
    private Long operatorId;
    private LocalDateTime createdAt;
}
