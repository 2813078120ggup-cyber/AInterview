package com.tyut.aiinterview.mapper;

import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Result;
import org.apache.ibatis.annotations.Results;
import org.apache.ibatis.annotations.Select;
import lombok.Data;

@Mapper
public interface AdminUserMapper {
    @Select("""
            <script>
            SELECT u.id, u.username, u.real_name, u.email, u.phone, u.avatar_url, u.company_id,
                   c.name AS company_name, u.status, u.last_login_at, u.created_at, u.updated_at
            FROM `user` u
            LEFT JOIN company c ON c.id = u.company_id AND c.deleted_at IS NULL
            WHERE u.deleted_at IS NULL
              <if test="keyword != null and keyword != ''">
                AND (u.username LIKE CONCAT('%', #{keyword}, '%')
                  OR u.real_name LIKE CONCAT('%', #{keyword}, '%')
                  OR u.email LIKE CONCAT('%', #{keyword}, '%'))
              </if>
              <if test="roleCode != null and roleCode != ''">
                AND EXISTS (SELECT 1 FROM user_role ur JOIN `role` r ON r.id = ur.role_id
                            WHERE ur.user_id = u.id AND r.role_code = #{roleCode})
              </if>
              <if test="companyId != null">
                AND u.company_id = #{companyId}
              </if>
              <if test="status != null">
                AND u.status = #{status}
              </if>
              <if test="createdFrom != null">
                AND u.created_at &gt;= #{createdFrom}
              </if>
              <if test="createdToExclusive != null">
                AND u.created_at &lt; #{createdToExclusive}
              </if>
            ORDER BY u.created_at DESC, u.id DESC
            LIMIT #{offset}, #{limit}
            </script>
            """)
    @Results(id = "adminUserRow", value = {
            @Result(column = "real_name", property = "realName"),
            @Result(column = "avatar_url", property = "avatarUrl"),
            @Result(column = "company_id", property = "companyId"),
            @Result(column = "company_name", property = "companyName"),
            @Result(column = "last_login_at", property = "lastLoginAt"),
            @Result(column = "created_at", property = "createdAt"),
            @Result(column = "updated_at", property = "updatedAt")
    })
    List<AdminUserRow> selectPage(@Param("keyword") String keyword, @Param("roleCode") String roleCode,
                                  @Param("companyId") Long companyId, @Param("status") Integer status,
                                  @Param("createdFrom") java.time.LocalDateTime createdFrom,
                                  @Param("createdToExclusive") java.time.LocalDateTime createdToExclusive,
                                  @Param("offset") long offset, @Param("limit") long limit);

    @Select("""
            <script>
            SELECT COUNT(*)
            FROM `user` u
            WHERE u.deleted_at IS NULL
              <if test="keyword != null and keyword != ''">
                AND (u.username LIKE CONCAT('%', #{keyword}, '%')
                  OR u.real_name LIKE CONCAT('%', #{keyword}, '%')
                  OR u.email LIKE CONCAT('%', #{keyword}, '%'))
              </if>
              <if test="roleCode != null and roleCode != ''">
                AND EXISTS (SELECT 1 FROM user_role ur JOIN `role` r ON r.id = ur.role_id
                            WHERE ur.user_id = u.id AND r.role_code = #{roleCode})
              </if>
              <if test="companyId != null">
                AND u.company_id = #{companyId}
              </if>
              <if test="status != null">
                AND u.status = #{status}
              </if>
              <if test="createdFrom != null">
                AND u.created_at &gt;= #{createdFrom}
              </if>
              <if test="createdToExclusive != null">
                AND u.created_at &lt; #{createdToExclusive}
              </if>
            </script>
            """)
    long count(@Param("keyword") String keyword, @Param("roleCode") String roleCode,
               @Param("companyId") Long companyId, @Param("status") Integer status,
               @Param("createdFrom") java.time.LocalDateTime createdFrom,
               @Param("createdToExclusive") java.time.LocalDateTime createdToExclusive);

    @Select("""
            SELECT u.id, u.username, u.real_name, u.email, u.phone, u.avatar_url, u.company_id,
                   c.name AS company_name, u.status, u.last_login_at, u.created_at, u.updated_at
            FROM `user` u
            LEFT JOIN company c ON c.id = u.company_id AND c.deleted_at IS NULL
            WHERE u.id = #{id} AND u.deleted_at IS NULL
            """)
    @Results(id = "adminUserDetailRow", value = {
            @Result(column = "real_name", property = "realName"),
            @Result(column = "avatar_url", property = "avatarUrl"),
            @Result(column = "company_id", property = "companyId"),
            @Result(column = "company_name", property = "companyName"),
            @Result(column = "last_login_at", property = "lastLoginAt"),
            @Result(column = "created_at", property = "createdAt"),
            @Result(column = "updated_at", property = "updatedAt")
    })
    AdminUserRow selectById(@Param("id") Long id);

    @Select("""
            SELECT COUNT(DISTINCT u.id)
            FROM `user` u
            JOIN user_role ur ON ur.user_id = u.id
            JOIN `role` r ON r.id = ur.role_id
            WHERE r.role_code = #{roleCode} AND u.status = 1 AND u.deleted_at IS NULL
            """)
    long countActiveUsersByRoleCode(@Param("roleCode") String roleCode);

    @Select("""
            SELECT DISTINCT u.id
            FROM `user` u
            JOIN user_role ur ON ur.user_id = u.id
            JOIN `role` r ON r.id = ur.role_id
            WHERE r.role_code = 'ADMIN' AND u.status = 1 AND u.deleted_at IS NULL
            FOR UPDATE
            """)
    List<Long> lockActiveAdminIds();

    @Select("SELECT COUNT(DISTINCT u.id) FROM `user` u JOIN user_role ur ON ur.user_id = u.id WHERE ur.role_id = #{roleId} AND u.deleted_at IS NULL")
    long countUsersByRoleId(@Param("roleId") Long roleId);

    @Select("""
            <script>
            SELECT DISTINCT u.id, u.username, u.real_name
            FROM `user` u
            JOIN user_role ur ON ur.user_id = u.id
            JOIN `role` r ON r.id = ur.role_id
            WHERE r.role_code = 'CANDIDATE' AND u.status = 1 AND u.deleted_at IS NULL
              <if test="keyword != null and keyword != ''">
                AND (u.username LIKE CONCAT('%', #{keyword}, '%') OR u.real_name LIKE CONCAT('%', #{keyword}, '%'))
              </if>
            ORDER BY u.real_name, u.id
            </script>
            """)
    List<UserOptionRow> selectCandidates(@Param("keyword") String keyword);

    @Data
    class UserOptionRow {
        private Long id;
        private String username;
        private String realName;
    }
}
