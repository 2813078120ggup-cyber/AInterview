package com.tyut.aiinterview.ticket;

import com.tyut.aiinterview.common.ApiResponse;
import com.tyut.aiinterview.common.PageResult;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/v1/admin/tickets")
@PreAuthorize("hasRole('ADMIN')")
public class AdminFeedbackTicketController {
    private final FeedbackTicketService service;

    public AdminFeedbackTicketController(FeedbackTicketService service) {
        this.service = service;
    }

    @GetMapping
    public ApiResponse<PageResult<FeedbackTicketDtos.TicketSummary>> page(FeedbackTicketDtos.TicketQuery query) {
        return ApiResponse.ok(service.adminPage(query));
    }

    @GetMapping("/assignees")
    public ApiResponse<List<FeedbackTicketDtos.Assignee>> assignees() {
        return ApiResponse.ok(service.assignees());
    }

    @PutMapping("/{id}/assignee")
    public ApiResponse<FeedbackTicketDtos.Detail> assign(@PathVariable Long id,
                                                          @Valid @RequestBody FeedbackTicketDtos.AssigneeRequest request) {
        return ApiResponse.ok(service.assign(id, request));
    }

    @PutMapping("/{id}/status")
    public ApiResponse<FeedbackTicketDtos.Detail> status(@PathVariable Long id,
                                                          @Valid @RequestBody FeedbackTicketDtos.StatusRequest request) {
        return ApiResponse.ok(service.changeStatus(id, request));
    }
}
