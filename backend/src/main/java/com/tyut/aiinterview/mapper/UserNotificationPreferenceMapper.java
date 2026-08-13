package com.tyut.aiinterview.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.tyut.aiinterview.domain.UserNotificationPreference;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface UserNotificationPreferenceMapper extends BaseMapper<UserNotificationPreference> {
    @Update("""
            UPDATE user_notification_preference
            SET site_enabled = #{siteEnabled}, email_enabled = #{emailEnabled}, sms_enabled = #{smsEnabled},
                version = version + 1, updated_at = CURRENT_TIMESTAMP
            WHERE user_id = #{userId} AND event_type = #{eventType} AND version = #{expectedVersion}
            """)
    int updateWithVersion(@Param("userId") Long userId,
                          @Param("eventType") String eventType,
                          @Param("siteEnabled") Integer siteEnabled,
                          @Param("emailEnabled") Integer emailEnabled,
                          @Param("smsEnabled") Integer smsEnabled,
                          @Param("expectedVersion") Integer expectedVersion);
}
