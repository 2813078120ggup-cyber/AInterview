package com.tyut.aiinterview.account;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.tyut.aiinterview.common.BusinessException;
import com.tyut.aiinterview.domain.MediaFile;
import com.tyut.aiinterview.domain.UserAccount;
import com.tyut.aiinterview.mapper.MediaFileMapper;
import com.tyut.aiinterview.mapper.UserMapper;
import com.tyut.aiinterview.media.LocalObjectStorage;
import com.tyut.aiinterview.media.UploadSecurityValidator;
import com.tyut.aiinterview.observability.OperationAuditService;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import static org.mockito.Mockito.lenient;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.mock.web.MockMultipartFile;

@ExtendWith(MockitoExtension.class)
class AccountAvatarServiceTest {
    @Mock private AccountService accountService;
    @Mock private UserMapper userMapper;
    @Mock private MediaFileMapper mediaFileMapper;
    @Mock private LocalObjectStorage storage;
    @Mock private UploadSecurityValidator uploadSecurity;
    @Mock private OperationAuditService auditService;

    @TempDir Path tempDir;
    private AccountAvatarService service;
    private UserAccount current;
    private AccountDtos.AccountProfile profile;

    @BeforeEach
    void setUp() throws Exception {
        service = new AccountAvatarService(accountService, userMapper, mediaFileMapper, storage, uploadSecurity, auditService);
        current = new UserAccount();
        current.setId(11L);
        current.setStatus(1);
        current.setVersion(4);
        profile = new AccountDtos.AccountProfile(11L, "candidate", "候选人", "CANDIDATE", 1,
                true, null, null, false, null, null, false, List.of("PASSWORD"), null, null, 5);
        when(accountService.requireCurrentUser()).thenReturn(current);
        lenient().when(accountService.profile()).thenReturn(profile);
        lenient().when(storage.save(eq("jpg"), any())).thenReturn("avatar-key.jpg");
        lenient().when(storage.path("avatar-key.jpg")).thenReturn(Path.of("avatar-test.jpg"));
        lenient().when(uploadSecurity.validateAvatar(any(), eq(2L * 1024 * 1024)))
                .thenReturn(new UploadSecurityValidator.ValidatedAvatar("image/jpeg", "jpg", "avatar.jpg"));
    }

    @Test
    void uploadBindsNewMediaAndReplacementLeavesOldMediaAvailable() throws Exception {
        Path storedFile = tempDir.resolve("avatar-test.jpg");
        when(storage.path("avatar-key.jpg")).thenReturn(storedFile);
        Files.write(storedFile, new byte[]{(byte) 0xff, (byte) 0xd8, (byte) 0xff});
        MockMultipartFile file = new MockMultipartFile("file", "avatar.jpg", "image/jpeg",
                new byte[]{(byte) 0xff, (byte) 0xd8, (byte) 0xff});
        when(mediaFileMapper.insert(any(MediaFile.class))).thenAnswer(invocation -> {
            invocation.<MediaFile>getArgument(0).setId(88L);
            return 1;
        });
        current.setAvatarMediaId(7L);
        MediaFile old = new MediaFile();
        old.setId(7L);
        old.setOwnerId(11L);
        when(mediaFileMapper.selectById(7L)).thenReturn(old);
        when(userMapper.updateAvatarBinding(11L, 88L, 7L, 4)).thenReturn(1);

        AccountDtos.AccountProfile result = service.upload(file);

        assertEquals(11L, result.id());
        verify(userMapper).updateAvatarBinding(11L, 88L, 7L, 4);
        verify(auditService).success("ACCOUNT", "AVATAR_REPLACED", "USER", 11L, null, "替换本人头像");
        verify(storage, never()).delete("avatar-key.jpg");
    }

    @Test
    void uploadConflictDoesNotLeaveUnboundMedia() throws Exception {
        Path storedFile = tempDir.resolve("avatar-test.jpg");
        when(storage.path("avatar-key.jpg")).thenReturn(storedFile);
        Files.write(storedFile, new byte[]{(byte) 0xff, (byte) 0xd8, (byte) 0xff});
        MockMultipartFile file = new MockMultipartFile("file", "avatar.jpg", "image/jpeg",
                new byte[]{(byte) 0xff, (byte) 0xd8, (byte) 0xff});
        when(mediaFileMapper.insert(any(MediaFile.class))).thenAnswer(invocation -> {
            invocation.<MediaFile>getArgument(0).setId(88L);
            return 1;
        });
        when(userMapper.updateAvatarBinding(11L, 88L, null, 4)).thenReturn(0);

        assertEquals(409, assertThrows(BusinessException.class, () -> service.upload(file)).getStatus().value());
        verify(storage).delete("avatar-key.jpg");
        verify(mediaFileMapper).updateById(any(MediaFile.class));
    }

    @Test
    void deleteOnlyUnbindsAndDoesNotPhysicallyDeleteSharedMedia() {
        current.setAvatarMediaId(7L);
        MediaFile media = new MediaFile();
        media.setId(7L);
        media.setOwnerId(11L);
        when(mediaFileMapper.selectById(7L)).thenReturn(media);
        when(userMapper.updateAvatarBinding(11L, null, 7L, 4)).thenReturn(1);

        service.delete();

        verify(userMapper).updateAvatarBinding(11L, null, 7L, 4);
        verify(auditService).success("ACCOUNT", "AVATAR_DELETED", "USER", 11L, null, "解除本人头像绑定");
        verify(storage, never()).delete(any());
        verify(mediaFileMapper, never()).updateById(any(MediaFile.class));
    }

    @Test
    void contentRejectsMediaOwnedByAnotherUser() throws Exception {
        current.setAvatarMediaId(7L);
        MediaFile media = new MediaFile();
        media.setId(7L);
        media.setOwnerId(99L);
        media.setStatus(MediaFile.AVAILABLE);
        media.setMediaType("image");
        media.setContentType("image/jpeg");
        when(mediaFileMapper.selectById(7L)).thenReturn(media);

        assertEquals(404, assertThrows(BusinessException.class, service::content).getStatus().value());
        verify(storage, never()).resource(any());
    }
}
