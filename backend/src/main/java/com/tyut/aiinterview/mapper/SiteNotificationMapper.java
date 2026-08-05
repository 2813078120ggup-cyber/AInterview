package com.tyut.aiinterview.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.tyut.aiinterview.domain.SiteNotification;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface SiteNotificationMapper extends BaseMapper<SiteNotification> {
    @Select("SELECT COUNT(*) FROM site_notification WHERE recipient_id = #{userId} AND read_at IS NULL")
    long countUnread(@Param("userId") Long userId);

    @Select("SELECT * FROM site_notification WHERE recipient_id = #{userId} ORDER BY created_at DESC, id DESC LIMIT #{limit} OFFSET #{offset}")
    List<SiteNotification> selectPageForUser(@Param("userId") Long userId, @Param("limit") int limit,
                                              @Param("offset") long offset);
}
