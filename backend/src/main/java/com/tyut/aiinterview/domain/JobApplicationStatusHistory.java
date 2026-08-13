package com.tyut.aiinterview.domain;

import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Data;

@Data
@TableName("job_application_status_history")
public class JobApplicationStatusHistory {
    private Long id;
    private Long applicationId;
    private String fromStatus;
    private String toStatus;
    private Long operatorId;
    private String note;
    private LocalDateTime createdAt;
}
