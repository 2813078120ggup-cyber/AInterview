package com.tyut.aiinterview.domain;

import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Data;

@Data
@TableName("algorithm_tag")
public class AlgorithmTag {
    private Long id;
    private String name;
    private String code;
    private Integer sortNo;
    private LocalDateTime createdAt;
}
