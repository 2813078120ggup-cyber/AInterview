package com.tyut.aiinterview.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.tyut.aiinterview.domain.AiTask;
import java.time.LocalDateTime;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface AiTaskMapper extends BaseMapper<AiTask> {
    @Update("UPDATE ai_task SET status = 'RUNNING', attempts = attempts + 1, started_at = #{now}, "
            + "claim_token = #{claimToken}, locked_by = #{lockedBy}, lease_expires_at = #{leaseExpiresAt}, heartbeat_at = #{now} "
            + "WHERE id = #{id} AND status = 'PENDING' AND scheduled_at <= #{now}")
    int claimPending(@Param("id") Long id, @Param("claimToken") String claimToken,
                     @Param("lockedBy") String lockedBy, @Param("now") LocalDateTime now,
                     @Param("leaseExpiresAt") LocalDateTime leaseExpiresAt);

    @Update("UPDATE ai_task SET heartbeat_at = #{now}, lease_expires_at = #{leaseExpiresAt} "
            + "WHERE id = #{id} AND status = 'RUNNING' AND claim_token = #{claimToken}")
    int extendLease(@Param("id") Long id, @Param("claimToken") String claimToken,
                    @Param("now") LocalDateTime now, @Param("leaseExpiresAt") LocalDateTime leaseExpiresAt);

    @Update("UPDATE ai_task SET status = 'PENDING', scheduled_at = #{now}, started_at = NULL, "
            + "claim_token = NULL, locked_by = NULL, lease_expires_at = NULL, heartbeat_at = NULL, "
            + "error_message = 'Worker lease expired; task requeued' "
            + "WHERE status = 'RUNNING' AND lease_expires_at IS NOT NULL AND lease_expires_at < #{now} "
            + "AND attempts < max_attempts")
    int requeueExpired(@Param("now") LocalDateTime now);

    @Select("SELECT id FROM ai_task WHERE status = 'RUNNING' AND lease_expires_at IS NOT NULL "
            + "AND lease_expires_at < #{now} AND attempts >= max_attempts ORDER BY id ASC LIMIT #{limit}")
    List<Long> selectExpiredExhausted(@Param("now") LocalDateTime now, @Param("limit") int limit);

    @Update("UPDATE ai_task SET status = 'FAILED', finished_at = #{now}, claim_token = NULL, locked_by = NULL, "
            + "lease_expires_at = NULL, heartbeat_at = NULL, error_message = 'Worker lease expired; retry limit reached' "
            + "WHERE id = #{id} AND status = 'RUNNING' AND lease_expires_at IS NOT NULL "
            + "AND lease_expires_at < #{now} AND attempts >= max_attempts")
    int failExpiredExhausted(@Param("id") Long id, @Param("now") LocalDateTime now);
}
