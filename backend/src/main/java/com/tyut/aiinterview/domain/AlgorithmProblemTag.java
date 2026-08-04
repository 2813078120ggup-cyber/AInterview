package com.tyut.aiinterview.domain;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

@Data
@TableName("algorithm_problem_tag")
public class AlgorithmProblemTag {
    private Long problemId;
    private Long tagId;
}
