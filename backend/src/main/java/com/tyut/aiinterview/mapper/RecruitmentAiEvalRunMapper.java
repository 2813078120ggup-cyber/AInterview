package com.tyut.aiinterview.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.tyut.aiinterview.domain.RecruitmentAiEvalRun;
import java.time.LocalDateTime;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface RecruitmentAiEvalRunMapper extends BaseMapper<RecruitmentAiEvalRun> {
    @Select("""
            SELECT * FROM recruitment_ai_eval_run
            WHERE suite_id = #{suiteId} AND status = 'PASSED'
              AND provider = #{provider} AND model = #{model}
              AND prompt_code = #{promptCode} AND prompt_version = #{promptVersion}
              AND finished_at >= #{validAfter}
            ORDER BY finished_at DESC, id DESC LIMIT 1
            """)
    RecruitmentAiEvalRun selectValidGate(@Param("suiteId") Long suiteId,
                                         @Param("provider") String provider,
                                         @Param("model") String model,
                                         @Param("promptCode") String promptCode,
                                         @Param("promptVersion") Integer promptVersion,
                                         @Param("validAfter") LocalDateTime validAfter);
}
