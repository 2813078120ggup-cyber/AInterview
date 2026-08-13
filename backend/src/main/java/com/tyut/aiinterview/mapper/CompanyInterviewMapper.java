package com.tyut.aiinterview.mapper;

import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface CompanyInterviewMapper {
    @Select("""
            <script>
            SELECT * FROM (
              SELECT 'AI' AS interview_kind, CONCAT('AI-', i.id) AS activity_id,
                     i.id AS interview_id, NULL AS offline_interview_id, a.id AS application_id,
                     a.company_id, a.position_id, p.name AS position_name,
                     u.id AS candidate_id, u.real_name AS candidate_name, u.email AS candidate_email, u.phone AS candidate_phone,
                     'AI' AS activity_type, i.status AS raw_status,
                     CASE WHEN i.status = 3 THEN 'CANCELLED'
                          WHEN i.status IN (2, 4, 6) THEN 'COMPLETED'
                          WHEN i.status IN (1, 5) THEN 'RUNNING'
                          WHEN i.status = 7 THEN 'FAILED'
                          ELSE 'SCHEDULED' END AS status,
                     i.scheduled_at, i.duration AS duration_minutes, NULL AS location, i.meeting_url,
                     NULL AS contact_name, NULL AS contact_phone, i.remark AS note,
                     a.status AS application_status,
                     CASE WHEN EXISTS (SELECT 1 FROM site_notification n
                                       WHERE n.recipient_id = u.id AND n.business_type = 'JOB_APPLICATION'
                                         AND n.business_id = a.id
                                         AND (n.dedupe_key LIKE 'ai-interview-invite-%'
                                              OR n.dedupe_key LIKE 'offline-interview-%'
                                              OR n.dedupe_key LIKE 'company-interview-%')) THEN 'SENT' ELSE 'NOT_SENT' END AS notification_status,
                     i.updated_at, i.interviewer_id
              FROM interview i
              JOIN job_application a ON a.interview_id = i.id
              JOIN job_position p ON p.id = a.position_id AND p.company_id = a.company_id
              JOIN `user` u ON u.id = a.candidate_id
              WHERE a.company_id = #{companyId}
              UNION ALL
              SELECT 'OFFLINE' AS interview_kind, CONCAT('OFFLINE-', oi.id) AS activity_id,
                     NULL AS interview_id, oi.id AS offline_interview_id, a.id AS application_id,
                     oi.company_id, a.position_id, p.name AS position_name,
                     u.id AS candidate_id, u.real_name AS candidate_name, u.email AS candidate_email, u.phone AS candidate_phone,
                     oi.interview_type AS activity_type, NULL AS raw_status, oi.status AS status,
                     oi.scheduled_at, oi.duration_minutes, oi.location, oi.meeting_url,
                     oi.contact_name, oi.contact_phone, oi.note,
                     a.status AS application_status,
                     CASE WHEN EXISTS (SELECT 1 FROM site_notification n
                                       WHERE n.recipient_id = u.id AND n.business_type = 'JOB_APPLICATION'
                                         AND n.business_id = a.id
                                         AND (n.dedupe_key LIKE 'ai-interview-invite-%'
                                              OR n.dedupe_key LIKE 'offline-interview-%'
                                              OR n.dedupe_key LIKE 'company-interview-%')) THEN 'SENT' ELSE 'NOT_SENT' END AS notification_status,
                     oi.updated_at, NULL AS interviewer_id
              FROM offline_interview oi
              JOIN job_application a ON a.id = oi.application_id AND a.company_id = oi.company_id
              JOIN job_position p ON p.id = a.position_id AND p.company_id = oi.company_id
              JOIN `user` u ON u.id = oi.candidate_id
              WHERE oi.company_id = #{companyId}
            ) x
            WHERE 1 = 1
            <if test="restricted"> AND x.interview_kind = 'AI' AND x.interviewer_id = #{userId}</if>
            <if test="activityId != null and activityId != ''"> AND x.activity_id = #{activityId}</if>
            <if test="positionId != null"> AND x.position_id = #{positionId}</if>
            <if test="keyword != null and keyword != ''">
              AND (x.candidate_name LIKE CONCAT('%', #{keyword}, '%')
                   OR x.candidate_email LIKE CONCAT('%', #{keyword}, '%')
                   OR x.position_name LIKE CONCAT('%', #{keyword}, '%'))
            </if>
            <if test="activityType != null and activityType != ''"> AND x.activity_type = #{activityType}</if>
            <if test="range == 'COMPLETED'"> AND x.status = 'COMPLETED'</if>
            <if test="range == 'CANCELLED'"> AND x.status = 'CANCELLED'</if>
            <if test="scheduledFrom != null"> AND x.scheduled_at &gt;= #{scheduledFrom}</if>
            <if test="scheduledTo != null"> AND x.scheduled_at &lt; #{scheduledTo}</if>
            <choose>
              <when test="sort == 'NEWEST'"> ORDER BY x.updated_at DESC, x.activity_id DESC</when>
              <when test="sort == 'CANDIDATE'"> ORDER BY x.candidate_name ASC, x.scheduled_at ASC, x.activity_id DESC</when>
              <otherwise> ORDER BY x.scheduled_at ASC, x.activity_id DESC</otherwise>
            </choose>
            LIMIT #{offset}, #{pageSize}
            </script>
            """)
    List<CompanyInterviewRow> selectPage(@Param("companyId") Long companyId,
                                         @Param("userId") Long userId,
                                         @Param("restricted") boolean restricted,
                                         @Param("activityId") String activityId,
                                         @Param("positionId") Long positionId,
                                         @Param("keyword") String keyword,
                                         @Param("activityType") String activityType,
                                         @Param("range") String range,
                                         @Param("scheduledFrom") java.time.LocalDateTime scheduledFrom,
                                         @Param("scheduledTo") java.time.LocalDateTime scheduledTo,
                                         @Param("sort") String sort,
                                         @Param("offset") int offset,
                                         @Param("pageSize") int pageSize);

    @Select("""
            <script>
            SELECT COUNT(*) FROM (
              SELECT CONCAT('AI-', i.id) AS activity_id, i.id AS interview_id, NULL AS offline_interview_id,
                     a.company_id, a.position_id, p.name AS position_name, u.real_name AS candidate_name,
                     u.email AS candidate_email, 'AI' AS activity_type,
                     CASE WHEN i.status = 3 THEN 'CANCELLED'
                          WHEN i.status IN (2, 4, 6) THEN 'COMPLETED'
                          WHEN i.status IN (1, 5) THEN 'RUNNING'
                          WHEN i.status = 7 THEN 'FAILED'
                          ELSE 'SCHEDULED' END AS status,
                     i.scheduled_at, i.updated_at, i.interviewer_id, 'AI' AS interview_kind
              FROM interview i JOIN job_application a ON a.interview_id = i.id
              JOIN job_position p ON p.id = a.position_id AND p.company_id = a.company_id
              JOIN `user` u ON u.id = a.candidate_id
              WHERE a.company_id = #{companyId}
              UNION ALL
              SELECT CONCAT('OFFLINE-', oi.id), NULL, oi.id, oi.company_id, a.position_id, p.name,
                     u.real_name, u.email, oi.interview_type, oi.status, oi.scheduled_at, oi.updated_at,
                     NULL, 'OFFLINE'
              FROM offline_interview oi JOIN job_application a ON a.id = oi.application_id AND a.company_id = oi.company_id
              JOIN job_position p ON p.id = a.position_id AND p.company_id = oi.company_id
              JOIN `user` u ON u.id = oi.candidate_id
              WHERE oi.company_id = #{companyId}
            ) x
            WHERE 1 = 1
            <if test="restricted"> AND x.interview_kind = 'AI' AND x.interviewer_id = #{userId}</if>
            <if test="activityId != null and activityId != ''"> AND x.activity_id = #{activityId}</if>
            <if test="positionId != null"> AND x.position_id = #{positionId}</if>
            <if test="keyword != null and keyword != ''">
              AND (x.candidate_name LIKE CONCAT('%', #{keyword}, '%')
                   OR x.candidate_email LIKE CONCAT('%', #{keyword}, '%')
                   OR x.position_name LIKE CONCAT('%', #{keyword}, '%'))
            </if>
            <if test="activityType != null and activityType != ''"> AND x.activity_type = #{activityType}</if>
            <if test="range == 'COMPLETED'"> AND x.status = 'COMPLETED'</if>
            <if test="range == 'CANCELLED'"> AND x.status = 'CANCELLED'</if>
            <if test="scheduledFrom != null"> AND x.scheduled_at &gt;= #{scheduledFrom}</if>
            <if test="scheduledTo != null"> AND x.scheduled_at &lt; #{scheduledTo}</if>
            </script>
            """)
    long count(@Param("companyId") Long companyId,
               @Param("userId") Long userId,
               @Param("restricted") boolean restricted,
               @Param("activityId") String activityId,
               @Param("positionId") Long positionId,
               @Param("keyword") String keyword,
               @Param("activityType") String activityType,
               @Param("range") String range,
               @Param("scheduledFrom") java.time.LocalDateTime scheduledFrom,
               @Param("scheduledTo") java.time.LocalDateTime scheduledTo);

    @Select("""
            SELECT 'AI' AS interview_kind, CONCAT('AI-', i.id) AS activity_id,
                   i.id AS interview_id, NULL AS offline_interview_id, a.id AS application_id,
                   a.company_id, a.position_id, p.name AS position_name,
                   u.id AS candidate_id, u.real_name AS candidate_name, u.email AS candidate_email, u.phone AS candidate_phone,
                   'AI' AS activity_type, i.status AS raw_status,
                   CASE WHEN i.status = 3 THEN 'CANCELLED'
                        WHEN i.status IN (2, 4, 6) THEN 'COMPLETED'
                        WHEN i.status IN (1, 5) THEN 'RUNNING'
                        WHEN i.status = 7 THEN 'FAILED'
                        ELSE 'SCHEDULED' END AS status,
                   i.scheduled_at, i.duration AS duration_minutes, NULL AS location, i.meeting_url,
                   NULL AS contact_name, NULL AS contact_phone, i.remark AS note,
                   a.status AS application_status,
                   CASE WHEN EXISTS (SELECT 1 FROM site_notification n WHERE n.recipient_id = u.id
                                     AND n.business_type = 'JOB_APPLICATION' AND n.business_id = a.id
                                     AND (n.dedupe_key LIKE 'ai-interview-invite-%'
                                          OR n.dedupe_key LIKE 'offline-interview-%'
                                          OR n.dedupe_key LIKE 'company-interview-%'))
                        THEN 'SENT' ELSE 'NOT_SENT' END AS notification_status,
                   i.updated_at, i.interviewer_id
            FROM interview i JOIN job_application a ON a.interview_id = i.id
            JOIN job_position p ON p.id = a.position_id AND p.company_id = a.company_id
            JOIN `user` u ON u.id = a.candidate_id
            WHERE a.company_id = #{companyId} AND i.id = #{numericId}
              AND (#{restricted} = false OR i.interviewer_id = #{userId})
            """)
    CompanyInterviewRow selectAi(@Param("companyId") Long companyId, @Param("userId") Long userId,
                                 @Param("restricted") boolean restricted, @Param("numericId") Long numericId);

    @Select("""
            SELECT 'OFFLINE' AS interview_kind, CONCAT('OFFLINE-', oi.id) AS activity_id,
                   NULL AS interview_id, oi.id AS offline_interview_id, a.id AS application_id,
                   oi.company_id, a.position_id, p.name AS position_name,
                   u.id AS candidate_id, u.real_name AS candidate_name, u.email AS candidate_email, u.phone AS candidate_phone,
                   oi.interview_type AS activity_type, NULL AS raw_status, oi.status AS status,
                   oi.scheduled_at, oi.duration_minutes, oi.location, oi.meeting_url,
                   oi.contact_name, oi.contact_phone, oi.note, a.status AS application_status,
                   CASE WHEN EXISTS (SELECT 1 FROM site_notification n WHERE n.recipient_id = u.id
                                     AND n.business_type = 'JOB_APPLICATION' AND n.business_id = a.id
                                     AND (n.dedupe_key LIKE 'ai-interview-invite-%'
                                          OR n.dedupe_key LIKE 'offline-interview-%'
                                          OR n.dedupe_key LIKE 'company-interview-%'))
                        THEN 'SENT' ELSE 'NOT_SENT' END AS notification_status,
                   oi.updated_at, NULL AS interviewer_id
            FROM offline_interview oi JOIN job_application a ON a.id = oi.application_id AND a.company_id = oi.company_id
            JOIN job_position p ON p.id = a.position_id AND p.company_id = oi.company_id
            JOIN `user` u ON u.id = oi.candidate_id
            WHERE oi.company_id = #{companyId} AND oi.id = #{numericId}
            """)
    CompanyInterviewRow selectOffline(@Param("companyId") Long companyId, @Param("userId") Long userId,
                                       @Param("restricted") boolean restricted, @Param("numericId") Long numericId);
}
