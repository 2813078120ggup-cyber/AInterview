package com.tyut.aiinterview.admin;

import com.tyut.aiinterview.mapper.AdminAiOpsTaskSummaryRow;
import com.tyut.aiinterview.mapper.AdminAiOperationsMapper;
import com.tyut.aiinterview.mapper.AiProviderConfigMapper;
import com.tyut.aiinterview.domain.AiProviderConfig;
import java.sql.Connection;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import javax.sql.DataSource;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.cloud.client.discovery.DiscoveryClient;
import org.springframework.stereotype.Service;

@Service
public class AdminOperationsService {
    private final AdminAiOperationsMapper aiMapper;
    private final AiProviderConfigMapper providerMapper;
    private final DataSource dataSource;
    private final StringRedisTemplate redisTemplate;
    private final ObjectProvider<DiscoveryClient> discoveryClientProvider;

    public AdminOperationsService(AdminAiOperationsMapper aiMapper, AiProviderConfigMapper providerMapper,
                                  DataSource dataSource, StringRedisTemplate redisTemplate,
                                  ObjectProvider<DiscoveryClient> discoveryClientProvider) {
        this.aiMapper = aiMapper;
        this.providerMapper = providerMapper;
        this.dataSource = dataSource;
        this.redisTemplate = redisTemplate;
        this.discoveryClientProvider = discoveryClientProvider;
    }

    public AdminOperationsDtos.Summary summary() {
        List<AdminOperationsDtos.Component> components = new ArrayList<>();
        components.add(component("backend", "backend", "UP", "正常",
                "当前管理接口已响应。", "暂不需要处理。"));
        components.add(discoveryComponent("cloud-gateway", "cloud-gateway", "网关实例已注册，等待路由验证。"));
        components.add(registryComponent());
        components.add(discoveryComponent("algorithm-judge-worker", "algorithm-judge-worker", "判题 Worker 实例已注册。"));
        components.add(databaseComponent());
        components.add(redisComponent());
        components.add(providerComponent());
        components.add(openTalkingComponent());

        AdminAiOpsTaskSummaryRow tasks = aiMapper.selectTaskSummary();
        long aiBacklog = value(tasks == null ? null : tasks.getBacklog());
        long reportBacklog = value(tasks == null ? null : tasks.getReportBacklog());
        components.add(component("ai-task-backlog", "AI 任务积压", aiBacklog == 0 ? "UP" : "ATTENTION",
                aiBacklog == 0 ? "无积压" : "需要关注", "当前有 " + aiBacklog + " 个 AI 任务排队或执行中。",
                aiBacklog == 0 ? "暂不需要处理。" : "先查看 AI 中心的任务关联和失败状态。"));
        components.add(component("report-backlog", "报告任务积压", reportBacklog == 0 ? "UP" : "ATTENTION",
                reportBacklog == 0 ? "无积压" : "需要关注", "当前有 " + reportBacklog + " 个报告任务排队或执行中。",
                reportBacklog == 0 ? "暂不需要处理。" : "检查报告任务是否持续等待 Provider。"));
        boolean degraded = components.stream().anyMatch(item -> "ATTENTION".equals(item.state()) || "DOWN".equals(item.state()));
        return new AdminOperationsDtos.Summary(LocalDateTime.now(), degraded, "/grafana/", "打开 Grafana 详细指标", components);
    }

    private AdminOperationsDtos.Component databaseComponent() {
        try (Connection connection = dataSource.getConnection()) {
            boolean valid = connection.isValid(2);
            return component("mysql", "MySQL", valid ? "UP" : "DOWN", valid ? "正常" : "异常",
                    valid ? "数据库连接可用。" : "数据库连接未通过校验。",
                    valid ? "暂不需要处理。" : "检查数据库服务和连接池状态。 ");
        } catch (Exception exception) {
            return component("mysql", "MySQL", "DOWN", "异常", "数据库连接检查失败。", "检查 MySQL 容器健康状态和连接配置。");
        }
    }

    private AdminOperationsDtos.Component redisComponent() {
        RedisConnection connection = null;
        try {
            if (redisTemplate.getConnectionFactory() == null) {
                return component("redis", "Redis", "UNKNOWN", "未知", "缓存连接工厂不可用。", "检查 Redis 服务和应用连接配置。");
            }
            connection = redisTemplate.getConnectionFactory().getConnection();
            String pong = connection.ping();
            boolean valid = "PONG".equalsIgnoreCase(pong);
            return component("redis", "Redis", valid ? "UP" : "DOWN", valid ? "正常" : "异常",
                    valid ? "缓存连接可用。" : "缓存连接未返回有效响应。",
                    valid ? "暂不需要处理。" : "检查 Redis 服务和连接配置。 ");
        } catch (Exception exception) {
            return component("redis", "Redis", "DOWN", "异常", "缓存连接检查失败。", "检查 Redis 容器健康状态和连接配置。");
        } finally {
            if (connection != null) {
                try {
                    connection.close();
                } catch (Exception ignored) {
                    // Health checks must not leak infrastructure details.
                }
            }
        }
    }

    private AdminOperationsDtos.Component providerComponent() {
        List<AiProviderConfig> providers = providerMapper.selectList(null);
        AiProviderConfig llm = providers.stream().filter(item -> "llm".equals(item.getKind()) && truthy(item.getEnabled())
                && truthy(item.getTextDefault())).findFirst().orElse(null);
        if (llm == null) return component("ai-provider", "AI Provider", "ATTENTION", "需要关注",
                "没有启用的文字默认 Provider。", "在 AI 中心检查 Provider 配置并执行带超时的连通性测试。");
        boolean configured = hasText(llm.getBaseUrl()) && hasText(llm.getChatModel()) && hasText(llm.getApiKeyCipher());
        return component("ai-provider", "AI Provider", configured ? "ATTENTION" : "DOWN",
                configured ? "已配置，待测试" : "配置不完整",
                configured ? "文字默认 Provider 已配置；本摘要不主动调用外部模型。" : "文字默认 Provider 缺少必要配置。",
                "打开 AI 中心执行明确显示成功、失败或超时的 Provider 测试。");
    }

    private AdminOperationsDtos.Component openTalkingComponent() {
        AiProviderConfig provider = providerMapper.selectOne(new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<AiProviderConfig>()
                .eq(AiProviderConfig::getCode, "open-talking-virtual-human").last("LIMIT 1"));
        if (provider == null || !truthy(provider.getEnabled())) {
            return component("opentalking", "OpenTalking 上游", "DOWN", "未启用",
                    "唯一虚拟人链路未启用。", "只配置并检查 OpenTalking，不恢复其他虚拟人或讯飞链路。");
        }
        return component("opentalking", "OpenTalking 上游", "ATTENTION", "待测试",
                "OpenTalking 配置已启用；本摘要不展示上游地址，也不主动暴露内部连接信息。",
                "在 AI Provider 中执行 OpenTalking 带超时的连通性测试。");
    }

    private AdminOperationsDtos.Component registryComponent() {
        DiscoveryClient client = discoveryClientProvider.getIfAvailable();
        if (client == null) return component("cloud-registry", "cloud-registry", "UNKNOWN", "未知",
                "服务发现客户端不可用。", "检查 cloud-registry 和服务发现配置。");
        try {
            List<String> services = client.getServices();
            boolean connected = services != null && !services.isEmpty();
            return component("cloud-registry", "cloud-registry", connected ? "UP" : "ATTENTION",
                    connected ? "正常" : "需要关注", connected ? "服务发现目录可读取。" : "暂未发现已注册服务。",
                    connected ? "暂不需要处理。" : "检查 cloud-registry 是否健康并确认服务注册。");
        } catch (Exception exception) {
            return component("cloud-registry", "cloud-registry", "DOWN", "异常", "服务发现目录读取失败。", "检查 cloud-registry 服务状态。");
        }
    }

    private AdminOperationsDtos.Component discoveryComponent(String serviceId, String label, String healthySummary) {
        DiscoveryClient client = discoveryClientProvider.getIfAvailable();
        if (client == null) return component(serviceId, label, "UNKNOWN", "未知", "服务发现客户端不可用。", "检查 cloud-registry 和服务注册。");
        try {
            int count = client.getInstances(serviceId).size();
            return component(serviceId, label, count > 0 ? "UP" : "DOWN", count > 0 ? "正常" : "异常",
                    count > 0 ? healthySummary : "未发现已注册实例。",
                    count > 0 ? "暂不需要处理。" : "检查服务容器和 cloud-registry 注册状态。");
        } catch (Exception exception) {
            return component(serviceId, label, "DOWN", "异常", "服务实例状态读取失败。", "检查服务容器和服务发现状态。");
        }
    }

    private AdminOperationsDtos.Component component(String code, String label, String state, String stateLabel,
                                                    String summary, String recommendation) {
        return new AdminOperationsDtos.Component(code, label, state, stateLabel, summary, recommendation);
    }

    private long value(Long value) {
        return value == null ? 0 : value;
    }

    private boolean truthy(Integer value) {
        return value != null && value == 1;
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
