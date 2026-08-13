package com.tyut.aiinterview.mapper;

import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Results;
import org.apache.ibatis.annotations.Result;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface AdminCompanyMapper {
    @Select("""
            SELECT c.id, c.company_code, c.name, c.short_name, c.logo_url, c.industry, c.company_size, c.city,
                   c.description, c.website_url, c.recruitment_contact_name, c.recruitment_contact_email,
                   c.recruitment_contact_phone, c.status, c.created_at, c.updated_at,
                   (SELECT COUNT(*) FROM job_position p
                    WHERE p.company_id = c.id AND p.status = 1 AND p.recruitment_status = 'PUBLISHED'
                      AND p.deleted_at IS NULL) AS recruiting_position_count,
                   (SELECT COUNT(*) FROM job_application a WHERE a.company_id = c.id) AS application_count,
                   (SELECT COUNT(*) FROM `user` u WHERE u.company_id = c.id AND u.deleted_at IS NULL) AS member_count
            FROM company c
            WHERE c.deleted_at IS NULL
              AND (#{keyword} IS NULL OR #{keyword} = ''
                   OR c.company_code LIKE CONCAT('%', #{keyword}, '%')
                   OR c.name LIKE CONCAT('%', #{keyword}, '%')
                   OR c.short_name LIKE CONCAT('%', #{keyword}, '%'))
              AND (#{status} IS NULL OR c.status = #{status})
            ORDER BY c.status DESC, c.updated_at DESC, c.id DESC
            LIMIT #{offset}, #{limit}
            """)
    @Results(id = "adminCompanyRow", value = {
            @Result(column = "company_code", property = "companyCode"),
            @Result(column = "short_name", property = "shortName"),
            @Result(column = "logo_url", property = "logoUrl"),
            @Result(column = "company_size", property = "companySize"),
            @Result(column = "website_url", property = "websiteUrl"),
            @Result(column = "recruitment_contact_name", property = "recruitmentContactName"),
            @Result(column = "recruitment_contact_email", property = "recruitmentContactEmail"),
            @Result(column = "recruitment_contact_phone", property = "recruitmentContactPhone"),
            @Result(column = "recruiting_position_count", property = "recruitingPositionCount"),
            @Result(column = "application_count", property = "applicationCount"),
            @Result(column = "member_count", property = "memberCount"),
            @Result(column = "created_at", property = "createdAt"),
            @Result(column = "updated_at", property = "updatedAt")
    })
    List<AdminCompanyRow> selectPage(@Param("keyword") String keyword, @Param("status") Integer status,
                                     @Param("offset") long offset, @Param("limit") long limit);

    @Select("""
            SELECT COUNT(*) FROM company c
            WHERE c.deleted_at IS NULL
              AND (#{keyword} IS NULL OR #{keyword} = ''
                   OR c.company_code LIKE CONCAT('%', #{keyword}, '%')
                   OR c.name LIKE CONCAT('%', #{keyword}, '%')
                   OR c.short_name LIKE CONCAT('%', #{keyword}, '%'))
              AND (#{status} IS NULL OR c.status = #{status})
            """)
    long count(@Param("keyword") String keyword, @Param("status") Integer status);

    @Select("""
            SELECT c.id, c.company_code, c.name, c.short_name, c.logo_url, c.industry, c.company_size, c.city,
                   c.description, c.website_url, c.recruitment_contact_name, c.recruitment_contact_email,
                   c.recruitment_contact_phone, c.status, c.created_at, c.updated_at,
                   (SELECT COUNT(*) FROM job_position p
                    WHERE p.company_id = c.id AND p.status = 1 AND p.recruitment_status = 'PUBLISHED'
                      AND p.deleted_at IS NULL) AS recruiting_position_count,
                   (SELECT COUNT(*) FROM job_application a WHERE a.company_id = c.id) AS application_count,
                   (SELECT COUNT(*) FROM `user` u WHERE u.company_id = c.id AND u.deleted_at IS NULL) AS member_count
            FROM company c
            WHERE c.id = #{companyId} AND c.deleted_at IS NULL
            """)
    @Results(id = "adminCompanyDetailRow", value = {
            @Result(column = "company_code", property = "companyCode"),
            @Result(column = "short_name", property = "shortName"),
            @Result(column = "logo_url", property = "logoUrl"),
            @Result(column = "company_size", property = "companySize"),
            @Result(column = "website_url", property = "websiteUrl"),
            @Result(column = "recruitment_contact_name", property = "recruitmentContactName"),
            @Result(column = "recruitment_contact_email", property = "recruitmentContactEmail"),
            @Result(column = "recruitment_contact_phone", property = "recruitmentContactPhone"),
            @Result(column = "recruiting_position_count", property = "recruitingPositionCount"),
            @Result(column = "application_count", property = "applicationCount"),
            @Result(column = "member_count", property = "memberCount"),
            @Result(column = "created_at", property = "createdAt"),
            @Result(column = "updated_at", property = "updatedAt")
    })
    AdminCompanyRow selectById(@Param("companyId") Long companyId);

    @Select("""
            SELECT COUNT(*) FROM interview i
            JOIN job_application a ON a.interview_id = i.id
            JOIN job_position p ON p.id = a.position_id AND p.company_id = a.company_id
            WHERE a.company_id = #{companyId} AND i.status = 1
            """)
    long countInProgressInterviews(@Param("companyId") Long companyId);

    @Select("""
            SELECT COUNT(*) FROM job_position p
            WHERE p.company_id = #{companyId} AND p.status = 1
              AND p.recruitment_status = 'PUBLISHED' AND p.deleted_at IS NULL
            """)
    long countPublishedPositions(@Param("companyId") Long companyId);
}
