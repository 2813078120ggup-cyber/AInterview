package com.tyut.aiinterview.common;

import java.time.LocalDateTime;
import org.slf4j.MDC;

public record ApiResponse<T>(int code, String message, T data, String requestId, LocalDateTime timestamp) {
    public static <T> ApiResponse<T> ok(T data) {
        return new ApiResponse<>(0, "success", data, currentRequestId(), LocalDateTime.now());
    }

    public static ApiResponse<Void> ok() {
        return ok(null);
    }

    public static ApiResponse<Void> failure(int code, String message) {
        return new ApiResponse<>(code, message, null, currentRequestId(), LocalDateTime.now());
    }

    private static String currentRequestId() {
        String requestId = MDC.get("requestId");
        return requestId == null ? "unknown" : requestId;
    }
}
