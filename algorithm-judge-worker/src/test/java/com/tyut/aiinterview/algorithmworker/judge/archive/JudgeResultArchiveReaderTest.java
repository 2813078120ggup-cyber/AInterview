package com.tyut.aiinterview.algorithmworker.judge.archive;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveOutputStream;
import org.junit.jupiter.api.Test;

class JudgeResultArchiveReaderTest {
    @Test
    void readsRegularResultFiles() throws IOException {
        byte[] archive = archive("summary.properties", "status=ACCEPTED\n");

        Map<String, byte[]> files = JudgeResultArchiveReader.read(new ByteArrayInputStream(archive));

        assertEquals("status=ACCEPTED\n", new String(files.get("summary.properties"), StandardCharsets.UTF_8));
    }

    @Test
    void rejectsPathTraversal() throws IOException {
        byte[] archive = archive("../summary.properties", "status=ACCEPTED\n");

        assertThrows(IOException.class,
                () -> JudgeResultArchiveReader.read(new ByteArrayInputStream(archive)));
    }

    private static byte[] archive(String name, String content) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (TarArchiveOutputStream tar = new TarArchiveOutputStream(bytes)) {
            TarArchiveEntry entry = new TarArchiveEntry(name);
            entry.setSize(content.getBytes(StandardCharsets.UTF_8).length);
            tar.putArchiveEntry(entry);
            tar.write(content.getBytes(StandardCharsets.UTF_8));
            tar.closeArchiveEntry();
            tar.finish();
        }
        return bytes.toByteArray();
    }
}
