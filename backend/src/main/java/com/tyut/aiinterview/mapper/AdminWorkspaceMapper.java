package com.tyut.aiinterview.mapper;

import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface AdminWorkspaceMapper {

    @Select("""
            SELECT
              (SELECT COUNT(*) FROM company WHERE status = 1 AND deleted_at IS NULL) AS company_count,
              (SELECT COUNT(*) FROM user WHERE status = 1 AND deleted_at IS NULL) AS active_user_count,
              (SELECT COUNT(*) FROM job_position
               WHERE company_id IS NOT NULL AND status = 1 AND recruitment_status = 'PUBLISHED') AS recruiting_position_count,
              (SELECT COUNT(*) FROM job_application
               WHERE submitted_at >= DATE_SUB(CURRENT_DATE, INTERVAL WEEKDAY(CURRENT_DATE) DAY)
                 AND submitted_at < DATE_ADD(CURRENT_DATE, INTERVAL 1 DAY)) AS weekly_application_count,
              (SELECT COUNT(*) FROM interview WHERE status = 1) AS in_progress_interview_count,
              (SELECT COUNT(*) FROM interview WHERE status = 5) AS report_backlog_count,
              (SELECT COUNT(*) FROM ai_task WHERE status = 'FAILED') AS ai_failed_task_count,
              (SELECT COUNT(*) FROM feedback_ticket WHERE status IN ('PENDING', 'PROCESSING')) AS pending_ticket_count,
              (SELECT COUNT(*) FROM algorithm_submission WHERE status = 'QUEUED') AS algorithm_queued_count,
              (SELECT COUNT(*) FROM algorithm_submission WHERE status IN ('COMPILING', 'RUNNING')) AS algorithm_running_count,
              (SELECT MIN(created_at) FROM algorithm_submission WHERE status = 'QUEUED') AS algorithm_oldest_queued_at
            """)
    AdminWorkspaceSummaryRow selectSummary();

    @Select("""
            SELECT 'REPORT_BACKLOG' AS action_type, COUNT(*) AS item_count
            FROM interview WHERE status = 5
            UNION ALL
            SELECT 'AI_FAILED', COUNT(*)
            FROM ai_task WHERE status = 'FAILED'
            UNION ALL
            SELECT 'WORKER_QUEUE', COUNT(*)
            FROM algorithm_submission WHERE status = 'QUEUED'
            UNION ALL
            SELECT 'TICKETS', COUNT(*)
            FROM feedback_ticket WHERE status IN ('PENDING', 'PROCESSING')
            UNION ALL
            SELECT 'SERVICE_ANOMALY',
                   (SELECT COUNT(*) FROM ai_generation_record
                    WHERE status = 'FAILED' AND created_at >= DATE_SUB(NOW(), INTERVAL 24 HOUR))
                   + (SELECT COUNT(*) FROM interview
                      WHERE status = 7 AND updated_at >= DATE_SUB(NOW(), INTERVAL 24 HOUR))
            """)
    List<AdminWorkspaceActionRow> selectActions();
}
