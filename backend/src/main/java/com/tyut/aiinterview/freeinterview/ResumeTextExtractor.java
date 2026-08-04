package com.tyut.aiinterview.freeinterview;

import com.tyut.aiinterview.common.BusinessException;
import com.tyut.aiinterview.media.UploadSecurityValidator;
import java.io.IOException;
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
    private static final int MAX_TEXT_LENGTH = 60_000;
    private final UploadSecurityValidator uploadSecurity;

    public ResumeTextExtractor(UploadSecurityValidator uploadSecurity) {
        this.uploadSecurity = uploadSecurity;
    }

    public String extract(MultipartFile file) {
        uploadSecurity.validateResume(file);
        String name = file.getOriginalFilename() == null ? "" : file.getOriginalFilename().toLowerCase(Locale.ROOT);
        try {
            String text = name.endsWith(".pdf") ? pdf(file) : name.endsWith(".docx") ? docx(file)
                    : name.endsWith(".txt") || name.endsWith(".md") ? new String(file.getBytes(), StandardCharsets.UTF_8)
                    : null;
            if (text == null) throw BusinessException.badRequest("仅支持 PDF、DOCX、TXT 或 Markdown 简历");
            text = text.replace('\u0000', ' ').trim();
            if (text.length() < 40) throw BusinessException.badRequest("未能从简历中提取有效文字，请上传可复制文本的 PDF、DOCX、TXT 或 Markdown 文件");
            return text.substring(0, Math.min(text.length(), MAX_TEXT_LENGTH));
        } catch (IOException exception) {
            throw BusinessException.badRequest("简历解析失败，请检查文件是否损坏或加密");
        }
    }

    private String pdf(MultipartFile file) throws IOException {
        try (var document = Loader.loadPDF(file.getBytes())) { return new PDFTextStripper().getText(document); }
    }

    private String docx(MultipartFile file) throws IOException {
        try (var document = new XWPFDocument(file.getInputStream()); var extractor = new XWPFWordExtractor(document)) { return extractor.getText(); }
    }
}
