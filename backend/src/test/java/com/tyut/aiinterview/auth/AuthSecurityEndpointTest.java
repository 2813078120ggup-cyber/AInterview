package com.tyut.aiinterview.auth;

import static org.assertj.core.api.Assertions.assertThat;

import com.tyut.aiinterview.AiInterviewApplication;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.TestRestTemplate;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureTestRestTemplate;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.ResponseEntity;

@SpringBootTest(classes = AiInterviewApplication.class, webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "DEEPSEEK_ENABLED=false")
@AutoConfigureTestRestTemplate
class AuthSecurityEndpointTest {
    @LocalServerPort
    private int port;

    @Autowired
    private TestRestTemplate restTemplate;

    @Test
    void unauthenticatedProtectedAuthEndpointReturns401() {
        ResponseEntity<String> response = restTemplate.getForEntity(
                "http://127.0.0.1:" + port + "/api/v1/auth/me", String.class);

        assertThat(response.getStatusCode().value()).isEqualTo(401);
    }

    @Test
    void unauthenticatedLogoutCannotRevokeRefreshToken() {
        ResponseEntity<String> response = restTemplate.postForEntity(
                "http://127.0.0.1:" + port + "/api/v1/auth/logout",
                new AuthDtos.LogoutRequest("not-a-real-refresh-token"), String.class);

        assertThat(response.getStatusCode().value()).isEqualTo(401);
    }

    @Test
    void passwordResetEndpointsArePublicButValidated() {
        ResponseEntity<String> sendResponse = restTemplate.postForEntity(
                "http://127.0.0.1:" + port + "/api/v1/auth/password/reset/code",
                new AuthDtos.PasswordResetCodeRequest("", ""), String.class);
        ResponseEntity<String> resetResponse = restTemplate.postForEntity(
                "http://127.0.0.1:" + port + "/api/v1/auth/password/reset",
                new AuthDtos.PasswordResetRequest("", "", "", ""), String.class);

        assertThat(sendResponse.getStatusCode().value()).isEqualTo(400);
        assertThat(resetResponse.getStatusCode().value()).isEqualTo(400);
    }

    @Test
    void securityEventsRequireAuthenticationAndCannotBeDeleted() {
        ResponseEntity<String> getResponse = restTemplate.getForEntity(
                "http://127.0.0.1:" + port + "/api/v1/account/security-events", String.class);
        ResponseEntity<String> deleteResponse = restTemplate.exchange(
                "http://127.0.0.1:" + port + "/api/v1/account/security-events",
                org.springframework.http.HttpMethod.DELETE, null, String.class);

        assertThat(getResponse.getStatusCode().value()).isEqualTo(401);
        assertThat(deleteResponse.getStatusCode().value()).isEqualTo(401);
    }
}
