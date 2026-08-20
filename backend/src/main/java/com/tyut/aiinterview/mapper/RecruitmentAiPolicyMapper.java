package com.tyut.aiinterview.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.tyut.aiinterview.domain.RecruitmentAiPolicy;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface RecruitmentAiPolicyMapper extends BaseMapper<RecruitmentAiPolicy> {
    @Select("SELECT * FROM recruitment_ai_policy WHERE scope_key = #{scopeKey} FOR UPDATE")
    RecruitmentAiPolicy selectForUpdate(@Param("scopeKey") String scopeKey);

    @Select("SELECT * FROM recruitment_ai_policy WHERE company_id = #{companyId} LIMIT 1")
    RecruitmentAiPolicy selectByCompanyId(@Param("companyId") Long companyId);
}
