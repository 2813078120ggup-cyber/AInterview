package com.tyut.aiinterview.virtualhuman;

public final class VirtualHumanDtos {
    private VirtualHumanDtos() {
    }

    /** Browser-safe configuration for the selected virtual-human runtime. */
    public record SdkConfigResponse(
            boolean enabled,
            String provider,
            String status,
            String message,
            String signedUrl,
            String appId,
            String sceneId,
            String avatarId,
            String vcn,
            String protocol,
            String endpoint
    ) {
    }
}
