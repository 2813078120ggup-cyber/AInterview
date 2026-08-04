package com.tyut.aiinterview.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.tyut.aiinterview.domain.AlgorithmSubmission;
import java.time.LocalDate;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface AlgorithmSubmissionMapper extends BaseMapper<AlgorithmSubmission> {

    @Select("""
            SELECT DISTINCT DATE(created_at) FROM algorithm_submission
            WHERE user_id = #{userId} AND submit_type = 'SUBMIT' AND status = 'ACCEPTED'
            ORDER BY 1 DESC
            """)
    List<LocalDate> selectAcceptedDates(@Param("userId") Long userId);

    @Select("""
            SELECT s.id, s.problem_id AS problem_id, s.language, s.status, s.submit_type,
                   s.passed_count, s.total_count, s.execution_time_ms, s.created_at,
                   p.title AS problem_title
            FROM algorithm_submission s
            JOIN algorithm_problem p ON p.id = s.problem_id
            WHERE s.user_id = #{userId}
            ORDER BY s.id DESC
            LIMIT #{limit}
            """)
    List<RecentSubmissionRow> selectRecent(@Param("userId") Long userId, @Param("limit") int limit);

    @Select("""
            SELECT p.id, p.title, p.slug, p.difficulty,
                   (SELECT COUNT(*) FROM algorithm_submission s2
                     WHERE s2.problem_id = p.id AND s2.user_id = #{userId} AND s2.submit_type = 'SUBMIT') AS my_submit_count,
                   IF(EXISTS (SELECT 1 FROM algorithm_favorite f
                     WHERE f.problem_id = p.id AND f.user_id = #{userId}), 1, 0) AS favorited,
                   IF(EXISTS (SELECT 1 FROM algorithm_note n
                     WHERE n.problem_id = p.id AND n.user_id = #{userId}), 1, 0) AS has_note
            FROM algorithm_problem p
            WHERE p.status = 1
              AND EXISTS (SELECT 1 FROM algorithm_submission s
                WHERE s.problem_id = p.id AND s.user_id = #{userId} AND s.submit_type = 'SUBMIT')
              AND NOT EXISTS (SELECT 1 FROM algorithm_submission a
                WHERE a.problem_id = p.id AND a.user_id = #{userId} AND a.status = 'ACCEPTED')
            ORDER BY p.sort_no, p.id
            """)
    List<WrongProblemRow> selectWrongProblems(@Param("userId") Long userId);
}
