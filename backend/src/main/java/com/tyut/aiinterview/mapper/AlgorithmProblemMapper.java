package com.tyut.aiinterview.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.tyut.aiinterview.domain.AlgorithmProblem;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface AlgorithmProblemMapper extends BaseMapper<AlgorithmProblem> {

    @Select("""
            <script>
            SELECT p.id, p.title, p.slug, p.difficulty,
                   up.progress_status AS progress_status,
                   COALESCE(up.submit_count, 0) AS my_submit_count
            FROM algorithm_problem p
            LEFT JOIN algorithm_user_progress up ON up.problem_id = p.id AND up.user_id = #{userId}
            WHERE p.status = 1
            <if test="keyword != null and keyword != ''"> AND p.title LIKE CONCAT('%', #{keyword}, '%')</if>
            <if test="difficulty != null and difficulty != ''"> AND p.difficulty = #{difficulty}</if>
            <if test="tagId != null"> AND EXISTS (SELECT 1 FROM algorithm_problem_tag pt
                WHERE pt.problem_id = p.id AND pt.tag_id = #{tagId})</if>
            <if test="progressStatus == 'ATTEMPTED'"> AND up.progress_status = 'ATTEMPTED'</if>
            <if test="progressStatus == 'ACCEPTED'"> AND up.progress_status = 'ACCEPTED'</if>
            <if test="progressStatus == 'NOT_STARTED'"> AND up.id IS NULL</if>
            ORDER BY p.sort_no, p.id
            LIMIT #{offset}, #{pageSize}
            </script>
            """)
    List<ProblemStatRow> selectProblemPage(@Param("userId") Long userId,
                                           @Param("keyword") String keyword,
                                           @Param("difficulty") String difficulty,
                                           @Param("tagId") Long tagId,
                                           @Param("progressStatus") String progressStatus,
                                           @Param("offset") int offset,
                                           @Param("pageSize") int pageSize);

    @Select("""
            <script>
            SELECT COUNT(*) FROM (
              SELECT p.id
              FROM algorithm_problem p
              LEFT JOIN algorithm_user_progress up ON up.problem_id = p.id AND up.user_id = #{userId}
              WHERE p.status = 1
              <if test="keyword != null and keyword != ''"> AND p.title LIKE CONCAT('%', #{keyword}, '%')</if>
              <if test="difficulty != null and difficulty != ''"> AND p.difficulty = #{difficulty}</if>
              <if test="tagId != null"> AND EXISTS (SELECT 1 FROM algorithm_problem_tag pt
                  WHERE pt.problem_id = p.id AND pt.tag_id = #{tagId})</if>
              <if test="progressStatus == 'ATTEMPTED'"> AND up.progress_status = 'ATTEMPTED'</if>
              <if test="progressStatus == 'ACCEPTED'"> AND up.progress_status = 'ACCEPTED'</if>
              <if test="progressStatus == 'NOT_STARTED'"> AND up.id IS NULL</if>
              GROUP BY p.id
            ) t
            </script>
            """)
    long countProblemPage(@Param("userId") Long userId,
                          @Param("keyword") String keyword,
                          @Param("difficulty") String difficulty,
                          @Param("tagId") Long tagId,
                          @Param("progressStatus") String progressStatus);

    /**
     * 批量统计题目提交数/通过数：一次 GROUP BY 替代逐行相关子查询。
     */
    @Select("""
            <script>
            SELECT problem_id AS id,
                   COUNT(DISTINCT id) AS submission_count,
                   SUM(CASE WHEN status = 'ACCEPTED' THEN 1 ELSE 0 END) AS accepted_count
            FROM algorithm_submission
            WHERE submit_type = 'SUBMIT'
              AND problem_id IN
              <foreach collection="problemIds" item="problemId" open="(" separator="," close=")">#{problemId}</foreach>
            GROUP BY problem_id
            </script>
            """)
    List<ProblemStatRow> selectSubmissionCounts(@Param("problemIds") List<Long> problemIds);

    @Select("""
            SELECT p.id, p.title, p.slug, p.difficulty, p.status, p.sort_no, p.created_at,
                   (SELECT COUNT(DISTINCT s.id) FROM algorithm_submission s
                     WHERE s.problem_id = p.id AND s.submit_type = 'SUBMIT') AS submission_count,
                   (SELECT COUNT(DISTINCT s.id) FROM algorithm_submission s
                     WHERE s.problem_id = p.id AND s.submit_type = 'SUBMIT' AND s.status = 'ACCEPTED') AS accepted_count
            FROM algorithm_problem p
            ORDER BY p.sort_no, p.id
            """)
    List<AdminProblemStatRow> selectAdminStats();

    @Select("""
            SELECT p.id, p.title, p.slug, p.difficulty,
                   COUNT(DISTINCT s.id) AS submission_count,
                   COUNT(DISTINCT CASE WHEN s.status = 'ACCEPTED' THEN s.id END) AS accepted_count
            FROM algorithm_problem p
            LEFT JOIN algorithm_submission s ON s.problem_id = p.id AND s.submit_type = 'SUBMIT'
            WHERE p.status = 1
            GROUP BY p.id, p.title, p.slug, p.difficulty
            ORDER BY submission_count DESC, p.id
            LIMIT #{limit}
            """)
    List<ProblemStatRow> selectHotProblems(@Param("limit") int limit);

    @Select("""
            SELECT p.id, p.title, p.slug, p.difficulty,
                   (SELECT COUNT(DISTINCT s.id) FROM algorithm_submission s
                     WHERE s.problem_id = p.id AND s.submit_type = 'SUBMIT') AS submission_count,
                   (SELECT COUNT(DISTINCT s.id) FROM algorithm_submission s
                     WHERE s.problem_id = p.id AND s.submit_type = 'SUBMIT' AND s.status = 'ACCEPTED') AS accepted_count
            FROM algorithm_problem p
            WHERE p.status = 1
              AND NOT EXISTS (SELECT 1 FROM algorithm_user_progress up
                WHERE up.problem_id = p.id AND up.user_id = #{userId} AND up.progress_status = 'ACCEPTED')
            ORDER BY p.sort_no, p.id
            LIMIT #{limit}
            """)
    List<ProblemStatRow> selectRecommended(@Param("userId") Long userId, @Param("limit") int limit);
}
