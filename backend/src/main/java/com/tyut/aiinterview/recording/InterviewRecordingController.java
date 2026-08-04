package com.tyut.aiinterview.recording;

import com.tyut.aiinterview.common.ApiResponse;
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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/v1/interviews/{interviewId}/recording")
public class InterviewRecordingController {
    private final InterviewRecordingService service;

    public InterviewRecordingController(InterviewRecordingService service) {
        this.service = service;
    }

    @GetMapping
    public ApiResponse<RecordingDtos.RecordingView> get(@PathVariable Long interviewId) {
        return ApiResponse.ok(service.get(interviewId));
    }

    @PostMapping("/select")
    public ApiResponse<RecordingDtos.RecordingView> select(@PathVariable Long interviewId,
                                                           @Valid @RequestBody RecordingDtos.SelectModeRequest request) {
        return ApiResponse.ok(service.selectMode(interviewId, request));
    }

    @PostMapping("/events")
    public ApiResponse<RecordingDtos.TimelineEventView> event(@PathVariable Long interviewId,
                                                               @Valid @RequestBody RecordingDtos.TimelineEventRequest request) {
        return ApiResponse.ok(service.addEvent(interviewId, request));
    }

    @PostMapping(value = "/segments", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResponse<RecordingDtos.SegmentView> segment(@PathVariable Long interviewId,
                                                          @RequestParam Long interviewQuestionId,
                                                          @RequestParam long startedOffsetMs,
                                                          @RequestParam long endedOffsetMs,
                                                          @RequestPart("file") MultipartFile file) {
        return ApiResponse.ok(service.uploadSegment(interviewId, interviewQuestionId,
                startedOffsetMs, endedOffsetMs, file));
    }

    @PostMapping("/complete")
    public ApiResponse<RecordingDtos.RecordingView> complete(@PathVariable Long interviewId) {
        return ApiResponse.ok(service.complete(interviewId));
    }

    @GetMapping("/segments/{segmentId}/content")
    public ResponseEntity<Resource> content(@PathVariable Long interviewId, @PathVariable Long segmentId) throws IOException {
        RecordingDtos.RecordingContent content = service.content(interviewId, segmentId);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(content.contentType()))
                .contentLength(content.sizeBytes())
                .header(HttpHeaders.ACCEPT_RANGES, "bytes")
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.inline().filename(content.filename()).build().toString())
                .body(content.resource());
    }
}
