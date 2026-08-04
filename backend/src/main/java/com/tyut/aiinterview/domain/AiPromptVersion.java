package com.tyut.aiinterview.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Data;

@Data
@TableName("ai_prompt_version")
public class AiPromptVersion {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String promptCode;
    private String promptName;
    private String category;
    private Integer versionNo;
    private String systemTemplate;
    private String userTemplate;
    @TableField("is_active")
    private Integer active;
    private String changeNote;
    private Long createdBy;
    private LocalDateTime createdAt;
    private LocalDateTime activatedAt;
}
