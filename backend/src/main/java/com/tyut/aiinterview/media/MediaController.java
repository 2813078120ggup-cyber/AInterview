package com.tyut.aiinterview.media;

import com.tyut.aiinterview.ai.AiTaskService;
import com.tyut.aiinterview.common.ApiResponse;
import com.tyut.aiinterview.domain.AiTask;
import jakarta.validation.Valid;
import java.io.IOException;
import org.springframework.core.io.Resource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
public class MediaController {
    private final MediaService mediaService;
    private final AiTaskService taskService;
    public MediaController(MediaService mediaService, AiTaskService taskService) { this.mediaService = mediaService; this.taskService = taskService; }

    @PostMapping(value = "/v1/media", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResponse<MediaDtos.MediaVO> upload(@RequestPart("file") MultipartFile file) { return ApiResponse.ok(mediaService.upload(file)); }
    @GetMapping("/v1/media/{id}") public ApiResponse<MediaDtos.MediaVO> get(@PathVariable Long id) { return ApiResponse.ok(mediaService.get(id)); }
    @GetMapping("/v1/media/{id}/content")
    public ResponseEntity<Resource> content(@PathVariable Long id) throws IOException {
        MediaDtos.MediaVO media = mediaService.get(id); Resource resource = mediaService.content(id);
        return ResponseEntity.ok().contentType(MediaType.parseMediaType(media.contentType())).contentLength(resource.contentLength())
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment().filename(media.originalName() == null ? "media" : media.originalName()).build().toString()).body(resource);
    }
    @PostMapping("/v1/interviews/{interviewId}/follow-ups")
    public ApiResponse<AiTask> followUp(@PathVariable Long interviewId, @Valid @RequestBody MediaDtos.FollowUpRequest request) { return ApiResponse.ok(taskService.requestFollowUp(interviewId, request.interviewQuestionId(), request.answer())); }
    @PostMapping("/v1/interviews/{interviewId}/ai-opening")
    public ApiResponse<AiTask> opening(@PathVariable Long interviewId) { return ApiResponse.ok(taskService.requestOpening(interviewId)); }
    @GetMapping("/v1/ai-tasks/{id}") public ApiResponse<AiTask> task(@PathVariable Long id) { return ApiResponse.ok(taskService.get(id)); }
    @GetMapping("/v1/interviews/{interviewId}/evaluation-task")
    public ApiResponse<AiTask> evaluationTask(@PathVariable Long interviewId) { return ApiResponse.ok(taskService.latestEvaluationTask(interviewId)); }
    @PostMapping("/v1/interviews/{interviewId}/evaluation-task/retry")
    public ApiResponse<AiTask> retryEvaluationTask(@PathVariable Long interviewId) { return ApiResponse.ok(taskService.retryAutomaticEvaluation(interviewId)); }
    @PostMapping("/v1/interviews/{interviewId}/evaluation-task/regenerate")
    public ApiResponse<AiTask> regenerateEvaluationTask(@PathVariable Long interviewId) { return ApiResponse.ok(taskService.regenerateAutomaticEvaluation(interviewId)); }
}
