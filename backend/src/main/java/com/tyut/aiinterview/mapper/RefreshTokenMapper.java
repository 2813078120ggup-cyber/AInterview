package com.tyut.aiinterview.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.tyut.aiinterview.domain.RefreshToken;
import java.time.LocalDateTime;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface RefreshTokenMapper extends BaseMapper<RefreshToken> {
    @Update("UPDATE refresh_token SET revoked_at = #{usedAt}, last_used_at = #{usedAt}, revoked_reason = 'ROTATED' "
            + "WHERE id = #{id} AND revoked_at IS NULL AND expires_at > #{usedAt}")
    int rotateActiveToken(@Param("id") Long id, @Param("usedAt") LocalDateTime usedAt);

    @Update("UPDATE refresh_token SET revoked_at = CURRENT_TIMESTAMP, last_used_at = CURRENT_TIMESTAMP, revoked_reason = #{reason} WHERE user_id = #{userId} AND revoked_at IS NULL AND expires_at > CURRENT_TIMESTAMP AND session_id <> #{sessionId}")
    int revokeOtherSessions(@Param("userId") Long userId, @Param("sessionId") String sessionId,
                            @Param("reason") String reason);

    @Update("UPDATE refresh_token SET revoked_at = CURRENT_TIMESTAMP, last_used_at = CURRENT_TIMESTAMP, revoked_reason = #{reason} WHERE user_id = #{userId} AND revoked_at IS NULL AND expires_at > CURRENT_TIMESTAMP")
    int revokeAllSessions(@Param("userId") Long userId, @Param("reason") String reason);

    @Select("SELECT COUNT(*) FROM refresh_token WHERE user_id = #{userId} AND session_id = #{sessionId}")
    long countUserSession(@Param("userId") Long userId, @Param("sessionId") String sessionId);

    @Update("UPDATE refresh_token SET revoked_at = CURRENT_TIMESTAMP, "
            + "last_used_at = COALESCE(last_used_at, CURRENT_TIMESTAMP), revoked_reason = #{reason} "
            + "WHERE user_id = #{userId} AND session_id = #{sessionId} AND revoked_at IS NULL")
    int revokeUserSession(@Param("userId") Long userId, @Param("sessionId") String sessionId,
                          @Param("reason") String reason);
}
