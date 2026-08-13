package com.tyut.aiinterview.observability;

import static org.assertj.core.api.Assertions.assertThat;

import com.tyut.aiinterview.AiInterviewApplication;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.TestRestTemplate;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureTestRestTemplate;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalManagementPort;
import org.springframework.http.ResponseEntity;

@SpringBootTest(
        classes = AiInterviewApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {"management.server.port=0", "DEEPSEEK_ENABLED=false"})
@AutoConfigureTestRestTemplate
class ActuatorEndpointTest {
    @LocalManagementPort
    private int managementPort;

    @Autowired
    private TestRestTemplate restTemplate;

    @Test
    void exposesPrometheusMetricsOnTheDedicatedManagementPort() {
        ResponseEntity<String> response = restTemplate.getForEntity(
                "http://127.0.0.1:" + managementPort + "/actuator/prometheus", String.class);

        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(response.getBody()).contains("ai_interview_ai_tasks");
    }
}
