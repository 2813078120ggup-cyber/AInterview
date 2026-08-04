package com.tyut.aiinterview.settings;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class ConfigSecretCodec {
    private static final String PREFIX = "enc:";
    private static final int IV_LENGTH = 12;
    private static final int TAG_LENGTH = 128;

    private final SecretKeySpec key;
    private final SecureRandom secureRandom = new SecureRandom();

    public ConfigSecretCodec(@Value("${app.config-secret:${JWT_SECRET:local-config-secret-change-me}}") String secret) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(secret.getBytes(StandardCharsets.UTF_8));
            this.key = new SecretKeySpec(digest, "AES");
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException("无法初始化配置密钥加密器", exception);
        }
    }

    public String encrypt(String value) {
        if (value == null || value.isBlank() || isMasked(value)) return value == null ? "" : value;
        try {
            byte[] iv = new byte[IV_LENGTH];
            secureRandom.nextBytes(iv);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(TAG_LENGTH, iv));
            byte[] encrypted = cipher.doFinal(value.getBytes(StandardCharsets.UTF_8));
            ByteBuffer buffer = ByteBuffer.allocate(iv.length + encrypted.length).put(iv).put(encrypted);
            return PREFIX + Base64.getEncoder().encodeToString(buffer.array());
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException("配置密钥加密失败", exception);
        }
    }

    public String decrypt(String value) {
        if (value == null || value.isBlank() || !value.startsWith(PREFIX)) return value == null ? "" : value;
        try {
            byte[] payload = Base64.getDecoder().decode(value.substring(PREFIX.length()));
            ByteBuffer buffer = ByteBuffer.wrap(payload);
            byte[] iv = new byte[IV_LENGTH];
            buffer.get(iv);
            byte[] encrypted = new byte[buffer.remaining()];
            buffer.get(encrypted);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(TAG_LENGTH, iv));
            return new String(cipher.doFinal(encrypted), StandardCharsets.UTF_8);
        } catch (GeneralSecurityException | IllegalArgumentException exception) {
            return "";
        }
    }

    public boolean isMasked(String value) {
        return value != null && value.contains("*");
    }
}
