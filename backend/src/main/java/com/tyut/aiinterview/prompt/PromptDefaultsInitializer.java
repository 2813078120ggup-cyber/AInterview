package com.tyut.aiinterview.prompt;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

@Component
public class PromptDefaultsInitializer implements ApplicationRunner {
    private final PromptTemplateService service;

    public PromptDefaultsInitializer(PromptTemplateService service) {
        this.service = service;
    }

    @Override
    public void run(ApplicationArguments args) {
        Map<String, PromptTemplateService.DefaultTemplate> defaults = new LinkedHashMap<>();
        for (PromptCatalog.Definition definition : PromptCatalog.definitions()) {
            defaults.put(definition.code(), new PromptTemplateService.DefaultTemplate(
                    read(definition.code() + ".system.txt"), read(definition.code() + ".user.txt")));
        }
        service.ensureDefaults(defaults);
    }

    private String read(String name) {
        try {
            return new ClassPathResource("prompts/defaults/" + name).getContentAsString(StandardCharsets.UTF_8);
        } catch (IOException exception) {
            throw new IllegalStateException("无法读取默认提示词资源：" + name, exception);
        }
    }
}
