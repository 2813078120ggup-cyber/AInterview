package com.tyut.aiinterview.domain;

import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Data;

@Data
@TableName("algorithm_favorite")
public class AlgorithmFavorite {
    private Long id;
    private Long userId;
    private Long problemId;
    private LocalDateTime createdAt;
}
