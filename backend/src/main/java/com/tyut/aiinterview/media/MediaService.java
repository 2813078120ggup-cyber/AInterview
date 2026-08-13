package com.tyut.aiinterview.media;

import com.tyut.aiinterview.common.BusinessException;
import com.tyut.aiinterview.config.StorageProperties;
import com.tyut.aiinterview.domain.MediaFile;
import com.tyut.aiinterview.mapper.MediaFileMapper;
import com.tyut.aiinterview.security.CurrentUser;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

@Service
public class MediaService {
    private final MediaFileMapper mediaMapper;
    private final LocalObjectStorage storage;
    private final StorageProperties properties;
    private final CurrentUser currentUser;
    private final UploadSecurityValidator uploadSecurity;
    private final long resumeMaxUploadBytes;

    public MediaService(MediaFileMapper mediaMapper, LocalObjectStorage storage, StorageProperties properties, CurrentUser currentUser,
                        UploadSecurityValidator uploadSecurity,
                        @Value("${app.recruitment.resume.max-upload-bytes:10485760}") long resumeMaxUploadBytes) {
        this.mediaMapper = mediaMapper; this.storage = storage; this.properties = properties; this.currentUser = currentUser; this.uploadSecurity = uploadSecurity;
        this.resumeMaxUploadBytes = Math.max(1_048_576L, resumeMaxUploadBytes);
    }

    @Transactional
    public MediaDtos.MediaVO upload(MultipartFile file) {
        if (file == null || file.isEmpty()) throw BusinessException.badRequest("上传文件不能为空");
        if (file.getSize() > properties.maxUploadBytes()) throw BusinessException.badRequest("上传文件超过大小限制");
        UploadSecurityValidator.ValidatedUpload validated = uploadSecurity.validateMedia(file);
        String mediaType = mediaType(validated.contentType());
        try (InputStream input = file.getInputStream()) {
            String key = storage.save(validated.extension(), input);
            MediaFile media = new MediaFile(); media.setOwnerId(currentUser.id()); media.setBucketName("local"); media.setObjectKey(key);
            media.setOriginalName(validated.originalName()); media.setContentType(validated.contentType());
            media.setMediaType(mediaType); media.setSizeBytes(file.getSize()); media.setChecksumSha256(sha256(storage.path(key))); media.setStatus(MediaFile.AVAILABLE);
            mediaMapper.insert(media); return toVO(media);
        } catch (IOException exception) { throw new IllegalStateException("保存上传文件失败", exception); }
    }

    @Transactional
    public MediaDtos.MediaVO uploadResume(MultipartFile file) {
        if (file == null || file.isEmpty()) throw BusinessException.badRequest("上传文件不能为空");
        if (file.getSize() > resumeMaxUploadBytes) throw BusinessException.badRequest("简历文件不能超过 10MB");
        UploadSecurityValidator.ValidatedResume validated = uploadSecurity.validateResumeUpload(file);
        String key = null;
        try (InputStream input = file.getInputStream()) {
            key = storage.save(validated.extension(), input);
            MediaFile media = new MediaFile();
            media.setOwnerId(currentUser.id());
            media.setBucketName("local");
            media.setObjectKey(key);
            media.setOriginalName(validated.originalName());
            media.setContentType(validated.contentType());
            media.setMediaType("resume");
            media.setSizeBytes(file.getSize());
            media.setChecksumSha256(sha256(storage.path(key)));
            media.setStatus(MediaFile.AVAILABLE);
            mediaMapper.insert(media);
            return toVO(media);
        } catch (IOException exception) {
            cleanupFailedUpload(key);
            throw new IllegalStateException("保存简历文件失败", exception);
        } catch (RuntimeException exception) {
            cleanupFailedUpload(key);
            throw exception;
        }
    }

    public MediaDtos.MediaVO get(Long id) { return toVO(requireOwned(id)); }
    public Resource content(Long id) { return storage.resource(requireOwned(id).getObjectKey()); }
    public MediaFile requireOwned(Long id) { MediaFile media = requireReadable(id); if (!currentUser.id().equals(media.getOwnerId())) throw BusinessException.forbidden("无权使用该媒体文件"); return media; }
    public MediaFile requireAvailable(Long id) { return requireReadable(id); }
    public Resource content(MediaFile media) { return storage.resource(media.getObjectKey()); }
    public MediaDtos.MediaVO view(MediaFile media) { return toVO(media); }
    @Transactional
    public void deleteOwned(Long id) {
        MediaFile media = requireOwned(id);
        storage.delete(media.getObjectKey());
        media.setStatus(MediaFile.DELETED);
        mediaMapper.updateById(media);
    }

    private MediaFile requireReadable(Long id) { MediaFile media = mediaMapper.selectById(id); if (media == null || media.getStatus() != MediaFile.AVAILABLE) throw BusinessException.notFound("媒体文件不存在或不可用"); return media; }
    private void cleanupFailedUpload(String key) { if (key == null) return; try { storage.delete(key); } catch (RuntimeException ignored) { } }
    private String mediaType(String contentType) { if (contentType == null) throw BusinessException.badRequest("无法识别媒体类型"); if (contentType.startsWith("audio/")) return "audio"; if (contentType.startsWith("video/")) return "video"; if (contentType.startsWith("image/")) return "image"; if ("application/pdf".equals(contentType)) return "pdf"; throw BusinessException.badRequest("不支持的媒体类型"); }
    private String sha256(java.nio.file.Path path) {
        try (InputStream input = Files.newInputStream(path)) {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] buffer = new byte[8192];
            int read;
            while ((read = input.read(buffer)) != -1) digest.update(buffer, 0, read);
            StringBuilder result = new StringBuilder();
            for (byte item : digest.digest()) result.append(String.format("%02x", item));
            return result.toString();
        } catch (IOException | NoSuchAlgorithmException exception) {
            throw new IllegalStateException("计算媒体摘要失败", exception);
        }
    }
    private MediaDtos.MediaVO toVO(MediaFile media) { return new MediaDtos.MediaVO(media.getId(), media.getOriginalName(), media.getContentType(), media.getMediaType(), media.getSizeBytes(), media.getStatus(), media.getCreatedAt()); }
}
