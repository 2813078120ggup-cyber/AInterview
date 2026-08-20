package com.tyut.aiinterview.settings;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.tyut.aiinterview.common.BusinessException;
import com.tyut.aiinterview.domain.PlatformUiSetting;
import com.tyut.aiinterview.mapper.PlatformUiSettingMapper;
import com.tyut.aiinterview.observability.OperationAuditService;
import com.tyut.aiinterview.security.CurrentUser;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class PlatformUiSettingsServiceTest {
    private final PlatformUiSettingMapper mapper = mock(PlatformUiSettingMapper.class);
    private final CurrentUser currentUser = mock(CurrentUser.class);
    private final OperationAuditService auditService = mock(OperationAuditService.class);
    private PlatformUiSettingsService service;

    @BeforeEach
    void setUp() {
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), "platform-ui-test"),
                PlatformUiSetting.class);
        service = new PlatformUiSettingsService(mapper, currentUser, auditService);
    }

    @Test
    void readRepairsMissingSingletonAndReturnsDefault() {
        PlatformUiSetting defaultSetting = setting(true);
        when(mapper.selectById(1L)).thenReturn(null, defaultSetting);
        when(mapper.insertDefaultIfMissing()).thenReturn(1);

        PlatformUiSettingsDtos.View view = service.read();

        assertTrue(view.mouseFollowerEnabled());
        verify(mapper).insertDefaultIfMissing();
    }

    @Test
    void adminCanUpdateAndChangeIsAuditedWithAuthenticatedOperator() {
        PlatformUiSetting setting = setting(true);
        when(currentUser.hasRole("ADMIN")).thenReturn(true);
        when(currentUser.id()).thenReturn(900L);
        when(mapper.selectById(1L)).thenReturn(setting);
        when(mapper.update(any(), any())).thenAnswer(invocation -> {
            PlatformUiSetting patch = invocation.getArgument(0);
            setting.setMouseFollowerEnabled(patch.getMouseFollowerEnabled());
            setting.setUpdatedBy(patch.getUpdatedBy());
            return 1;
        });

        PlatformUiSettingsDtos.View view = service.update(new PlatformUiSettingsDtos.UpdateRequest(false));

        assertFalse(view.mouseFollowerEnabled());
        ArgumentCaptor<PlatformUiSetting> patch = ArgumentCaptor.forClass(PlatformUiSetting.class);
        verify(mapper).update(patch.capture(), any());
        assertEquals(0, patch.getValue().getMouseFollowerEnabled());
        assertEquals(900L, patch.getValue().getUpdatedBy());
        verify(auditService).success("PLATFORM_UI", "PLATFORM_UI_SETTINGS_UPDATED", "PLATFORM_UI_SETTING",
                1L, null, "更新鼠标跟随状态为 false");
    }

    @Test
    void nonAdminCannotUpdate() {
        when(currentUser.hasRole("ADMIN")).thenReturn(false);

        BusinessException exception = assertThrows(BusinessException.class,
                () -> service.update(new PlatformUiSettingsDtos.UpdateRequest(false)));

        assertEquals("仅超级管理员可以修改主题设置", exception.getMessage());
        verify(mapper, never()).update(any(), any());
        verify(auditService, never()).success(any(), any(), any(), any(), any(), any());
    }

    private PlatformUiSetting setting(boolean enabled) {
        PlatformUiSetting setting = new PlatformUiSetting();
        setting.setId(1L);
        setting.setMouseFollowerEnabled(enabled ? 1 : 0);
        return setting;
    }
}
