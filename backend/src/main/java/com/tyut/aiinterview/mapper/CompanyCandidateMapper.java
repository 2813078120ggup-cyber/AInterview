package com.tyut.aiinterview.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.tyut.aiinterview.domain.CompanyCandidate;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface CompanyCandidateMapper extends BaseMapper<CompanyCandidate> {
    @Select("SELECT * FROM company_candidate WHERE company_id = #{companyId} AND candidate_id = #{candidateId} FOR UPDATE")
    CompanyCandidate selectForUpdate(@Param("companyId") Long companyId, @Param("candidateId") Long candidateId);
}
