package com.tyut.aiinterview.domain;

import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Data;

@Data
@TableName("user_notification_preference")
public class UserNotificationPreference {
    private Long id;
    private Long userId;
    private String eventType;
    private Integer siteEnabled;
    private Integer emailEnabled;
    private Integer smsEnabled;
    private Integer version;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
