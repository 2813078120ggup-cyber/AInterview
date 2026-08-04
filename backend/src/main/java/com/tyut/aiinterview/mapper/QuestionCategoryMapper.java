package com.tyut.aiinterview.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.tyut.aiinterview.domain.QuestionCategory;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface QuestionCategoryMapper extends BaseMapper<QuestionCategory> {
}
