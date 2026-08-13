package com.tyut.aiinterview.recruitment;

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
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/v1/recruitment")
@PreAuthorize("hasRole('CANDIDATE')")
public class CandidateRecruitmentController {
    private final RecruitmentService service;
    private final CandidateResumeService resumeService;

    public CandidateRecruitmentController(RecruitmentService service, CandidateResumeService resumeService) {
        this.service = service;
        this.resumeService = resumeService;
    }

    @GetMapping("/jobs")
    public ApiResponse<PageResult<RecruitmentDtos.JobView>> jobs(RecruitmentDtos.JobQuery query) {
        return ApiResponse.ok(service.jobHall(query));
    }

    @GetMapping("/jobs/{id}")
    public ApiResponse<RecruitmentDtos.JobView> job(@PathVariable Long id) {
        return ApiResponse.ok(service.jobDetail(id));
    }

    @GetMapping("/resumes")
    public ApiResponse<List<RecruitmentDtos.ResumeView>> resumes() {
        return ApiResponse.ok(resumeService.list());
    }

    @PostMapping(value = "/resumes", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResponse<RecruitmentDtos.ResumeView> uploadResume(@RequestPart("file") org.springframework.web.multipart.MultipartFile file,
                                                                  @RequestParam(required = false) String title,
                                                                  @RequestParam(defaultValue = "false") boolean defaultResume) {
        return ApiResponse.ok(resumeService.upload(file, title, defaultResume));
    }

    @GetMapping("/resumes/{id}")
    public ApiResponse<RecruitmentDtos.ResumeView> resume(@PathVariable Long id) {
        return ApiResponse.ok(resumeService.detail(id));
    }

    @GetMapping("/resumes/{id}/analysis")
    public ApiResponse<RecruitmentDtos.ResumeAnalysisView> resumeAnalysis(@PathVariable Long id) {
        return ApiResponse.ok(resumeService.analysis(id));
    }

    @PutMapping("/resumes/{id}/default")
    public ApiResponse<RecruitmentDtos.ResumeView> defaultResume(@PathVariable Long id) {
        return ApiResponse.ok(resumeService.setDefault(id));
    }

    @DeleteMapping("/resumes/{id}")
    public ApiResponse<Void> deleteResume(@PathVariable Long id) {
        resumeService.delete(id);
        return ApiResponse.ok();
    }

    @PostMapping("/resumes/{id}/parse/retry")
    public ApiResponse<RecruitmentDtos.ResumeParseTaskView> retryResumeParse(@PathVariable Long id) {
        return ApiResponse.ok(resumeService.retryParse(id));
    }

    @GetMapping("/resumes/{id}/parse-task")
    public ApiResponse<RecruitmentDtos.ResumeParseTaskView> resumeParseTask(@PathVariable Long id) {
        return ApiResponse.ok(resumeService.parseTask(id));
    }

    @GetMapping("/resumes/{id}/content")
    public ResponseEntity<Resource> resumeContent(@PathVariable Long id) throws IOException {
        CandidateResumeService.ResumeContent content = resumeService.content(id);
        String fileName = content.metadata().originalName() == null ? "resume" : content.metadata().originalName();
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).contentType(MediaType.parseMediaType(content.metadata().contentType()))
                .contentLength(content.resource().contentLength())
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.inline().filename(fileName).build().toString())
                .header("X-Content-Type-Options", "nosniff")
                .body(content.resource());
    }

    @PostMapping("/jobs/{id}/applications")
    public ApiResponse<RecruitmentDtos.ApplicationView> apply(@PathVariable Long id,
                                                               @Valid @RequestBody RecruitmentDtos.ApplyRequest request) {
        return ApiResponse.ok(service.apply(id, request));
    }

    @GetMapping("/applications")
    public ApiResponse<PageResult<RecruitmentDtos.ApplicationView>> applications(RecruitmentDtos.ApplicationQuery query) {
        return ApiResponse.ok(service.myApplications(query));
    }

    @GetMapping("/applications/{id}")
    public ApiResponse<RecruitmentDtos.ApplicationView> application(@PathVariable Long id) {
        return ApiResponse.ok(service.myApplicationDetail(id));
    }

    @PostMapping("/applications/{id}/match/retry")
    public ApiResponse<RecruitmentDtos.ApplicationView> retryMatch(@PathVariable Long id) {
        return ApiResponse.ok(service.retryCandidateMatch(id));
    }

    @GetMapping("/applications/{id}/match")
    public ApiResponse<RecruitmentDtos.MatchEvaluationView> match(@PathVariable Long id) {
        return ApiResponse.ok(service.candidateMatch(id));
    }

    @GetMapping("/applications/{id}/match/history")
    public ApiResponse<PageResult<RecruitmentDtos.MatchEvaluationView>> matchHistory(
            @PathVariable Long id, @RequestParam(defaultValue = "1") Long pageNo,
            @RequestParam(defaultValue = "5") Long pageSize) {
        return ApiResponse.ok(service.candidateMatchHistory(id, pageNo, pageSize));
    }
}
