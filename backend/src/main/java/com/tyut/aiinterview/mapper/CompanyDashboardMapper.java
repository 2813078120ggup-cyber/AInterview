package com.tyut.aiinterview.mapper;

import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface CompanyDashboardMapper {

    @Select("""
            SELECT c.id AS company_id, c.name AS company_name, c.short_name AS company_short_name, c.city,
                   (SELECT COUNT(*) FROM job_position p
                    WHERE p.company_id = c.id AND p.status = 1 AND p.recruitment_status = 'PUBLISHED') AS published_positions,
                   (SELECT COUNT(*) FROM job_position p
                    WHERE p.company_id = c.id AND p.status = 1 AND p.recruitment_status = 'DRAFT') AS draft_positions,
                   (SELECT COUNT(*) FROM job_application a WHERE a.company_id = c.id) AS total_applications,
                   (SELECT COUNT(*) FROM job_application a
                    WHERE a.company_id = c.id AND a.status IN ('SUBMITTED', 'AI_INTERVIEW_PENDING', 'AI_INTERVIEWING', 'UNDER_REVIEW', 'OFFLINE_INTERVIEW')) AS pending_applications,
                   ((SELECT COUNT(*) FROM offline_interview oi
                     WHERE oi.company_id = c.id AND oi.status = 'SCHEDULED'
                       AND oi.scheduled_at >= CURRENT_DATE AND oi.scheduled_at < DATE_ADD(CURRENT_DATE, INTERVAL 1 DAY))
                    + (SELECT COUNT(*) FROM interview i
                       INNER JOIN job_application a ON a.interview_id = i.id AND a.company_id = c.id
                       WHERE i.status <> 3 AND i.scheduled_at >= CURRENT_DATE
                         AND i.scheduled_at < DATE_ADD(CURRENT_DATE, INTERVAL 1 DAY))) AS today_interviews,
                   ((SELECT COUNT(*) FROM job_application a
                     WHERE a.company_id = c.id AND a.match_status = 'FAILED')
                    + (SELECT COUNT(*) FROM job_application a
                       INNER JOIN interview i ON i.id = a.interview_id
                       WHERE a.company_id = c.id AND i.status = 5
                         AND i.updated_at < DATE_SUB(NOW(), INTERVAL 30 MINUTE))
                    + (SELECT COUNT(*) FROM offline_interview oi
                       WHERE oi.company_id = c.id AND oi.status = 'SCHEDULED' AND oi.scheduled_at < NOW())) AS overdue_items,
                   (SELECT COUNT(*) FROM job_application a WHERE a.company_id = c.id AND a.status = 'HIRED') AS hired_applications,
                   (SELECT AVG(a.match_score) FROM job_application a
                    WHERE a.company_id = c.id AND a.match_score IS NOT NULL) AS average_match_score,
                   GREATEST(c.updated_at,
                            COALESCE((SELECT MAX(p.updated_at) FROM job_position p WHERE p.company_id = c.id), c.updated_at),
                            COALESCE((SELECT MAX(a.updated_at) FROM job_application a WHERE a.company_id = c.id), c.updated_at),
                            COALESCE((SELECT MAX(oi.updated_at) FROM offline_interview oi WHERE oi.company_id = c.id), c.updated_at)) AS last_updated_at
            FROM company c
            WHERE c.id = #{companyId} AND c.status = 1
            """)
    CompanyDashboardSummaryRow selectSummary(@Param("companyId") Long companyId);

    @Select("""
            SELECT action_type AS actionType, COUNT(*) AS itemCount
            FROM (
                SELECT 'NEW_APPLICATION' AS action_type
                FROM job_application a
                WHERE a.company_id = #{companyId} AND a.status = 'SUBMITTED'
                UNION ALL
                SELECT 'MATCH_FAILED'
                FROM job_application a
                WHERE a.company_id = #{companyId} AND a.match_status = 'FAILED'
                UNION ALL
                SELECT 'AI_INTERVIEW_REVIEW'
                FROM job_application a
                INNER JOIN interview i ON i.id = a.interview_id
                WHERE a.company_id = #{companyId} AND a.status = 'UNDER_REVIEW' AND i.status IN (2, 4, 6)
                UNION ALL
                SELECT 'REPORT_TIMEOUT'
                FROM job_application a
                INNER JOIN interview i ON i.id = a.interview_id
                WHERE a.company_id = #{companyId} AND i.status = 5
                  AND i.updated_at < DATE_SUB(NOW(), INTERVAL 30 MINUTE)
                UNION ALL
                SELECT 'OFFLINE_CONFIRMATION'
                FROM offline_interview oi
                WHERE oi.company_id = #{companyId} AND oi.status = 'SCHEDULED'
            ) actions
            GROUP BY action_type
            """)
    List<CompanyDashboardActionCountRow> selectActionCounts(@Param("companyId") Long companyId);

    @Select("""
            SELECT action_type AS actionType, application_id AS applicationId, interview_id AS interviewId,
                   candidate_name AS candidateName, position_name AS positionName, status, match_status AS matchStatus,
                   due_at AS dueAt, created_at AS createdAt, priority
            FROM (
                SELECT 'NEW_APPLICATION' AS action_type, a.id AS application_id, NULL AS interview_id,
                       u.real_name AS candidate_name, p.name AS position_name, a.status, a.match_status,
                       a.submitted_at AS due_at, a.updated_at AS created_at, 1 AS priority
                FROM job_application a
                INNER JOIN user u ON u.id = a.candidate_id
                INNER JOIN job_position p ON p.id = a.position_id AND p.company_id = #{companyId}
                WHERE a.company_id = #{companyId} AND a.status = 'SUBMITTED'
                UNION ALL
                SELECT 'MATCH_FAILED', a.id, NULL, u.real_name, p.name, a.status, a.match_status,
                       a.updated_at, a.updated_at, 2
                FROM job_application a
                INNER JOIN user u ON u.id = a.candidate_id
                INNER JOIN job_position p ON p.id = a.position_id AND p.company_id = #{companyId}
                WHERE a.company_id = #{companyId} AND a.match_status = 'FAILED'
                UNION ALL
                SELECT 'AI_INTERVIEW_REVIEW', a.id, i.id, u.real_name, p.name, a.status, a.match_status,
                       i.updated_at, i.updated_at, 3
                FROM job_application a
                INNER JOIN interview i ON i.id = a.interview_id
                INNER JOIN user u ON u.id = a.candidate_id
                INNER JOIN job_position p ON p.id = a.position_id AND p.company_id = #{companyId}
                WHERE a.company_id = #{companyId} AND a.status = 'UNDER_REVIEW' AND i.status IN (2, 4, 6)
                UNION ALL
                SELECT 'REPORT_TIMEOUT', a.id, i.id, u.real_name, p.name, a.status, a.match_status,
                       i.updated_at, i.updated_at, 4
                FROM job_application a
                INNER JOIN interview i ON i.id = a.interview_id
                INNER JOIN user u ON u.id = a.candidate_id
                INNER JOIN job_position p ON p.id = a.position_id AND p.company_id = #{companyId}
                WHERE a.company_id = #{companyId} AND i.status = 5
                  AND i.updated_at < DATE_SUB(NOW(), INTERVAL 30 MINUTE)
                UNION ALL
                SELECT 'OFFLINE_CONFIRMATION', oi.application_id, NULL, u.real_name, p.name, a.status, a.match_status,
                       oi.scheduled_at, oi.updated_at, 5
                FROM offline_interview oi
                INNER JOIN job_application a ON a.id = oi.application_id AND a.company_id = #{companyId}
                INNER JOIN user u ON u.id = oi.candidate_id
                INNER JOIN job_position p ON p.id = a.position_id AND p.company_id = #{companyId}
                WHERE oi.company_id = #{companyId} AND oi.status = 'SCHEDULED'
            ) actions
            ORDER BY priority, due_at, created_at, application_id
            LIMIT #{limit}
            """)
    List<CompanyDashboardActionRow> selectActionItems(@Param("companyId") Long companyId, @Param("limit") int limit);

    @Select("""
            SELECT source, interview_id AS interviewId, application_id AS applicationId,
                   candidate_name AS candidateName, position_name AS positionName,
                   scheduled_at AS scheduledAt, duration_minutes AS durationMinutes, status, location
            FROM (
                SELECT 'AI' AS source, i.id AS interview_id, a.id AS application_id,
                       u.real_name AS candidate_name, p.name AS position_name,
                       i.scheduled_at, i.duration AS duration_minutes, CAST(i.status AS CHAR) AS status,
                       NULL AS location
                FROM interview i
                INNER JOIN job_application a ON a.interview_id = i.id AND a.company_id = #{companyId}
                INNER JOIN user u ON u.id = a.candidate_id
                INNER JOIN job_position p ON p.id = a.position_id AND p.company_id = #{companyId}
                WHERE a.company_id = #{companyId} AND i.status <> 3
                  AND i.scheduled_at >= CURRENT_DATE AND i.scheduled_at < DATE_ADD(CURRENT_DATE, INTERVAL 8 DAY)
                UNION ALL
                SELECT 'OFFLINE', oi.id, oi.application_id, u.real_name, p.name,
                       oi.scheduled_at, oi.duration_minutes, oi.status, COALESCE(oi.location, oi.meeting_url)
                FROM offline_interview oi
                INNER JOIN job_application a ON a.id = oi.application_id AND a.company_id = #{companyId}
                INNER JOIN user u ON u.id = oi.candidate_id
                INNER JOIN job_position p ON p.id = a.position_id AND p.company_id = #{companyId}
                WHERE oi.company_id = #{companyId} AND oi.status <> 'CANCELLED'
                  AND oi.scheduled_at >= CURRENT_DATE AND oi.scheduled_at < DATE_ADD(CURRENT_DATE, INTERVAL 8 DAY)
            ) interviews
            ORDER BY scheduled_at, application_id
            LIMIT #{limit}
            """)
    List<CompanyUpcomingInterviewRow> selectUpcomingInterviews(@Param("companyId") Long companyId, @Param("limit") int limit);

    @Select("""
            SELECT a.status, COUNT(*) AS itemCount
            FROM job_application a
            WHERE a.company_id = #{companyId}
            GROUP BY a.status
            """)
    List<CompanyFunnelRow> selectFunnel(@Param("companyId") Long companyId);

    @Select("""
            SELECT p.id AS positionId, p.name AS positionName, p.recruitment_status AS recruitmentStatus,
                   COUNT(a.id) AS applicationCount,
                   SUM(CASE WHEN a.status IN ('SUBMITTED', 'AI_INTERVIEW_PENDING', 'AI_INTERVIEWING', 'UNDER_REVIEW', 'OFFLINE_INTERVIEW') THEN 1 ELSE 0 END) AS pendingCount,
                   SUM(CASE WHEN a.status = 'HIRED' THEN 1 ELSE 0 END) AS hiredCount,
                   AVG(a.match_score) AS averageMatchScore
            FROM job_position p
            LEFT JOIN job_application a ON a.position_id = p.id AND a.company_id = #{companyId}
            WHERE p.company_id = #{companyId} AND p.status = 1
            GROUP BY p.id, p.name, p.recruitment_status, p.updated_at
            ORDER BY applicationCount DESC, hiredCount DESC, p.updated_at DESC, p.id
            LIMIT #{limit}
            """)
    List<CompanyPositionAnalyticsRow> selectPositionAnalytics(@Param("companyId") Long companyId, @Param("limit") int limit);
}
