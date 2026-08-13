package com.tyut.aiinterview.freeinterview;

import com.tyut.aiinterview.common.BusinessException;
import com.tyut.aiinterview.media.UploadSecurityValidator;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.poi.xwpf.extractor.XWPFWordExtractor;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

@Component
public class ResumeTextExtractor {
    private static final int MAX_SOURCE_BYTES = 10 * 1024 * 1024;
    private static final int MAX_TEXT_LENGTH = 60_000;
    private final UploadSecurityValidator uploadSecurity;

    public ResumeTextExtractor(UploadSecurityValidator uploadSecurity) {
        this.uploadSecurity = uploadSecurity;
    }

    public String extract(MultipartFile file) {
        uploadSecurity.validateResume(file);
        try (InputStream inputStream = file.getInputStream()) {
            return extractBytes(file.getOriginalFilename(), readAtMost(inputStream));
        } catch (IOException exception) {
            throw BusinessException.badRequest("简历解析失败，请检查文件是否损坏或加密");
        }
    }

    public String extract(String fileName, InputStream inputStream) {
        try {
            return extractBytes(fileName, readAtMost(inputStream));
        } catch (IOException exception) {
            throw BusinessException.badRequest("简历解析失败，请检查文件是否损坏或加密");
        }
    }

    private byte[] readAtMost(InputStream inputStream) throws IOException {
        if (inputStream == null) throw BusinessException.badRequest("简历文件读取失败");
        ByteArrayOutputStream output = new ByteArrayOutputStream(Math.min(MAX_SOURCE_BYTES, 64 * 1024));
        byte[] buffer = new byte[8192];
        int total = 0;
        int read;
        while ((read = inputStream.read(buffer)) != -1) {
            if (read > MAX_SOURCE_BYTES - total) throw BusinessException.badRequest("简历文件不能超过 10MB");
            output.write(buffer, 0, read);
            total += read;
        }
        return output.toByteArray();
    }

    private String extractBytes(String fileName, byte[] bytes) throws IOException {
        String name = fileName == null ? "" : fileName.toLowerCase(Locale.ROOT);
        String text = name.endsWith(".pdf") ? pdf(bytes) : name.endsWith(".docx") ? docx(bytes)
                : name.endsWith(".txt") || name.endsWith(".md") ? new String(bytes, StandardCharsets.UTF_8)
                : null;
        if (text == null) throw BusinessException.badRequest("仅支持 PDF、DOCX、TXT 或 Markdown 简历");
        text = text.replace('\u0000', ' ').trim();
        if (text.length() < 40) throw BusinessException.badRequest("未能从简历中提取有效文字，请上传可复制文本的 PDF、DOCX、TXT 或 Markdown 文件");
        return text.substring(0, Math.min(text.length(), MAX_TEXT_LENGTH));
    }

    private String pdf(byte[] bytes) throws IOException {
        try (var document = Loader.loadPDF(bytes)) { return new PDFTextStripper().getText(document); }
    }

    private String docx(byte[] bytes) throws IOException {
        try (var document = new XWPFDocument(new ByteArrayInputStream(bytes)); var extractor = new XWPFWordExtractor(document)) { return extractor.getText(); }
    }
}
