package com.tyut.aiinterview.media;

import com.tyut.aiinterview.common.BusinessException;
import com.tyut.aiinterview.config.StorageProperties;
import com.tyut.aiinterview.config.UploadSecurityProperties;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

@Component
public class UploadSecurityValidator {
    private static final int SIGNATURE_LENGTH = 16;
    private final StorageProperties storageProperties;
    private final UploadSecurityProperties properties;

    public UploadSecurityValidator(StorageProperties storageProperties, UploadSecurityProperties properties) {
        this.storageProperties = storageProperties;
        this.properties = properties;
    }

    public ValidatedUpload validateMedia(MultipartFile file) {
        validateCommon(file);
        String contentType = normalizedContentType(file);
        byte[] header = readHeader(file);
        String extension = switch (contentType) {
            case "application/pdf" -> checked(pdf(header), "文件内容不是有效的 PDF", "pdf");
            case "image/jpeg" -> checked(jpeg(header), "文件内容不是有效的 JPEG 图片", "jpg");
            case "image/png" -> checked(png(header), "文件内容不是有效的 PNG 图片", "png");
            case "image/gif" -> checked(gif(header), "文件内容不是有效的 GIF 图片", "gif");
            case "audio/wav", "audio/x-wav" -> checked(wav(header), "文件内容不是有效的 WAV 音频", "wav");
            case "audio/webm", "video/webm" -> checked(webm(header), "文件内容不是有效的 WebM 媒体", "webm");
            case "video/mp4" -> checked(mp4(header), "文件内容不是有效的 MP4 视频", "mp4");
            case "audio/mpeg" -> checked(mp3(header), "文件内容不是有效的 MP3 音频", "mp3");
            default -> throw BusinessException.badRequest("不支持的上传文件类型");
        };
        scan(file);
        return new ValidatedUpload(contentType, extension, safeOriginalName(file.getOriginalFilename()));
    }

    public void validateResume(MultipartFile file) {
        validateCommon(file);
        String extension = extension(file.getOriginalFilename());
        byte[] header = readHeader(file);
        boolean valid = switch (extension) {
            case "pdf" -> pdf(header);
            case "docx" -> zip(header);
            case "txt", "md" -> text(header);
            default -> false;
        };
        if (!valid) throw BusinessException.badRequest("简历文件内容与扩展名不匹配，或文件格式不受支持");
        scan(file);
    }

    private void validateCommon(MultipartFile file) {
        if (file == null || file.isEmpty()) throw BusinessException.badRequest("上传文件不能为空");
        if (file.getSize() > storageProperties.maxUploadBytes()) throw BusinessException.badRequest("上传文件超过大小限制");
    }

    private String normalizedContentType(MultipartFile file) {
        String contentType = file.getContentType();
        if (contentType == null || contentType.isBlank()) throw BusinessException.badRequest("无法识别上传文件类型");
        return contentType.split(";", 2)[0].trim().toLowerCase(Locale.ROOT);
    }

    private byte[] readHeader(MultipartFile file) {
        try (InputStream input = file.getInputStream()) {
            return input.readNBytes(SIGNATURE_LENGTH);
        } catch (IOException exception) {
            throw BusinessException.badRequest("无法读取上传文件");
        }
    }

    private void scan(MultipartFile file) {
        if (!properties.clamavRequired()) return;
        if (properties.clamavHost() == null || properties.clamavHost().isBlank()) {
            throw BusinessException.serviceUnavailable("生产环境已要求病毒扫描，但未配置 ClamAV 服务");
        }
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(properties.clamavHost(), properties.clamavPort()), 5_000);
            socket.setSoTimeout(30_000);
            try (DataOutputStream output = new DataOutputStream(socket.getOutputStream()); InputStream response = socket.getInputStream(); InputStream input = file.getInputStream()) {
                output.write("zINSTREAM\0".getBytes(StandardCharsets.US_ASCII));
                byte[] buffer = new byte[8192];
                int read;
                while ((read = input.read(buffer)) != -1) {
                    output.writeInt(read);
                    output.write(buffer, 0, read);
                }
                output.writeInt(0);
                output.flush();
                String result = new String(response.readAllBytes(), StandardCharsets.US_ASCII);
                if (!result.contains("OK")) throw BusinessException.badRequest("上传文件未通过病毒扫描");
            }
        } catch (BusinessException exception) {
            throw exception;
        } catch (IOException exception) {
            throw BusinessException.serviceUnavailable("病毒扫描服务不可用，请稍后重试");
        }
    }

    private String safeOriginalName(String originalName) {
        String name = originalName == null ? "upload" : originalName.replace('\\', '/');
        int slash = name.lastIndexOf('/');
        if (slash >= 0) name = name.substring(slash + 1);
        name = name.replaceAll("[\\p{Cntrl}]", "").trim();
        if (name.isBlank()) return "upload";
        return name.length() > 128 ? name.substring(0, 128) : name;
    }

    private String extension(String originalName) {
        String name = safeOriginalName(originalName).toLowerCase(Locale.ROOT);
        int index = name.lastIndexOf('.');
        return index < 0 ? "" : name.substring(index + 1).replaceAll("[^a-z0-9]", "");
    }

    private String checked(boolean condition, String message, String extension) {
        if (!condition) throw BusinessException.badRequest(message);
        return extension;
    }

    private boolean pdf(byte[] bytes) { return startsWith(bytes, 0x25, 0x50, 0x44, 0x46, 0x2d); }
    private boolean jpeg(byte[] bytes) { return startsWith(bytes, 0xff, 0xd8, 0xff); }
    private boolean png(byte[] bytes) { return startsWith(bytes, 0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a); }
    private boolean gif(byte[] bytes) { return startsWith(bytes, "GIF87a") || startsWith(bytes, "GIF89a"); }
    private boolean wav(byte[] bytes) { return startsWith(bytes, "RIFF") && bytes.length >= 12 && startsWith(bytes, 8, "WAVE"); }
    private boolean webm(byte[] bytes) { return startsWith(bytes, 0x1a, 0x45, 0xdf, 0xa3); }
    private boolean mp4(byte[] bytes) { return bytes.length >= 8 && startsWith(bytes, 4, "ftyp"); }
    private boolean mp3(byte[] bytes) { return startsWith(bytes, "ID3") || (bytes.length >= 2 && (bytes[0] & 0xff) == 0xff && (bytes[1] & 0xe0) == 0xe0); }
    private boolean zip(byte[] bytes) { return startsWith(bytes, 0x50, 0x4b, 0x03, 0x04) || startsWith(bytes, 0x50, 0x4b, 0x05, 0x06); }
    private boolean text(byte[] bytes) { for (byte item : bytes) { int value = item & 0xff; if (value == 0) return false; } return true; }
    private boolean startsWith(byte[] bytes, int... signature) { if (bytes.length < signature.length) return false; for (int index = 0; index < signature.length; index++) if ((bytes[index] & 0xff) != signature[index]) return false; return true; }
    private boolean startsWith(byte[] bytes, String signature) { return startsWith(bytes, 0, signature); }
    private boolean startsWith(byte[] bytes, int offset, String signature) { byte[] value = signature.getBytes(StandardCharsets.US_ASCII); if (bytes.length < offset + value.length) return false; for (int index = 0; index < value.length; index++) if (bytes[offset + index] != value[index]) return false; return true; }

    public record ValidatedUpload(String contentType, String extension, String originalName) { }
}
