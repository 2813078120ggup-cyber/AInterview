package com.tyut.aiinterview.recruitment;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.tyut.aiinterview.common.BusinessException;
import com.tyut.aiinterview.domain.Company;
import com.tyut.aiinterview.domain.Interview;
import com.tyut.aiinterview.domain.JobApplication;
import com.tyut.aiinterview.domain.JobPosition;
import com.tyut.aiinterview.domain.Report;
import com.tyut.aiinterview.mapper.CompanyMapper;
import com.tyut.aiinterview.mapper.InterviewMapper;
import com.tyut.aiinterview.mapper.JobApplicationMapper;
import com.tyut.aiinterview.mapper.JobPositionMapper;
import com.tyut.aiinterview.mapper.ReportMapper;
import com.tyut.aiinterview.security.CurrentUser;
import com.tyut.aiinterview.security.LoginUser;
import java.util.List;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.apache.ibatis.session.Configuration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

class CompanyAccessServiceTest {
    private final CurrentUser currentUser = mock(CurrentUser.class);
    private final CompanyMapper companyMapper = mock(CompanyMapper.class);
    private final JobPositionMapper positionMapper = mock(JobPositionMapper.class);
    private final JobApplicationMapper applicationMapper = mock(JobApplicationMapper.class);
    private final InterviewMapper interviewMapper = mock(InterviewMapper.class);
    private final ReportMapper reportMapper = mock(ReportMapper.class);
    private final CompanyAccessService service = new CompanyAccessService(currentUser, companyMapper, positionMapper,
            applicationMapper, interviewMapper, reportMapper);

    @BeforeEach
    void initializeMyBatisPlusLambdaMetadata() {
        Configuration configuration = new Configuration();
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(configuration, "test");
        for (Class<?> entityType : List.of(Company.class, JobPosition.class, JobApplication.class,
                Interview.class, Report.class)) {
            if (TableInfoHelper.getTableInfo(entityType) == null) TableInfoHelper.initTableInfo(assistant, entityType);
        }
    }

    @Test
    void companyACanReadReportAttachedToItsOwnApplication() {
        when(currentUser.require()).thenReturn(companyUser(100L));
        when(companyMapper.selectById(100L)).thenReturn(activeCompany(100L));
        when(applicationMapper.selectById(11L)).thenReturn(application(11L, 100L, 101L));
        Interview assignedInterview = interview(101L, 7L, 31L);
        assignedInterview.setInterviewerId(99L);
        when(interviewMapper.selectById(101L)).thenReturn(assignedInterview);
        Report report = new Report();
        report.setId(91L);
        report.setInterviewId(101L);
        when(reportMapper.selectOne(any())).thenReturn(report);

        Report result = service.requireReportForApplication(11L);

        assertEquals(91L, result.getId());
    }

    @Test
    void companyBCannotReadCompanyAReport() {
        when(currentUser.require()).thenReturn(companyUser(200L));
        when(companyMapper.selectById(200L)).thenReturn(activeCompany(200L));
        when(applicationMapper.selectById(11L)).thenReturn(application(11L, 100L, 101L));

        BusinessException exception = assertThrows(BusinessException.class,
                () -> service.requireReportForApplication(11L));

        assertEquals(HttpStatus.NOT_FOUND, exception.getStatus());
        verify(interviewMapper, never()).selectById(any());
        verify(reportMapper, never()).selectOne(any());
    }

    @Test
    void candidateCannotEnterCompanyScope() {
        when(currentUser.require()).thenReturn(new LoginUser(7L, "candidate", "", true,
                List.of("CANDIDATE"), null));

        BusinessException exception = assertThrows(BusinessException.class, service::requireCompanyId);

        assertEquals(HttpStatus.FORBIDDEN, exception.getStatus());
    }

    @Test
    void superAdminCannotUseCompanyScopeWithoutCompanyRole() {
        when(currentUser.require()).thenReturn(new LoginUser(999L, "admin", "", true,
                List.of("ADMIN"), null));

        BusinessException exception = assertThrows(BusinessException.class, service::requireCompanyId);

        assertEquals(HttpStatus.FORBIDDEN, exception.getStatus());
    }

    @Test
    void interviewMustMatchApplicationCandidateAndPosition() {
        when(currentUser.require()).thenReturn(companyUser(100L));
        when(companyMapper.selectById(100L)).thenReturn(activeCompany(100L));
        when(applicationMapper.selectById(11L)).thenReturn(application(11L, 100L, 101L));
        when(interviewMapper.selectById(101L)).thenReturn(interview(101L, 7L, 999L));

        BusinessException exception = assertThrows(BusinessException.class,
                () -> service.requireInterviewForApplication(11L));

        assertEquals(HttpStatus.NOT_FOUND, exception.getStatus());
    }

    @Test
    void interviewerCannotReadAnUnassignedCompanyApplication() {
        when(currentUser.require()).thenReturn(new LoginUser(88L, "company_interviewer", "", true,
                List.of("COMPANY_INTERVIEWER"), 100L,
                List.of("company:read", "application:read", "interview:read", "interview:review")));
        when(companyMapper.selectById(100L)).thenReturn(activeCompany(100L));
        when(applicationMapper.selectById(11L)).thenReturn(application(11L, 100L, 101L));
        JobPosition position = new JobPosition();
        position.setId(31L);
        position.setCompanyId(100L);
        when(positionMapper.selectById(31L)).thenReturn(position);
        when(interviewMapper.selectById(101L)).thenReturn(interview(101L, 7L, 31L));

        BusinessException exception = assertThrows(BusinessException.class,
                () -> service.requireApplication(11L));

        assertEquals(HttpStatus.NOT_FOUND, exception.getStatus());
    }

    private LoginUser companyUser(Long companyId) {
        return new LoginUser(88L, "company_hr", "", true, List.of("COMPANY_ADMIN"), companyId);
    }

    private Company activeCompany(Long id) {
        Company company = new Company();
        company.setId(id);
        company.setStatus(1);
        return company;
    }

    private JobApplication application(Long id, Long companyId, Long interviewId) {
        JobApplication application = new JobApplication();
        application.setId(id);
        application.setCompanyId(companyId);
        application.setPositionId(31L);
        application.setCandidateId(7L);
        application.setInterviewId(interviewId);
        return application;
    }

    private Interview interview(Long id, Long candidateId, Long positionId) {
        Interview interview = new Interview();
        interview.setId(id);
        interview.setCandidateId(candidateId);
        interview.setPositionId(positionId);
        return interview;
    }
}
