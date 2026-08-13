package com.tyut.aiinterview.recruitment;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tyut.aiinterview.ai.AiTaskService;
import com.tyut.aiinterview.common.BusinessException;
import com.tyut.aiinterview.domain.CandidateResume;
import com.tyut.aiinterview.domain.CandidateResumeAnalysis;
import com.tyut.aiinterview.domain.JobApplication;
import com.tyut.aiinterview.domain.MediaFile;
import com.tyut.aiinterview.mapper.CandidateResumeAnalysisMapper;
import com.tyut.aiinterview.mapper.CandidateResumeMapper;
import com.tyut.aiinterview.mapper.JobApplicationMapper;
import com.tyut.aiinterview.media.MediaDtos;
import com.tyut.aiinterview.media.MediaService;
import com.tyut.aiinterview.security.CurrentUser;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.Resource;

class CandidateResumeServiceTest {
    private final CandidateResumeMapper resumeMapper = mock(CandidateResumeMapper.class);
    private final CandidateResumeAnalysisMapper analysisMapper = mock(CandidateResumeAnalysisMapper.class);
    private final JobApplicationMapper applicationMapper = mock(JobApplicationMapper.class);
    private final MediaService mediaService = mock(MediaService.class);
    private final AiTaskService taskService = mock(AiTaskService.class);
    private final CurrentUser currentUser = mock(CurrentUser.class);
    private final CompanyAccessService companyAccess = mock(CompanyAccessService.class);
    private final CandidateResumeService service = new CandidateResumeService(resumeMapper, analysisMapper,
            applicationMapper, mediaService, taskService, currentUser, new ObjectMapper(), companyAccess);

    @Test
    void companyCanReadOnlyResumeAttachedToItsOwnApplication() throws Exception {
        JobApplication application = new JobApplication();
        application.setId(11L);
        application.setCompanyId(100L);
        application.setCandidateId(7L);
        application.setResumeId(21L);
        when(companyAccess.requireApplication(11L)).thenReturn(application);
        CandidateResume resume = new CandidateResume();
        resume.setId(21L);
        resume.setCandidateId(7L);
        resume.setMediaId(31L);
        resume.setStatus(1);
        when(resumeMapper.selectById(21L)).thenReturn(resume);
        MediaFile media = new MediaFile();
        media.setId(31L);
        media.setOwnerId(7L);
        media.setStatus(MediaFile.AVAILABLE);
        when(mediaService.requireAvailable(31L)).thenReturn(media);
        Resource resource = mock(Resource.class);
        MediaDtos.MediaVO metadata = new MediaDtos.MediaVO(31L, "resume.pdf", "application/pdf", "resume",
                12L, MediaFile.AVAILABLE, null);
        when(mediaService.view(media)).thenReturn(metadata);
        when(mediaService.content(media)).thenReturn(resource);

        CandidateResumeService.ResumeContent content = service.companyContent(11L);

        assertSame(resource, content.resource());
        assertEquals("resume.pdf", content.metadata().originalName());
    }

    @Test
    void companyCannotReadApplicationBelongingToAnotherCompany() throws Exception {
        when(companyAccess.requireApplication(11L)).thenThrow(BusinessException.notFound("申请不存在"));

        assertThrows(BusinessException.class, () -> service.companyContent(11L));
        verify(resumeMapper, never()).selectById(any());
    }

    @Test
    void candidateAnalysisReturnsAllowlistedStructuredProfileWithoutResumeText() {
        when(currentUser.hasRole("CANDIDATE")).thenReturn(true);
        when(currentUser.id()).thenReturn(7L);
        CandidateResume resume = new CandidateResume();
        resume.setId(21L);
        resume.setCandidateId(7L);
        resume.setStatus(1);
        resume.setParseStatus("SUCCESS");
        resume.setParseVersion(2);
        when(resumeMapper.selectById(21L)).thenReturn(resume);
        CandidateResumeAnalysis analysis = new CandidateResumeAnalysis();
        analysis.setResumeId(21L);
        analysis.setAnalysisVersion(2);
        analysis.setStatus("SUCCESS");
        analysis.setProfileJson("""
                {"candidateProfile":"三年 Java 后端经验","skills":[{"name":"Java","level":"熟练","evidence":"订单系统"}],
                 "targetRoles":["Java 开发工程师"],"experienceHighlights":["负责订单服务"],
                 "projects":[{"name":"订单系统","role":"后端开发","evidence":"响应时间降低30%"}],
                 "interviewFocus":["缓存一致性"],"riskPoints":["分布式事务待核实"],
                 "extractedText":"不应返回","prompt":"不应返回"}
                """);
        when(analysisMapper.selectOne(any())).thenReturn(analysis);

        RecruitmentDtos.ResumeAnalysisView view = service.analysis(21L);

        assertEquals("SUCCESS", view.status());
        assertEquals("三年 Java 后端经验", view.summary());
        assertEquals(List.of("Java"), view.skills());
        assertEquals("订单系统", view.projects().get(0).name());
        assertEquals(List.of("缓存一致性"), view.interviewFocus());
    }

    @Test
    void companyAnalysisReturnsOnlyStructuredProfileForApplicationResume() {
        JobApplication application = new JobApplication();
        application.setId(11L);
        application.setCandidateId(7L);
        application.setResumeId(21L);
        when(companyAccess.requirePermission("application:read")).thenReturn(100L);
        when(companyAccess.requireApplication(11L)).thenReturn(application);

        CandidateResume resume = new CandidateResume();
        resume.setId(21L);
        resume.setCandidateId(7L);
        resume.setStatus(1);
        resume.setParseVersion(2);
        resume.setParseStatus("SUCCESS");
        when(resumeMapper.selectById(21L)).thenReturn(resume);

        CandidateResumeAnalysis analysis = new CandidateResumeAnalysis();
        analysis.setResumeId(21L);
        analysis.setAnalysisVersion(2);
        analysis.setStatus("SUCCESS");
        analysis.setProfileJson("{\"candidateProfile\":\"Java 后端工程师\",\"skills\":[\"Java\"],\"extractedText\":\"不应返回\"}");
        when(analysisMapper.selectOne(any())).thenReturn(analysis);

        RecruitmentDtos.ResumeAnalysisView view = service.companyAnalysis(11L);

        assertEquals("Java 后端工程师", view.summary());
        assertEquals(List.of("Java"), view.skills());
        assertFalse(view.toString().contains("不应返回"));
    }

    @Test
    void companyStructuredAnalysisReturnsOnlyTheProfileAllowlist() throws Exception {
        JobApplication application = new JobApplication();
        application.setId(12L);
        application.setCandidateId(7L);
        application.setResumeId(22L);
        when(companyAccess.requirePermission("application:read")).thenReturn(100L);
        when(companyAccess.requireApplication(12L)).thenReturn(application);

        CandidateResume resume = new CandidateResume();
        resume.setId(22L);
        resume.setCandidateId(7L);
        resume.setStatus(1);
        resume.setParseVersion(3);
        resume.setParseStatus("SUCCESS");
        when(resumeMapper.selectById(22L)).thenReturn(resume);

        CandidateResumeAnalysis analysis = new CandidateResumeAnalysis();
        analysis.setResumeId(22L);
        analysis.setAnalysisVersion(3);
        analysis.setStatus("SUCCESS");
        analysis.setProfileJson("""
                {"skills":["Java"],"workExperience":["负责订单服务"],
                 "projects":[{"name":"订单系统","role":"后端开发","evidence":"吞吐提升"}],
                 "education":["计算机科学学士"],"strengths":["问题定位清晰"],
                 "risks":["分布式事务待核实"],"followUpDirections":["缓存一致性"],
                 "extractedText":"不应返回","prompt":"不应返回","providerResponse":"不应返回",
                 "storagePath":"D:/private/resume.pdf"}
                """);
        when(analysisMapper.selectOne(any())).thenReturn(analysis);

        RecruitmentDtos.CompanyResumeAnalysisView view = service.companyStructuredAnalysis(12L);
        String json = new ObjectMapper().writeValueAsString(view);

        assertEquals(List.of("Java"), view.skills());
        assertEquals(List.of("负责订单服务"), view.workExperience());
        assertEquals(List.of("计算机科学学士"), view.education());
        assertEquals(List.of("问题定位清晰"), view.strengths());
        assertEquals(List.of("缓存一致性"), view.followUpDirections());
        assertEquals(3, view.analysisVersion());
        assertEquals("SUCCESS", view.status());
        assertFalse(json.contains("extractedText"));
        assertFalse(json.contains("providerResponse"));
        assertFalse(json.contains("storagePath"));
        assertFalse(json.contains("不应返回"));
    }

    @Test
    void companyStructuredAnalysisKeepsHistoricalApplicationSafeWhenResumeIsStopped() {
        JobApplication application = new JobApplication();
        application.setId(13L);
        application.setCandidateId(7L);
        application.setResumeId(23L);
        when(companyAccess.requirePermission("application:read")).thenReturn(100L);
        when(companyAccess.requireApplication(13L)).thenReturn(application);

        CandidateResume resume = new CandidateResume();
        resume.setId(23L);
        resume.setCandidateId(7L);
        resume.setStatus(0);
        when(resumeMapper.selectById(23L)).thenReturn(resume);

        RecruitmentDtos.CompanyResumeAnalysisView view = service.companyStructuredAnalysis(13L);

        assertEquals("NOT_AVAILABLE", view.status());
        verify(analysisMapper, never()).selectOne(any());
    }

    @Test
    void companyStructuredAnalysisCannotCrossCompanyApplicationBoundary() {
        when(companyAccess.requirePermission("application:read")).thenReturn(100L);
        when(companyAccess.requireApplication(99L)).thenThrow(BusinessException.notFound("申请不存在"));

        assertThrows(BusinessException.class, () -> service.companyStructuredAnalysis(99L));
        verify(resumeMapper, never()).selectById(any());
    }

    @Test
    void candidateCannotDeleteResumeAlreadyUsedByHistoricalApplication() {
        when(currentUser.hasRole("CANDIDATE")).thenReturn(true);
        when(currentUser.id()).thenReturn(7L);
        CandidateResume resume = new CandidateResume();
        resume.setId(24L);
        resume.setCandidateId(7L);
        resume.setStatus(1);
        when(resumeMapper.selectById(24L)).thenReturn(resume);
        when(applicationMapper.exists(any())).thenReturn(true);

        assertThrows(BusinessException.class, () -> service.delete(24L));
        verify(resumeMapper, never()).deleteById(24L);
    }
}
