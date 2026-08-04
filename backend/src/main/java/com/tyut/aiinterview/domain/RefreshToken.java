package com.tyut.aiinterview.domain;

import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Data;

@Data
@TableName("refresh_token")
public class RefreshToken {
    private Long id;
    private Long userId;
    private String tokenHash;
    private LocalDateTime expiresAt;
    private LocalDateTime revokedAt;
    private String clientIp;
    private String userAgent;
    private LocalDateTime createdAt;
}
