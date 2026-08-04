package com.tyut.aiinterview.ai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import com.tyut.aiinterview.config.DeepSeekProperties;
import com.tyut.aiinterview.domain.AiGenerationRecord;
import com.tyut.aiinterview.prompt.PromptTemplateService;
import com.tyut.aiinterview.settings.AiProviderService;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class DeepSeekGatewayProviderConfigTest {
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final PromptTemplateService promptTemplates = mock(PromptTemplateService.class);
    private final AiGenerationAuditService auditService = mock(AiGenerationAuditService.class);
    private final AiProviderService providerService = mock(AiProviderService.class);
    private final AtomicReference<String> authorization = new AtomicReference<>();
    private final AtomicReference<String> requestedModel = new AtomicReference<>();
    private HttpServer server;

    @BeforeEach
    void startServer() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/chat/completions", exchange -> {
            authorization.set(exchange.getRequestHeaders().getFirst("Authorization"));
            JsonNode request = objectMapper.readTree(exchange.getRequestBody());
            requestedModel.set(request.path("model").asText());
            byte[] response = """
                    {"choices":[{"message":{"content":"数据库配置已生效"}}],
                     "usage":{"prompt_tokens":8,"completion_tokens":4,"total_tokens":12}}
                    """.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, response.length);
            exchange.getResponseBody().write(response);
            exchange.close();
        });
        server.start();

        AiGenerationRecord audit = new AiGenerationRecord();
        audit.setRequestId("request-test");
        audit.setStartedAt(LocalDateTime.now());
        when(auditService.start(any(), anyString(), anyInt(), anyString(), anyString(), anyInt()))
                .thenReturn(audit);
    }

    @AfterEach
    void stopServer() {
        if (server != null) server.stop(0);
    }

    @Test
    void usesDatabaseTextDefaultWhenEnvironmentDeepSeekIsDisabled() {
        String baseUrl = "http://127.0.0.1:" + server.getAddress().getPort();
        when(providerService.defaultLlmProvider()).thenReturn(Optional.of(new AiProviderService.RuntimeProvider(
                1L, "DeepSeek", "deepseek", baseUrl, "deepseek-chat", "", "",
                "database-api-key", "", "", "文字默认模型")));

        DeepSeekGateway gateway = new DeepSeekGateway(
                new DeepSeekProperties(false, "", "https://unused.example", "unused-model"),
                objectMapper, promptTemplates, auditService, providerService);

        String response = gateway.interviewCoach("请进行一次连通性测试");

        assertThat(response).isEqualTo("数据库配置已生效");
        assertThat(authorization.get()).isEqualTo("Bearer database-api-key");
        assertThat(requestedModel.get()).isEqualTo("deepseek-chat");
    }
}
