package com.tyut.aiinterview.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.tyut.aiinterview.domain.CompanyCandidateTag;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface CompanyCandidateTagMapper extends BaseMapper<CompanyCandidateTag> {
    @Select("SELECT * FROM company_candidate_tag WHERE company_id = #{companyId} AND name = #{name} FOR UPDATE")
    CompanyCandidateTag selectForUpdate(@Param("companyId") Long companyId, @Param("name") String name);
}
