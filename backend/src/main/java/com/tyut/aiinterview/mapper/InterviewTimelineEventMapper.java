package com.tyut.aiinterview.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.tyut.aiinterview.domain.InterviewTimelineEvent;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface InterviewTimelineEventMapper extends BaseMapper<InterviewTimelineEvent> {
}
