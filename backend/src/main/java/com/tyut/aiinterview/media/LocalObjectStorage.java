package com.tyut.aiinterview.media;

import com.tyut.aiinterview.config.StorageProperties;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.UUID;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;

@Component
public class LocalObjectStorage {
    private final Path root;

    public LocalObjectStorage(StorageProperties properties) {
        this.root = Path.of(properties.rootDirectory()).toAbsolutePath().normalize();
        try {
            Files.createDirectories(root);
        } catch (IOException exception) {
            throw new IllegalStateException("无法创建媒体存储目录", exception);
        }
    }

    public String save(String extension, InputStream inputStream) throws IOException {
        String key = UUID.randomUUID() + (extension == null || extension.isBlank() ? "" : "." + extension);
        Path target = resolve(key);
        Files.copy(inputStream, target, StandardCopyOption.REPLACE_EXISTING);
        return key;
    }

    public Path path(String key) {
        Path path = resolve(key);
        if (!Files.isRegularFile(path)) throw new IllegalArgumentException("媒体文件不存在");
        return path;
    }

    public Resource resource(String key) {
        return new FileSystemResource(path(key));
    }

    private Path resolve(String key) {
        Path target = root.resolve(key).normalize();
        if (!target.startsWith(root)) throw new IllegalArgumentException("非法媒体路径");
        return target;
    }
}
