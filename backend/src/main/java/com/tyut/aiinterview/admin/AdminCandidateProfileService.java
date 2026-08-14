package com.tyut.aiinterview.admin;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tyut.aiinterview.common.BusinessException;
import com.tyut.aiinterview.domain.CandidateResume;
import com.tyut.aiinterview.domain.Company;
import com.tyut.aiinterview.domain.Interview;
import com.tyut.aiinterview.domain.JobApplication;
import com.tyut.aiinterview.domain.JobPosition;
import com.tyut.aiinterview.domain.MediaFile;
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
import java.io.IOException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class AdminCandidateProfileService {
    private static final int MAX_RECENT_ITEMS = 50;

    private final UserMapper userMapper;
    private final UserRoleMapper userRoleMapper;
    private final RoleMapper roleMapper;
    private final MediaFileMapper mediaFileMapper;
    private final CandidateResumeMapper resumeMapper;
    private final JobApplicationMapper applicationMapper;
    private final CompanyMapper companyMapper;
    private final JobPositionMapper positionMapper;
    private final InterviewMapper interviewMapper;
    private final ReportMapper reportMapper;
    private final LocalObjectStorage storage;
    private final ObjectMapper objectMapper;

    public AdminCandidateProfileService(
            UserMapper userMapper,
            UserRoleMapper userRoleMapper,
            RoleMapper roleMapper,
            MediaFileMapper mediaFileMapper,
            CandidateResumeMapper resumeMapper,
            JobApplicationMapper applicationMapper,
            CompanyMapper companyMapper,
            JobPositionMapper positionMapper,
            InterviewMapper interviewMapper,
            ReportMapper reportMapper,
            LocalObjectStorage storage,
            ObjectMapper objectMapper) {
        this.userMapper = userMapper;
        this.userRoleMapper = userRoleMapper;
        this.roleMapper = roleMapper;
        this.mediaFileMapper = mediaFileMapper;
        this.resumeMapper = resumeMapper;
        this.applicationMapper = applicationMapper;
        this.companyMapper = companyMapper;
        this.positionMapper = positionMapper;
        this.interviewMapper = interviewMapper;
        this.reportMapper = reportMapper;
        this.storage = storage;
        this.objectMapper = objectMapper;
    }

    public AdminCandidateDtos.Detail detail(Long candidateId) {
        CandidateAccount candidate = requireCandidate(candidateId);
        UserAccount user = candidate.user();

        List<CandidateResume> allResumes = resumeMapper.selectList(new LambdaQueryWrapper<CandidateResume>()
                .eq(CandidateResume::getCandidateId, candidateId)
                .eq(CandidateResume::getStatus, 1)
                .orderByDesc(CandidateResume::getIsDefault)
                .orderByDesc(CandidateResume::getUpdatedAt));
        List<JobApplication> allApplications = applicationMapper.selectList(new LambdaQueryWrapper<JobApplication>()
                .eq(JobApplication::getCandidateId, candidateId)
                .orderByDesc(JobApplication::getUpdatedAt)
                .orderByDesc(JobApplication::getId));
        List<Interview> allInterviews = interviewMapper.selectList(new LambdaQueryWrapper<Interview>()
                .eq(Interview::getCandidateId, candidateId)
                .orderByDesc(Interview::getScheduledAt)
                .orderByDesc(Interview::getId));
        List<Long> interviewIds = allInterviews.stream().map(Interview::getId).toList();
        List<Report> allReports = interviewIds.isEmpty() ? List.of() : reportMapper.selectList(new LambdaQueryWrapper<Report>()
                .in(Report::getInterviewId, interviewIds)
                .orderByDesc(Report::getGeneratedAt)
                .orderByDesc(Report::getId));

        Map<Long, Company> companies = companyMap(allApplications);
        Map<Long, JobPosition> positions = positionMap(allApplications);
        Map<Long, Interview> interviewsById = new HashMap<>();
        allInterviews.forEach(interview -> interviewsById.put(interview.getId(), interview));

        List<AdminCandidateDtos.ResumeSummary> resumes = allResumes.stream().limit(MAX_RECENT_ITEMS)
                .map(this::resumeView).toList();
        List<AdminCandidateDtos.ApplicationSummary> applications = allApplications.stream().limit(MAX_RECENT_ITEMS)
                .map(application -> applicationView(application, companies, positions)).toList();
        List<AdminCandidateDtos.InterviewSummary> interviews = allInterviews.stream().limit(MAX_RECENT_ITEMS)
                .map(this::interviewView).toList();
        List<AdminCandidateDtos.ReportSummary> reports = allReports.stream().limit(MAX_RECENT_ITEMS)
                .map(report -> reportView(report, interviewsById)).toList();

        LocalDateTime latestActivity = latestActivity(user, allResumes, allApplications, allInterviews, allReports);
        return new AdminCandidateDtos.Detail(
                accountView(user, candidate.roles()),
                new AdminCandidateDtos.Overview(
                        allResumes.size(), allApplications.size(), allInterviews.size(), allReports.size(),
                        allReports.isEmpty() ? null : allReports.get(0).getTotalScore(), latestActivity),
                resumes, applications, interviews, reports);
    }

    public AvatarContent avatar(Long candidateId) throws IOException {
        UserAccount user = requireCandidate(candidateId).user();
        MediaFile media = availableAvatar(user);
        if (media == null) throw BusinessException.notFound("候选人头像不存在");
        try {
            Resource resource = storage.resource(media.getObjectKey());
            if (!resource.exists()) throw BusinessException.notFound("候选人头像不存在");
            return new AvatarContent(media.getContentType(), resource);
        } catch (IllegalArgumentException exception) {
            throw BusinessException.notFound("候选人头像不存在");
        }
    }

    private CandidateAccount requireCandidate(Long candidateId) {
        UserAccount user = userMapper.selectById(candidateId);
        if (user == null) throw BusinessException.notFound("候选人不存在");
        List<Long> roleIds = userRoleMapper.selectList(new LambdaQueryWrapper<UserRole>()
                        .eq(UserRole::getUserId, candidateId))
                .stream().map(UserRole::getRoleId).distinct().toList();
        List<String> roles = roleIds.isEmpty() ? List.of() : roleMapper.selectBatchIds(roleIds).stream()
                .map(Role::getRoleCode).filter(StringUtils::hasText).distinct().sorted().toList();
        if (!roles.contains("CANDIDATE")) throw BusinessException.notFound("候选人不存在");
        return new CandidateAccount(user, roles);
    }

    private AdminCandidateDtos.Account accountView(UserAccount user, List<String> roles) {
        String email = normalize(user.getEmail());
        String phone = normalize(user.getPhone());
        List<String> loginMethods = new ArrayList<>();
        if (StringUtils.hasText(user.getPasswordHash())) loginMethods.add("PASSWORD");
        if (phone != null && user.getPhoneVerifiedAt() != null) loginMethods.add("SMS");
        if (email != null && user.getEmailVerifiedAt() != null) loginMethods.add("EMAIL");
        return new AdminCandidateDtos.Account(
                user.getId(), user.getUsername(), user.getRealName(), user.getStatus(), availableAvatar(user) != null,
                email, email != null && user.getEmailVerifiedAt() != null,
                phone, phone != null && user.getPhoneVerifiedAt() != null,
                List.copyOf(loginMethods), roles, roles.size() == 1 && roles.contains("CANDIDATE"),
                user.getLastLoginAt(), user.getCreatedAt(), user.getUpdatedAt());
    }

    private AdminCandidateDtos.ResumeSummary resumeView(CandidateResume resume) {
        return new AdminCandidateDtos.ResumeSummary(
                resume.getId(), resume.getTitle(), resume.getFileName(), resume.getSummary(), parseSkills(resume.getSkills()),
                Integer.valueOf(1).equals(resume.getIsDefault()), resume.getParseStatus(), resume.getParseVersion(),
                resume.getParsedAt(), resume.getUpdatedAt());
    }

    private AdminCandidateDtos.ApplicationSummary applicationView(
            JobApplication application, Map<Long, Company> companies, Map<Long, JobPosition> positions) {
        Company company = companies.get(application.getCompanyId());
        JobPosition position = positions.get(application.getPositionId());
        return new AdminCandidateDtos.ApplicationSummary(
                application.getId(), application.getApplicationNo(), application.getCompanyId(),
                company == null ? "企业资料不可用" : company.getName(), application.getPositionId(),
                position == null ? "岗位资料不可用" : position.getName(), application.getStatus(),
                application.getMatchScore(), application.getMatchStatus(), application.getSubmittedAt(), application.getUpdatedAt());
    }

    private AdminCandidateDtos.InterviewSummary interviewView(Interview interview) {
        return new AdminCandidateDtos.InterviewSummary(
                interview.getId(), interview.getTitle(), interview.getScheduledAt(), interview.getDuration(),
                interview.getStatus(), interview.getType(), interview.getUpdatedAt());
    }

    private AdminCandidateDtos.ReportSummary reportView(Report report, Map<Long, Interview> interviews) {
        Interview interview = interviews.get(report.getInterviewId());
        return new AdminCandidateDtos.ReportSummary(
                report.getId(), report.getInterviewId(), interview == null ? "面试记录不可用" : interview.getTitle(),
                interview == null ? null : interview.getScheduledAt(), report.getTotalScore(),
                report.getProfessionalScore(), report.getExpressionScore(), report.getLogicScore(),
                report.getAdaptabilityScore(), report.getStatus(), report.getPublishedAt(), report.getGeneratedAt());
    }

    private Map<Long, Company> companyMap(List<JobApplication> applications) {
        List<Long> ids = applications.stream().map(JobApplication::getCompanyId).filter(Objects::nonNull).distinct().toList();
        if (ids.isEmpty()) return Map.of();
        Map<Long, Company> result = new HashMap<>();
        companyMapper.selectBatchIds(ids).forEach(company -> result.put(company.getId(), company));
        return result;
    }

    private Map<Long, JobPosition> positionMap(List<JobApplication> applications) {
        List<Long> ids = applications.stream().map(JobApplication::getPositionId).filter(Objects::nonNull).distinct().toList();
        if (ids.isEmpty()) return Map.of();
        Map<Long, JobPosition> result = new HashMap<>();
        positionMapper.selectBatchIds(ids).forEach(position -> result.put(position.getId(), position));
        return result;
    }

    private MediaFile availableAvatar(UserAccount user) {
        if (user.getAvatarMediaId() == null) return null;
        MediaFile media = mediaFileMapper.selectById(user.getAvatarMediaId());
        return media != null && Objects.equals(media.getOwnerId(), user.getId())
                && Objects.equals(media.getStatus(), MediaFile.AVAILABLE)
                && "image".equalsIgnoreCase(media.getMediaType())
                && isAvatarContentType(media.getContentType()) ? media : null;
    }

    private boolean isAvatarContentType(String contentType) {
        return "image/jpeg".equalsIgnoreCase(contentType)
                || "image/png".equalsIgnoreCase(contentType)
                || "image/webp".equalsIgnoreCase(contentType);
    }

    private List<String> parseSkills(String value) {
        if (!StringUtils.hasText(value)) return List.of();
        try {
            List<String> values = objectMapper.readValue(value, new TypeReference<>() {});
            return values.stream().filter(StringUtils::hasText).map(String::trim).distinct().limit(20).toList();
        } catch (JsonProcessingException | ClassCastException ignored) {
            return List.of();
        }
    }

    private LocalDateTime latestActivity(
            UserAccount user,
            List<CandidateResume> resumes,
            List<JobApplication> applications,
            List<Interview> interviews,
            List<Report> reports) {
        List<LocalDateTime> values = new ArrayList<>();
        values.add(user.getUpdatedAt());
        values.add(user.getLastLoginAt());
        resumes.forEach(item -> values.add(item.getUpdatedAt()));
        applications.forEach(item -> values.add(item.getUpdatedAt()));
        interviews.forEach(item -> values.add(item.getUpdatedAt()));
        reports.forEach(item -> values.add(item.getPublishedAt() == null ? item.getGeneratedAt() : item.getPublishedAt()));
        return values.stream().filter(Objects::nonNull).max(Comparator.naturalOrder()).orElse(user.getCreatedAt());
    }

    private String normalize(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    private record CandidateAccount(UserAccount user, List<String> roles) {}

    public record AvatarContent(String contentType, Resource resource) {}
}
