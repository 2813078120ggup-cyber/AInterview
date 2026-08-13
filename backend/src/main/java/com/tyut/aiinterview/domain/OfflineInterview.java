package com.tyut.aiinterview.domain;

import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Data;

@Data
@TableName("offline_interview")
public class OfflineInterview {
    private Long id;
    private Long applicationId;
    private Long companyId;
    private Long candidateId;
    private LocalDateTime scheduledAt;
    private Integer durationMinutes;
    private String interviewType;
    private String location;
    private String meetingUrl;
    private String contactName;
    private String contactPhone;
    private String note;
    private String status;
    private Long createdBy;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
