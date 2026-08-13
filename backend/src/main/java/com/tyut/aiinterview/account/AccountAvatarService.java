package com.tyut.aiinterview.account;

import com.tyut.aiinterview.common.BusinessException;
import com.tyut.aiinterview.domain.MediaFile;
import com.tyut.aiinterview.domain.UserAccount;
import com.tyut.aiinterview.mapper.MediaFileMapper;
import com.tyut.aiinterview.mapper.UserMapper;
import com.tyut.aiinterview.media.LocalObjectStorage;
import com.tyut.aiinterview.media.UploadSecurityValidator;
import com.tyut.aiinterview.observability.OperationAuditService;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Objects;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

@Service
public class AccountAvatarService {
    private static final long MAX_AVATAR_BYTES = 2L * 1024 * 1024;

    private final AccountService accountService;
    private final UserMapper userMapper;
    private final MediaFileMapper mediaFileMapper;
    private final LocalObjectStorage storage;
    private final UploadSecurityValidator uploadSecurity;
    private final OperationAuditService auditService;

    public AccountAvatarService(AccountService accountService, UserMapper userMapper,
                                MediaFileMapper mediaFileMapper, LocalObjectStorage storage,
                                UploadSecurityValidator uploadSecurity, OperationAuditService auditService) {
        this.accountService = accountService;
        this.userMapper = userMapper;
        this.mediaFileMapper = mediaFileMapper;
        this.storage = storage;
        this.uploadSecurity = uploadSecurity;
        this.auditService = auditService;
    }

    @Transactional
    public AccountDtos.AccountProfile upload(MultipartFile file) {
        UserAccount current = accountService.requireCurrentUser();
        ensureCurrentAvatarOwnership(current);
        UploadSecurityValidator.ValidatedAvatar validated = uploadSecurity.validateAvatar(file, MAX_AVATAR_BYTES);
        String key = null;
        MediaFile media = null;
        boolean success = false;
        try (InputStream input = file.getInputStream()) {
            key = storage.save(validated.extension(), input);
            media = new MediaFile();
            media.setOwnerId(current.getId());
            media.setBucketName("local");
            media.setObjectKey(key);
            media.setOriginalName(validated.originalName());
            media.setContentType(validated.contentType());
            media.setMediaType("image");
            media.setSizeBytes(file.getSize());
            media.setChecksumSha256(sha256(storage.path(key)));
            media.setStatus(MediaFile.AVAILABLE);
            mediaFileMapper.insert(media);

            Long expectedMediaId = current.getAvatarMediaId();
            int expectedVersion = current.getVersion() == null ? 0 : current.getVersion();
            int updated = userMapper.updateAvatarBinding(current.getId(), media.getId(), expectedMediaId, expectedVersion);
            if (updated != 1) {
                auditService.denied("ACCOUNT", "AVATAR_UPDATED", "USER", current.getId(), current.getCompanyId(),
                        "头像更新版本冲突");
                throw BusinessException.conflict("账户资料已被其他请求更新，请刷新后重试");
            }
            auditService.success("ACCOUNT", expectedMediaId == null ? "AVATAR_UPLOADED" : "AVATAR_REPLACED",
                    "USER", current.getId(), current.getCompanyId(),
                    expectedMediaId == null ? "上传本人头像" : "替换本人头像");
            AccountDtos.AccountProfile result = accountService.profile();
            success = true;
            return result;
        } catch (BusinessException exception) {
            if (!success) cleanupUnboundMedia(media, key);
            throw exception;
        } catch (IOException exception) {
            if (!success) cleanupUnboundMedia(media, key);
            throw new IllegalStateException("头像文件保存失败", exception);
        } catch (RuntimeException exception) {
            if (!success) cleanupUnboundMedia(media, key);
            throw exception;
        }
    }

    public AvatarContent content() throws IOException {
        UserAccount current = accountService.requireCurrentUser();
        Long mediaId = current.getAvatarMediaId();
        if (mediaId == null) throw BusinessException.notFound("头像不存在");
        MediaFile media = mediaFileMapper.selectById(mediaId);
        if (media == null || !Objects.equals(media.getOwnerId(), current.getId())
                || !Objects.equals(media.getStatus(), MediaFile.AVAILABLE)
                || !"image".equalsIgnoreCase(media.getMediaType())
                || !isAvatarContentType(media.getContentType())) {
            throw BusinessException.notFound("头像不存在");
        }
        Resource resource = storage.resource(media.getObjectKey());
        if (!resource.exists()) throw BusinessException.notFound("头像不存在");
        return new AvatarContent(media.getContentType(), resource);
    }

    @Transactional
    public AccountDtos.AccountProfile delete() {
        UserAccount current = accountService.requireCurrentUser();
        Long expectedMediaId = current.getAvatarMediaId();
        if (expectedMediaId == null) return accountService.profile();
        ensureCurrentAvatarOwnership(current);

        int expectedVersion = current.getVersion() == null ? 0 : current.getVersion();
        int updated = userMapper.updateAvatarBinding(current.getId(), null, expectedMediaId, expectedVersion);
        if (updated != 1) {
            auditService.denied("ACCOUNT", "AVATAR_DELETED", "USER", current.getId(), current.getCompanyId(),
                    "头像删除版本冲突");
            throw BusinessException.conflict("账户资料已被其他请求更新，请刷新后重试");
        }
        auditService.success("ACCOUNT", "AVATAR_DELETED", "USER", current.getId(), current.getCompanyId(),
                "解除本人头像绑定");
        return accountService.profile();
    }

    private boolean isAvatarContentType(String contentType) {
        return "image/jpeg".equalsIgnoreCase(contentType)
                || "image/png".equalsIgnoreCase(contentType)
                || "image/webp".equalsIgnoreCase(contentType);
    }

    private void ensureCurrentAvatarOwnership(UserAccount current) {
        if (current.getAvatarMediaId() == null) return;
        MediaFile media = mediaFileMapper.selectById(current.getAvatarMediaId());
        if (media == null || !Objects.equals(media.getOwnerId(), current.getId())) {
            throw BusinessException.notFound("头像不存在");
        }
    }

    private void cleanupUnboundMedia(MediaFile media, String key) {
        if (media != null && media.getId() != null) {
            media.setStatus(MediaFile.DELETED);
            mediaFileMapper.updateById(media);
        }
        if (key != null) {
            try {
                storage.delete(key);
            } catch (RuntimeException ignored) {
                // The database row is marked deleted; lifecycle cleanup may retry storage deletion.
            }
        }
    }

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
            throw new IllegalStateException("计算头像摘要失败", exception);
        }
    }

    public record AvatarContent(String contentType, Resource resource) {
    }
}
