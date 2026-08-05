package com.tyut.aiinterview.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.tyut.aiinterview.domain.FeedbackTicket;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface FeedbackTicketMapper extends BaseMapper<FeedbackTicket> {
    @Select("SELECT * FROM feedback_ticket WHERE id = #{id} AND deleted_at IS NULL FOR UPDATE")
    FeedbackTicket selectForUpdate(@Param("id") Long id);
}
