package com.tyut.aiinterview.interview;

import com.tyut.aiinterview.common.ApiResponse;
import com.tyut.aiinterview.common.PageResult;
import com.tyut.aiinterview.domain.Interview;
import com.tyut.aiinterview.domain.InterviewAnswer;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/v1/interviews")
public class InterviewController {
    private final InterviewService service;

    public InterviewController(InterviewService service) {
        this.service = service;
    }

    /** Compatibility endpoint consumed by the existing interview hall. */
    @GetMapping
    public ApiResponse<List<Interview>> mine() {
        return ApiResponse.ok(service.mine());
    }

    @GetMapping("/page")
    public ApiResponse<PageResult<Interview>> page(InterviewDtos.InterviewQuery query) {
        return ApiResponse.ok(service.page(query));
    }

    @PostMapping
    public ApiResponse<Interview> create(@Valid @RequestBody InterviewDtos.CreateRequest request) {
        return ApiResponse.ok(service.create(request));
    }

    @GetMapping("/practice/banks")
    public ApiResponse<List<InterviewDtos.PracticeBankView>> practiceBanks() {
        return ApiResponse.ok(service.practiceBanks());
    }

    @PostMapping("/practice")
    public ApiResponse<Interview> createPractice(@Valid @RequestBody InterviewDtos.PracticeRequest request) {
        return ApiResponse.ok(service.createPractice(request));
    }

    @GetMapping("/{id}")
    public ApiResponse<Interview> detail(@PathVariable Long id) {
        return ApiResponse.ok(service.detail(id));
    }

    @PutMapping("/{id}/schedule")
    public ApiResponse<Interview> reschedule(@PathVariable Long id, @Valid @RequestBody InterviewDtos.RescheduleRequest request) {
        return ApiResponse.ok(service.reschedule(id, request));
    }

    @PostMapping("/{id}/cancel")
    public ApiResponse<Void> cancel(@PathVariable Long id, @RequestParam(required = false) String reason) {
        service.cancel(id, reason);
        return ApiResponse.ok();
    }

    @PostMapping("/{id}/pass")
    public ApiResponse<Interview> pass(@PathVariable Long id) {
        return ApiResponse.ok(service.pass(id));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        service.delete(id);
        return ApiResponse.ok();
    }

    @PostMapping("/{id}/start")
    @PreAuthorize("hasRole('CANDIDATE')")
    public ApiResponse<Interview> start(@PathVariable Long id) {
        return ApiResponse.ok(service.start(id));
    }

    @PostMapping("/{id}/end")
    @PreAuthorize("hasRole('CANDIDATE')")
    public ApiResponse<InterviewDtos.EndResponse> end(@PathVariable Long id) {
        return ApiResponse.ok(service.end(id));
    }

    @GetMapping("/{id}/questions")
    public ApiResponse<List<InterviewDtos.QuestionView>> questions(@PathVariable Long id) {
        return ApiResponse.ok(service.questions(id));
    }

    @GetMapping("/{id}/answers")
    public ApiResponse<List<InterviewDtos.AnswerView>> answers(@PathVariable Long id) {
        return ApiResponse.ok(service.answers(id));
    }

    @GetMapping("/{id}/progress")
    public ApiResponse<InterviewDtos.ProgressView> progress(@PathVariable Long id) {
        return ApiResponse.ok(service.progress(id));
    }

    @PutMapping("/{id}/progress")
    @PreAuthorize("hasRole('CANDIDATE')")
    public ApiResponse<InterviewDtos.ProgressView> updateProgress(@PathVariable Long id,
                                                                  @Valid @RequestBody InterviewDtos.ProgressRequest request) {
        return ApiResponse.ok(service.updateProgress(id, request));
    }

    @PutMapping("/{id}/questions/{interviewQuestionId}/answer")
    @PreAuthorize("hasRole('CANDIDATE')")
    public ApiResponse<InterviewAnswer> answer(@PathVariable Long id, @PathVariable Long interviewQuestionId,
                                                @Valid @RequestBody InterviewDtos.AnswerRequest request) {
        return ApiResponse.ok(service.submitAnswer(id, interviewQuestionId, request));
    }
}
