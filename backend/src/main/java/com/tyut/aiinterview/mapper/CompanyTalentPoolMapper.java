package com.tyut.aiinterview.mapper;

import java.time.LocalDateTime;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface CompanyTalentPoolMapper {
    @Select("""
            <script>
            SELECT cc.id AS pool_id, cc.candidate_id,
                   CASE WHEN u.deleted_at IS NULL AND u.status = 1 THEN COALESCE(u.real_name, '候选人') ELSE '候选人（已停用）' END AS candidate_name,
                   CASE WHEN u.deleted_at IS NULL AND u.status = 1 THEN u.email ELSE NULL END AS email,
                   CASE WHEN u.deleted_at IS NULL AND u.status = 1 THEN u.phone ELSE NULL END AS phone,
                   u.status AS candidate_status, cc.status AS pool_status,
                   cc.last_contacted_at, cc.created_at AS added_at, cc.updated_at,
                   (SELECT COUNT(*) FROM application_note n WHERE n.company_id = cc.company_id AND n.company_candidate_id = cc.id) AS note_count,
                   (SELECT COUNT(*) FROM job_application a WHERE a.company_id = cc.company_id AND a.candidate_id = cc.candidate_id) AS application_count,
                   (SELECT MAX(a.submitted_at) FROM job_application a WHERE a.company_id = cc.company_id AND a.candidate_id = cc.candidate_id) AS last_application_at,
                   COALESCE((SELECT MAX(a.updated_at) FROM job_application a WHERE a.company_id = cc.company_id AND a.candidate_id = cc.candidate_id), cc.updated_at) AS last_activity_at,
                   COALESCE((SELECT GROUP_CONCAT(CONCAT(t.id, '::', t.name) ORDER BY t.name SEPARATOR '||')
                             FROM company_candidate_tag_relation tr
                             JOIN company_candidate_tag t ON t.id = tr.tag_id AND t.company_id = tr.company_id AND t.status = 1
                             WHERE tr.company_id = cc.company_id AND tr.company_candidate_id = cc.id AND tr.status = 1), '') AS tag_summary
            FROM company_candidate cc
            JOIN `user` u ON u.id = cc.candidate_id
            WHERE cc.company_id = #{companyId} AND cc.status = 'ACTIVE'
            <if test="candidateId != null"> AND cc.candidate_id = #{candidateId}</if>
            <if test="keyword != null and keyword != ''">
              AND (u.real_name LIKE CONCAT('%', #{keyword}, '%')
                   OR u.email LIKE CONCAT('%', #{keyword}, '%')
                   OR u.phone LIKE CONCAT('%', #{keyword}, '%')
                   OR EXISTS (SELECT 1 FROM job_application ak JOIN job_position pk ON pk.id = ak.position_id
                              WHERE ak.company_id = cc.company_id AND ak.candidate_id = cc.candidate_id
                                AND pk.name LIKE CONCAT('%', #{keyword}, '%')))
            </if>
            <if test="tagId != null">
              AND EXISTS (SELECT 1 FROM company_candidate_tag_relation trf
                          WHERE trf.company_id = cc.company_id AND trf.company_candidate_id = cc.id
                            AND trf.tag_id = #{tagId} AND trf.status = 1)
            </if>
            <if test="skill != null and skill != ''">
              AND EXISTS (SELECT 1 FROM job_application aks JOIN candidate_resume rks ON rks.id = aks.resume_id
                          WHERE aks.company_id = cc.company_id AND aks.candidate_id = cc.candidate_id
                            AND rks.status = 1 AND CAST(rks.skills AS CHAR) LIKE CONCAT('%', #{skill}, '%'))
            </if>
            <if test="positionId != null">
              AND EXISTS (SELECT 1 FROM job_application apf
                          WHERE apf.company_id = cc.company_id AND apf.candidate_id = cc.candidate_id
                            AND apf.position_id = #{positionId})
            </if>
            <if test="lastContactFrom != null"> AND (cc.last_contacted_at IS NOT NULL AND cc.last_contacted_at &gt;= #{lastContactFrom})</if>
            <if test="lastContactTo != null"> AND cc.last_contacted_at &lt;= #{lastContactTo}</if>
            <if test="restricted">
              AND EXISTS (SELECT 1 FROM job_application ar JOIN interview ir ON ir.id = ar.interview_id
                          WHERE ar.company_id = cc.company_id AND ar.candidate_id = cc.candidate_id
                            AND ir.interviewer_id = #{userId})
            </if>
            <choose>
              <when test="sort == 'LAST_CONTACTED'"> ORDER BY cc.last_contacted_at DESC, cc.updated_at DESC, cc.id DESC</when>
              <when test="sort == 'NAME'"> ORDER BY u.real_name ASC, cc.id DESC</when>
              <when test="sort == 'APPLICATIONS'"> ORDER BY application_count DESC, cc.updated_at DESC, cc.id DESC</when>
              <otherwise> ORDER BY cc.updated_at DESC, cc.id DESC</otherwise>
            </choose>
            LIMIT #{offset}, #{pageSize}
            </script>
            """)
    List<CompanyTalentPoolRow> selectPage(@Param("companyId") Long companyId,
                                          @Param("userId") Long userId,
                                          @Param("restricted") boolean restricted,
                                          @Param("candidateId") Long candidateId,
                                          @Param("keyword") String keyword,
                                          @Param("tagId") Long tagId,
                                          @Param("skill") String skill,
                                          @Param("positionId") Long positionId,
                                          @Param("lastContactFrom") LocalDateTime lastContactFrom,
                                          @Param("lastContactTo") LocalDateTime lastContactTo,
                                          @Param("sort") String sort,
                                          @Param("offset") int offset,
                                          @Param("pageSize") int pageSize);

    @Select("""
            <script>
            SELECT COUNT(*)
            FROM company_candidate cc
            JOIN `user` u ON u.id = cc.candidate_id
            WHERE cc.company_id = #{companyId} AND cc.status = 'ACTIVE'
            <if test="candidateId != null"> AND cc.candidate_id = #{candidateId}</if>
            <if test="keyword != null and keyword != ''">
              AND (u.real_name LIKE CONCAT('%', #{keyword}, '%')
                   OR u.email LIKE CONCAT('%', #{keyword}, '%')
                   OR u.phone LIKE CONCAT('%', #{keyword}, '%')
                   OR EXISTS (SELECT 1 FROM job_application ak JOIN job_position pk ON pk.id = ak.position_id
                              WHERE ak.company_id = cc.company_id AND ak.candidate_id = cc.candidate_id
                                AND pk.name LIKE CONCAT('%', #{keyword}, '%')))
            </if>
            <if test="tagId != null"> AND EXISTS (SELECT 1 FROM company_candidate_tag_relation trf WHERE trf.company_id = cc.company_id AND trf.company_candidate_id = cc.id AND trf.tag_id = #{tagId} AND trf.status = 1)</if>
            <if test="skill != null and skill != ''"> AND EXISTS (SELECT 1 FROM job_application aks JOIN candidate_resume rks ON rks.id = aks.resume_id WHERE aks.company_id = cc.company_id AND aks.candidate_id = cc.candidate_id AND rks.status = 1 AND CAST(rks.skills AS CHAR) LIKE CONCAT('%', #{skill}, '%'))</if>
            <if test="positionId != null"> AND EXISTS (SELECT 1 FROM job_application apf WHERE apf.company_id = cc.company_id AND apf.candidate_id = cc.candidate_id AND apf.position_id = #{positionId})</if>
            <if test="lastContactFrom != null"> AND (cc.last_contacted_at IS NOT NULL AND cc.last_contacted_at &gt;= #{lastContactFrom})</if>
            <if test="lastContactTo != null"> AND cc.last_contacted_at &lt;= #{lastContactTo}</if>
            <if test="restricted"> AND EXISTS (SELECT 1 FROM job_application ar JOIN interview ir ON ir.id = ar.interview_id WHERE ar.company_id = cc.company_id AND ar.candidate_id = cc.candidate_id AND ir.interviewer_id = #{userId})</if>
            </script>
            """)
    long count(@Param("companyId") Long companyId,
               @Param("userId") Long userId,
               @Param("restricted") boolean restricted,
               @Param("candidateId") Long candidateId,
               @Param("keyword") String keyword,
               @Param("tagId") Long tagId,
               @Param("skill") String skill,
               @Param("positionId") Long positionId,
               @Param("lastContactFrom") LocalDateTime lastContactFrom,
               @Param("lastContactTo") LocalDateTime lastContactTo);
}
