package com.tyut.aiinterview.account;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.tyut.aiinterview.common.GlobalExceptionHandler;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class AccountControllerTest {
    private final AccountService service = org.mockito.Mockito.mock(AccountService.class);
    private final AccountAvatarService avatarService = org.mockito.Mockito.mock(AccountAvatarService.class);
    private final AccountContactService contactService = org.mockito.Mockito.mock(AccountContactService.class);
    private final AccountPasswordService passwordService = org.mockito.Mockito.mock(AccountPasswordService.class);
    private final AccountSessionService sessionService = org.mockito.Mockito.mock(AccountSessionService.class);
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.standaloneSetup(new AccountController(service, avatarService, contactService,
                        passwordService, sessionService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void profileRouteIgnoresCallerSuppliedUserIdAndReturnsOnlyWhitelist() throws Exception {
        when(service.profile()).thenReturn(profile());

        mvc.perform(get("/v1/account/profile").queryParam("userId", "999"))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("candidate")))
                .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("passwordHash"))))
                .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("roleId"))))
                .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("companyId"))))
                .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("deletedAt"))));

        verify(service).profile();
    }

    @Test
    void updateRouteAcceptsOnlyRealNameAndVersion() throws Exception {
        when(service.updateProfile(any())).thenReturn(profile());

        mvc.perform(put("/v1/account/profile")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"realName\":\" 新姓名 \",\"version\":3,\"role\":\"ADMIN\",\"status\":0,\"companyId\":999}"))
                .andExpect(status().isOk());

        ArgumentCaptor<AccountDtos.UpdateProfileRequest> captor = ArgumentCaptor.forClass(AccountDtos.UpdateProfileRequest.class);
        verify(service).updateProfile(captor.capture());
        org.junit.jupiter.api.Assertions.assertEquals(" 新姓名 ", captor.getValue().realName());
        org.junit.jupiter.api.Assertions.assertEquals(3, captor.getValue().version());
    }

    @Test
    void avatarContentUsesPrivateInlineResponseHeaders() throws Exception {
        org.springframework.core.io.ByteArrayResource resource = new org.springframework.core.io.ByteArrayResource(new byte[]{1, 2, 3});
        when(avatarService.content()).thenReturn(new AccountAvatarService.AvatarContent("image/png", resource));

        mvc.perform(get("/v1/account/avatar/content"))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.IMAGE_PNG))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.header().longValue("Content-Length", 3L))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.header().string("Cache-Control", "private, no-store"))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.header().string("X-Content-Type-Options", "nosniff"))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.header().string("Content-Disposition", "inline"));
    }

    @Test
    void contactCodeAndChangeRoutesUseCurrentAccountService() throws Exception {
        when(contactService.sendCode("PHONE", new AccountDtos.ChangeCodeRequest("13800001111")))
                .thenReturn(new AccountDtos.ChangeCodeResponse(60, 300));
        when(contactService.change(eq("PHONE"), any(), any(), any())).thenReturn(
                new AccountDtos.ContactChangeResponse(profile(), "access-token", "refresh-token"));

        mvc.perform(post("/v1/account/phone/code")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"target\":\"13800001111\"}"))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("cooldownSeconds")));
        mvc.perform(put("/v1/account/phone")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"target\":\"13800001111\",\"verificationCode\":\"123456\",\"currentPassword\":\"password\",\"refreshToken\":\"opaque\",\"version\":3}"))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("access-token")));

        verify(contactService).sendCode("PHONE", new AccountDtos.ChangeCodeRequest("13800001111"));
        verify(contactService).change(eq("PHONE"), any(), any(), any());
    }

    @Test
    void passwordChangeRouteAcceptsCredentialsWithoutUserId() throws Exception {
        when(passwordService.change(any(), any(), any())).thenReturn(
                new AccountDtos.ChangePasswordResponse("new-access", "new-refresh", "当前设备继续登录"));

        mvc.perform(post("/v1/account/password/change")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"currentPassword\":\"Current123!\",\"newPassword\":\"Next12345!\",\"refreshToken\":\"opaque\",\"userId\":999}"))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("new-access")))
                .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("userId"))));

        verify(passwordService).change(any(), any(), any());
    }

    @Test
    void passwordChangeRouteEnforcesSharedCredentialPolicy() throws Exception {
        mvc.perform(post("/v1/account/password/change")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"currentPassword\":\"Current123!\",\"newPassword\":\"中文 password\",\"refreshToken\":\"opaque\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("8-64")));

        org.mockito.Mockito.verifyNoInteractions(passwordService);
    }

    @Test
    void sessionRoutesReturnOnlyWhitelistAndDelegateRevocation() throws Exception {
        when(sessionService.sessions()).thenReturn(List.of(new AccountDtos.AccountSession(
                "session-a", true, "DESKTOP", "Chrome", "Windows", "192.168.1.*",
                LocalDateTime.of(2026, 8, 1, 9, 0), LocalDateTime.of(2026, 8, 12, 9, 0),
                LocalDateTime.of(2026, 9, 1, 9, 0))));

        mvc.perform(get("/v1/account/sessions"))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("session-a")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("192.168.1.*")))
                .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("tokenHash"))))
                .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("userAgent"))))
                .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("clientIp"))));
        mvc.perform(delete("/v1/account/sessions/session-a")).andExpect(status().isOk());
        mvc.perform(delete("/v1/account/sessions/others")).andExpect(status().isOk());

        verify(sessionService).revoke("session-a");
        verify(sessionService).revokeOthers();
    }

    private AccountDtos.AccountProfile profile() {
        return new AccountDtos.AccountProfile(11L, "candidate", "候选人", "CANDIDATE", 1,
                false, "candidate@example.com", "c***@example.com", true,
                "13800000000", "138****0000", true, List.of("PASSWORD", "SMS"),
                LocalDateTime.of(2026, 8, 12, 9, 0), LocalDateTime.of(2026, 8, 1, 9, 0), 3);
    }
}
