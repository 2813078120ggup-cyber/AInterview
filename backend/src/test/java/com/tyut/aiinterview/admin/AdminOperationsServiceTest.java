package com.tyut.aiinterview.admin;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.tyut.aiinterview.domain.AiProviderConfig;
import com.tyut.aiinterview.mapper.AdminAiOperationsMapper;
import com.tyut.aiinterview.mapper.AiProviderConfigMapper;
import java.sql.Connection;
import java.time.LocalDateTime;
import java.util.List;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.cloud.client.discovery.DiscoveryClient;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;

class AdminOperationsServiceTest {
    private final AdminAiOperationsMapper aiMapper = mock(AdminAiOperationsMapper.class);
    private final AiProviderConfigMapper providerMapper = mock(AiProviderConfigMapper.class);
    private final DataSource dataSource = mock(DataSource.class);
    private final StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
    private final ObjectProvider<DiscoveryClient> discoveryProvider = mock(ObjectProvider.class);
    private final AdminOperationsService service = new AdminOperationsService(
            aiMapper, providerMapper, dataSource, redisTemplate, discoveryProvider);

    @Test
    void openTalkingUsesPersistedSuccessfulTestStatus() throws Exception {
        configureHealthyDependencies();
        AiProviderConfig openTalking = provider("open-talking-virtual-human", "virtual-human");
        openTalking.setLastTestState("SUCCESS");
        openTalking.setLastTestStatusCode(200);
        openTalking.setLastTestLatencyMs(23L);
        openTalking.setLastTestMessage("OpenTalking 服务可用");
        openTalking.setLastTestedAt(LocalDateTime.of(2026, 8, 13, 21, 0));
        AiProviderConfig llm = provider("deepseek", "llm");
        llm.setTextDefault(1);
        llm.setApiKeyCipher("encrypted");
        when(providerMapper.selectList(null)).thenReturn(List.of(llm, openTalking));

        AdminOperationsDtos.Component result = service.summary().components().stream()
                .filter(item -> "opentalking".equals(item.code())).findFirst().orElseThrow();

        assertEquals("UP", result.state());
        assertEquals("测试通过", result.stateLabel());
        assertEquals("最近一次连通性测试成功，耗时 23 ms，测试时间 2026-08-13 21:00:00。", result.summary());
    }

    @Test
    void openTalkingWithoutResultRemainsPending() throws Exception {
        configureHealthyDependencies();
        AiProviderConfig openTalking = provider("open-talking-virtual-human", "virtual-human");
        when(providerMapper.selectList(null)).thenReturn(List.of(openTalking));

        AdminOperationsDtos.Component result = service.summary().components().stream()
                .filter(item -> "opentalking".equals(item.code())).findFirst().orElseThrow();

        assertEquals("ATTENTION", result.state());
        assertEquals("待测试", result.stateLabel());
    }

    private void configureHealthyDependencies() throws Exception {
        Connection connection = mock(Connection.class);
        when(connection.isValid(2)).thenReturn(true);
        when(dataSource.getConnection()).thenReturn(connection);
        RedisConnectionFactory factory = mock(RedisConnectionFactory.class);
        RedisConnection redis = mock(RedisConnection.class);
        when(redis.ping()).thenReturn("PONG");
        when(factory.getConnection()).thenReturn(redis);
        when(redisTemplate.getConnectionFactory()).thenReturn(factory);
        DiscoveryClient discovery = mock(DiscoveryClient.class);
        when(discovery.getServices()).thenReturn(List.of("backend-core"));
        when(discovery.getInstances("cloud-gateway")).thenReturn(List.of());
        when(discovery.getInstances("algorithm-judge-worker")).thenReturn(List.of());
        when(discoveryProvider.getIfAvailable()).thenReturn(discovery);
    }

    private AiProviderConfig provider(String code, String kind) {
        AiProviderConfig provider = new AiProviderConfig();
        provider.setCode(code);
        provider.setKind(kind);
        provider.setEnabled(1);
        provider.setBaseUrl("/provider");
        provider.setChatModel("model");
        provider.setAvatarModel("avatar");
        return provider;
    }
}
