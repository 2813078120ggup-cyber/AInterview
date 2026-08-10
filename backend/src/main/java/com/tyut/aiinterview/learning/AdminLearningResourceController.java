package com.tyut.aiinterview.learning;

import com.tyut.aiinterview.common.ApiResponse;
import com.tyut.aiinterview.common.PageResult;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/v1/admin/learning-resources")
@PreAuthorize("hasRole('ADMIN')")
public class AdminLearningResourceController {
    private final LearningResourceService service;

    public AdminLearningResourceController(LearningResourceService service) { this.service = service; }

    @GetMapping
    public ApiResponse<PageResult<LearningResourceDtos.ResourceSummary>> page(LearningResourceDtos.ResourceQuery query) { return ApiResponse.ok(service.adminPage(query)); }

    @GetMapping("/{publicId}")
    public ApiResponse<LearningResourceDtos.ResourceSummary> detail(@PathVariable String publicId) { return ApiResponse.ok(service.adminDetail(publicId)); }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResponse<LearningResourceDtos.ResourceSummary> create(@Valid @RequestPart("metadata") LearningResourceDtos.CreateRequest request, @RequestPart("file") MultipartFile file) { return ApiResponse.ok(service.create(request, file)); }

    @PutMapping("/{publicId}")
    public ApiResponse<LearningResourceDtos.ResourceSummary> update(@PathVariable String publicId, @Valid @RequestBody LearningResourceDtos.UpdateRequest request) { return ApiResponse.ok(service.update(publicId, request)); }

    @DeleteMapping("/{publicId}")
    public ApiResponse<Void> delete(@PathVariable String publicId) { service.delete(publicId); return ApiResponse.ok(); }

    @GetMapping("/{publicId}/permissions")
    public ApiResponse<List<LearningResourceDtos.PermissionView>> permissions(@PathVariable String publicId) { return ApiResponse.ok(service.permissions(publicId)); }

    @PutMapping("/{publicId}/permissions")
    public ApiResponse<LearningResourceDtos.PermissionResponse> permissions(@PathVariable String publicId, @RequestBody List<LearningResourceDtos.PermissionRequest> requests) { return ApiResponse.ok(service.replacePermissions(publicId, requests)); }
}
