package com.tyut.aiinterview.admin;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tyut.aiinterview.common.BusinessException;
import com.tyut.aiinterview.domain.CandidateResume;
import com.tyut.aiinterview.domain.Company;
import com.tyut.aiinterview.domain.Interview;
import com.tyut.aiinterview.domain.JobApplication;
import com.tyut.aiinterview.domain.JobPosition;
import com.tyut.aiinterview.domain.Report;
import com.tyut.aiinterview.domain.Role;
import com.tyut.aiinterview.domain.UserAccount;
import com.tyut.aiinterview.domain.UserRole;
import com.tyut.aiinterview.mapper.CandidateResumeMapper;
import com.tyut.aiinterview.mapper.CompanyMapper;
import com.tyut.aiinterview.mapper.InterviewMapper;
import com.tyut.aiinterview.mapper.JobApplicationMapper;
import com.tyut.aiinterview.mapper.JobPositionMapper;
import com.tyut.aiinterview.mapper.MediaFileMapper;
import com.tyut.aiinterview.mapper.ReportMapper;
import com.tyut.aiinterview.mapper.RoleMapper;
import com.tyut.aiinterview.mapper.UserMapper;
import com.tyut.aiinterview.mapper.UserRoleMapper;
import com.tyut.aiinterview.media.LocalObjectStorage;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.apache.ibatis.session.Configuration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AdminCandidateProfileServiceTest {
    @Mock private UserMapper userMapper;
    @Mock private UserRoleMapper userRoleMapper;
    @Mock private RoleMapper roleMapper;
    @Mock private MediaFileMapper mediaFileMapper;
    @Mock private CandidateResumeMapper resumeMapper;
    @Mock private JobApplicationMapper applicationMapper;
    @Mock private CompanyMapper companyMapper;
    @Mock private JobPositionMapper positionMapper;
    @Mock private InterviewMapper interviewMapper;
    @Mock private ReportMapper reportMapper;
    @Mock private LocalObjectStorage storage;

    private AdminCandidateProfileService service;

    @BeforeEach
    void setUp() {
        Configuration configuration = new Configuration();
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(configuration, "admin-candidate-profile-test");
        for (Class<?> entityType : List.of(UserAccount.class, UserRole.class, Role.class, CandidateResume.class,
                JobApplication.class, Company.class, JobPosition.class, Interview.class, Report.class)) {
            if (TableInfoHelper.getTableInfo(entityType) == null) TableInfoHelper.initTableInfo(assistant, entityType);
        }
        service = new AdminCandidateProfileService(
                userMapper, userRoleMapper, roleMapper, mediaFileMapper, resumeMapper, applicationMapper,
                companyMapper, positionMapper, interviewMapper, reportMapper, storage, new ObjectMapper());
    }

    @Test
    void detailAggregatesLatestCandidateBusinessProfileAndFlagsLegacyRoleConflict() {
        UserAccount user = new UserAccount();
        user.setId(10L);
        user.setUsername("candidate_xu");
        user.setRealName("许博");
        user.setStatus(1);
        user.setPasswordHash("hash");
        user.setEmail("xu.bo@example.test");
        user.setEmailVerifiedAt(LocalDateTime.of(2026, 8, 10, 9, 0));
        user.setPhone("13800001010");
        user.setCreatedAt(LocalDateTime.of(2026, 2, 3, 9, 0));
        user.setUpdatedAt(LocalDateTime.of(2026, 8, 12, 18, 0));
        when(userMapper.selectById(10L)).thenReturn(user);

        UserRole candidateBinding = new UserRole();
        candidateBinding.setRoleId(2L);
        UserRole adminBinding = new UserRole();
        adminBinding.setRoleId(1L);
        when(userRoleMapper.selectList(any())).thenReturn(List.of(candidateBinding, adminBinding));
        Role candidateRole = new Role();
        candidateRole.setRoleCode("CANDIDATE");
        Role adminRole = new Role();
        adminRole.setRoleCode("ADMIN");
        when(roleMapper.selectBatchIds(any())).thenReturn(List.of(candidateRole, adminRole));

        CandidateResume resume = new CandidateResume();
        resume.setId(21L);
        resume.setCandidateId(10L);
        resume.setTitle("Java 后端简历");
        resume.setSkills("[\"Java\",\"Spring Boot\"]");
        resume.setIsDefault(1);
        resume.setStatus(1);
        resume.setParseStatus("SUCCESS");
        resume.setUpdatedAt(LocalDateTime.of(2026, 8, 11, 10, 0));
        when(resumeMapper.selectList(any())).thenReturn(List.of(resume));

        JobApplication application = new JobApplication();
        application.setId(31L);
        application.setApplicationNo("APP-31");
        application.setCandidateId(10L);
        application.setCompanyId(41L);
        application.setPositionId(51L);
        application.setStatus("UNDER_REVIEW");
        application.setMatchScore(new BigDecimal("88.50"));
        application.setUpdatedAt(LocalDateTime.of(2026, 8, 12, 10, 0));
        when(applicationMapper.selectList(any())).thenReturn(List.of(application));
        Company company = new Company();
        company.setId(41L);
        company.setName("星云科技有限公司");
        when(companyMapper.selectBatchIds(any())).thenReturn(List.of(company));
        JobPosition position = new JobPosition();
        position.setId(51L);
        position.setName("Java 后端工程师");
        when(positionMapper.selectBatchIds(any())).thenReturn(List.of(position));

        Interview interview = new Interview();
        interview.setId(61L);
        interview.setCandidateId(10L);
        interview.setTitle("Java 后端一面");
        interview.setStatus(Interview.COMPLETED);
        interview.setScheduledAt(LocalDateTime.of(2026, 8, 12, 14, 0));
        interview.setUpdatedAt(LocalDateTime.of(2026, 8, 12, 15, 0));
        when(interviewMapper.selectList(any())).thenReturn(List.of(interview));
        Report report = new Report();
        report.setId(71L);
        report.setInterviewId(61L);
        report.setTotalScore(new BigDecimal("89.00"));
        report.setGeneratedAt(LocalDateTime.of(2026, 8, 12, 16, 0));
        when(reportMapper.selectList(any())).thenReturn(List.of(report));

        AdminCandidateDtos.Detail detail = service.detail(10L);

        assertEquals("许博", detail.account().realName());
        assertTrue(detail.account().emailVerified());
        assertFalse(detail.account().phoneVerified());
        assertFalse(detail.account().identityConsistent());
        assertEquals(List.of("ADMIN", "CANDIDATE"), detail.account().roles());
        assertEquals(List.of("Java", "Spring Boot"), detail.resumes().get(0).skills());
        assertEquals("星云科技有限公司", detail.applications().get(0).companyName());
        assertEquals("Java 后端工程师", detail.applications().get(0).positionName());
        assertEquals("Java 后端一面", detail.reports().get(0).interviewTitle());
        assertEquals(new BigDecimal("89.00"), detail.overview().latestScore());
        assertEquals(1, detail.overview().reportCount());
    }

    @Test
    void detailRejectsAccountWithoutCandidateRole() {
        UserAccount user = new UserAccount();
        user.setId(9L);
        when(userMapper.selectById(9L)).thenReturn(user);
        UserRole binding = new UserRole();
        binding.setRoleId(1L);
        when(userRoleMapper.selectList(any())).thenReturn(List.of(binding));
        Role role = new Role();
        role.setRoleCode("ADMIN");
        when(roleMapper.selectBatchIds(any())).thenReturn(List.of(role));

        BusinessException exception = assertThrows(BusinessException.class, () -> service.detail(9L));
        assertEquals(40400, exception.getCode());
    }
}
