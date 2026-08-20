package com.tyut.aiinterview.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.tyut.aiinterview.domain.PlatformUiSetting;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface PlatformUiSettingMapper extends BaseMapper<PlatformUiSetting> {
    /**
     * The migration creates the singleton row, but this idempotent repair path
     * keeps a partially restored database safe and handles concurrent first
     * reads without surfacing a duplicate-key error.
     */
    @Insert("INSERT IGNORE INTO platform_ui_setting (id, mouse_follower_enabled) VALUES (1, 1)")
    int insertDefaultIfMissing();
}
