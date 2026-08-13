package com.tyut.aiinterview.account;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.tyut.aiinterview.common.GlobalExceptionHandler;
import com.tyut.aiinterview.common.PageResult;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class AccountSecurityEventControllerTest {
    private final AccountSecurityEventService service = org.mockito.Mockito.mock(AccountSecurityEventService.class);
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.standaloneSetup(new AccountSecurityEventController(service))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void getReturnsPagedWhitelistAndIgnoresForeignUserId() throws Exception {
        var event = new AccountSecurityEventDtos.SecurityEvent("PASSWORD_LOGIN", "SUCCESS", "密码登录成功",
                "192.168.1.*", "Chrome · Windows · 桌面设备", LocalDateTime.of(2026, 8, 12, 9, 0));
        when(service.page(any())).thenReturn(PageResult.of(List.of(event), 1, 2, 15));

        mvc.perform(get("/v1/account/security-events")
                        .queryParam("pageNo", "2").queryParam("pageSize", "15").queryParam("userId", "999"))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("PASSWORD_LOGIN")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("192.168.1.*")))
                .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("tokenHash"))))
                .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("userAgent"))))
                .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("ipAddress"))))
                .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("resourceId"))));

        ArgumentCaptor<AccountSecurityEventDtos.Query> query = ArgumentCaptor.forClass(AccountSecurityEventDtos.Query.class);
        verify(service).page(query.capture());
        org.junit.jupiter.api.Assertions.assertEquals(2L, query.getValue().pageNo());
        org.junit.jupiter.api.Assertions.assertEquals(15L, query.getValue().pageSize());
    }

    @Test
    void deleteIsNotExposed() throws Exception {
        mvc.perform(delete("/v1/account/security-events"))
                .andExpect(status().isMethodNotAllowed());
    }
}
