package com.tyut.aiinterview.algorithmworker.judge.archive;

import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveOutputStream;

/** Generates the bounded input tar consumed by the Java runner image. */
public final class JudgeInputArchiveWriter {
    private JudgeInputArchiveWriter() {}

    public record TestCaseData(String input, String expectedOutput) {}

    public static Path create(String sourceCode, List<TestCaseData> testCases,
                              int compileTimeoutSeconds, int timeLimitMs, int javaXmxMb,
                              int outputLimitKb, boolean compareOutput) throws IOException {
        Path archive = Files.createTempFile("judge-input-", ".tar");
        try (OutputStream fileOutput = Files.newOutputStream(archive);
             BufferedOutputStream bufferedOutput = new BufferedOutputStream(fileOutput);
             TarArchiveOutputStream tarOutput = new TarArchiveOutputStream(bufferedOutput)) {
            tarOutput.setLongFileMode(TarArchiveOutputStream.LONGFILE_POSIX);
            addDirectory(tarOutput, "input/");
            addDirectory(tarOutput, "input/cases/");
            addText(tarOutput, "input/Main.java", sourceCode);
            String config = """
                    compileTimeoutSeconds=%d
                    timeLimitMs=%d
                    javaXmxMb=%d
                    outputLimitKb=%d
                    compareOutput=%s
                    """.formatted(compileTimeoutSeconds, timeLimitMs, javaXmxMb, outputLimitKb, compareOutput);
            addText(tarOutput, "input/config.properties", config);
            for (int index = 0; index < testCases.size(); index++) {
                String caseId = "%04d".formatted(index + 1);
                TestCaseData testCase = testCases.get(index);
                addText(tarOutput, "input/cases/" + caseId + ".in", nullToEmpty(testCase.input()));
                addText(tarOutput, "input/cases/" + caseId + ".expected",
                        nullToEmpty(testCase.expectedOutput()));
            }
            tarOutput.finish();
        }
        return archive;
    }

    private static void addDirectory(TarArchiveOutputStream output, String name) throws IOException {
        TarArchiveEntry entry = new TarArchiveEntry(name);
        entry.setMode(0755);
        entry.setSize(0);
        output.putArchiveEntry(entry);
        output.closeArchiveEntry();
    }

    private static void addText(TarArchiveOutputStream output, String name, String content) throws IOException {
        byte[] data = nullToEmpty(content).getBytes(StandardCharsets.UTF_8);
        TarArchiveEntry entry = new TarArchiveEntry(name);
        entry.setMode(0644);
        entry.setSize(data.length);
        output.putArchiveEntry(entry);
        output.write(data);
        output.closeArchiveEntry();
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }
}
