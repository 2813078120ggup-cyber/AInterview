package com.tyut.aiinterview.algorithmworker.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.tyut.aiinterview.algorithmworker.domain.AlgorithmSubmission;
import java.time.LocalDateTime;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

public interface AlgorithmSubmissionMapper extends BaseMapper<AlgorithmSubmission> {
    @Update("""
            UPDATE algorithm_submission
            SET status = #{status},
                started_at = #{startedAt},
                finished_at = NULL,
                score = 0,
                passed_count = 0,
                total_count = 0,
                execution_time_ms = NULL,
                memory_usage_kb = NULL,
                compile_message = NULL,
                runtime_message = NULL
            WHERE id = #{id}
              AND submit_type = 'SUBMIT'
              AND status = 'QUEUED'
            """)
    int claimQueued(@Param("id") Long id,
                    @Param("status") String status,
                    @Param("startedAt") LocalDateTime startedAt);

    @Update("""
            UPDATE algorithm_submission
            SET status = #{status},
                started_at = #{startedAt},
                finished_at = NULL,
                score = 0,
                passed_count = 0,
                total_count = 0,
                execution_time_ms = NULL,
                memory_usage_kb = NULL,
                compile_message = NULL,
                runtime_message = NULL
            WHERE id = #{id}
              AND submit_type = 'SUBMIT'
              AND status IN ('COMPILING', 'RUNNING')
              AND started_at IS NOT NULL
              AND started_at < #{staleBefore}
            """)
    int reclaimStale(@Param("id") Long id,
                     @Param("status") String status,
                     @Param("startedAt") LocalDateTime startedAt,
                     @Param("staleBefore") LocalDateTime staleBefore);
}
