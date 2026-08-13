package com.tyut.aiinterview.mapper;

import java.time.LocalDateTime;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Result;
import org.apache.ibatis.annotations.ResultMap;
import org.apache.ibatis.annotations.Results;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface AdminRecruitmentMapper {
    @Select("""
            SELECT a.id, a.application_no, a.company_id, c.company_code, c.name AS company_name,
                   a.position_id, p.name AS position_name, p.department AS position_department,
                   a.candidate_id, u.username AS candidate_username, u.real_name AS candidate_real_name,
                   a.status, a.match_score, a.match_status, a.submitted_at, a.updated_at,
                   i.id AS interview_id, i.type AS interview_type, i.status AS interview_status,
                   i.scheduled_at AS interview_scheduled_at, i.started_at AS interview_started_at,
                   i.ended_at AS interview_ended_at,
                   r.status AS report_status, r.generated_at AS report_generated_at, r.published_at AS report_published_at,
                   mt.id AS match_task_id, mt.status AS match_task_status, mt.attempts AS match_task_attempts,
                   mt.max_attempts AS match_task_max_attempts, mt.scheduled_at AS match_task_scheduled_at,
                   mt.started_at AS match_task_started_at, mt.finished_at AS match_task_finished_at,
                   rt.id AS report_task_id, rt.status AS report_task_status, rt.attempts AS report_task_attempts,
                   rt.max_attempts AS report_task_max_attempts, rt.scheduled_at AS report_task_scheduled_at,
                   rt.started_at AS report_task_started_at, rt.finished_at AS report_task_finished_at
            FROM job_application a
            JOIN company c ON c.id = a.company_id AND c.deleted_at IS NULL
            JOIN job_position p ON p.id = a.position_id
            JOIN `user` u ON u.id = a.candidate_id
            LEFT JOIN interview i ON i.id = a.interview_id
            LEFT JOIN report r ON r.interview_id = a.interview_id
            LEFT JOIN ai_task mt ON mt.id = (
                SELECT t.id FROM ai_task t
                WHERE t.task_type = 'JOB_MATCH'
                  AND JSON_UNQUOTE(JSON_EXTRACT(t.input_payload, '$.applicationId')) = CAST(a.id AS CHAR)
                ORDER BY t.id DESC LIMIT 1)
            LEFT JOIN ai_task rt ON rt.id = (
                SELECT t.id FROM ai_task t
                WHERE t.task_type = 'AUTO_EVALUATION' AND t.interview_id = a.interview_id
                ORDER BY t.id DESC LIMIT 1)
            WHERE (#{companyId} IS NULL OR a.company_id = #{companyId})
              AND (#{positionId} IS NULL OR a.position_id = #{positionId})
              AND (#{status} IS NULL OR #{status} = '' OR a.status = #{status})
              AND (#{companyKeyword} IS NULL OR #{companyKeyword} = ''
                   OR c.company_code LIKE CONCAT('%', #{companyKeyword}, '%')
                   OR c.name LIKE CONCAT('%', #{companyKeyword}, '%')
                   OR c.short_name LIKE CONCAT('%', #{companyKeyword}, '%'))
              AND (#{positionKeyword} IS NULL OR #{positionKeyword} = ''
                   OR p.position_code LIKE CONCAT('%', #{positionKeyword}, '%')
                   OR p.name LIKE CONCAT('%', #{positionKeyword}, '%'))
              AND (#{keyword} IS NULL OR #{keyword} = ''
                   OR a.application_no LIKE CONCAT('%', #{keyword}, '%')
                   OR u.username LIKE CONCAT('%', #{keyword}, '%')
                   OR u.real_name LIKE CONCAT('%', #{keyword}, '%'))
              AND (#{fromTime} IS NULL OR a.submitted_at >= #{fromTime})
              AND (#{toTime} IS NULL OR a.submitted_at < #{toTime})
              AND (#{staleOnly} = 0 OR (a.updated_at < #{staleBefore} AND a.status NOT IN ('REJECTED', 'HIRED')))
            ORDER BY CASE WHEN (a.updated_at < #{staleBefore} AND a.status NOT IN ('REJECTED', 'HIRED')) THEN 0 ELSE 1 END,
                     a.updated_at ASC, a.id DESC
            LIMIT #{offset}, #{limit}
            """)
    @Results(id = "adminRecruitmentApplication", value = {
            @Result(column = "application_no", property = "applicationNo"),
            @Result(column = "company_id", property = "companyId"),
            @Result(column = "company_code", property = "companyCode"),
            @Result(column = "company_name", property = "companyName"),
            @Result(column = "position_id", property = "positionId"),
            @Result(column = "position_name", property = "positionName"),
            @Result(column = "position_department", property = "positionDepartment"),
            @Result(column = "candidate_id", property = "candidateId"),
            @Result(column = "candidate_username", property = "candidateUsername"),
            @Result(column = "candidate_real_name", property = "candidateRealName"),
            @Result(column = "match_score", property = "matchScore"),
            @Result(column = "match_status", property = "matchStatus"),
            @Result(column = "submitted_at", property = "submittedAt"),
            @Result(column = "updated_at", property = "updatedAt"),
            @Result(column = "interview_id", property = "interviewId"),
            @Result(column = "interview_type", property = "interviewType"),
            @Result(column = "interview_status", property = "interviewStatus"),
            @Result(column = "interview_scheduled_at", property = "interviewScheduledAt"),
            @Result(column = "interview_started_at", property = "interviewStartedAt"),
            @Result(column = "interview_ended_at", property = "interviewEndedAt"),
            @Result(column = "report_status", property = "reportStatus"),
            @Result(column = "report_generated_at", property = "reportGeneratedAt"),
            @Result(column = "report_published_at", property = "reportPublishedAt"),
            @Result(column = "match_task_id", property = "matchTaskId"),
            @Result(column = "match_task_status", property = "matchTaskStatus"),
            @Result(column = "match_task_attempts", property = "matchTaskAttempts"),
            @Result(column = "match_task_max_attempts", property = "matchTaskMaxAttempts"),
            @Result(column = "match_task_scheduled_at", property = "matchTaskScheduledAt"),
            @Result(column = "match_task_started_at", property = "matchTaskStartedAt"),
            @Result(column = "match_task_finished_at", property = "matchTaskFinishedAt"),
            @Result(column = "report_task_id", property = "reportTaskId"),
            @Result(column = "report_task_status", property = "reportTaskStatus"),
            @Result(column = "report_task_attempts", property = "reportTaskAttempts"),
            @Result(column = "report_task_max_attempts", property = "reportTaskMaxAttempts"),
            @Result(column = "report_task_scheduled_at", property = "reportTaskScheduledAt"),
            @Result(column = "report_task_started_at", property = "reportTaskStartedAt"),
            @Result(column = "report_task_finished_at", property = "reportTaskFinishedAt")
    })
    List<AdminRecruitmentApplicationRow> selectPage(@Param("companyId") Long companyId,
                                                     @Param("positionId") Long positionId,
                                                     @Param("status") String status,
                                                     @Param("companyKeyword") String companyKeyword,
                                                     @Param("positionKeyword") String positionKeyword,
                                                     @Param("keyword") String keyword,
                                                     @Param("fromTime") LocalDateTime fromTime,
                                                     @Param("toTime") LocalDateTime toTime,
                                                     @Param("staleOnly") int staleOnly,
                                                     @Param("staleBefore") LocalDateTime staleBefore,
                                                     @Param("offset") long offset,
                                                     @Param("limit") long limit);

    @Select("""
            SELECT a.id, a.application_no, a.company_id, c.company_code, c.name AS company_name,
                   a.position_id, p.name AS position_name, p.department AS position_department,
                   a.candidate_id, u.username AS candidate_username, u.real_name AS candidate_real_name,
                   a.status, a.match_score, a.match_status, a.submitted_at, a.updated_at,
                   i.id AS interview_id, i.type AS interview_type, i.status AS interview_status,
                   i.scheduled_at AS interview_scheduled_at, i.started_at AS interview_started_at,
                   i.ended_at AS interview_ended_at,
                   r.status AS report_status, r.generated_at AS report_generated_at, r.published_at AS report_published_at,
                   mt.id AS match_task_id, mt.status AS match_task_status, mt.attempts AS match_task_attempts,
                   mt.max_attempts AS match_task_max_attempts, mt.scheduled_at AS match_task_scheduled_at,
                   mt.started_at AS match_task_started_at, mt.finished_at AS match_task_finished_at,
                   rt.id AS report_task_id, rt.status AS report_task_status, rt.attempts AS report_task_attempts,
                   rt.max_attempts AS report_task_max_attempts, rt.scheduled_at AS report_task_scheduled_at,
                   rt.started_at AS report_task_started_at, rt.finished_at AS report_task_finished_at
            FROM job_application a
            JOIN company c ON c.id = a.company_id AND c.deleted_at IS NULL
            JOIN job_position p ON p.id = a.position_id
            JOIN `user` u ON u.id = a.candidate_id
            LEFT JOIN interview i ON i.id = a.interview_id
            LEFT JOIN report r ON r.interview_id = a.interview_id
            LEFT JOIN ai_task mt ON mt.id = (
                SELECT t.id FROM ai_task t
                WHERE t.task_type = 'JOB_MATCH'
                  AND JSON_UNQUOTE(JSON_EXTRACT(t.input_payload, '$.applicationId')) = CAST(a.id AS CHAR)
                ORDER BY t.id DESC LIMIT 1)
            LEFT JOIN ai_task rt ON rt.id = (
                SELECT t.id FROM ai_task t
                WHERE t.task_type = 'AUTO_EVALUATION' AND t.interview_id = a.interview_id
                ORDER BY t.id DESC LIMIT 1)
            WHERE a.id = #{id}
            """)
    @ResultMap("adminRecruitmentApplication")
    AdminRecruitmentApplicationRow selectById(@Param("id") Long id);

    @Select("""
            SELECT COUNT(*) FROM job_application a
            JOIN company c ON c.id = a.company_id AND c.deleted_at IS NULL
            JOIN job_position p ON p.id = a.position_id
            JOIN `user` u ON u.id = a.candidate_id
            WHERE (#{companyId} IS NULL OR a.company_id = #{companyId})
              AND (#{positionId} IS NULL OR a.position_id = #{positionId})
              AND (#{status} IS NULL OR #{status} = '' OR a.status = #{status})
              AND (#{companyKeyword} IS NULL OR #{companyKeyword} = ''
                   OR c.company_code LIKE CONCAT('%', #{companyKeyword}, '%')
                   OR c.name LIKE CONCAT('%', #{companyKeyword}, '%')
                   OR c.short_name LIKE CONCAT('%', #{companyKeyword}, '%'))
              AND (#{positionKeyword} IS NULL OR #{positionKeyword} = ''
                   OR p.position_code LIKE CONCAT('%', #{positionKeyword}, '%')
                   OR p.name LIKE CONCAT('%', #{positionKeyword}, '%'))
              AND (#{keyword} IS NULL OR #{keyword} = ''
                   OR a.application_no LIKE CONCAT('%', #{keyword}, '%')
                   OR u.username LIKE CONCAT('%', #{keyword}, '%')
                   OR u.real_name LIKE CONCAT('%', #{keyword}, '%'))
              AND (#{fromTime} IS NULL OR a.submitted_at >= #{fromTime})
              AND (#{toTime} IS NULL OR a.submitted_at < #{toTime})
              AND (#{staleOnly} = 0 OR (a.updated_at < #{staleBefore} AND a.status NOT IN ('REJECTED', 'HIRED')))
            """)
    long count(@Param("companyId") Long companyId, @Param("positionId") Long positionId,
               @Param("status") String status, @Param("companyKeyword") String companyKeyword,
               @Param("positionKeyword") String positionKeyword, @Param("keyword") String keyword,
               @Param("fromTime") LocalDateTime fromTime, @Param("toTime") LocalDateTime toTime,
               @Param("staleOnly") int staleOnly, @Param("staleBefore") LocalDateTime staleBefore);

    @Select("""
            SELECT a.status, COUNT(*) AS item_count
            FROM job_application a
            JOIN company c ON c.id = a.company_id AND c.deleted_at IS NULL
            JOIN job_position p ON p.id = a.position_id
            JOIN `user` u ON u.id = a.candidate_id
            WHERE (#{companyId} IS NULL OR a.company_id = #{companyId})
              AND (#{positionId} IS NULL OR a.position_id = #{positionId})
              AND (#{status} IS NULL OR #{status} = '' OR a.status = #{status})
              AND (#{companyKeyword} IS NULL OR #{companyKeyword} = ''
                   OR c.company_code LIKE CONCAT('%', #{companyKeyword}, '%')
                   OR c.name LIKE CONCAT('%', #{companyKeyword}, '%')
                   OR c.short_name LIKE CONCAT('%', #{companyKeyword}, '%'))
              AND (#{positionKeyword} IS NULL OR #{positionKeyword} = ''
                   OR p.position_code LIKE CONCAT('%', #{positionKeyword}, '%')
                   OR p.name LIKE CONCAT('%', #{positionKeyword}, '%'))
              AND (#{keyword} IS NULL OR #{keyword} = ''
                   OR a.application_no LIKE CONCAT('%', #{keyword}, '%')
                   OR u.username LIKE CONCAT('%', #{keyword}, '%')
                   OR u.real_name LIKE CONCAT('%', #{keyword}, '%'))
              AND (#{fromTime} IS NULL OR a.submitted_at >= #{fromTime})
              AND (#{toTime} IS NULL OR a.submitted_at < #{toTime})
              AND (#{staleOnly} = 0 OR (a.updated_at < #{staleBefore} AND a.status NOT IN ('REJECTED', 'HIRED')))
            GROUP BY a.status
            """)
    @Results({@Result(column = "item_count", property = "itemCount")})
    List<AdminRecruitmentFunnelRow> selectFunnel(@Param("companyId") Long companyId,
                                                  @Param("positionId") Long positionId,
                                                  @Param("status") String status,
                                                  @Param("companyKeyword") String companyKeyword,
                                                  @Param("positionKeyword") String positionKeyword,
                                                  @Param("keyword") String keyword,
                                                  @Param("fromTime") LocalDateTime fromTime,
                                                  @Param("toTime") LocalDateTime toTime,
                                                  @Param("staleOnly") int staleOnly,
                                                  @Param("staleBefore") LocalDateTime staleBefore);

    @Select("""
            SELECT COUNT(*) FROM job_application a
            JOIN company c ON c.id = a.company_id AND c.deleted_at IS NULL
            WHERE (#{companyId} IS NULL OR a.company_id = #{companyId})
              AND a.updated_at < #{staleBefore}
              AND a.status NOT IN ('REJECTED', 'HIRED')
            """)
    long countStale(@Param("companyId") Long companyId, @Param("staleBefore") LocalDateTime staleBefore);
}
