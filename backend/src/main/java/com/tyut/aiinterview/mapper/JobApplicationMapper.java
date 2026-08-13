package com.tyut.aiinterview.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.tyut.aiinterview.domain.JobApplication;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface JobApplicationMapper extends BaseMapper<JobApplication> {
    /** Locks the application row so AI and offline creation cannot race on the same application. */
    @Select("SELECT * FROM job_application WHERE id = #{id} FOR UPDATE")
    JobApplication selectForUpdate(@Param("id") Long id);
}
