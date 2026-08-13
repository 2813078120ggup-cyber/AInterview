package com.tyut.aiinterview.recruitment;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
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
import java.util.Objects;
import java.util.Set;
import org.springframework.stereotype.Service;

/**
 * Server-side tenant boundary for company recruitment operations.
 *
 * <p>All company-owned resource lookups must pass through this service. The
 * caller never supplies a company id; it is always taken from the authenticated
 * user and checked against the resource relationship.</p>
 */
@Service
public class CompanyAccessService {
    private static final Set<String> COMPANY_ROLES = Set.of(
            "COMPANY_ADMIN", "COMPANY_RECRUITER", "COMPANY_INTERVIEWER");

    private final CurrentUser currentUser;
    private final CompanyMapper companyMapper;
    private final JobPositionMapper positionMapper;
    private final JobApplicationMapper applicationMapper;
    private final InterviewMapper interviewMapper;
    private final ReportMapper reportMapper;

    public CompanyAccessService(CurrentUser currentUser, CompanyMapper companyMapper,
                                JobPositionMapper positionMapper, JobApplicationMapper applicationMapper,
                                InterviewMapper interviewMapper, ReportMapper reportMapper) {
        this.currentUser = currentUser;
        this.companyMapper = companyMapper;
        this.positionMapper = positionMapper;
        this.applicationMapper = applicationMapper;
        this.interviewMapper = interviewMapper;
        this.reportMapper = reportMapper;
    }

    public Long requireCompanyId() {
        LoginUser user = currentUser.require();
        if (!isCompanyUser(user) || user.getCompanyId() == null) {
            throw BusinessException.forbidden("仅已绑定企业的企业成员可执行此操作");
        }
        requireActiveCompany(user.getCompanyId());
        return user.getCompanyId();
    }

    public Long requirePermission(String permission) {
        LoginUser user = currentUser.require();
        if (!isCompanyUser(user) || user.getCompanyId() == null || !user.hasPermission(permission)) {
            throw BusinessException.forbidden("企业账号缺少权限：" + permission);
        }
        requireActiveCompany(user.getCompanyId());
        return user.getCompanyId();
    }

    public Long requireAnyPermission(String... permissions) {
        LoginUser user = currentUser.require();
        boolean allowed = permissions != null && java.util.Arrays.stream(permissions)
                .anyMatch(user::hasPermission);
        if (!isCompanyUser(user) || user.getCompanyId() == null || !allowed) {
            throw BusinessException.forbidden("企业账号缺少所需权限");
        }
        requireActiveCompany(user.getCompanyId());
        return user.getCompanyId();
    }

    /** Used by method-security expressions; it never accepts a caller-supplied company id. */
    public boolean hasPermission(String permission) {
        LoginUser user = currentUser.require();
        if (!isCompanyUser(user) || user.getCompanyId() == null || !user.hasPermission(permission)) return false;
        requireActiveCompany(user.getCompanyId());
        return true;
    }

    public Company requireActiveCompany(Long companyId) {
        Company company = companyId == null ? null : companyMapper.selectById(companyId);
        if (company == null || !Integer.valueOf(1).equals(company.getStatus())) {
            throw BusinessException.notFound("企业不存在或已停用");
        }
        return company;
    }

    public JobPosition requirePosition(Long positionId) {
        Long companyId = requireCompanyId();
        JobPosition position = positionMapper.selectById(positionId);
        if (position == null || !companyId.equals(position.getCompanyId())) {
            throw BusinessException.notFound("岗位不存在");
        }
        return position;
    }

    public JobApplication requireApplication(Long applicationId) {
        Long companyId = requireCompanyId();
        JobApplication application = applicationMapper.selectById(applicationId);
        if (application == null || !companyId.equals(application.getCompanyId())) {
            throw BusinessException.notFound("申请不存在");
        }
        LoginUser user = currentUser.require();
        if (isRestrictedInterviewer(user)
                && !isAuthorizedInterview(companyId, application.getInterviewId(), user)) {
            throw BusinessException.notFound("申请不存在");
        }
        return application;
    }

    /**
     * Verifies that the candidate has an application in the current company.
     * Restricted interviewers may only see candidates attached to an interview
     * assigned to the current interviewer.
     */
    public void requireCandidateAccess(Long candidateId) {
        Long companyId = requireCompanyId();
        List<JobApplication> applications = applicationMapper.selectList(new LambdaQueryWrapper<JobApplication>()
                .eq(JobApplication::getCompanyId, companyId)
                .eq(JobApplication::getCandidateId, candidateId));
        if (applications.isEmpty()) {
            throw BusinessException.notFound("候选人不存在");
        }
        LoginUser user = currentUser.require();
        if (isRestrictedInterviewer(user)
                && applications.stream().noneMatch(application ->
                isAuthorizedInterview(companyId, application.getInterviewId(), user))) {
            throw BusinessException.notFound("候选人不存在");
        }
    }

    public Interview requireAuthorizedInterview(Long interviewId) {
        Long companyId = requireCompanyId();
        Interview interview = interviewMapper.selectById(interviewId);
        if (interview == null || !isAuthorizedInterview(companyId, interviewId, currentUser.require())) {
            throw BusinessException.notFound("面试不存在");
        }
        return interview;
    }

    /** Restricts company interviewer application lists to interviews explicitly assigned to that user. */
    public void applyApplicationScope(LambdaQueryWrapper<JobApplication> wrapper) {
        LoginUser user = currentUser.require();
        if (!isRestrictedInterviewer(user)) return;
        List<Long> interviewIds = authorizedInterviewIds();
        if (interviewIds.isEmpty()) wrapper.eq(JobApplication::getId, -1L);
        else wrapper.in(JobApplication::getInterviewId, interviewIds);
    }

    public List<Long> authorizedInterviewIds() {
        LoginUser user = currentUser.require();
        if (!isRestrictedInterviewer(user)) return List.of();
        Long companyId = requireCompanyId();
        List<Long> positionIds = positionMapper.selectList(new LambdaQueryWrapper<JobPosition>()
                        .select(JobPosition::getId)
                        .eq(JobPosition::getCompanyId, companyId))
                .stream().map(JobPosition::getId).filter(Objects::nonNull).toList();
        if (positionIds.isEmpty()) return List.of();
        return interviewMapper.selectList(new LambdaQueryWrapper<Interview>()
                        .select(Interview::getId)
                        .eq(Interview::getInterviewerId, user.getId())
                        .in(Interview::getPositionId, positionIds))
                .stream().map(Interview::getId).filter(Objects::nonNull).toList();
    }

    public boolean isRestrictedInterviewer() {
        return isRestrictedInterviewer(currentUser.require());
    }

    public Interview requireInterviewForApplication(Long applicationId) {
        return requireInterviewForApplication(requireApplication(applicationId));
    }

    public Interview requireInterviewForApplication(JobApplication application) {
        Long interviewId = application.getInterviewId();
        Interview interview = interviewId == null ? null : interviewMapper.selectById(interviewId);
        if (interview == null
                || !Objects.equals(application.getInterviewId(), interview.getId())
                || !Objects.equals(application.getCandidateId(), interview.getCandidateId())
                || !Objects.equals(application.getPositionId(), interview.getPositionId())) {
            throw BusinessException.notFound("申请关联的面试不存在");
        }
        return interview;
    }

    public Report requireReportForApplication(Long applicationId) {
        JobApplication application = requireApplication(applicationId);
        Interview interview = requireInterviewForApplication(application);
        Report report = reportMapper.selectOne(new LambdaQueryWrapper<Report>()
                .eq(Report::getInterviewId, interview.getId()));
        if (report == null || !Objects.equals(application.getInterviewId(), report.getInterviewId())) {
            throw BusinessException.notFound("申请关联的报告不存在");
        }
        return report;
    }

    private boolean isCompanyUser(LoginUser user) {
        return COMPANY_ROLES.stream().anyMatch(user::hasRole);
    }

    private boolean isRestrictedInterviewer(LoginUser user) {
        return user.hasRole("COMPANY_INTERVIEWER")
                && !user.hasPermission("application:review")
                && !user.hasPermission("interview:create");
    }

    private boolean isAuthorizedInterview(Long companyId, Long interviewId, LoginUser user) {
        if (interviewId == null) return false;
        Interview interview = interviewMapper.selectById(interviewId);
        JobPosition position = interview == null || interview.getPositionId() == null
                ? null : positionMapper.selectById(interview.getPositionId());
        if (interview == null || position == null || !companyId.equals(position.getCompanyId())) return false;
        return !isRestrictedInterviewer(user) || user.getId().equals(interview.getInterviewerId());
    }
}
