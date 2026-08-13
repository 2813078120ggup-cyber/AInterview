package com.tyut.aiinterview.observability;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.tyut.aiinterview.domain.OperationAuditLog;
import com.tyut.aiinterview.mapper.OperationAuditLogMapper;
import com.tyut.aiinterview.security.CurrentUser;
import com.tyut.aiinterview.security.LoginUser;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.slf4j.MDC;

class OperationAuditServiceTest {
    private final OperationAuditLogMapper mapper = org.mockito.Mockito.mock(OperationAuditLogMapper.class);
    private final CurrentUser currentUser = org.mockito.Mockito.mock(CurrentUser.class);
    private final OperationAuditService service = new OperationAuditService(mapper, currentUser);

    @AfterEach
    void clearRequestContext() {
        MDC.clear();
    }

    @Test
    void persistsSafeAppendOnlyRecordWithRequestAndActorContext() {
        MDC.put("requestId", "req-123");
        when(currentUser.id()).thenReturn(7L);
        when(currentUser.require()).thenReturn(new LoginUser(7L, "admin", "not-used", true,
                List.of("ADMIN"), null));

        service.success("AI_PROVIDER", "AI_PROVIDER_UPDATED", "AI_PROVIDER", 9L, null,
                "更新配置 password=secret token=abc code=123456 email=person@example.com phone=13800000000 "
                        + "jwt=eyJhbGciOiJIUzI1NiJ9.payload.signature prompt=private resumeRaw=full-resume");

        ArgumentCaptor<OperationAuditLog> captor = ArgumentCaptor.forClass(OperationAuditLog.class);
        verify(mapper).insert(captor.capture());
        OperationAuditLog saved = captor.getValue();
        assertTrue("req-123".equals(saved.getRequestId()));
        assertTrue("SUCCESS".equals(saved.getResult()));
        assertTrue(saved.getSummary().contains("password=[REDACTED]"));
        assertFalse(saved.getSummary().contains("secret"));
        assertFalse(saved.getSummary().contains("private"));
        assertFalse(saved.getSummary().contains("full-resume"));
        assertFalse(saved.getSummary().contains("abc"));
        assertFalse(saved.getSummary().contains("person@example.com"));
        assertFalse(saved.getSummary().contains("13800000000"));
        assertFalse(saved.getSummary().contains("eyJhbGciOiJIUzI1NiJ9"));
        assertTrue(saved.getCreatedAt() != null);
    }

    @Test
    void normalizesUnknownResultToFailureAndDoesNotAcceptPayloadAsResource() {
        when(currentUser.id()).thenThrow(new RuntimeException("no request user"));

        service.record("REPORT", "REPORT_GENERATED", "REPORT", 11L, "unexpected", "ok",
                null, null, null);

        ArgumentCaptor<OperationAuditLog> captor = ArgumentCaptor.forClass(OperationAuditLog.class);
        verify(mapper).insert(captor.capture());
        assertTrue("FAILURE".equals(captor.getValue().getResult()));
        assertTrue("11".equals(captor.getValue().getResourceId()));
        assertFalse(captor.getValue().getSummary().contains("no request user"));
    }
}
