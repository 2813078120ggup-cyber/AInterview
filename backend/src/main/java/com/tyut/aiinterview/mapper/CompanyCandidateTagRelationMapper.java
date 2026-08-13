package com.tyut.aiinterview.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.tyut.aiinterview.domain.CompanyCandidateTagRelation;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface CompanyCandidateTagRelationMapper extends BaseMapper<CompanyCandidateTagRelation> {
    @Select("SELECT * FROM company_candidate_tag_relation WHERE company_id = #{companyId} "
            + "AND company_candidate_id = #{candidateId} AND tag_id = #{tagId} FOR UPDATE")
    CompanyCandidateTagRelation selectForUpdate(@Param("companyId") Long companyId,
                                                @Param("candidateId") Long candidateId,
                                                @Param("tagId") Long tagId);
}
