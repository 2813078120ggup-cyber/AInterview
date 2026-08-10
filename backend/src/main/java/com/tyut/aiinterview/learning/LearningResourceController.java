package com.tyut.aiinterview.learning;

import com.tyut.aiinterview.common.ApiResponse;
import com.tyut.aiinterview.common.PageResult;
import jakarta.validation.Valid;
import java.io.IOException;
import java.util.List;
import org.springframework.core.io.Resource;
import org.springframework.http.CacheControl;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/v1/learning-resources")
public class LearningResourceController {
    private final LearningResourceService service;

    public LearningResourceController(LearningResourceService service) { this.service = service; }

    @GetMapping
    public ApiResponse<List<LearningResourceDtos.ResourceSummary>> list() { return ApiResponse.ok(service.visibleResources()); }

    @GetMapping("/page")
    public ApiResponse<PageResult<LearningResourceDtos.ResourceSummary>> page(LearningResourceDtos.ResourceQuery query) { return ApiResponse.ok(service.visiblePage(query)); }

    @GetMapping("/{publicId}")
    public ApiResponse<LearningResourceDtos.ResourceSummary> detail(@PathVariable String publicId) { return ApiResponse.ok(service.detail(publicId)); }

    @GetMapping("/{publicId}/content")
    public ResponseEntity<Resource> content(@PathVariable String publicId) throws IOException {
        return fileResponse(service.content(publicId), false);
    }

    @GetMapping("/{publicId}/download")
    public ResponseEntity<Resource> download(@PathVariable String publicId) throws IOException {
        return fileResponse(service.download(publicId), true);
    }

    private ResponseEntity<Resource> fileResponse(LearningResourceService.FileContent file, boolean download) throws IOException {
        String fileName = file.originalName() == null || file.originalName().isBlank() ? "learning-resource.pdf" : file.originalName();
        ContentDisposition disposition = download ? ContentDisposition.attachment().filename(fileName).build() : ContentDisposition.inline().filename(fileName).build();
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_PDF).contentLength(file.resource().contentLength())
                .cacheControl(CacheControl.noStore().cachePrivate()).header(HttpHeaders.CONTENT_DISPOSITION, disposition.toString())
                .header("X-Content-Type-Options", "nosniff").body(file.resource());
    }

    @GetMapping("/{publicId}/annotations")
    public ApiResponse<List<LearningResourceDtos.AnnotationView>> annotations(@PathVariable String publicId) { return ApiResponse.ok(service.annotations(publicId)); }

    @PostMapping("/{publicId}/annotations")
    public ApiResponse<LearningResourceDtos.AnnotationView> createAnnotation(@PathVariable String publicId, @Valid @RequestBody LearningResourceDtos.AnnotationRequest request) { return ApiResponse.ok(service.createAnnotation(publicId, request)); }

    @PutMapping("/annotations/{annotationPublicId}")
    public ApiResponse<LearningResourceDtos.AnnotationView> updateAnnotation(@PathVariable String annotationPublicId, @Valid @RequestBody LearningResourceDtos.AnnotationRequest request) { return ApiResponse.ok(service.updateAnnotation(annotationPublicId, request)); }

    @DeleteMapping("/annotations/{annotationPublicId}")
    public ApiResponse<Void> deleteAnnotation(@PathVariable String annotationPublicId) { service.deleteAnnotation(annotationPublicId); return ApiResponse.ok(); }
}
