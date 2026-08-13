package com.tyut.aiinterview.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.tyut.aiinterview.domain.JobPosition;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Result;
import org.apache.ibatis.annotations.Results;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface JobPositionMapper extends BaseMapper<JobPosition> {
    @Select("""
            SELECT COUNT(DISTINCT a.id) AS application_count,
                   COALESCE(AVG(a.match_score), 0) AS average_match_score,
                   COUNT(DISTINCT CASE WHEN a.interview_id IS NOT NULL OR oi.id IS NOT NULL THEN a.id END) AS interview_count,
                   COUNT(DISTINCT CASE WHEN a.status = 'HIRED' THEN a.id END) AS hired_count
            FROM job_position p
            LEFT JOIN job_application a
              ON a.position_id = p.id AND a.company_id = p.company_id
            LEFT JOIN offline_interview oi
              ON oi.application_id = a.id AND oi.company_id = p.company_id
            WHERE p.id = #{positionId}
              AND p.company_id = #{companyId}
              AND p.status = 1
            """)
    @Results({
            @Result(column = "application_count", property = "applicationCount"),
            @Result(column = "average_match_score", property = "averageMatchScore"),
            @Result(column = "interview_count", property = "interviewCount"),
            @Result(column = "hired_count", property = "hiredCount")
    })
    CompanyPositionStatisticsRow selectCompanyStatistics(@Param("companyId") Long companyId,
                                                          @Param("positionId") Long positionId);
}
