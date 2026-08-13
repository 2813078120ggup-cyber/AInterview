package com.tyut.aiinterview.recruitment;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.tyut.aiinterview.common.BusinessException;
import com.tyut.aiinterview.domain.Company;
import com.tyut.aiinterview.domain.JobPosition;
import com.tyut.aiinterview.mapper.CandidateResumeMapper;
import com.tyut.aiinterview.mapper.CompanyMapper;
import com.tyut.aiinterview.mapper.CompanyPositionStatisticsRow;
import com.tyut.aiinterview.mapper.InterviewMapper;
import com.tyut.aiinterview.mapper.JobApplicationMapper;
import com.tyut.aiinterview.mapper.JobApplicationStatusHistoryMapper;
import com.tyut.aiinterview.mapper.JobMatchEvaluationMapper;
import com.tyut.aiinterview.mapper.JobPositionMapper;
import com.tyut.aiinterview.mapper.OfflineInterviewMapper;
import com.tyut.aiinterview.mapper.QuestionBankMapper;
import com.tyut.aiinterview.mapper.UserMapper;
import com.tyut.aiinterview.ai.AiTaskService;
import com.tyut.aiinterview.interview.InterviewService;
import com.tyut.aiinterview.notification.SiteNotificationService;
import com.tyut.aiinterview.security.CurrentUser;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import org.mockito.ArgumentCaptor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class RecruitmentPositionServiceTest {
    private final JobPositionMapper positionMapper = mock(JobPositionMapper.class);
    private final CompanyMapper companyMapper = mock(CompanyMapper.class);
    private final JobApplicationMapper applicationMapper = mock(JobApplicationMapper.class);
    private final CandidateResumeMapper resumeMapper = mock(CandidateResumeMapper.class);
    private final JobMatchEvaluationMapper matchEvaluationMapper = mock(JobMatchEvaluationMapper.class);
    private final JobApplicationStatusHistoryMapper historyMapper = mock(JobApplicationStatusHistoryMapper.class);
    private final OfflineInterviewMapper offlineInterviewMapper = mock(OfflineInterviewMapper.class);
    private final UserMapper userMapper = mock(UserMapper.class);
    private final InterviewMapper interviewMapper = mock(InterviewMapper.class);
    private final QuestionBankMapper questionBankMapper = mock(QuestionBankMapper.class);
    private final InterviewService interviewService = mock(InterviewService.class);
    private final SiteNotificationService notificationService = mock(SiteNotificationService.class);
    private final CurrentUser currentUser = mock(CurrentUser.class);
    private final AiTaskService taskService = mock(AiTaskService.class);
    private final CompanyAccessService companyAccess = mock(CompanyAccessService.class);
    private final ApplicationStatusService statusService = mock(ApplicationStatusService.class);
    private final RecruitmentAuditService auditService = mock(RecruitmentAuditService.class);
    private RecruitmentService service;

    @BeforeEach
    void setUp() {
        service = new RecruitmentService(positionMapper, companyMapper, applicationMapper, resumeMapper,
                matchEvaluationMapper, historyMapper, offlineInterviewMapper, userMapper, interviewMapper,
                questionBankMapper, interviewService, notificationService, currentUser, new ObjectMapper(),
                taskService, companyAccess, statusService, auditService);
        when(currentUser.id()).thenReturn(900L);
        when(companyAccess.requireActiveCompany(100L)).thenReturn(activeCompany(100L));
    }

    @Test
    void createAlwaysStartsAsDraftAndUsesServerAuditBoundary() {
        when(companyAccess.requirePermission("recruitment:position:write")).thenReturn(100L);
        when(positionMapper.exists(any())).thenReturn(false);

        service.createPosition(request(null));

        ArgumentCaptor<JobPosition> captor = ArgumentCaptor.forClass(JobPosition.class);
        verify(positionMapper).insert(captor.capture());
        assertEquals("DRAFT", captor.getValue().getRecruitmentStatus());
        verify(auditService).recordPositionOperation("POSITION_CREATED", 100L, captor.getValue().getId(), "POS-1", "创建岗位草稿");
    }

    @Test
    void editCannotChangeStatusThroughContentRequest() {
        JobPosition position = position("DRAFT");
        when(companyAccess.requirePermission("recruitment:position:write")).thenReturn(100L);
        when(companyAccess.requirePosition(1L)).thenReturn(position);

        RecruitmentDtos.PositionRequest request = request("PUBLISHED");

        BusinessException exception = assertThrows(BusinessException.class, () -> service.updatePosition(1L, request));

        assertEquals("招聘状态必须通过发布或关闭动作修改", exception.getMessage());
        verify(positionMapper, never()).updateById(any(JobPosition.class));
    }

    @Test
    void publishRejectsIncompleteDraftBeforeDatabaseUpdate() {
        JobPosition position = position("DRAFT");
        position.setDescription(null);
        when(companyAccess.requirePermission(anyString())).thenReturn(100L);
        when(companyAccess.requirePosition(1L)).thenReturn(position);

        BusinessException exception = assertThrows(BusinessException.class,
                () -> service.updatePositionStatus(1L, new RecruitmentDtos.PositionStatusRequest("PUBLISHED", null)));

        assertEquals("发布前请补充岗位介绍", exception.getMessage());
        verify(positionMapper, never()).updateById(any(JobPosition.class));
    }

    @Test
    void publishAndCloseAreExplicitStatusActions() {
        JobPosition position = position("DRAFT");
        when(companyAccess.requirePermission(anyString())).thenReturn(100L);
        when(companyAccess.requirePosition(1L)).thenReturn(position);

        service.updatePositionStatus(1L, new RecruitmentDtos.PositionStatusRequest("PUBLISHED", null));
        assertEquals("PUBLISHED", position.getRecruitmentStatus());
        service.updatePositionStatus(1L, new RecruitmentDtos.PositionStatusRequest("CLOSED", "招聘计划已完成"));
        assertEquals("CLOSED", position.getRecruitmentStatus());
        verify(positionMapper, org.mockito.Mockito.times(2)).updateById(any(JobPosition.class));
        verify(auditService).recordPositionOperation("POSITION_STATUS_CHANGED", 100L, 1L, "POS-1", "DRAFT -> PUBLISHED");
        verify(auditService).recordPositionOperation("POSITION_STATUS_CHANGED", 100L, 1L, "POS-1", "PUBLISHED -> CLOSED; 招聘计划已完成");
    }

    @Test
    void crossCompanyDetailStopsAtCompanyBoundary() {
        when(companyAccess.requirePermission("recruitment:position:read")).thenReturn(200L);
        doThrow(BusinessException.notFound("岗位不存在")).when(companyAccess).requirePosition(1L);

        BusinessException exception = assertThrows(BusinessException.class, () -> service.companyPositionDetail(1L));

        assertEquals(404, exception.getStatus().value());
        verify(positionMapper, never()).selectCompanyStatistics(any(), any());
    }

    @Test
    void statisticsAreMappedFromBoundedDatabaseAggregate() {
        JobPosition position = position("PUBLISHED");
        CompanyPositionStatisticsRow row = new CompanyPositionStatisticsRow();
        row.setApplicationCount(12L);
        row.setAverageMatchScore(new BigDecimal("82.36"));
        row.setInterviewCount(5L);
        row.setHiredCount(2L);
        when(companyAccess.requirePermission("recruitment:position:read")).thenReturn(100L);
        when(companyAccess.requirePosition(1L)).thenReturn(position);
        when(positionMapper.selectCompanyStatistics(100L, 1L)).thenReturn(row);

        RecruitmentDtos.PositionDetail result = service.companyPositionDetail(1L);

        assertEquals(12L, result.statistics().applicationCount());
        assertEquals(new BigDecimal("82.4"), result.statistics().averageMatchScore());
        assertEquals(5L, result.statistics().interviewCount());
        assertEquals(2L, result.statistics().hiredCount());
    }

    private RecruitmentDtos.PositionRequest request(String status) {
        return new RecruitmentDtos.PositionRequest("POS-1", "Java 工程师", "研发部", 15, 25, "太原",
                "3-5年", "本科及以上", "FULL_TIME", "负责后端研发", "熟悉 Java 和 Spring Boot",
                List.of("Java", "Spring Boot"), status, LocalDateTime.now().plusDays(30));
    }

    private JobPosition position(String status) {
        JobPosition position = new JobPosition();
        position.setId(1L);
        position.setCompanyId(100L);
        position.setPositionCode("POS-1");
        position.setName("Java 工程师");
        position.setCity("太原");
        position.setJobType("FULL_TIME");
        position.setDescription("负责后端研发");
        position.setRequirements("熟悉 Java 和 Spring Boot");
        position.setSkillTags("[\"Java\",\"Spring Boot\"]");
        position.setRecruitmentStatus(status);
        position.setUpdatedAt(LocalDateTime.now());
        return position;
    }

    private Company activeCompany(Long id) {
        Company company = new Company();
        company.setId(id);
        company.setName("企业");
        company.setStatus(1);
        return company;
    }
}
