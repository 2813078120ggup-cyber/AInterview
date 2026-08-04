package com.tyut.aiinterview.settings;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
public class AiProviderSchemaInitializer {
    public AiProviderSchemaInitializer(JdbcTemplate jdbcTemplate) {
        jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS ai_provider_config (
                  id BIGINT PRIMARY KEY AUTO_INCREMENT,
                  name VARCHAR(80) NOT NULL,
                  code VARCHAR(80) NOT NULL,
                  kind VARCHAR(32) NOT NULL,
                  base_url VARCHAR(255) NULL,
                  chat_model VARCHAR(120) NULL,
                  voice_model VARCHAR(120) NULL,
                  avatar_model VARCHAR(120) NULL,
                  api_key_cipher VARCHAR(1024) NULL,
                  api_secret_cipher VARCHAR(1024) NULL,
                  app_id_cipher VARCHAR(1024) NULL,
                  enabled TINYINT NOT NULL DEFAULT 1,
                  text_default TINYINT NOT NULL DEFAULT 0,
                  voice_default TINYINT NOT NULL DEFAULT 0,
                  remark VARCHAR(1000) NULL,
                  created_by BIGINT NULL,
                  updated_by BIGINT NULL,
                  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
                  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
                  UNIQUE KEY uk_ai_provider_code (code),
                  KEY idx_ai_provider_kind_enabled (kind, enabled),
                  KEY idx_ai_provider_text_default (text_default),
                  KEY idx_ai_provider_voice_default (voice_default)
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci
                """);
    }
}
