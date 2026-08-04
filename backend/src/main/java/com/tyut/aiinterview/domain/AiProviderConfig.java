package com.tyut.aiinterview.domain;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Data;

@Data
@TableName("ai_provider_config")
public class AiProviderConfig {
    private Long id;
    private String name;
    private String code;
    private String kind;
    private String baseUrl;
    private String chatModel;
    private String voiceModel;
    private String avatarModel;
    @TableField("api_key_cipher")
    private String apiKeyCipher;
    @TableField("api_secret_cipher")
    private String apiSecretCipher;
    @TableField("app_id_cipher")
    private String appIdCipher;
    private Integer enabled;
    private Integer textDefault;
    private Integer voiceDefault;
    private String remark;
    private Long createdBy;
    private Long updatedBy;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
