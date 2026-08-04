package com.tyut.aiinterview.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.tyut.aiinterview.domain.AlgorithmUserProgress;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface AlgorithmUserProgressMapper extends BaseMapper<AlgorithmUserProgress> {

    @Select("""
            SELECT COUNT(DISTINCT up.problem_id)
            FROM algorithm_user_progress up
            JOIN algorithm_problem p ON p.id = up.problem_id
            WHERE up.user_id = #{userId} AND up.progress_status = 'ACCEPTED' AND p.difficulty = #{difficulty}
            """)
    long countAcceptedByDifficulty(@Param("userId") Long userId, @Param("difficulty") String difficulty);
}
