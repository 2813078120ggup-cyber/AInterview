package com.tyut.aiinterview.domain;

import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Data;

@Data
@TableName("site_notification")
public class SiteNotification {
    private Long id;
    private Long recipientId;
    private String notificationType;
    private String title;
    private String content;
    private String businessType;
    private Long businessId;
    private String dedupeKey;
    private LocalDateTime readAt;
    private LocalDateTime createdAt;
}
