package com.tyut.aiinterview.mapper;

import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface AdminAiOperationsMapper {
    @Select("""
            SELECT
              COUNT(*) AS total,
              COALESCE(SUM(CASE WHEN status = 'SUCCESS' THEN 1 ELSE 0 END), 0) AS success,
              COALESCE(SUM(CASE WHEN status = 'FAILED' THEN 1 ELSE 0 END), 0) AS failed,
              COALESCE(SUM(CASE WHEN status = 'RUNNING' THEN 1 ELSE 0 END), 0) AS running,
              CAST(COALESCE(AVG(CASE WHEN latency_ms IS NOT NULL THEN latency_ms END), 0) AS UNSIGNED) AS averageLatencyMs,
              COALESCE(SUM(total_tokens), 0) AS totalTokens
            FROM ai_generation_record
            WHERE created_at >= DATE_SUB(NOW(), INTERVAL 24 HOUR)
            """)
    AdminAiOpsGenerationSummaryRow selectGenerationSummary();

    @Select("""
            SELECT
              COALESCE(SUM(CASE WHEN status = 'PENDING' THEN 1 ELSE 0 END), 0) AS pending,
              COALESCE(SUM(CASE WHEN status = 'RUNNING' THEN 1 ELSE 0 END), 0) AS running,
              COALESCE(SUM(CASE WHEN status = 'FAILED' THEN 1 ELSE 0 END), 0) AS failed,
              COALESCE(SUM(CASE WHEN status IN ('PENDING', 'RUNNING') THEN 1 ELSE 0 END), 0) AS backlog,
              COALESCE(SUM(CASE WHEN task_type = 'AUTO_EVALUATION' AND status IN ('PENDING', 'RUNNING') THEN 1 ELSE 0 END), 0) AS reportBacklog,
              MIN(CASE WHEN status = 'PENDING' THEN scheduled_at ELSE NULL END) AS oldestPendingAt
            FROM ai_task
            """)
    AdminAiOpsTaskSummaryRow selectTaskSummary();

    @Select("""
            SELECT
              t.id AS id,
              t.task_type AS taskType,
              t.status AS status,
              t.attempts AS attempts,
              t.max_attempts AS maxAttempts,
              t.scheduled_at AS scheduledAt,
              t.started_at AS startedAt,
              t.finished_at AS finishedAt,
              t.interview_id AS interviewId,
              t.answer_id AS answerId,
              t.created_at AS createdAt,
              g.id AS generationId,
              g.request_id AS generationRequestId,
              g.provider AS provider,
              g.model AS model,
              g.prompt_code AS promptCode,
              g.prompt_version_no AS promptVersion
            FROM ai_task t
            LEFT JOIN ai_generation_record g
              ON g.task_id = t.id
             AND g.id = (SELECT MAX(g2.id) FROM ai_generation_record g2 WHERE g2.task_id = t.id)
            ORDER BY t.updated_at DESC, t.id DESC
            LIMIT #{limit}
            """)
    List<AdminAiOpsTaskRow> selectRecentTasks(@Param("limit") int limit);

    @Select("""
            SELECT
              id AS id,
              request_id AS requestId,
              task_id AS taskId,
              interview_id AS interviewId,
              free_interview_session_id AS freeInterviewSessionId,
              generation_type AS generationType,
              prompt_code AS promptCode,
              prompt_version_no AS promptVersion,
              provider AS provider,
              model AS model,
              status AS status,
              latency_ms AS latencyMs,
              input_chars AS inputChars,
              output_chars AS outputChars,
              total_tokens AS totalTokens,
              http_status AS httpStatus,
              error_message AS errorMessage,
              started_at AS startedAt,
              finished_at AS finishedAt
            FROM ai_generation_record
            ORDER BY id DESC
            LIMIT #{limit}
            """)
    List<AdminAiOpsCallRow> selectRecentCalls(@Param("limit") int limit);
}
