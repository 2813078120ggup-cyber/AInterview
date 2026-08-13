package com.tyut.aiinterview.media;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.tyut.aiinterview.common.BusinessException;
import com.tyut.aiinterview.config.StorageProperties;
import com.tyut.aiinterview.config.UploadSecurityProperties;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

class UploadSecurityValidatorTest {
    private final UploadSecurityValidator validator = new UploadSecurityValidator(
            new StorageProperties("./target/test-media", 100_000_000),
            new UploadSecurityProperties(false, "", 3310));

    @Test
    void acceptsPdfResumeAndReturnsSafeMetadata() {
        MockMultipartFile file = new MockMultipartFile("file", "../刘洋-简历.pdf", "application/pdf",
                "%PDF-1.7\nresume content".getBytes(StandardCharsets.UTF_8));

        UploadSecurityValidator.ValidatedResume result = validator.validateResumeUpload(file);

        assertEquals("application/pdf", result.contentType());
        assertEquals("pdf", result.extension());
        assertEquals("刘洋-简历.pdf", result.originalName());
    }

    @Test
    void rejectsPdfWithWrongSignature() {
        MockMultipartFile file = new MockMultipartFile("file", "resume.pdf", "application/pdf",
                "not a pdf".getBytes(StandardCharsets.UTF_8));

        assertThrows(BusinessException.class, () -> validator.validateResumeUpload(file));
    }

    @Test
    void acceptsDocxSignatureAndMapsContentType() {
        MockMultipartFile file = new MockMultipartFile("file", "resume.docx",
                "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                new byte[]{0x50, 0x4b, 0x03, 0x04, 0x01});

        UploadSecurityValidator.ValidatedResume result = validator.validateResumeUpload(file);

        assertEquals("application/vnd.openxmlformats-officedocument.wordprocessingml.document", result.contentType());
        assertEquals("docx", result.extension());
    }

    @Test
    void acceptsTxtResumeWithoutTreatingItAsBinary() {
        MockMultipartFile file = new MockMultipartFile("file", "resume.txt", "text/plain",
                "Java backend resume with enough readable text for validation".getBytes(StandardCharsets.UTF_8));

        UploadSecurityValidator.ValidatedResume result = validator.validateResumeUpload(file);

        assertEquals("text/plain", result.contentType());
        assertEquals("txt", result.extension());
    }

    @Test
    void rejectsDocxWithWrongSignature() {
        MockMultipartFile file = new MockMultipartFile("file", "resume.docx",
                "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                "not a zip file".getBytes(StandardCharsets.UTF_8));

        assertThrows(BusinessException.class, () -> validator.validateResumeUpload(file));
    }

    @Test
    void rejectsResumeBeyondConfiguredStorageLimit() {
        UploadSecurityValidator limited = new UploadSecurityValidator(
                new StorageProperties("./target/test-media", 32),
                new UploadSecurityProperties(false, "", 3310));
        MockMultipartFile file = new MockMultipartFile("file", "resume.txt", "text/plain",
                new byte[33]);

        assertThrows(BusinessException.class, () -> limited.validateResumeUpload(file));
    }

    @Test
    void acceptsJpegPngAndWebpAvatarsOnlyWhenExtensionMimeAndSignatureAgree() {
        MockMultipartFile jpeg = new MockMultipartFile("file", "avatar.jpeg", "image/jpeg",
                new byte[]{(byte) 0xff, (byte) 0xd8, (byte) 0xff, 0x01});
        MockMultipartFile png = new MockMultipartFile("file", "avatar.png", "image/png",
                new byte[]{(byte) 0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a});
        MockMultipartFile webp = new MockMultipartFile("file", "avatar.webp", "image/webp",
                new byte[]{'R', 'I', 'F', 'F', 0, 0, 0, 0, 'W', 'E', 'B', 'P'});

        assertEquals("jpeg", validator.validateAvatar(jpeg, 2 * 1024 * 1024).extension());
        assertEquals("png", validator.validateAvatar(png, 2 * 1024 * 1024).extension());
        assertEquals("webp", validator.validateAvatar(webp, 2 * 1024 * 1024).extension());
    }

    @Test
    void rejectsWrongAvatarSignatureMimeExtensionAndSize() {
        MockMultipartFile pdfAsImage = new MockMultipartFile("file", "avatar.png", "image/png",
                "%PDF-1.7".getBytes(StandardCharsets.US_ASCII));
        MockMultipartFile wrongExtension = new MockMultipartFile("file", "avatar.jpg", "image/png",
                new byte[]{(byte) 0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a});
        MockMultipartFile wrongMime = new MockMultipartFile("file", "avatar.png", "application/pdf",
                new byte[]{(byte) 0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a});
        byte[] oversized = new byte[2 * 1024 * 1024 + 1];
        oversized[0] = (byte) 0xff;
        oversized[1] = (byte) 0xd8;
        oversized[2] = (byte) 0xff;
        MockMultipartFile tooLarge = new MockMultipartFile("file", "avatar.jpg", "image/jpeg", oversized);

        assertThrows(BusinessException.class, () -> validator.validateAvatar(pdfAsImage, 2 * 1024 * 1024));
        assertThrows(BusinessException.class, () -> validator.validateAvatar(wrongExtension, 2 * 1024 * 1024));
        assertThrows(BusinessException.class, () -> validator.validateAvatar(wrongMime, 2 * 1024 * 1024));
        assertThrows(BusinessException.class, () -> validator.validateAvatar(tooLarge, 2 * 1024 * 1024));
    }
}
