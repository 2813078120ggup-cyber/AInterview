package com.tyut.aiinterview.admin;

import com.tyut.aiinterview.common.ApiResponse;
import java.io.IOException;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/v1/admin/candidates")
@PreAuthorize("hasRole('ADMIN')")
public class AdminCandidateProfileController {
    private final AdminCandidateProfileService service;

    public AdminCandidateProfileController(AdminCandidateProfileService service) {
        this.service = service;
    }

    @GetMapping("/{id}")
    public ApiResponse<AdminCandidateDtos.Detail> detail(@PathVariable Long id) {
        return ApiResponse.ok(service.detail(id));
    }

    @GetMapping("/{id}/avatar")
    public ResponseEntity<Resource> avatar(@PathVariable Long id) throws IOException {
        AdminCandidateProfileService.AvatarContent content = service.avatar(id);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(content.contentType()))
                .contentLength(content.resource().contentLength())
                .header(HttpHeaders.CACHE_CONTROL, "private, no-store")
                .header("X-Content-Type-Options", "nosniff")
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline")
                .body(content.resource());
    }
}
