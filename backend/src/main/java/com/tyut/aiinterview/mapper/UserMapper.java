package com.tyut.aiinterview.mapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.tyut.aiinterview.domain.UserAccount;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;
@Mapper public interface UserMapper extends BaseMapper<UserAccount> {
    @Select("""
            SELECT COUNT(*) > 0
            FROM user_role ur
            JOIN `role` r ON r.id = ur.role_id
            WHERE ur.user_id = #{userId} AND r.role_code = #{roleCode} AND r.status = 1
            """)
    boolean hasActiveRole(@Param("userId") Long userId, @Param("roleCode") String roleCode);

    @Update("UPDATE `user` SET `real_name` = #{realName}, `version` = COALESCE(`version`, 0) + 1 WHERE `id` = #{userId} AND COALESCE(`version`, 0) = #{expectedVersion} AND `status` = 1 AND `deleted_at` IS NULL")
    int updateProfileWithVersion(@Param("userId") Long userId, @Param("realName") String realName,
                                 @Param("expectedVersion") Integer expectedVersion);
    @Update("UPDATE `user` SET `security_version` = COALESCE(`security_version`, 0) + 1 WHERE `company_id` = #{companyId} AND `status` = 1 AND `deleted_at` IS NULL")
    int bumpSecurityVersionForCompany(@Param("companyId") Long companyId);

    @Update("""
            <script>
            UPDATE `user`
            SET `avatar_media_id` = #{newMediaId}, `version` = COALESCE(`version`, 0) + 1
            WHERE `id` = #{userId}
              AND `status` = 1
              AND `deleted_at` IS NULL
              AND COALESCE(`version`, 0) = #{expectedVersion}
              <choose>
                <when test="expectedMediaId != null">AND `avatar_media_id` = #{expectedMediaId}</when>
                <otherwise>AND `avatar_media_id` IS NULL</otherwise>
              </choose>
            </script>
            """)
    int updateAvatarBinding(@Param("userId") Long userId, @Param("newMediaId") Long newMediaId,
                            @Param("expectedMediaId") Long expectedMediaId,
                            @Param("expectedVersion") Integer expectedVersion);

    @Update("UPDATE `user` SET `phone` = #{phone}, `phone_verified_at` = CURRENT_TIMESTAMP, `security_version` = COALESCE(`security_version`, 0) + 1, `version` = COALESCE(`version`, 0) + 1 WHERE `id` = #{userId} AND `status` = 1 AND `deleted_at` IS NULL AND COALESCE(`version`, 0) = #{expectedVersion}")
    int updatePhoneWithVerification(@Param("userId") Long userId, @Param("phone") String phone,
                                    @Param("expectedVersion") Integer expectedVersion);

    @Update("UPDATE `user` SET `email` = #{email}, `email_verified_at` = CURRENT_TIMESTAMP, `security_version` = COALESCE(`security_version`, 0) + 1, `version` = COALESCE(`version`, 0) + 1 WHERE `id` = #{userId} AND `status` = 1 AND `deleted_at` IS NULL AND COALESCE(`version`, 0) = #{expectedVersion}")
    int updateEmailWithVerification(@Param("userId") Long userId, @Param("email") String email,
                                    @Param("expectedVersion") Integer expectedVersion);

    @Update("UPDATE `user` SET `password_hash` = #{passwordHash}, `security_version` = COALESCE(`security_version`, 0) + 1, `version` = COALESCE(`version`, 0) + 1 WHERE `id` = #{userId} AND `status` = 1 AND `deleted_at` IS NULL AND COALESCE(`security_version`, 0) = #{expectedSecurityVersion}")
    int updatePasswordAndSecurityVersion(@Param("userId") Long userId,
                                         @Param("passwordHash") String passwordHash,
                                         @Param("expectedSecurityVersion") Integer expectedSecurityVersion);
}
