package com.tyut.aiinterview.settings;

import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.tyut.aiinterview.common.BusinessException;
import com.tyut.aiinterview.domain.PlatformUiSetting;
import com.tyut.aiinterview.mapper.PlatformUiSettingMapper;
import com.tyut.aiinterview.observability.OperationAuditService;
import com.tyut.aiinterview.security.CurrentUser;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PlatformUiSettingsService {
    private static final long SINGLETON_ID = 1L;

    private final PlatformUiSettingMapper mapper;
    private final CurrentUser currentUser;
    private final OperationAuditService auditService;

    public PlatformUiSettingsService(PlatformUiSettingMapper mapper, CurrentUser currentUser,
                                     OperationAuditService auditService) {
        this.mapper = mapper;
        this.currentUser = currentUser;
        this.auditService = auditService;
    }

    /**
     * This endpoint is deliberately available before login so that a client
     * can apply the same presentation policy on every route.  It returns only
     * the boolean presentation flag and never returns operator metadata.
     */
    @Transactional
    public PlatformUiSettingsDtos.View read() {
        return toView(ensureSetting());
    }

    @Transactional
    public PlatformUiSettingsDtos.View update(PlatformUiSettingsDtos.UpdateRequest request) {
        requireAdmin();
        if (request == null || request.mouseFollowerEnabled() == null) {
            throw BusinessException.badRequest("鼠标跟随状态不能为空");
        }
        ensureSetting();
        PlatformUiSetting patch = new PlatformUiSetting();
        patch.setMouseFollowerEnabled(request.mouseFollowerEnabled() ? 1 : 0);
        patch.setUpdatedBy(currentUser.id());
        int updated = mapper.update(patch, new LambdaUpdateWrapper<PlatformUiSetting>()
                .eq(PlatformUiSetting::getId, SINGLETON_ID));
        if (updated != 1) {
            throw BusinessException.conflict("主题设置在更新期间发生变化，请刷新后重试");
        }
        auditService.success("PLATFORM_UI", "PLATFORM_UI_SETTINGS_UPDATED", "PLATFORM_UI_SETTING",
                SINGLETON_ID, null, "更新鼠标跟随状态为 " + request.mouseFollowerEnabled());
        return read();
    }

    private PlatformUiSetting ensureSetting() {
        PlatformUiSetting setting = mapper.selectById(SINGLETON_ID);
        if (setting != null) return setting;
        mapper.insertDefaultIfMissing();
        setting = mapper.selectById(SINGLETON_ID);
        if (setting == null) {
            throw BusinessException.serviceUnavailable("主题设置暂不可用，请稍后重试");
        }
        return setting;
    }

    private PlatformUiSettingsDtos.View toView(PlatformUiSetting setting) {
        return new PlatformUiSettingsDtos.View(Integer.valueOf(1).equals(setting.getMouseFollowerEnabled()));
    }

    private void requireAdmin() {
        if (!currentUser.hasRole("ADMIN")) {
            throw BusinessException.forbidden("仅超级管理员可以修改主题设置");
        }
    }
}
