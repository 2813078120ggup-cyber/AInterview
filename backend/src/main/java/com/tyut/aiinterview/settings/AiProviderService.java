package com.tyut.aiinterview.settings;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tyut.aiinterview.common.BusinessException;
import com.tyut.aiinterview.domain.AiProviderConfig;
import com.tyut.aiinterview.mapper.AiProviderConfigMapper;
import com.tyut.aiinterview.observability.OperationAuditService;
import com.tyut.aiinterview.security.CurrentUser;
import java.net.URI;
import java.net.http.HttpTimeoutException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AiProviderService {
    private static final List<String> KINDS = List.of("llm", "virtual-human", "speech", "asr", "tts");

    private final AiProviderConfigMapper mapper;
    private final ConfigSecretCodec secretCodec;
    private final CurrentUser currentUser;
    private final ObjectMapper objectMapper;
    private final String openTalkingUpstream;
    private final OperationAuditService auditService;
    private final HttpClient httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(8)).build();

    public AiProviderService(AiProviderConfigMapper mapper, ConfigSecretCodec secretCodec, CurrentUser currentUser,
                             ObjectMapper objectMapper,
                             @Value("${app.opentalking.upstream:${OPENTALKING_UPSTREAM:}}") String openTalkingUpstream) {
        this(mapper, secretCodec, currentUser, objectMapper, openTalkingUpstream, null);
    }

    @Autowired
    public AiProviderService(AiProviderConfigMapper mapper, ConfigSecretCodec secretCodec, CurrentUser currentUser,
                             ObjectMapper objectMapper,
                             @Value("${app.opentalking.upstream:${OPENTALKING_UPSTREAM:}}") String openTalkingUpstream,
                             OperationAuditService auditService) {
        this.mapper = mapper;
        this.secretCodec = secretCodec;
        this.currentUser = currentUser;
        this.objectMapper = objectMapper;
        this.openTalkingUpstream = trim(openTalkingUpstream);
        this.auditService = auditService;
    }

    @Transactional
    public List<AiProviderDtos.ProviderView> list() {
        requireAdmin();
        ensureDefaults();
        ensureOpenTalkingVirtualHuman();
        return mapper.selectList(new LambdaQueryWrapper<AiProviderConfig>().orderByAsc(AiProviderConfig::getKind).orderByAsc(AiProviderConfig::getId))
                .stream().map(this::toView).toList();
    }

    public Optional<RuntimeProvider> defaultVirtualHumanProvider() {
        List<AiProviderConfig> supported = mapper.selectList(new LambdaQueryWrapper<AiProviderConfig>()
                .eq(AiProviderConfig::getKind, "virtual-human")
                .eq(AiProviderConfig::getCode, "open-talking-virtual-human")
                .eq(AiProviderConfig::getEnabled, 1));
        return supported.stream().filter(item -> truthy(item.getVoiceDefault())).findFirst()
                .map(this::toRuntimeProvider);
    }

    /**
     * Returns the enabled LLM selected by the administrator. This lookup is
     * intentionally performed for every AI request so configuration changes
     * take effect without restarting the application.
     */
    public Optional<RuntimeProvider> defaultLlmProvider() {
        List<AiProviderConfig> enabled = mapper.selectList(new LambdaQueryWrapper<AiProviderConfig>()
                .eq(AiProviderConfig::getKind, "llm")
                .eq(AiProviderConfig::getEnabled, 1)
                .orderByAsc(AiProviderConfig::getId));
        return enabled.stream().filter(item -> truthy(item.getTextDefault())).findFirst()
                .or(() -> enabled.stream().filter(item -> "deepseek".equalsIgnoreCase(item.getCode())).findFirst())
                .or(() -> enabled.stream().findFirst())
                .map(this::toRuntimeProvider);
    }

    private RuntimeProvider toRuntimeProvider(AiProviderConfig config) {
        return new RuntimeProvider(
                config.getId(),
                config.getName(),
                config.getCode(),
                trim(config.getBaseUrl()),
                trim(config.getChatModel()),
                trim(config.getVoiceModel()),
                trim(config.getAvatarModel()),
                secretCodec.decrypt(config.getApiKeyCipher()),
                secretCodec.decrypt(config.getApiSecretCipher()),
                secretCodec.decrypt(config.getAppIdCipher()),
                trim(config.getRemark())
        );
    }

    @Transactional
    public AiProviderDtos.ProviderView create(AiProviderDtos.ProviderRequest request) {
        requireAdmin();
        validate(request);
        if (mapper.exists(new LambdaQueryWrapper<AiProviderConfig>().eq(AiProviderConfig::getCode, request.code().trim()))) {
            throw BusinessException.badRequest("Provider 编码已存在");
        }
        AiProviderConfig config = new AiProviderConfig();
        apply(config, request);
        config.setCreatedBy(currentUser.id());
        config.setUpdatedBy(currentUser.id());
        config.setCreatedAt(LocalDateTime.now());
        config.setUpdatedAt(LocalDateTime.now());
        mapper.insert(config);
        normalizeDefaults(config);
        audit("AI_PROVIDER_CREATED", config.getId(), "创建 Provider 配置 " + config.getCode());
        return toView(mapper.selectById(config.getId()));
    }

    @Transactional
    public AiProviderDtos.ProviderView update(Long id, AiProviderDtos.ProviderRequest request) {
        requireAdmin();
        validate(request);
        AiProviderConfig config = require(id);
        AiProviderConfig duplicated = mapper.selectOne(new LambdaQueryWrapper<AiProviderConfig>()
                .eq(AiProviderConfig::getCode, request.code().trim()).ne(AiProviderConfig::getId, id).last("LIMIT 1"));
        if (duplicated != null) throw BusinessException.badRequest("Provider 编码已存在");
        apply(config, request);
        config.setUpdatedBy(currentUser.id());
        config.setUpdatedAt(LocalDateTime.now());
        mapper.updateById(config);
        normalizeDefaults(config);
        audit("AI_PROVIDER_UPDATED", config.getId(), "更新 Provider 配置 " + config.getCode());
        return toView(mapper.selectById(id));
    }

    @Transactional
    public void delete(Long id) {
        requireAdmin();
        AiProviderConfig config = require(id);
        if (truthy(config.getTextDefault())) throw BusinessException.badRequest("当前是文字默认 Provider，请先切换默认项后再删除");
        if (truthy(config.getVoiceDefault())) throw BusinessException.badRequest("当前是语音默认 Provider，请先切换默认项后再删除");
        mapper.deleteById(id);
        audit("AI_PROVIDER_DELETED", id, "删除 Provider 配置 " + config.getCode());
    }

    public AiProviderDtos.ProviderTestResult test(Long id) {
        requireAdmin();
        AiProviderConfig config = require(id);
        AiProviderDtos.ProviderTestResult result = probe(config);
        audit("AI_PROVIDER_TEST", config.getId(), "Provider 测试完成，状态 " + result.state());
        return result;
    }

    private AiProviderDtos.ProviderTestResult probe(AiProviderConfig config) {
        long started = System.nanoTime();
        if (!truthy(config.getEnabled())) {
            return result(false, null, started, "Provider 已停用，请先启用后再测试");
        }
        if ("speech".equals(config.getKind())) {
            return result(true, null, started, "浏览器 Web Speech API 为客户端能力，服务端无需连通测试");
        }
        String baseUrl = trim(config.getBaseUrl());
        if (baseUrl.isBlank() || baseUrl.contains("待配置")) {
            return result(false, null, started, "Base URL 尚未配置");
        }
        final String apiKey;
        try {
            apiKey = secretCodec.decrypt(config.getApiKeyCipher());
        } catch (Exception exception) {
            return result(false, null, started, "FAILED", "Provider 密钥配置不可用，请重新保存配置");
        }
        if ("virtual-human".equals(config.getKind()) && "open-talking-virtual-human".equals(config.getCode())) {
            if (trim(config.getChatModel()).isBlank() || trim(config.getChatModel()).contains("待配置")
                    || trim(config.getAvatarModel()).isBlank() || trim(config.getAvatarModel()).contains("待配置")) {
                return result(false, null, started, "请填写 OpenTalking 模型和 Avatar ID");
            }
            try {
                HttpRequest request = HttpRequest.newBuilder(openTalkingHealthUri(baseUrl, openTalkingUpstream))
                        .timeout(Duration.ofSeconds(10)).GET().build();
                HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
                boolean success = response.statusCode() >= 200 && response.statusCode() < 300;
                return result(success, response.statusCode(), started,
                        success ? "OpenTalking 服务可用，浏览器会使用 WebRTC 连接数字人会话" : "OpenTalking /health 返回 HTTP " + response.statusCode());
            } catch (HttpTimeoutException exception) {
                return result(false, null, started, "TIMEOUT", "OpenTalking 连通性测试超时");
            } catch (Exception exception) {
                return result(false, null, started, "FAILED", "OpenTalking 连通性测试失败，请检查服务状态");
            }
        }
        try {
            if ("llm".equals(config.getKind())) return testLlm(config, baseUrl, apiKey, started);
            return testEndpoint(baseUrl, apiKey, started);
        } catch (HttpTimeoutException exception) {
            return result(false, null, started, "TIMEOUT", "Provider 连通性测试超时");
        } catch (Exception exception) {
            return result(false, null, started, "FAILED", "Provider 连通性测试失败，请检查服务状态");
        }
    }

    static URI openTalkingHealthUri(String browserBaseUrl, String upstream) {
        String configured = browserBaseUrl == null ? "" : browserBaseUrl.trim();
        if (configured.isBlank()) throw new IllegalArgumentException("OpenTalking Base URL 为空");

        URI configuredUri = URI.create(configured);
        String healthBase = configuredUri.isAbsolute() ? configured : upstream == null ? "" : upstream.trim();
        if (healthBase.isBlank()) {
            throw new IllegalStateException("相对 Base URL 需要配置 OPENTALKING_UPSTREAM");
        }
        if (!healthBase.matches("^[A-Za-z][A-Za-z0-9+.-]*://.*$")) healthBase = "http://" + healthBase;
        return URI.create(healthBase.replaceAll("/+$", "") + "/health");
    }

    private AiProviderDtos.ProviderTestResult testLlm(AiProviderConfig config, String baseUrl, String apiKey, long started) throws Exception {
        if (apiKey.isBlank()) return result(false, null, started, "API Key 尚未配置");
        String body = objectMapper.writeValueAsString(new ChatRequest(config.getChatModel(), List.of(
                new ChatMessage("system", "你是连通性探测服务，只需要回复 ok。"),
                new ChatMessage("user", "ping")
        ), 0.1, 8));
        HttpRequest request = HttpRequest.newBuilder(URI.create(baseUrl.replaceAll("/$", "") + "/chat/completions"))
                .timeout(Duration.ofSeconds(18))
                .header("Authorization", "Bearer " + apiKey)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();
        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        boolean success = response.statusCode() >= 200 && response.statusCode() < 300;
        return result(success, response.statusCode(), started, success ? "大模型连通正常" : "大模型返回 HTTP " + response.statusCode());
    }

    private AiProviderDtos.ProviderTestResult testEndpoint(String baseUrl, String apiKey, long started) throws Exception {
        HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(baseUrl))
                .timeout(Duration.ofSeconds(10))
                .method("GET", HttpRequest.BodyPublishers.noBody());
        if (!apiKey.isBlank()) builder.header("Authorization", "Bearer " + apiKey);
        HttpResponse<String> response = httpClient.send(builder.build(), HttpResponse.BodyHandlers.ofString());
        boolean success = response.statusCode() < 500;
        return result(success, response.statusCode(), started, success ? "服务地址可访问，请按供应商文档继续完成专用参数" : "服务地址返回 HTTP " + response.statusCode());
    }

    private void apply(AiProviderConfig config, AiProviderDtos.ProviderRequest request) {
        config.setName(request.name().trim());
        config.setCode(request.code().trim());
        config.setKind(request.kind().trim());
        config.setBaseUrl(trim(request.baseUrl()));
        config.setChatModel(trim(request.chatModel()));
        config.setVoiceModel(trim(request.voiceModel()));
        config.setAvatarModel(trim(request.avatarModel()));
        config.setApiKeyCipher(nextSecret(config.getApiKeyCipher(), request.apiKey()));
        config.setApiSecretCipher(nextSecret(config.getApiSecretCipher(), request.apiSecret()));
        config.setAppIdCipher(nextSecret(config.getAppIdCipher(), request.appId()));
        config.setEnabled(Boolean.FALSE.equals(request.enabled()) ? 0 : 1);
        config.setTextDefault(Boolean.TRUE.equals(request.textDefault()) ? 1 : 0);
        config.setVoiceDefault(Boolean.TRUE.equals(request.voiceDefault()) ? 1 : 0);
        config.setRemark(trim(request.remark()));
    }

    private String nextSecret(String oldCipher, String incoming) {
        // The settings UI explicitly states that an empty secret keeps the
        // previous value. Clearing it here silently invalidated an otherwise
        // valid provider after an operator changed only its avatar or voice
        // setting.
        if (incoming == null || incoming.isBlank()) return oldCipher == null ? "" : oldCipher;
        if (secretCodec.isMasked(incoming)) return oldCipher == null ? "" : oldCipher;
        return secretCodec.encrypt(incoming.trim());
    }

    private void validate(AiProviderDtos.ProviderRequest request) {
        if (request.kind() == null || !KINDS.contains(request.kind())) throw BusinessException.badRequest("Provider 类型不合法");
        if (Boolean.TRUE.equals(request.textDefault()) && !"llm".equals(request.kind())) throw BusinessException.badRequest("只有大模型 Provider 可以设为文字默认");
        if (Boolean.TRUE.equals(request.voiceDefault()) && "llm".equals(request.kind())) throw BusinessException.badRequest("大模型 Provider 不能设为语音默认");
    }

    private void normalizeDefaults(AiProviderConfig current) {
        if (truthy(current.getTextDefault())) {
            mapper.selectList(new LambdaQueryWrapper<AiProviderConfig>().eq(AiProviderConfig::getTextDefault, 1).ne(AiProviderConfig::getId, current.getId()))
                    .forEach(item -> {
                        item.setTextDefault(0);
                        mapper.updateById(item);
                    });
        }
        if (truthy(current.getVoiceDefault())) {
            mapper.selectList(new LambdaQueryWrapper<AiProviderConfig>().eq(AiProviderConfig::getVoiceDefault, 1).ne(AiProviderConfig::getId, current.getId()))
                    .forEach(item -> {
                        item.setVoiceDefault(0);
                        mapper.updateById(item);
                    });
        }
    }

    private AiProviderConfig require(Long id) {
        AiProviderConfig config = mapper.selectById(id);
        if (config == null) throw BusinessException.notFound("Provider 配置不存在");
        return config;
    }

    private AiProviderDtos.ProviderView toView(AiProviderConfig config) {
        return new AiProviderDtos.ProviderView(
                config.getId(),
                config.getName(),
                config.getCode(),
                config.getKind(),
                config.getBaseUrl(),
                config.getChatModel(),
                config.getVoiceModel(),
                config.getAvatarModel(),
                mask(secretCodec.decrypt(config.getApiKeyCipher())),
                mask(secretCodec.decrypt(config.getApiSecretCipher())),
                mask(secretCodec.decrypt(config.getAppIdCipher())),
                truthy(config.getEnabled()),
                truthy(config.getTextDefault()),
                truthy(config.getVoiceDefault()),
                config.getRemark()
        );
    }

    private void ensureDefaults() {
        if (mapper.selectCount(null) > 0) return;
        insertDefault("DeepSeek", "deepseek", "llm", "https://api.deepseek.com", "deepseek-v4-flash", "不支持", "不支持", true, true, false,
                "当前 AI 面试官的大模型服务，负责提问、追问、评分和报告总结。");
        insertDefault("浏览器语音", "browser-speech", "speech", "Web Speech API", "不支持", "speechSynthesis / SpeechRecognition", "不支持", true, false, true,
                "本地浏览器语音朗读和语音识别兜底方案，无需服务端密钥。");
        insertDefault("语音识别服务", "asr-provider", "asr", "待配置", "不支持", "ASR", "不支持", false, false, false,
                "用于候选人语音回答转文字，可后续接入其他 ASR 服务。");
    }

    private void ensureOpenTalkingVirtualHuman() {
        if (mapper.exists(new LambdaQueryWrapper<AiProviderConfig>().eq(AiProviderConfig::getCode, "open-talking-virtual-human"))) {
            return;
        }
        insertDefault("OpenTalking 开源虚拟人", "open-talking-virtual-human", "virtual-human", "/opentalking",
                "mock", "zh-CN-XiaoxiaoNeural", "请填写 OpenTalking Avatar ID", false, false, false,
                "OpenTalking 实时数字人接入项。先以 mock 模型验证 WebRTC、TTS 和面试流程；部署 QuickTalk 后将模型改为 quicktalk。APP ID 字段填写 TTS Provider（默认 edge）。");
    }

    private void insertDefault(String name, String code, String kind, String baseUrl, String chatModel, String voiceModel,
                               String avatarModel, boolean enabled, boolean textDefault, boolean voiceDefault, String remark) {
        AiProviderConfig config = new AiProviderConfig();
        config.setName(name);
        config.setCode(code);
        config.setKind(kind);
        config.setBaseUrl(baseUrl);
        config.setChatModel(chatModel);
        config.setVoiceModel(voiceModel);
        config.setAvatarModel(avatarModel);
        config.setApiKeyCipher("");
        config.setApiSecretCipher("");
        config.setAppIdCipher("");
        config.setEnabled(enabled ? 1 : 0);
        config.setTextDefault(textDefault ? 1 : 0);
        config.setVoiceDefault(voiceDefault ? 1 : 0);
        config.setRemark(remark);
        config.setCreatedBy(currentUser.id());
        config.setUpdatedBy(currentUser.id());
        mapper.insert(config);
    }

    private AiProviderDtos.ProviderTestResult result(boolean success, Integer statusCode, long started, String message) {
        return result(success, statusCode, started, success ? "SUCCESS" : "FAILED", message);
    }

    private AiProviderDtos.ProviderTestResult result(boolean success, Integer statusCode, long started,
                                                     String state, String message) {
        return new AiProviderDtos.ProviderTestResult(success, statusCode, state,
                Math.max(1, (System.nanoTime() - started) / 1_000_000), message);
    }

    private void requireAdmin() {
        if (!currentUser.hasRole("ADMIN")) throw BusinessException.forbidden("仅管理员可管理系统配置");
    }

    private void audit(String action, Long providerId, String summary) {
        if (auditService != null) auditService.success("AI_PROVIDER", action, "AI_PROVIDER", providerId, null, summary);
    }

    private boolean truthy(Integer value) {
        return value != null && value == 1;
    }

    private String trim(String value) {
        return value == null ? "" : value.trim();
    }

    private String mask(String value) {
        if (value == null || value.isBlank()) return "";
        if (value.length() <= 8) return value.charAt(0) + "***" + value.charAt(value.length() - 1);
        return value.substring(0, 4) + "****" + value.substring(value.length() - 4);
    }

    private String shortMessage(Exception exception) {
        String message = exception.getMessage();
        if (message == null || message.isBlank()) return exception.getClass().getSimpleName();
        return message.length() > 160 ? message.substring(0, 160) : message;
    }

    private record ChatRequest(String model, List<ChatMessage> messages, double temperature, int max_tokens) {
        private ChatRequest {
            if (model == null || model.isBlank()) model = "deepseek-v4-flash";
        }
    }

    private record ChatMessage(String role, String content) {
    }

    public record RuntimeProvider(
            Long id,
            String name,
            String code,
            String baseUrl,
            String serviceId,
            String voiceModel,
            String avatarModel,
            String apiKey,
            String apiSecret,
            String appId,
            String remark
    ) {
    }
}
