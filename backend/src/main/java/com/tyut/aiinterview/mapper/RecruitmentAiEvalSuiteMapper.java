package com.tyut.aiinterview.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.tyut.aiinterview.domain.RecruitmentAiEvalSuite;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface RecruitmentAiEvalSuiteMapper extends BaseMapper<RecruitmentAiEvalSuite> {
    @Select("SELECT * FROM recruitment_ai_eval_suite WHERE id = #{id} FOR UPDATE")
    RecruitmentAiEvalSuite selectForUpdate(@Param("id") Long id);
}
