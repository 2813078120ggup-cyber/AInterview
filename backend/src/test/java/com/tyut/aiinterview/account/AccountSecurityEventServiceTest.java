package com.tyut.aiinterview.account;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.tyut.aiinterview.common.PageResult;
import com.tyut.aiinterview.domain.OperationAuditLog;
import com.tyut.aiinterview.domain.UserAccount;
import com.tyut.aiinterview.mapper.OperationAuditLogMapper;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class AccountSecurityEventServiceTest {
    private final OperationAuditLogMapper mapper = org.mockito.Mockito.mock(OperationAuditLogMapper.class);
    private final AccountService accountService = org.mockito.Mockito.mock(AccountService.class);
    private final AccountSecurityEventService service = new AccountSecurityEventService(mapper, accountService);

    @BeforeEach
    void setUp() {
        UserAccount current = new UserAccount();
        current.setId(7L);
        when(accountService.requireCurrentUser()).thenReturn(current);
    }

    @Test
    @SuppressWarnings({"rawtypes", "unchecked"})
    void scopesQueryToCurrentUserAndReturnsOnlyMaskedWhitelist() {
        OperationAuditLog login = log("AUTHENTICATION", "AUTH_LOGIN_SUCCESS", "SUCCESS",
                "密码登录成功 candidate@example.com token=should-not-leak", "203.0.113.45", chromeWindows());
        login.setActorId(null);
        login.setResourceType("USER");
        login.setResourceId("7");
        when(mapper.selectPage(any(Page.class), any(Wrapper.class))).thenAnswer(invocation -> {
            Page<OperationAuditLog> page = invocation.getArgument(0);
            page.setRecords(List.of(login));
            page.setTotal(31);
            return page;
        });

        PageResult<AccountSecurityEventDtos.SecurityEvent> result = service.page(
                new AccountSecurityEventDtos.Query(2L, 500L));

        assertEquals(2, result.pageNo());
        assertEquals(50, result.pageSize());
        assertEquals(31, result.total());
        assertEquals(1, result.records().size());
        AccountSecurityEventDtos.SecurityEvent event = result.records().get(0);
        assertEquals("PASSWORD_LOGIN", event.eventType());
        assertEquals("SUCCESS", event.result());
        assertEquals("密码登录成功", event.summary());
        assertEquals("203.0.113.*", event.maskedIp());
        assertEquals("Chrome · Windows · 桌面设备", event.deviceSummary());
        assertFalse(event.summary().contains("example.com"));
        assertFalse(event.summary().contains("token"));

        ArgumentCaptor<Wrapper<OperationAuditLog>> wrapperCaptor = ArgumentCaptor.forClass(Wrapper.class);
        org.mockito.Mockito.verify(mapper).selectPage(any(Page.class), wrapperCaptor.capture());
        String sql = wrapperCaptor.getValue().getSqlSegment();
        assertTrue(sql.contains("actor_id"));
        assertTrue(sql.contains("resource_type"));
        assertTrue(sql.contains("resource_id"));
        assertTrue(sql.contains("created_at"));
        QueryWrapper<OperationAuditLog> queryWrapper =
                (QueryWrapper<OperationAuditLog>) wrapperCaptor.getValue();
        assertTrue(queryWrapper.getParamNameValuePairs().containsValue(7L));
        assertTrue(queryWrapper.getParamNameValuePairs().containsValue("7"));
        assertTrue(queryWrapper.getParamNameValuePairs().containsValue("AUTHENTICATION"));
        assertTrue(queryWrapper.getParamNameValuePairs().containsValue("USER_MANAGEMENT"));
    }

    @Test
    @SuppressWarnings({"rawtypes", "unchecked"})
    void mapsSupportedAuditCoverageToStableBusinessEvents() {
        List<OperationAuditLog> logs = List.of(
                log("AUTHENTICATION", "AUTH_LOGIN_CODE_FAILED", "FAILURE", "验证码登录失败", null, null),
                log("AUTHENTICATION", "AUTH_SESSION_CREATED", "SUCCESS", "创建新的密码登录会话", null, null),
                log("ACCOUNT", "PASSWORD_CHANGED", "SUCCESS", "登录密码已修改", null, null),
                log("AUTHENTICATION", "PASSWORD_RESET_SUCCESS", "SUCCESS", "登录密码已重置", null, null),
                log("ACCOUNT", "CONTACT_CHANGED", "SUCCESS", "变更手机号并撤销其他会话", null, null),
                log("ACCOUNT", "CONTACT_CHANGED", "SUCCESS", "变更邮箱并撤销其他会话", null, null),
                log("ACCOUNT", "AVATAR_REPLACED", "SUCCESS", "替换本人头像", null, null),
                log("ACCOUNT", "PROFILE_UPDATED", "SUCCESS", "更新本人账户资料", null, null),
                log("ACCOUNT", "SESSION_REVOKE", "SUCCESS", "撤销本人登录设备会话", null, null),
                log("ACCOUNT", "OTHER_SESSIONS_REVOKE", "SUCCESS", "撤销其他设备", null, null),
                log("USER_MANAGEMENT", "USER_STATUS_UPDATED", "SUCCESS", "管理员停用账号", null, null));
        when(mapper.selectPage(any(Page.class), any(Wrapper.class))).thenAnswer(invocation -> {
            Page<OperationAuditLog> page = invocation.getArgument(0);
            page.setRecords(logs);
            page.setTotal(logs.size());
            return page;
        });

        List<AccountSecurityEventDtos.SecurityEvent> events = service.page(null).records();

        assertEquals(List.of(
                        "VERIFICATION_CODE_LOGIN", "NEW_SESSION", "PASSWORD_CHANGED", "PASSWORD_RESET",
                        "PHONE_CHANGED", "EMAIL_CHANGED", "AVATAR_CHANGED", "PROFILE_CHANGED",
                        "SESSION_REVOKED", "OTHER_SESSIONS_REVOKED", "ACCOUNT_STATUS_CHANGED"),
                events.stream().map(AccountSecurityEventDtos.SecurityEvent::eventType).toList());
        assertEquals("FAILURE", events.get(0).result());
        assertEquals("管理员已停用账号", events.get(10).summary());
    }

    private OperationAuditLog log(String module, String action, String result, String summary,
                                  String ip, String userAgent) {
        OperationAuditLog log = new OperationAuditLog();
        log.setId(1L);
        log.setActorId(7L);
        log.setModule(module);
        log.setAction(action);
        log.setResult(result);
        log.setSummary(summary);
        log.setIpAddress(ip);
        log.setUserAgent(userAgent);
        log.setCreatedAt(LocalDateTime.of(2026, 8, 12, 10, 30));
        return log;
    }

    private String chromeWindows() {
        return "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 "
                + "(KHTML, like Gecko) Chrome/127.0.0.0 Safari/537.36";
    }
}
