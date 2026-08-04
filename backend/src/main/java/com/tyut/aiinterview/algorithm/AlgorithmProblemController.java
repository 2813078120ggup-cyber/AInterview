package com.tyut.aiinterview.algorithm;

import com.tyut.aiinterview.common.ApiResponse;
import com.tyut.aiinterview.common.PageResult;
import com.tyut.aiinterview.security.CurrentUser;
import java.util.List;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/algorithm")
public class AlgorithmProblemController {
    private final AlgorithmProblemService service;
    private final CurrentUser currentUser;

    public AlgorithmProblemController(AlgorithmProblemService service, CurrentUser currentUser) {
        this.service = service;
        this.currentUser = currentUser;
    }

    @GetMapping("/problems")
    public ApiResponse<PageResult<AlgorithmDtos.ProblemListItem>> list(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String difficulty,
            @RequestParam(required = false) Long tagId,
            @RequestParam(required = false) String progressStatus,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int pageSize) {
        return ApiResponse.ok(service.listProblems(currentUser.id(), keyword, difficulty,
                tagId, progressStatus, page, pageSize));
    }

    @GetMapping("/problems/{problemId}")
    public ApiResponse<AlgorithmDtos.ProblemDetailView> detail(@PathVariable Long problemId) {
        return ApiResponse.ok(service.detail(currentUser.id(), problemId));
    }

    @GetMapping("/tags")
    public ApiResponse<List<AlgorithmDtos.TagView>> tags() {
        return ApiResponse.ok(service.tags());
    }

    @PostMapping("/problems/{problemId}/favorite")
    public ApiResponse<Void> favorite(@PathVariable Long problemId) {
        service.toggleFavorite(currentUser.id(), problemId, true);
        return ApiResponse.ok();
    }

    @DeleteMapping("/problems/{problemId}/favorite")
    public ApiResponse<Void> unfavorite(@PathVariable Long problemId) {
        service.toggleFavorite(currentUser.id(), problemId, false);
        return ApiResponse.ok();
    }

    @PutMapping("/problems/{problemId}/note")
    public ApiResponse<Void> saveNote(@PathVariable Long problemId,
                                      @RequestBody AlgorithmDtos.NoteRequest request) {
        service.saveNote(currentUser.id(), problemId, request.content());
        return ApiResponse.ok();
    }
}
