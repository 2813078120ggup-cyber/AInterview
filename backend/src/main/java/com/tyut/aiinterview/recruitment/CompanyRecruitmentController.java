package com.tyut.aiinterview.recruitment;

import com.tyut.aiinterview.common.ApiResponse;
import com.tyut.aiinterview.common.PageResult;
import com.tyut.aiinterview.report.ReportDtos;
import com.tyut.aiinterview.report.ReportService;
import jakarta.validation.Valid;
import java.io.IOException;
import java.time.LocalDate;
import org.springframework.core.io.Resource;
import org.springframework.http.CacheControl;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.beans.factory.annotation.Autowired;

@RestController
@RequestMapping("/v1/company/recruitment")
public class CompanyRecruitmentController {
    private final RecruitmentService service;
    private final CandidateResumeService resumeService;
    private final ReportService reportService;
    private final CompanyDashboardService dashboardService;
    private final CompanyAnalyticsService analyticsService;
    private final CompanyApplicationTimelineService timelineService;
    private CompanyInterviewService companyInterviewService;
    private CompanyReportReviewService companyReportReviewService;
    private CompanyTalentPoolService companyTalentPoolService;

    public CompanyRecruitmentController(RecruitmentService service, CandidateResumeService resumeService,
                                        ReportService reportService, CompanyDashboardService dashboardService,
                                        CompanyApplicationTimelineService timelineService) {
        this(service, resumeService, reportService, dashboardService, timelineService, null);
    }

    @Autowired
    public CompanyRecruitmentController(RecruitmentService service, CandidateResumeService resumeService,
                                        ReportService reportService, CompanyDashboardService dashboardService,
                                        CompanyApplicationTimelineService timelineService,
                                        CompanyAnalyticsService analyticsService) {
        this.service = service;
        this.resumeService = resumeService;
        this.reportService = reportService;
        this.dashboardService = dashboardService;
        this.timelineService = timelineService;
        this.analyticsService = analyticsService;
    }

    @Autowired
    public void setCompanyInterviewService(CompanyInterviewService companyInterviewService) {
        this.companyInterviewService = companyInterviewService;
    }

    @Autowired
    public void setCompanyReportReviewService(CompanyReportReviewService companyReportReviewService) {
        this.companyReportReviewService = companyReportReviewService;
    }

    @Autowired
    public void setCompanyTalentPoolService(CompanyTalentPoolService companyTalentPoolService) {
        this.companyTalentPoolService = companyTalentPoolService;
    }

    @GetMapping("/dashboard")
    @PreAuthorize("@companyAccessService.hasPermission('analytics:read')")
    public ApiResponse<RecruitmentDtos.Dashboard> dashboard() {
        return ApiResponse.ok(service.companyDashboard());
    }

    @GetMapping("/dashboard/summary")
    @PreAuthorize("@companyAccessService.hasPermission('analytics:read')")
    public ApiResponse<RecruitmentDtos.DashboardSummary> dashboardSummary() {
        return ApiResponse.ok(dashboardService.summary());
    }

    @GetMapping("/dashboard/actions")
    @PreAuthorize("@companyAccessService.hasPermission('analytics:read')")
    public ApiResponse<RecruitmentDtos.ActionCenter> dashboardActions() {
        return ApiResponse.ok(dashboardService.actions());
    }

    @GetMapping("/dashboard/upcoming-interviews")
    @PreAuthorize("@companyAccessService.hasPermission('analytics:read')")
    public ApiResponse<java.util.List<RecruitmentDtos.UpcomingInterview>> dashboardUpcomingInterviews() {
        return ApiResponse.ok(dashboardService.upcomingInterviews());
    }

    @GetMapping("/analytics/funnel")
    @PreAuthorize("@companyAccessService.hasPermission('analytics:read')")
    public ApiResponse<java.util.List<RecruitmentDtos.FunnelStage>> analyticsFunnel() {
        return ApiResponse.ok(dashboardService.funnel());
    }

    @GetMapping("/analytics/positions")
    @PreAuthorize("@companyAccessService.hasPermission('analytics:read')")
    public ApiResponse<java.util.List<RecruitmentDtos.PositionAnalytics>> analyticsPositions() {
        return ApiResponse.ok(dashboardService.positions());
    }

    @GetMapping("/analytics/overview")
    @PreAuthorize("@companyAccessService.hasPermission('analytics:read')")
    public ApiResponse<CompanyAnalyticsDtos.Overview> analyticsOverview(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return ApiResponse.ok(analyticsService.overview(from, to));
    }

    @GetMapping("/analytics/positions/page")
    @PreAuthorize("@companyAccessService.hasPermission('analytics:read')")
    public ApiResponse<PageResult<CompanyAnalyticsDtos.PositionAnalytics>> analyticsPositionPage(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(defaultValue = "1") long pageNo,
            @RequestParam(defaultValue = "20") long pageSize) {
        return ApiResponse.ok(analyticsService.positions(from, to, pageNo, pageSize));
    }

    @GetMapping("/positions")
    @PreAuthorize("@companyAccessService.hasPermission('recruitment:position:read')")
    public ApiResponse<PageResult<RecruitmentDtos.JobView>> positions(RecruitmentDtos.JobQuery query) {
        return ApiResponse.ok(service.companyPositions(query));
    }

    @PostMapping("/positions")
    @PreAuthorize("@companyAccessService.hasPermission('recruitment:position:write')")
    public ApiResponse<RecruitmentDtos.JobView> createPosition(@Valid @RequestBody RecruitmentDtos.PositionRequest request) {
        return ApiResponse.ok(service.createPosition(request));
    }

    @PutMapping("/positions/{id}")
    @PreAuthorize("@companyAccessService.hasPermission('recruitment:position:write')")
    public ApiResponse<RecruitmentDtos.JobView> updatePosition(@PathVariable Long id,
                                                               @Valid @RequestBody RecruitmentDtos.PositionRequest request) {
        return ApiResponse.ok(service.updatePosition(id, request));
    }

    @GetMapping("/positions/{id}")
    @PreAuthorize("@companyAccessService.hasPermission('recruitment:position:read')")
    public ApiResponse<RecruitmentDtos.PositionDetail> position(@PathVariable Long id) {
        return ApiResponse.ok(service.companyPositionDetail(id));
    }

    @GetMapping("/positions/{id}/statistics")
    @PreAuthorize("@companyAccessService.hasPermission('recruitment:position:read')")
    public ApiResponse<RecruitmentDtos.PositionStatistics> positionStatistics(@PathVariable Long id) {
        return ApiResponse.ok(service.companyPositionStatistics(id));
    }

    @PostMapping("/positions/{id}/clone")
    @PreAuthorize("@companyAccessService.hasPermission('recruitment:position:write')")
    public ApiResponse<RecruitmentDtos.JobView> clonePosition(@PathVariable Long id) {
        return ApiResponse.ok(service.clonePosition(id));
    }

    @PutMapping("/positions/{id}/status")
    @PreAuthorize("@companyAccessService.hasPermission('recruitment:position:write')")
    public ApiResponse<RecruitmentDtos.JobView> updatePositionStatus(
            @PathVariable Long id, @Valid @RequestBody RecruitmentDtos.PositionStatusRequest request) {
        return ApiResponse.ok(service.updatePositionStatus(id, request));
    }

    @GetMapping("/applications")
    @PreAuthorize("@companyAccessService.hasPermission('application:read')")
    public ApiResponse<PageResult<RecruitmentDtos.ApplicationView>> applications(RecruitmentDtos.ApplicationQuery query) {
        return ApiResponse.ok(service.companyApplications(query));
    }

    @GetMapping("/applications/{id}")
    @PreAuthorize("@companyAccessService.hasPermission('application:read')")
    public ApiResponse<RecruitmentDtos.ApplicationView> application(@PathVariable Long id) {
        return ApiResponse.ok(service.companyApplicationDetail(id));
    }

    @GetMapping("/applications/{id}/resume/profile")
    @PreAuthorize("@companyAccessService.hasPermission('application:read')")
    public ApiResponse<RecruitmentDtos.ResumeAnalysisView> resumeProfile(@PathVariable Long id) {
        return ApiResponse.ok(resumeService.companyAnalysis(id));
    }

    @GetMapping("/applications/{id}/resume/analysis")
    @PreAuthorize("@companyAccessService.hasPermission('application:read')")
    public ApiResponse<RecruitmentDtos.CompanyResumeAnalysisView> resumeAnalysis(@PathVariable Long id) {
        return ApiResponse.ok(resumeService.companyStructuredAnalysis(id));
    }

    @PostMapping("/applications/{id}/resume/analysis/retry")
    @PreAuthorize("@companyAccessService.hasPermission('application:review')")
    public ApiResponse<RecruitmentDtos.ResumeParseRetryView> retryResumeAnalysis(@PathVariable Long id) {
        return ApiResponse.ok(resumeService.companyRetryAnalysis(id));
    }

    @GetMapping("/applications/{id}/interview")
    @PreAuthorize("@companyAccessService.hasPermission('application:read')")
    public ApiResponse<RecruitmentDtos.ApplicationInterviewView> interview(@PathVariable Long id) {
        return ApiResponse.ok(service.companyInterviewDetail(id));
    }

    @GetMapping("/applications/{id}/timeline")
    @PreAuthorize("@companyAccessService.hasPermission('application:read')")
    public ApiResponse<java.util.List<RecruitmentDtos.ApplicationTimelineEventView>> timeline(@PathVariable Long id) {
        return ApiResponse.ok(timelineService.timeline(id));
    }

    @GetMapping("/applications/{id}/resume/content")
    @PreAuthorize("@companyAccessService.hasPermission('application:read')")
    public ResponseEntity<Resource> resumeContent(@PathVariable Long id) throws IOException {
        CandidateResumeService.ResumeContent content = resumeService.companyContent(id);
        String fileName = content.metadata().originalName() == null ? "resume" : content.metadata().originalName();
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .contentType(MediaType.parseMediaType(content.metadata().contentType()))
                .contentLength(content.resource().contentLength())
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.inline().filename(fileName).build().toString())
                .header("X-Content-Type-Options", "nosniff")
                .body(content.resource());
    }

    @GetMapping("/applications/{id}/report")
    @PreAuthorize("@companyAccessService.hasPermission('report:read') || @companyAccessService.hasPermission('interview:review')")
    public ApiResponse<ReportDtos.CompanyReportDetail> report(@PathVariable Long id) {
        return ApiResponse.ok(companyReportReviewService == null ? reportService.companyDetail(id)
                : companyReportReviewService.companyDetail(id));
    }

    @PostMapping("/applications/{id}/report/retry")
    @PreAuthorize("@companyAccessService.hasPermission('interview:review')")
    public ApiResponse<ReportDtos.CompanyReportDetail> retryReport(@PathVariable Long id) {
        return ApiResponse.ok(companyReportReviewService.retry(id));
    }

    @PostMapping("/applications/{id}/report/publish")
    @PreAuthorize("@companyAccessService.hasPermission('interview:review')")
    public ApiResponse<ReportDtos.CompanyReportDetail> publishReport(@PathVariable Long id) {
        return ApiResponse.ok(companyReportReviewService.publish(id));
    }

    @GetMapping("/interview-question-banks")
    @PreAuthorize("@companyAccessService.hasPermission('interview:create')")
    public ApiResponse<java.util.List<RecruitmentDtos.InterviewQuestionBankView>> interviewQuestionBanks() {
        return ApiResponse.ok(service.companyInterviewQuestionBanks());
    }

    @PostMapping("/applications/{id}/match/retry")
    @PreAuthorize("@companyAccessService.hasPermission('application:review')")
    public ApiResponse<RecruitmentDtos.ApplicationView> retryMatch(@PathVariable Long id) {
        return ApiResponse.ok(service.retryCompanyMatch(id));
    }

    @GetMapping("/applications/{id}/match")
    @PreAuthorize("@companyAccessService.hasPermission('application:read')")
    public ApiResponse<RecruitmentDtos.MatchEvaluationView> match(@PathVariable Long id) {
        return ApiResponse.ok(service.companyMatch(id));
    }

    @GetMapping("/applications/{id}/match/history")
    @PreAuthorize("@companyAccessService.hasPermission('application:read')")
    public ApiResponse<PageResult<RecruitmentDtos.MatchEvaluationView>> matchHistory(
            @PathVariable Long id, @RequestParam(defaultValue = "1") Long pageNo,
            @RequestParam(defaultValue = "5") Long pageSize) {
        return ApiResponse.ok(service.companyMatchHistory(id, pageNo, pageSize));
    }

    @PostMapping("/applications/{id}/ai-interview")
    @PreAuthorize("@companyAccessService.hasPermission('interview:create')")
    public ApiResponse<RecruitmentDtos.ApplicationView> createAiInterview(@PathVariable Long id,
                                                                           @Valid @RequestBody RecruitmentDtos.AiInterviewRequest request) {
        return ApiResponse.ok(service.createAiInterview(id, request));
    }

    @PutMapping("/applications/{id}/status")
    @PreAuthorize("@companyAccessService.hasPermission('application:review')")
    public ApiResponse<RecruitmentDtos.ApplicationView> updateStatus(@PathVariable Long id,
                                                                     @Valid @RequestBody RecruitmentDtos.StatusUpdateRequest request) {
        return ApiResponse.ok(service.updateApplicationStatus(id, request));
    }

    @PostMapping("/applications/{id}/offline-interview")
    @PreAuthorize("@companyAccessService.hasPermission('interview:create')")
    public ApiResponse<RecruitmentDtos.ApplicationView> inviteOfflineInterview(
            @PathVariable Long id, @Valid @RequestBody RecruitmentDtos.OfflineInterviewRequest request) {
        return ApiResponse.ok(service.inviteOfflineInterview(id, request));
    }

    @GetMapping("/talent-pool")
    @PreAuthorize("@companyAccessService.hasPermission('application:read')")
    public ApiResponse<PageResult<TalentPoolDtos.CandidateView>> talentPool(TalentPoolDtos.Query query) {
        return ApiResponse.ok(companyTalentPoolService.page(query));
    }

    @GetMapping("/talent-pool/tags")
    @PreAuthorize("@companyAccessService.hasPermission('application:read')")
    public ApiResponse<java.util.List<TalentPoolDtos.TagView>> talentPoolTags() {
        return ApiResponse.ok(companyTalentPoolService.listTags());
    }

    @PostMapping("/talent-pool/tags")
    @PreAuthorize("@companyAccessService.hasPermission('application:review')")
    public ApiResponse<TalentPoolDtos.TagView> createTalentPoolTag(@Valid @RequestBody TalentPoolDtos.TagRequest request) {
        return ApiResponse.ok(companyTalentPoolService.createTag(request));
    }

    @GetMapping("/talent-pool/candidates/{candidateId}/membership")
    @PreAuthorize("@companyAccessService.hasPermission('application:read')")
    public ApiResponse<TalentPoolDtos.MembershipView> talentPoolMembership(@PathVariable Long candidateId) {
        return ApiResponse.ok(companyTalentPoolService.membership(candidateId));
    }

    @PostMapping("/talent-pool/candidates/{candidateId}")
    @PreAuthorize("@companyAccessService.hasPermission('application:review')")
    public ApiResponse<TalentPoolDtos.MembershipView> addTalentPoolCandidate(@PathVariable Long candidateId) {
        return ApiResponse.ok(companyTalentPoolService.add(candidateId));
    }

    @GetMapping("/talent-pool/{candidateId}")
    @PreAuthorize("@companyAccessService.hasPermission('application:read')")
    public ApiResponse<TalentPoolDtos.Detail> talentPoolDetail(
            @PathVariable Long candidateId,
            @RequestParam(defaultValue = "1") Long notePageNo,
            @RequestParam(defaultValue = "20") Long notePageSize) {
        return ApiResponse.ok(companyTalentPoolService.detail(candidateId, notePageNo, notePageSize));
    }

    @DeleteMapping("/talent-pool/{candidateId}")
    @PreAuthorize("@companyAccessService.hasPermission('application:review')")
    public ApiResponse<TalentPoolDtos.MembershipView> removeTalentPoolCandidate(@PathVariable Long candidateId) {
        return ApiResponse.ok(companyTalentPoolService.remove(candidateId));
    }

    @PostMapping("/talent-pool/{candidateId}/contact")
    @PreAuthorize("@companyAccessService.hasPermission('application:review')")
    public ApiResponse<TalentPoolDtos.MembershipView> markTalentPoolContacted(@PathVariable Long candidateId) {
        return ApiResponse.ok(companyTalentPoolService.markContacted(candidateId));
    }

    @GetMapping("/talent-pool/{candidateId}/notes")
    @PreAuthorize("@companyAccessService.hasPermission('application:read')")
    public ApiResponse<PageResult<TalentPoolDtos.NoteView>> talentPoolNotes(
            @PathVariable Long candidateId,
            @RequestParam(defaultValue = "1") Long pageNo,
            @RequestParam(defaultValue = "20") Long pageSize) {
        return ApiResponse.ok(companyTalentPoolService.notes(candidateId, pageNo, pageSize));
    }

    @PostMapping("/talent-pool/{candidateId}/notes")
    @PreAuthorize("@companyAccessService.hasPermission('application:review')")
    public ApiResponse<TalentPoolDtos.NoteView> addTalentPoolNote(
            @PathVariable Long candidateId, @Valid @RequestBody TalentPoolDtos.NoteRequest request) {
        return ApiResponse.ok(companyTalentPoolService.createNote(candidateId, request));
    }

    @PutMapping("/talent-pool/{candidateId}/notes/{noteId}")
    @PreAuthorize("@companyAccessService.hasPermission('application:review')")
    public ApiResponse<TalentPoolDtos.NoteView> updateTalentPoolNote(
            @PathVariable Long candidateId, @PathVariable Long noteId,
            @Valid @RequestBody TalentPoolDtos.NoteUpdateRequest request) {
        return ApiResponse.ok(companyTalentPoolService.updateNote(candidateId, noteId, request));
    }

    @PostMapping("/talent-pool/{candidateId}/tags/{tagId}")
    @PreAuthorize("@companyAccessService.hasPermission('application:review')")
    public ApiResponse<java.util.List<TalentPoolDtos.TagView>> addTalentPoolTag(
            @PathVariable Long candidateId, @PathVariable Long tagId) {
        return ApiResponse.ok(companyTalentPoolService.addTag(candidateId, tagId));
    }

    @DeleteMapping("/talent-pool/{candidateId}/tags/{tagId}")
    @PreAuthorize("@companyAccessService.hasPermission('application:review')")
    public ApiResponse<java.util.List<TalentPoolDtos.TagView>> removeTalentPoolTag(
            @PathVariable Long candidateId, @PathVariable Long tagId) {
        return ApiResponse.ok(companyTalentPoolService.removeTag(candidateId, tagId));
    }

    @GetMapping("/interviews")
    @PreAuthorize("@companyAccessService.hasPermission('application:read')")
    public ApiResponse<CompanyInterviewDtos.Page> interviews(CompanyInterviewDtos.Query query) {
        return ApiResponse.ok(companyInterviewService.page(query));
    }

    @GetMapping("/interviews/calendar")
    @PreAuthorize("@companyAccessService.hasPermission('application:read')")
    public ApiResponse<CompanyInterviewDtos.Page> interviewCalendar(CompanyInterviewDtos.Query query) {
        return ApiResponse.ok(companyInterviewService.page(query));
    }

    @GetMapping("/interviews/{id}")
    @PreAuthorize("@companyAccessService.hasPermission('application:read')")
    public ApiResponse<CompanyInterviewDtos.Detail> interviewDetail(@PathVariable String id) {
        return ApiResponse.ok(companyInterviewService.detail(id));
    }

    @PutMapping("/interviews/{id}/schedule")
    @PreAuthorize("@companyAccessService.hasPermission('interview:review')")
    public ApiResponse<CompanyInterviewDtos.Detail> rescheduleInterview(
            @PathVariable String id, @Valid @RequestBody CompanyInterviewDtos.RescheduleRequest request) {
        return ApiResponse.ok(companyInterviewService.reschedule(id, request));
    }

    @PostMapping("/interviews/{id}/cancel")
    @PreAuthorize("@companyAccessService.hasPermission('interview:review')")
    public ApiResponse<CompanyInterviewDtos.Detail> cancelInterview(
            @PathVariable String id, @Valid @RequestBody(required = false) CompanyInterviewDtos.ActionRequest request) {
        return ApiResponse.ok(companyInterviewService.cancel(id, request));
    }

    @PostMapping("/interviews/{id}/complete")
    @PreAuthorize("@companyAccessService.hasPermission('interview:review')")
    public ApiResponse<CompanyInterviewDtos.Detail> completeInterview(
            @PathVariable String id, @Valid @RequestBody(required = false) CompanyInterviewDtos.ActionRequest request) {
        return ApiResponse.ok(companyInterviewService.complete(id, request));
    }

    @PostMapping("/interviews/{id}/retry")
    @PreAuthorize("@companyAccessService.hasPermission('interview:review')")
    public ApiResponse<CompanyInterviewDtos.RetryView> retryInterview(@PathVariable String id) {
        return ApiResponse.ok(companyInterviewService.retry(id));
    }
}
