package com.tyut.aiinterview.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.tyut.aiinterview.domain.ApplicationNote;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface ApplicationNoteMapper extends BaseMapper<ApplicationNote> {
    @Select("SELECT * FROM application_note WHERE id = #{id} AND company_id = #{companyId} FOR UPDATE")
    ApplicationNote selectForUpdate(@Param("id") Long id, @Param("companyId") Long companyId);
}
