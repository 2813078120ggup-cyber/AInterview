package com.tyut.aiinterview.config;

import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import com.fasterxml.jackson.databind.module.SimpleModule;
import org.springframework.boot.jackson2.autoconfigure.Jackson2ObjectMapperBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class JsonSerializationConfig {
    @Bean
    public Jackson2ObjectMapperBuilderCustomizer longAsStringCustomizer() {
        return builder -> builder.serializerByType(Long.class, ToStringSerializer.instance)
                .serializerByType(Long.TYPE, ToStringSerializer.instance);
    }

    @Bean
    public SimpleModule longAsStringModule() {
        SimpleModule module = new SimpleModule("ainterview-long-as-string");
        module.addSerializer(Long.class, ToStringSerializer.instance);
        module.addSerializer(Long.TYPE, ToStringSerializer.instance);
        return module;
    }

    @Bean
    public tools.jackson.databind.module.SimpleModule longAsStringJackson3Module() {
        tools.jackson.databind.module.SimpleModule module = new tools.jackson.databind.module.SimpleModule("ainterview-long-as-string-jackson3");
        module.addSerializer(Long.class, tools.jackson.databind.ser.std.ToStringSerializer.instance);
        module.addSerializer(Long.TYPE, tools.jackson.databind.ser.std.ToStringSerializer.instance);
        return module;
    }
}
