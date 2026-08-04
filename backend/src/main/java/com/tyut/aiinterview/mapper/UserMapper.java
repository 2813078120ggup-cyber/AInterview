package com.tyut.aiinterview.mapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.tyut.aiinterview.domain.UserAccount;
import org.apache.ibatis.annotations.Mapper;
@Mapper public interface UserMapper extends BaseMapper<UserAccount> {}
