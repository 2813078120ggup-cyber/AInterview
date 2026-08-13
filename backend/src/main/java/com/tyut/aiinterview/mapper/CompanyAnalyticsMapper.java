package com.tyut.aiinterview.mapper;

import java.time.LocalDateTime;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface CompanyAnalyticsMapper {
    @Select("""
            SELECT COUNT(*) AS application_count,
                   COUNT(DISTINCT CASE WHEN EXISTS (
                       SELECT 1 FROM job_application_status_history h
                       WHERE h.application_id = a.id
                         AND h.to_status IN ('AI_INTERVIEW_PENDING', 'AI_INTERVIEWING', 'OFFLINE_INTERVIEW')
                   ) OR a.status IN ('AI_INTERVIEW_PENDING', 'AI_INTERVIEWING', 'OFFLINE_INTERVIEW', 'HIRED')
                   THEN a.id END) AS interview_application_count,
                   SUM(CASE WHEN a.status = 'HIRED' THEN 1 ELSE 0 END) AS hired_count,
                   AVG(CASE WHEN COALESCE(
                       (SELECT MIN(h.created_at) FROM job_application_status_history h
                        WHERE h.application_id = a.id AND h.to_status = 'UNDER_REVIEW'), a.reviewed_at) IS NOT NULL
                       THEN TIMESTAMPDIFF(SECOND, a.submitted_at, COALESCE(
                       (SELECT MIN(h.created_at) FROM job_application_status_history h
                        WHERE h.application_id = a.id AND h.to_status = 'UNDER_REVIEW'), a.reviewed_at)) / 3600.0 END)
                       AS average_initial_screening_hours,
                   AVG(CASE WHEN COALESCE(
                       (SELECT MIN(h.created_at) FROM job_application_status_history h
                        WHERE h.application_id = a.id
                          AND h.to_status IN ('AI_INTERVIEW_PENDING', 'AI_INTERVIEWING', 'OFFLINE_INTERVIEW')),
                       CASE WHEN a.status IN ('AI_INTERVIEW_PENDING', 'AI_INTERVIEWING', 'OFFLINE_INTERVIEW', 'HIRED')
                            THEN a.updated_at END) IS NOT NULL
                       THEN TIMESTAMPDIFF(SECOND, a.submitted_at, COALESCE(
                       (SELECT MIN(h.created_at) FROM job_application_status_history h
                        WHERE h.application_id = a.id
                          AND h.to_status IN ('AI_INTERVIEW_PENDING', 'AI_INTERVIEWING', 'OFFLINE_INTERVIEW')),
                       CASE WHEN a.status IN ('AI_INTERVIEW_PENDING', 'AI_INTERVIEWING', 'OFFLINE_INTERVIEW', 'HIRED')
                            THEN a.updated_at END)) / 3600.0 END)
                       AS average_time_to_interview_hours,
                   AVG(CASE WHEN COALESCE(
                       (SELECT MIN(h.created_at) FROM job_application_status_history h
                        WHERE h.application_id = a.id AND h.to_status = 'HIRED'),
                       CASE WHEN a.status = 'HIRED' THEN a.updated_at END) IS NOT NULL
                       THEN TIMESTAMPDIFF(SECOND, a.submitted_at, COALESCE(
                       (SELECT MIN(h.created_at) FROM job_application_status_history h
                        WHERE h.application_id = a.id AND h.to_status = 'HIRED'),
                       CASE WHEN a.status = 'HIRED' THEN a.updated_at END)) / 24.0 / 3600.0 END)
                       AS average_hiring_cycle_days
            FROM job_application a
            WHERE a.company_id = #{companyId}
              AND a.submitted_at >= #{fromAt}
              AND a.submitted_at < #{toExclusive}
            """)
    CompanyAnalyticsSummaryRow selectSummary(@Param("companyId") Long companyId,
                                              @Param("fromAt") LocalDateTime fromAt,
                                              @Param("toExclusive") LocalDateTime toExclusive);

    @Select("""
            SELECT 'SUBMITTED' AS status, COUNT(DISTINCT a.id) AS item_count
            FROM job_application a
            WHERE a.company_id = #{companyId} AND a.submitted_at >= #{fromAt} AND a.submitted_at < #{toExclusive}
            UNION ALL
            SELECT 'AI_INTERVIEW_PENDING', COUNT(DISTINCT a.id)
            FROM job_application a
            WHERE a.company_id = #{companyId} AND a.submitted_at >= #{fromAt} AND a.submitted_at < #{toExclusive}
              AND (a.status = 'AI_INTERVIEW_PENDING' OR EXISTS (SELECT 1 FROM job_application_status_history h WHERE h.application_id = a.id AND h.to_status = 'AI_INTERVIEW_PENDING'))
            UNION ALL
            SELECT 'AI_INTERVIEWING', COUNT(DISTINCT a.id)
            FROM job_application a
            WHERE a.company_id = #{companyId} AND a.submitted_at >= #{fromAt} AND a.submitted_at < #{toExclusive}
              AND (a.status = 'AI_INTERVIEWING' OR EXISTS (SELECT 1 FROM job_application_status_history h WHERE h.application_id = a.id AND h.to_status = 'AI_INTERVIEWING'))
            UNION ALL
            SELECT 'UNDER_REVIEW', COUNT(DISTINCT a.id)
            FROM job_application a
            WHERE a.company_id = #{companyId} AND a.submitted_at >= #{fromAt} AND a.submitted_at < #{toExclusive}
              AND (a.status = 'UNDER_REVIEW' OR EXISTS (SELECT 1 FROM job_application_status_history h WHERE h.application_id = a.id AND h.to_status = 'UNDER_REVIEW'))
            UNION ALL
            SELECT 'OFFLINE_INTERVIEW', COUNT(DISTINCT a.id)
            FROM job_application a
            WHERE a.company_id = #{companyId} AND a.submitted_at >= #{fromAt} AND a.submitted_at < #{toExclusive}
              AND (a.status = 'OFFLINE_INTERVIEW' OR EXISTS (SELECT 1 FROM job_application_status_history h WHERE h.application_id = a.id AND h.to_status = 'OFFLINE_INTERVIEW'))
            UNION ALL
            SELECT 'REJECTED', COUNT(DISTINCT a.id)
            FROM job_application a
            WHERE a.company_id = #{companyId} AND a.submitted_at >= #{fromAt} AND a.submitted_at < #{toExclusive}
              AND (a.status = 'REJECTED' OR EXISTS (SELECT 1 FROM job_application_status_history h WHERE h.application_id = a.id AND h.to_status = 'REJECTED'))
            UNION ALL
            SELECT 'HIRED', COUNT(DISTINCT a.id)
            FROM job_application a
            WHERE a.company_id = #{companyId} AND a.submitted_at >= #{fromAt} AND a.submitted_at < #{toExclusive}
              AND (a.status = 'HIRED' OR EXISTS (SELECT 1 FROM job_application_status_history h WHERE h.application_id = a.id AND h.to_status = 'HIRED'))
            """)
    List<CompanyAnalyticsFunnelRow> selectFunnel(@Param("companyId") Long companyId,
                                                @Param("fromAt") LocalDateTime fromAt,
                                                @Param("toExclusive") LocalDateTime toExclusive);

    @Select("""
            SELECT bucket_key, bucket_label, COUNT(*) AS item_count
            FROM (
                SELECT CASE
                    WHEN a.match_score < 60 THEN '0_59'
                    WHEN a.match_score < 70 THEN '60_69'
                    WHEN a.match_score < 80 THEN '70_79'
                    WHEN a.match_score < 90 THEN '80_89'
                    ELSE '90_100' END AS bucket_key,
                    CASE
                    WHEN a.match_score < 60 THEN '0–59'
                    WHEN a.match_score < 70 THEN '60–69'
                    WHEN a.match_score < 80 THEN '70–79'
                    WHEN a.match_score < 90 THEN '80–89'
                    ELSE '90–100' END AS bucket_label
                FROM job_application a
                WHERE a.company_id = #{companyId}
                  AND a.submitted_at >= #{fromAt} AND a.submitted_at < #{toExclusive}
                  AND a.match_score IS NOT NULL
            ) scored
            GROUP BY bucket_key, bucket_label
            ORDER BY FIELD(bucket_key, '0_59', '60_69', '70_79', '80_89', '90_100')
            """)
    List<CompanyAnalyticsScoreBucketRow> selectScoreBuckets(@Param("companyId") Long companyId,
                                                             @Param("fromAt") LocalDateTime fromAt,
                                                             @Param("toExclusive") LocalDateTime toExclusive);

    @Select("""
            SELECT p.id AS position_id, p.name AS position_name, p.recruitment_status,
                   COUNT(a.id) AS application_count, AVG(a.match_score) AS average_match_score,
                   COUNT(DISTINCT CASE WHEN EXISTS (
                       SELECT 1 FROM job_application_status_history h
                       WHERE h.application_id = a.id
                         AND h.to_status IN ('AI_INTERVIEW_PENDING', 'AI_INTERVIEWING', 'OFFLINE_INTERVIEW')
                   ) OR a.status IN ('AI_INTERVIEW_PENDING', 'AI_INTERVIEWING', 'OFFLINE_INTERVIEW', 'HIRED')
                   THEN a.id END) AS interview_count,
                   COUNT(DISTINCT CASE WHEN a.status = 'HIRED' OR EXISTS (
                       SELECT 1 FROM job_application_status_history h
                       WHERE h.application_id = a.id AND h.to_status = 'HIRED'
                   ) THEN a.id END) AS hired_count
            FROM job_position p
            LEFT JOIN job_application a ON a.position_id = p.id AND a.company_id = #{companyId}
                AND a.submitted_at >= #{fromAt} AND a.submitted_at < #{toExclusive}
            WHERE p.company_id = #{companyId} AND p.status = 1
            GROUP BY p.id, p.name, p.recruitment_status, p.updated_at
            ORDER BY application_count DESC, hired_count DESC, p.updated_at DESC, p.id
            LIMIT #{offset}, #{limit}
            """)
    List<CompanyAnalyticsPositionRow> selectPositionPage(@Param("companyId") Long companyId,
                                                          @Param("fromAt") LocalDateTime fromAt,
                                                          @Param("toExclusive") LocalDateTime toExclusive,
                                                          @Param("offset") long offset,
                                                          @Param("limit") long limit);

    @Select("""
            SELECT COUNT(*) FROM job_position p
            WHERE p.company_id = #{companyId} AND p.status = 1
            """)
    long countPositions(@Param("companyId") Long companyId);
}
