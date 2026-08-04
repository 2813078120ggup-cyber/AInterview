package com.tyut.aiinterview.virtualhuman;

import com.tyut.aiinterview.settings.AiProviderService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Browser-safe configuration boundary for supported virtual-human runtimes.
 */
@Service
public class VirtualHumanService {
    private static final Logger log = LoggerFactory.getLogger(VirtualHumanService.class);
    private static final String OPENTALKING_PROVIDER_CODE = "open-talking-virtual-human";
    private final AiProviderService aiProviderService;

    public VirtualHumanService(AiProviderService aiProviderService) {
        this.aiProviderService = aiProviderService;
    }

    public VirtualHumanDtos.SdkConfigResponse sdkConfig() {
        return aiProviderService.defaultVirtualHumanProvider()
                .filter(this::isConfigured)
                .map(provider -> {
                    return openTalkingConfig(provider);
                })
                .orElseGet(() -> unavailable("未找到已启用且配置完整的 OpenTalking Provider，请在系统设置中启用并设为语音默认。"));
    }

    private VirtualHumanDtos.SdkConfigResponse openTalkingConfig(AiProviderService.RuntimeProvider provider) {
        String endpoint = provider.baseUrl().replaceAll("/+$", "");
        String ttsProvider = provider.appId().isBlank() ? "edge" : provider.appId();
        log.info("Preparing OpenTalking WebRTC session config with providerCode={}, providerId={}, endpoint={}, avatar={}, model={}",
                provider.code(), provider.id(), endpoint, provider.avatarModel(), provider.serviceId());
        return new VirtualHumanDtos.SdkConfigResponse(
                true, OPENTALKING_PROVIDER_CODE, "READY", "OpenTalking WebRTC 配置已就绪。",
                "", ttsProvider, provider.serviceId(), provider.avatarModel(), provider.voiceModel(), "opentalking", endpoint);
    }

    private boolean isConfigured(AiProviderService.RuntimeProvider provider) {
        return OPENTALKING_PROVIDER_CODE.equalsIgnoreCase(provider.code())
                && !provider.baseUrl().isBlank()
                && !provider.baseUrl().contains("待配置")
                && !provider.serviceId().isBlank()
                && !provider.serviceId().contains("待配置")
                && !provider.avatarModel().isBlank()
                && !provider.avatarModel().contains("待配置");
    }

    private VirtualHumanDtos.SdkConfigResponse unavailable(String message) {
        return new VirtualHumanDtos.SdkConfigResponse(false, OPENTALKING_PROVIDER_CODE, "UNAVAILABLE", message,
                "", "", "", "", "", "opentalking", "");
    }
}
