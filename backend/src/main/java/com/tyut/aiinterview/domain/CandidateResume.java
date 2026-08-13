package com.tyut.aiinterview.domain;

import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Data;

@Data
@TableName("candidate_resume")
public class CandidateResume {
    private Long id;
    private Long candidateId;
    private Long mediaId;
    private String title;
    private String fileName;
    private String summary;
    private String skills;
    private String parseStatus;
    private Integer parseVersion;
    private String parseError;
    private LocalDateTime parsedAt;
    private String contentHash;
    private Integer isDefault;
    private Integer status;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    @TableLogic(value = "null", delval = "now()")
    private LocalDateTime deletedAt;
}
