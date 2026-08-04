package com.tyut.aiinterview.domain;

import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Data;

@Data
@TableName("algorithm_note")
public class AlgorithmNote {
    private Long id;
    private Long userId;
    private Long problemId;
    private String contentMd;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
