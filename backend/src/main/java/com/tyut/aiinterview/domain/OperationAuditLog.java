package com.tyut.aiinterview.domain;

import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Data;

@Data
@TableName("operation_audit_log")
public class OperationAuditLog {
    private Long id;
    private String requestId;
    private Long actorId;
    private String actorRole;
    private Long companyId;
    private String module;
    private String action;
    private String resourceType;
    private String resourceId;
    private String result;
    private String summary;
    private String ipAddress;
    private String userAgent;
    private LocalDateTime createdAt;
}
