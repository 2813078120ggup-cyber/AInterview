package com.tyut.aiinterview.media;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.tyut.aiinterview.common.BusinessException;
import com.tyut.aiinterview.config.StorageProperties;
import com.tyut.aiinterview.config.UploadSecurityProperties;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

class UploadSecurityValidatorTest {
    private final UploadSecurityValidator validator = new UploadSecurityValidator(
            new StorageProperties("./target/test-media", 1_024), new UploadSecurityProperties(false, "", 3310));

    @Test
    void acceptsPdfWithMatchingSignatureAndNormalizesName() {
        MockMultipartFile file = new MockMultipartFile("file", "../resume.pdf", "application/pdf",
                "%PDF-1.7\nexample".getBytes());

        UploadSecurityValidator.ValidatedUpload result = validator.validateMedia(file);

        assertEquals("application/pdf", result.contentType());
        assertEquals("pdf", result.extension());
        assertEquals("resume.pdf", result.originalName());
    }

    @Test
    void rejectsMismatchedDeclaredContentType() {
        MockMultipartFile file = new MockMultipartFile("file", "not-a-pdf.pdf", "application/pdf",
                "plain text".getBytes());

        assertThrows(BusinessException.class, () -> validator.validateMedia(file));
    }

    @Test
    void rejectsUnsupportedResumeExtensionBeforeParsing() {
        MockMultipartFile file = new MockMultipartFile("resume", "resume.exe", "application/octet-stream",
                new byte[] {0x4d, 0x5a});

        assertThrows(BusinessException.class, () -> validator.validateResume(file));
    }
}
