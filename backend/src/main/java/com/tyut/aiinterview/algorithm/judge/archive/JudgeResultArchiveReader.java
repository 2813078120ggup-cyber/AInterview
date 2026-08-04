package com.tyut.aiinterview.algorithm.judge.archive;

import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;
import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream;

/**
 * 安全读取结果 tar：限制条目数、单文件与总大小，拒绝符号链接和路径穿越。
 */
public final class JudgeResultArchiveReader {

    private static final int MAX_ENTRIES = 500;
    private static final long MAX_TOTAL_BYTES = 10L * 1024 * 1024;
    private static final long MAX_SINGLE_FILE_BYTES = 1024L * 1024;

    private JudgeResultArchiveReader() {}

    public static Map<String, byte[]> read(InputStream archiveInput) throws IOException {
        Map<String, byte[]> files = new HashMap<>();
        long totalBytes = 0;
        int entryCount = 0;
        try (
                BufferedInputStream bufferedInput = new BufferedInputStream(archiveInput);
                TarArchiveInputStream tarInput = new TarArchiveInputStream(bufferedInput)
        ) {
            TarArchiveEntry entry;
            while ((entry = tarInput.getNextEntry()) != null) {
                entryCount++;
                if (entryCount > MAX_ENTRIES) {
                    throw new IOException("结果归档条目数量超限");
                }
                if (entry.isDirectory()) {
                    continue;
                }
                if (entry.isSymbolicLink() || entry.isLink()) {
                    throw new IOException("结果归档禁止包含链接");
                }
                String name = normalizeEntryName(entry.getName());
                long size = entry.getSize();
                if (size < 0 || size > MAX_SINGLE_FILE_BYTES) {
                    throw new IOException("结果文件大小非法：" + name);
                }
                totalBytes += size;
                if (totalBytes > MAX_TOTAL_BYTES) {
                    throw new IOException("结果归档总大小超限");
                }
                ByteArrayOutputStream output = new ByteArrayOutputStream(Math.toIntExact(size));
                tarInput.transferTo(output);
                files.put(name, output.toByteArray());
            }
        }
        return files;
    }

    private static String normalizeEntryName(String rawName) throws IOException {
        String name = rawName.replace('\\', '/');
        while (name.startsWith("./")) {
            name = name.substring(2);
        }
        if (name.startsWith("/") || name.contains("../") || name.equals("..")) {
            throw new IOException("结果归档包含非法路径：" + rawName);
        }
        return name;
    }
}
