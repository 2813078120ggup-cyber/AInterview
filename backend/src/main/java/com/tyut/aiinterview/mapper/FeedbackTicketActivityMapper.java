package com.tyut.aiinterview.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.tyut.aiinterview.domain.FeedbackTicketActivity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface FeedbackTicketActivityMapper extends BaseMapper<FeedbackTicketActivity> {
    @Select("SELECT * FROM feedback_ticket_activity WHERE ticket_id = #{ticketId} AND (#{afterId} IS NULL OR id > #{afterId}) ORDER BY id ASC LIMIT #{limit}")
    List<FeedbackTicketActivity> selectAfter(@Param("ticketId") Long ticketId, @Param("afterId") Long afterId,
                                             @Param("limit") int limit);

    @Select("SELECT COUNT(*) FROM feedback_ticket_activity a LEFT JOIN feedback_ticket_read_state r ON r.ticket_id = a.ticket_id AND r.user_id = #{userId} WHERE a.ticket_id = #{ticketId} AND (r.last_read_activity_id IS NULL OR a.id > r.last_read_activity_id) AND (a.actor_id IS NULL OR a.actor_id <> #{userId})")
    long countUnread(@Param("ticketId") Long ticketId, @Param("userId") Long userId);
}
