package com.tyut.aiinterview.account;

import com.tyut.aiinterview.common.ApiResponse;
import jakarta.validation.Valid;
import java.io.IOException;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/v1/account")
public class AccountController {
    private final AccountService service;
    private final AccountAvatarService avatarService;
    private final AccountContactService contactService;
    private final AccountPasswordService passwordService;
    private final AccountSessionService sessionService;

    public AccountController(AccountService service, AccountAvatarService avatarService,
                             AccountContactService contactService, AccountPasswordService passwordService,
                             AccountSessionService sessionService) {
        this.service = service;
        this.avatarService = avatarService;
        this.contactService = contactService;
        this.passwordService = passwordService;
        this.sessionService = sessionService;
    }

    @GetMapping("/profile")
    public ApiResponse<AccountDtos.AccountProfile> profile() {
        return ApiResponse.ok(service.profile());
    }

    @PutMapping("/profile")
    public ApiResponse<AccountDtos.AccountProfile> updateProfile(
            @Valid @RequestBody AccountDtos.UpdateProfileRequest request) {
        return ApiResponse.ok(service.updateProfile(request));
    }

    @PostMapping(value = "/avatar", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResponse<AccountDtos.AccountProfile> uploadAvatar(@RequestPart("file") MultipartFile file) {
        return ApiResponse.ok(avatarService.upload(file));
    }

    @GetMapping("/avatar/content")
    public ResponseEntity<Resource> avatarContent() throws IOException {
        AccountAvatarService.AvatarContent content = avatarService.content();
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(content.contentType()))
                .contentLength(content.resource().contentLength())
                .header(HttpHeaders.CACHE_CONTROL, "private, no-store")
                .header("X-Content-Type-Options", "nosniff")
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline")
                .body(content.resource());
    }

    @DeleteMapping("/avatar")
    public ApiResponse<AccountDtos.AccountProfile> deleteAvatar() {
        return ApiResponse.ok(avatarService.delete());
    }

    @PostMapping("/phone/code")
    public ApiResponse<AccountDtos.ChangeCodeResponse> sendPhoneCode(
            @Valid @RequestBody AccountDtos.ChangeCodeRequest request) {
        return ApiResponse.ok(contactService.sendCode("PHONE", request));
    }

    @PutMapping("/phone")
    public ApiResponse<AccountDtos.ContactChangeResponse> changePhone(
            @Valid @RequestBody AccountDtos.ChangeContactRequest request, HttpServletRequest servletRequest) {
        return ApiResponse.ok(contactService.change("PHONE", request, servletRequest.getRemoteAddr(), servletRequest.getHeader("User-Agent")));
    }

    @PostMapping("/email/code")
    public ApiResponse<AccountDtos.ChangeCodeResponse> sendEmailCode(
            @Valid @RequestBody AccountDtos.ChangeCodeRequest request) {
        return ApiResponse.ok(contactService.sendCode("EMAIL", request));
    }

    @PutMapping("/email")
    public ApiResponse<AccountDtos.ContactChangeResponse> changeEmail(
            @Valid @RequestBody AccountDtos.ChangeContactRequest request, HttpServletRequest servletRequest) {
        return ApiResponse.ok(contactService.change("EMAIL", request, servletRequest.getRemoteAddr(), servletRequest.getHeader("User-Agent")));
    }

    @PostMapping("/password/change")
    public ApiResponse<AccountDtos.ChangePasswordResponse> changePassword(
            @Valid @RequestBody AccountDtos.ChangePasswordRequest request, HttpServletRequest servletRequest) {
        return ApiResponse.ok(passwordService.change(request, servletRequest.getRemoteAddr(),
                servletRequest.getHeader("User-Agent")));
    }

    @GetMapping("/sessions")
    public ApiResponse<java.util.List<AccountDtos.AccountSession>> sessions() {
        return ApiResponse.ok(sessionService.sessions());
    }

    @DeleteMapping("/sessions/{sessionId}")
    public ApiResponse<Void> revokeSession(@PathVariable String sessionId) {
        sessionService.revoke(sessionId);
        return ApiResponse.ok();
    }

    @DeleteMapping("/sessions/others")
    public ApiResponse<Void> revokeOtherSessions() {
        sessionService.revokeOthers();
        return ApiResponse.ok();
    }
}
