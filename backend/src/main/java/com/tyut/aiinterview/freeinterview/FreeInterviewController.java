package com.tyut.aiinterview.freeinterview;

import com.tyut.aiinterview.common.ApiResponse;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.MediaType;
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
@RequestMapping("/v1/free-interviews")
public class FreeInterviewController {
    private final FreeInterviewService service;
    public FreeInterviewController(FreeInterviewService service) { this.service = service; }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResponse<FreeInterviewDtos.SessionView> create(@RequestPart("resume") MultipartFile resume,
                                                              @RequestParam(required = false) String targetRole) {
        return ApiResponse.ok(service.create(resume, targetRole));
    }
    @GetMapping public ApiResponse<List<FreeInterviewDtos.HistoryView>> history() { return ApiResponse.ok(service.history()); }
    @GetMapping("/{id}") public ApiResponse<FreeInterviewDtos.DetailView> detail(@PathVariable Long id) { return ApiResponse.ok(service.detail(id)); }
    @PostMapping("/{id}/turns") public ApiResponse<FreeInterviewDtos.TurnResult> submitTurn(@PathVariable Long id, @Valid @RequestBody FreeInterviewDtos.SubmitTurnRequest request) { return ApiResponse.ok(service.submitTurn(id, request)); }
    @PostMapping("/{id}/report") public ApiResponse<FreeInterviewDtos.TaskResult> report(@PathVariable Long id) { return ApiResponse.ok(service.requestReport(id)); }
}
