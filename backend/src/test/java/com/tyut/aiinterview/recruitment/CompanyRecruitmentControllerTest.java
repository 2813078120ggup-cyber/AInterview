package com.tyut.aiinterview.recruitment;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.tyut.aiinterview.domain.MediaFile;
import com.tyut.aiinterview.media.MediaDtos;
import com.tyut.aiinterview.report.ReportService;
import java.io.IOException;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.Resource;
import org.springframework.http.ResponseEntity;

class CompanyRecruitmentControllerTest {
    private final RecruitmentService recruitmentService = mock(RecruitmentService.class);
    private final CandidateResumeService resumeService = mock(CandidateResumeService.class);
    private final ReportService reportService = mock(ReportService.class);
    private final CompanyDashboardService dashboardService = mock(CompanyDashboardService.class);
    private final CompanyApplicationTimelineService timelineService = mock(CompanyApplicationTimelineService.class);
    private final CompanyRecruitmentController controller = new CompanyRecruitmentController(recruitmentService,
            resumeService, reportService, dashboardService, timelineService);

    @Test
    void privateResumeResponseDisablesCachingAndSniffing() throws IOException {
        Resource resource = mock(Resource.class);
        when(resource.contentLength()).thenReturn(32L);
        MediaDtos.MediaVO metadata = new MediaDtos.MediaVO(31L, "candidate.docx",
                "application/vnd.openxmlformats-officedocument.wordprocessingml.document", "resume", 32L,
                MediaFile.AVAILABLE, null);
        when(resumeService.companyContent(7L)).thenReturn(new CandidateResumeService.ResumeContent(metadata, resource));

        ResponseEntity<Resource> response = controller.resumeContent(7L);

        assertTrue(response.getHeaders().getCacheControl().contains("no-store"));
        assertEquals("nosniff", response.getHeaders().getFirst("X-Content-Type-Options"));
        assertTrue(response.getHeaders().getFirst("Content-Disposition").contains("candidate.docx"));
    }
}
