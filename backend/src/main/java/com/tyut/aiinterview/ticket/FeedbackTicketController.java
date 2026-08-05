package com.tyut.aiinterview.ticket;

import com.tyut.aiinterview.common.ApiResponse;
import com.tyut.aiinterview.common.PageResult;
import jakarta.validation.Valid;
import java.io.IOException;
import java.util.List;
import org.springframework.core.io.Resource;
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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/v1/tickets")
public class FeedbackTicketController {
    private final FeedbackTicketService service;

    public FeedbackTicketController(FeedbackTicketService service) {
        this.service = service;
    }

    @PostMapping
    public ApiResponse<FeedbackTicketDtos.Detail> create(@RequestBody FeedbackTicketDtos.CreateRequest request) {
        return ApiResponse.ok(service.create(request));
    }

    @GetMapping("/my")
    public ApiResponse<PageResult<FeedbackTicketDtos.TicketSummary>> mine(FeedbackTicketDtos.TicketQuery query) {
        return ApiResponse.ok(service.mine(query));
    }

    @GetMapping("/{id}")
    public ApiResponse<FeedbackTicketDtos.Detail> get(@PathVariable Long id) {
        return ApiResponse.ok(service.get(id));
    }

    @PutMapping("/{id}")
    public ApiResponse<FeedbackTicketDtos.Detail> update(@PathVariable Long id,
                                                          @Valid @RequestBody FeedbackTicketDtos.UpdateDraftRequest request) {
        return ApiResponse.ok(service.updateDraft(id, request));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        service.deleteDraft(id);
        return ApiResponse.ok();
    }

    @PostMapping("/{id}/submit")
    public ApiResponse<FeedbackTicketDtos.Detail> submit(@PathVariable Long id) {
        return ApiResponse.ok(service.submit(id));
    }

    @GetMapping("/{id}/activities")
    public ApiResponse<List<FeedbackTicketDtos.Activity>> activities(@PathVariable Long id, FeedbackTicketDtos.ActivityQuery query) {
        return ApiResponse.ok(service.activities(id, query));
    }

    @PostMapping("/{id}/messages")
    public ApiResponse<FeedbackTicketDtos.Activity> message(@PathVariable Long id,
                                                             @Valid @RequestBody FeedbackTicketDtos.MessageRequest request) {
        return ApiResponse.ok(service.message(id, request));
    }

    @PostMapping(value = "/{id}/attachments", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResponse<FeedbackTicketDtos.Attachment> attachment(@PathVariable Long id,
                                                                  @RequestPart("file") MultipartFile file) {
        return ApiResponse.ok(service.uploadAttachment(id, file));
    }

    @GetMapping("/{ticketId}/attachments/{attachmentId}/content")
    public ResponseEntity<Resource> attachmentContent(@PathVariable Long ticketId, @PathVariable Long attachmentId) throws IOException {
        FeedbackTicketService.AttachmentContent content = service.attachmentContent(ticketId, attachmentId);
        String filename = content.originalName() == null ? "attachment" : content.originalName();
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(content.contentType()))
                .contentLength(content.resource().contentLength())
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.inline().filename(filename).build().toString())
                .body(content.resource());
    }

    @PutMapping("/{id}/read")
    public ApiResponse<Void> read(@PathVariable Long id, @RequestParam(required = false) Long activityId) {
        service.markRead(id, activityId);
        return ApiResponse.ok();
    }
}
