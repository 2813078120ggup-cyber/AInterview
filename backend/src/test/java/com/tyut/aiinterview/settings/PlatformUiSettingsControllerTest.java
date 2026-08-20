package com.tyut.aiinterview.settings;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.tyut.aiinterview.common.ApiResponse;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.prepost.PreAuthorize;

class PlatformUiSettingsControllerTest {
    private final PlatformUiSettingsService service = mock(PlatformUiSettingsService.class);
    private final PlatformUiSettingsController controller = new PlatformUiSettingsController(service);

    @Test
    void publicReadDelegatesToService() {
        PlatformUiSettingsDtos.View expected = new PlatformUiSettingsDtos.View(true);
        when(service.read()).thenReturn(expected);

        ApiResponse<PlatformUiSettingsDtos.View> response = controller.read();

        assertEquals(expected, response.data());
        verify(service).read();
    }

    @Test
    void updateEndpointRequiresAdminRole() throws NoSuchMethodException {
        PreAuthorize annotation = PlatformUiSettingsController.class
                .getDeclaredMethod("update", PlatformUiSettingsDtos.UpdateRequest.class)
                .getAnnotation(PreAuthorize.class);

        assertEquals("hasRole('ADMIN')", annotation.value());
    }
}
