package com.tyut.aiinterview;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.tyut.aiinterview.prompt.PromptCatalog;
import com.tyut.aiinterview.prompt.PromptTemplateService;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
class AiInterviewApplicationContextTest {
    @Autowired
    private PromptTemplateService promptTemplates;

    @Test
    void applicationContextLoads() {
    }

    @Test
    void activeFollowUpPromptRendersAfterFlywayMigration() {
        PromptTemplateService.RenderedPrompt prompt = promptTemplates.render(PromptCatalog.SIMULATION_FOLLOW_UP,
                Map.of("interviewerStyle", "严谨", "originalQuestion", "什么是 volatile？", "answer", "保证可见性。"));

        assertTrue(prompt.userPrompt().contains("什么是 volatile？"));
        assertFalse(prompt.userPrompt().contains("${"));
        assertTrue(prompt.userPrompt().contains("不必每次都先表扬"));
    }
}
