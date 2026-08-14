package com.tyut.aiinterview.settings;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tyut.aiinterview.domain.AiProviderConfig;
import com.tyut.aiinterview.mapper.AiProviderConfigMapper;
import com.tyut.aiinterview.security.CurrentUser;
import java.net.URI;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class AiProviderServiceTest {

    @Test
    void resolvesRelativeBrowserEndpointThroughConfiguredUpstream() {
        URI uri = AiProviderService.openTalkingHealthUri(
                "/opentalking", "host.docker.internal:8210");

        assertEquals("http://host.docker.internal:8210/health", uri.toString());
    }

    @Test
    void preservesAbsoluteProviderEndpoint() {
        URI uri = AiProviderService.openTalkingHealthUri(
                "https://avatar.example.test/api/", "host.docker.internal:8210");

        assertEquals("https://avatar.example.test/api/health", uri.toString());
    }

    @Test
    void rejectsRelativeEndpointWithoutUpstream() {
        assertThrows(IllegalStateException.class,
                () -> AiProviderService.openTalkingHealthUri("/opentalking", ""));
    }

    @Test
    void testPersistsFailureStatusForDisabledProvider() {
        AiProviderConfigMapper mapper = mock(AiProviderConfigMapper.class);
        ConfigSecretCodec secretCodec = mock(ConfigSecretCodec.class);
        CurrentUser currentUser = mock(CurrentUser.class);
        AiProviderConfig provider = new AiProviderConfig();
        provider.setId(7L);
        provider.setEnabled(0);
        when(currentUser.hasRole("ADMIN")).thenReturn(true);
        when(mapper.selectById(7L)).thenReturn(provider);
        when(mapper.update(any(), any())).thenReturn(1);
        AiProviderService service = new AiProviderService(
                mapper, secretCodec, currentUser, new ObjectMapper(), "host.docker.internal:8210");

        AiProviderDtos.ProviderTestResult result = service.test(7L);

        assertEquals("FAILED", result.state());
        ArgumentCaptor<AiProviderConfig> captor = ArgumentCaptor.forClass(AiProviderConfig.class);
        verify(mapper).update(captor.capture(), any());
        assertEquals("FAILED", captor.getValue().getLastTestState());
        assertEquals(result.testedAt(), captor.getValue().getLastTestedAt());
    }

    @Test
    void configurationUpdateInvalidatesPreviousTestStatus() {
        AiProviderConfigMapper mapper = mock(AiProviderConfigMapper.class);
        ConfigSecretCodec secretCodec = mock(ConfigSecretCodec.class);
        CurrentUser currentUser = mock(CurrentUser.class);
        AiProviderConfig provider = new AiProviderConfig();
        provider.setId(8L);
        provider.setCode("open-talking-virtual-human");
        provider.setLastTestState("SUCCESS");
        provider.setLastTestStatusCode(200);
        provider.setLastTestLatencyMs(20L);
        provider.setLastTestMessage("OpenTalking 服务可用");
        provider.setLastTestedAt(LocalDateTime.now());
        when(currentUser.hasRole("ADMIN")).thenReturn(true);
        when(currentUser.id()).thenReturn(1L);
        when(mapper.selectById(8L)).thenReturn(provider);
        when(mapper.selectOne(any())).thenReturn(null);
        when(secretCodec.isMasked(any())).thenReturn(false);
        AiProviderService service = new AiProviderService(
                mapper, secretCodec, currentUser, new ObjectMapper(), "host.docker.internal:8210");
        AiProviderDtos.ProviderRequest request = new AiProviderDtos.ProviderRequest(
                "OpenTalking", "open-talking-virtual-human", "virtual-human", "/opentalking",
                "mock", "zh-CN-XiaoxiaoNeural", "dogo-light2d", "", "", "edge",
                true, false, true, "virtual human");

        service.update(8L, request);

        ArgumentCaptor<AiProviderConfig> captor = ArgumentCaptor.forClass(AiProviderConfig.class);
        verify(mapper).updateById(captor.capture());
        assertNull(captor.getValue().getLastTestState());
        assertNull(captor.getValue().getLastTestedAt());
    }
}
