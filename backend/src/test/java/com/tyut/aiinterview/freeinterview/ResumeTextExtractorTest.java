package com.tyut.aiinterview.freeinterview;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;

import com.tyut.aiinterview.common.BusinessException;
import com.tyut.aiinterview.media.UploadSecurityValidator;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.junit.jupiter.api.Test;

class ResumeTextExtractorTest {
    private final ResumeTextExtractor extractor = new ResumeTextExtractor(mock(UploadSecurityValidator.class));

    @Test
    void extractsTextFromBoundedStreamAndLimitsPromptInputLength() {
        String source = "Java 后端开发经验。".repeat(10_000);

        String extracted = extractor.extract("resume.txt",
                new ByteArrayInputStream(source.getBytes(StandardCharsets.UTF_8)));

        assertEquals(60_000, extracted.length());
    }

    @Test
    void rejectsOversizedPrivateResumeStreamBeforeParsing() {
        byte[] source = new byte[10 * 1024 * 1024 + 1];

        BusinessException exception = assertThrows(BusinessException.class,
                () -> extractor.extract("resume.txt", new ByteArrayInputStream(source)));

        assertEquals("简历文件不能超过 10MB", exception.getMessage());
    }

    @Test
    void extractsTextFromRealPdfBytes() throws Exception {
        byte[] pdf;
        try (PDDocument document = new PDDocument(); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            PDPage page = new PDPage();
            document.addPage(page);
            try (PDPageContentStream content = new PDPageContentStream(document, page)) {
                content.beginText();
                content.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 12);
                content.newLineAtOffset(72, 700);
                content.showText("Real PDF resume validation: Java backend, Spring Boot, MySQL, Redis, and testing.");
                content.endText();
            }
            document.save(output);
            pdf = output.toByteArray();
        }

        String extracted = extractor.extract("resume.pdf", new ByteArrayInputStream(pdf));

        org.junit.jupiter.api.Assertions.assertTrue(extracted.contains("Real PDF resume validation"));
    }

    @Test
    void extractsTextFromRealDocxBytes() throws Exception {
        byte[] docx;
        try (XWPFDocument document = new XWPFDocument(); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            document.createParagraph().createRun().setText(
                    "Real DOCX resume validation: Java backend, Spring Boot, MySQL, Redis, and testing.");
            document.write(output);
            docx = output.toByteArray();
        }

        String extracted = extractor.extract("resume.docx", new ByteArrayInputStream(docx));

        org.junit.jupiter.api.Assertions.assertTrue(extracted.contains("Real DOCX resume validation"));
    }

    @Test
    void extractsTextFromRealTxtBytes() {
        String source = "Real TXT resume validation: Java backend, Spring Boot, MySQL, Redis, and testing.";

        String extracted = extractor.extract("resume.txt",
                new ByteArrayInputStream(source.getBytes(StandardCharsets.UTF_8)));

        assertEquals(source, extracted);
    }
}
