package com.tyut.aiinterview.domain;

import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Data;

@Data
@TableName("media_file")
public class MediaFile {
    public static final int PENDING = 0;
    public static final int AVAILABLE = 1;
    public static final int FAILED = 2;
    public static final int DELETED = 3;
    private Long id;
    private Long ownerId;
    private String bucketName;
    private String objectKey;
    private String originalName;
    private String contentType;
    private String mediaType;
    private Long sizeBytes;
    private Integer durationSeconds;
    private String checksumSha256;
    private Integer status;
    private LocalDateTime createdAt;
    @TableLogic(value = "null", delval = "now()")
    private LocalDateTime deletedAt;
}
