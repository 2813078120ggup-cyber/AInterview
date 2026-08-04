package com.tyut.aiinterview.settings;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.net.URI;
import org.junit.jupiter.api.Test;

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
}
