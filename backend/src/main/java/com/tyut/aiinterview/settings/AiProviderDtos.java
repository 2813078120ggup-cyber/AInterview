package com.tyut.aiinterview.settings;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public final class AiProviderDtos {
    private AiProviderDtos() {
    }

    public record ProviderRequest(
            @NotBlank(message = "Provider 名称不能为空") String name,
            @NotBlank(message = "Provider 编码不能为空") String code,
            @Pattern(regexp = "llm|virtual-human|speech|asr|tts", message = "Provider 类型不合法") String kind,
            String baseUrl,
            String chatModel,
            String voiceModel,
            String avatarModel,
            String apiKey,
            String apiSecret,
            String appId,
            Boolean enabled,
            Boolean textDefault,
            Boolean voiceDefault,
            String remark
    ) {
    }

    public record ProviderView(
            Long id,
            String name,
            String code,
            String kind,
            String baseUrl,
            String chatModel,
            String voiceModel,
            String avatarModel,
            String apiKey,
            String apiSecret,
            String appId,
            boolean enabled,
            boolean textDefault,
            boolean voiceDefault,
            String remark
    ) {
    }

    public record ProviderTestResult(
            boolean success,
            Integer statusCode,
            long latencyMs,
            String message
    ) {
    }
}
