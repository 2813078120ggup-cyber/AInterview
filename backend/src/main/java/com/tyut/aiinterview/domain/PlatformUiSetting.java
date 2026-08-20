package com.tyut.aiinterview.domain;

import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Data;

@Data
@TableName("platform_ui_setting")
public class PlatformUiSetting {
    private Long id;
    private Integer mouseFollowerEnabled;
    private Long updatedBy;
    private LocalDateTime updatedAt;
}
