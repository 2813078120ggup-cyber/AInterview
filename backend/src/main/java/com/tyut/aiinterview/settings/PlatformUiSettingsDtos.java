package com.tyut.aiinterview.settings;

import jakarta.validation.constraints.NotNull;

public final class PlatformUiSettingsDtos {
    private PlatformUiSettingsDtos() {
    }

    public record View(boolean mouseFollowerEnabled) {
    }

    public record UpdateRequest(@NotNull(message = "鼠标跟随状态不能为空") Boolean mouseFollowerEnabled) {
    }
}
