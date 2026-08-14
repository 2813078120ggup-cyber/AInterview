package com.tyut.aiinterview.recruitment;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tyut.aiinterview.common.BusinessException;
import com.tyut.aiinterview.ai.AiTaskService;
import com.tyut.aiinterview.common.PageResult;
import com.tyut.aiinterview.domain.CandidateResume;
import com.tyut.aiinterview.domain.Company;
import com.tyut.aiinterview.domain.Interview;
import com.tyut.aiinterview.domain.InterviewStatusHistory;
import com.tyut.aiinterview.domain.JobApplication;
import com.tyut.aiinterview.domain.JobApplicationStatusHistory;
import com.tyut.aiinterview.domain.JobMatchEvaluation;
import com.tyut.aiinterview.domain.JobPosition;
import com.tyut.aiinterview.domain.OfflineInterview;
import com.tyut.aiinterview.domain.QuestionBank;
import com.tyut.aiinterview.domain.UserAccount;
import com.tyut.aiinterview.interview.InterviewDtos;
import com.tyut.aiinterview.interview.InterviewService;
import com.tyut.aiinterview.mapper.CandidateResumeMapper;
import com.tyut.aiinterview.mapper.CompanyMapper;
import com.tyut.aiinterview.mapper.CompanyPositionStatisticsRow;
import com.tyut.aiinterview.mapper.InterviewMapper;
import com.tyut.aiinterview.mapper.InterviewStatusHistoryMapper;
import com.tyut.aiinterview.mapper.JobApplicationMapper;
import com.tyut.aiinterview.mapper.JobApplicationStatusHistoryMapper;
import com.tyut.aiinterview.mapper.JobMatchEvaluationMapper;
import com.tyut.aiinterview.mapper.JobPositionMapper;
import com.tyut.aiinterview.mapper.OfflineInterviewMapper;
import com.tyut.aiinterview.mapper.QuestionBankMapper;
import com.tyut.aiinterview.mapper.UserMapper;
import com.tyut.aiinterview.notification.SiteNotificationService;
import com.tyut.aiinterview.security.CurrentUser;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
public class RecruitmentService {
    private static final Set<String> JOB_TYPES = Set.of("FULL_TIME", "PART_TIME", "INTERNSHIP");
    private static final Set<String> POSITION_STATUSES = Set.of("DRAFT", "PUBLISHED", "CLOSED");
    private final JobPositionMapper positionMapper;
    private final CompanyMapper companyMapper;
    private final JobApplicationMapper applicationMapper;
    private final CandidateResumeMapper resumeMapper;
    private final JobMatchEvaluationMapper matchEvaluationMapper;
    private final JobApplicationStatusHistoryMapper historyMapper;
    private final OfflineInterviewMapper offlineInterviewMapper;
    private final UserMapper userMapper;
    private final InterviewMapper interviewMapper;
    private final QuestionBankMapper questionBankMapper;
    private final InterviewService interviewService;
    private final SiteNotificationService notificationService;
    private final CurrentUser currentUser;
    private final ObjectMapper objectMapper;
    private final AiTaskService taskService;
    private final CompanyAccessService companyAccess;
    private final ApplicationStatusService statusService;
    private final RecruitmentAuditService auditService;
    private InterviewStatusHistoryMapper interviewStatusHistoryMapper;

    public RecruitmentService(JobPositionMapper positionMapper, CompanyMapper companyMapper,
                              JobApplicationMapper applicationMapper, CandidateResumeMapper resumeMapper,
                              JobMatchEvaluationMapper matchEvaluationMapper,
                              JobApplicationStatusHistoryMapper historyMapper, OfflineInterviewMapper offlineInterviewMapper,
                              UserMapper userMapper, InterviewMapper interviewMapper, QuestionBankMapper questionBankMapper,
                               InterviewService interviewService,
                               SiteNotificationService notificationService, CurrentUser currentUser, ObjectMapper objectMapper,
                               AiTaskService taskService, CompanyAccessService companyAccess,
                               ApplicationStatusService statusService, RecruitmentAuditService auditService) {
        this.positionMapper = positionMapper;
        this.companyMapper = companyMapper;
        this.applicationMapper = applicationMapper;
        this.resumeMapper = resumeMapper;
        this.matchEvaluationMapper = matchEvaluationMapper;
        this.historyMapper = historyMapper;
        this.offlineInterviewMapper = offlineInterviewMapper;
        this.userMapper = userMapper;
        this.interviewMapper = interviewMapper;
        this.questionBankMapper = questionBankMapper;
        this.interviewService = interviewService;
        this.notificationService = notificationService;
        this.currentUser = currentUser;
        this.objectMapper = objectMapper;
        this.taskService = taskService;
        this.companyAccess = companyAccess;
        this.statusService = statusService;
        this.auditService = auditService;
    }

    @Autowired
    public void setInterviewStatusHistoryMapper(InterviewStatusHistoryMapper interviewStatusHistoryMapper) {
        this.interviewStatusHistoryMapper = interviewStatusHistoryMapper;
    }

    public PageResult<RecruitmentDtos.JobView> jobHall(RecruitmentDtos.JobQuery query) {
        requireCandidate();
        long pageNo = pageNo(query.pageNo());
        long pageSize = pageSize(query.pageSize());
        LocalDateTime now = LocalDateTime.now();
        LambdaQueryWrapper<JobPosition> wrapper = new LambdaQueryWrapper<JobPosition>()
                .eq(JobPosition::getStatus, 1)
                .isNotNull(JobPosition::getCompanyId)
                .eq(JobPosition::getRecruitmentStatus, "PUBLISHED")
                .exists("SELECT 1 FROM company c WHERE c.id = job_position.company_id AND c.status = 1 AND c.deleted_at IS NULL")
                .le(JobPosition::getPublishedAt, now)
                .and(item -> item.isNull(JobPosition::getExpiresAt).or().gt(JobPosition::getExpiresAt, now))
                .orderByDesc(JobPosition::getPublishedAt).orderByDesc(JobPosition::getId);
        applyJobFilters(wrapper, query);
        Page<JobPosition> result = positionMapper.selectPage(new Page<>(pageNo, pageSize), wrapper);
        Set<Long> appliedPositionIds = applicationMapper.selectList(new LambdaQueryWrapper<JobApplication>()
                        .eq(JobApplication::getCandidateId, currentUser.id()))
                .stream().map(JobApplication::getPositionId).collect(Collectors.toSet());
        return PageResult.of(result.getRecords().stream().map(item -> toJobView(item, appliedPositionIds.contains(item.getId()))).toList(),
                result.getTotal(), pageNo, pageSize);
    }

    public RecruitmentDtos.JobView jobDetail(Long id) {
        requireCandidate();
        JobPosition position = requirePublishedPosition(id);
        boolean applied = applicationMapper.exists(new LambdaQueryWrapper<JobApplication>()
                .eq(JobApplication::getCandidateId, currentUser.id()).eq(JobApplication::getPositionId, id));
        return toJobView(position, applied);
    }

    public List<RecruitmentDtos.ResumeView> myResumes() {
        requireCandidate();
        return resumeMapper.selectList(new LambdaQueryWrapper<CandidateResume>()
                        .eq(CandidateResume::getCandidateId, currentUser.id())
                        .eq(CandidateResume::getStatus, 1)
                        .orderByDesc(CandidateResume::getIsDefault)
                        .orderByDesc(CandidateResume::getUpdatedAt))
                .stream().map(this::toResumeView).toList();
    }

    @Transactional
    public RecruitmentDtos.ApplicationView apply(Long positionId, RecruitmentDtos.ApplyRequest request) {
        requireCandidate();
        Long candidateId = currentUser.id();
        JobPosition position = requirePublishedPosition(positionId);
        if (applicationMapper.exists(new LambdaQueryWrapper<JobApplication>()
                .eq(JobApplication::getCandidateId, candidateId).eq(JobApplication::getPositionId, positionId))) {
            throw BusinessException.conflict("你已投递过该岗位，请在我的申请中查看进度");
        }
        CandidateResume resume = resolveResume(candidateId, request.resumeId());
        boolean resumeReady = resume.getParseStatus() == null || Set.of("SUCCESS", "MANUAL").contains(resume.getParseStatus());
        MatchResult match = resumeReady
                ? calculateMatch(position, resume)
                : new MatchResult(null, "简历正在解析，匹配结果将在解析完成后生成。", writeObject(Map.of("status", resume.getParseStatus())));
        JobApplication application = new JobApplication();
        application.setApplicationNo(nextApplicationNo());
        application.setCompanyId(position.getCompanyId());
        application.setPositionId(position.getId());
        application.setCandidateId(candidateId);
        application.setResumeId(resume == null ? null : resume.getId());
        statusService.initializeSubmitted(application);
        application.setSource("JOB_HALL");
        application.setMatchScore(match.score());
        application.setMatchSummary(match.summary());
        application.setMatchDetails(match.details());
        application.setMatchStatus("PENDING");
        application.setMatchVersion(resumeVersion(resume));
        application.setMatchEvaluationVersion(1);
        application.setCandidateMessage(trimToNull(request.candidateMessage()));
        application.setSubmittedAt(LocalDateTime.now());
        application.setVersion(0);
        applicationMapper.insert(application);
        if (resumeReady) {
            taskService.enqueueJobMatch(application.getId(), position.getId(), resume.getId(), resumeVersion(resume),
                    application.getMatchEvaluationVersion(), candidateId);
        }
        statusService.recordInitial(application, candidateId, "候选人通过岗位大厅投递");
        notifyCompanyManagers(position.getCompanyId(), "收到新的岗位申请",
                "候选人已投递“" + position.getName() + "”，请及时查看。", application.getId(), "new");
        return toApplicationView(application, true);
    }

    public PageResult<RecruitmentDtos.ApplicationView> myApplications(RecruitmentDtos.ApplicationQuery query) {
        requireCandidate();
        long pageNo = pageNo(query.pageNo());
        long pageSize = pageSize(query.pageSize());
        LambdaQueryWrapper<JobApplication> wrapper = new LambdaQueryWrapper<JobApplication>()
                .eq(JobApplication::getCandidateId, currentUser.id())
                .orderByDesc(JobApplication::getSubmittedAt).orderByDesc(JobApplication::getId);
        applyApplicationFilters(wrapper, query, null);
        Page<JobApplication> result = applicationMapper.selectPage(new Page<>(pageNo, pageSize), wrapper);
        return PageResult.of(result.getRecords().stream().map(item -> toApplicationView(item, false)).toList(),
                result.getTotal(), pageNo, pageSize);
    }

    public RecruitmentDtos.ApplicationView myApplicationDetail(Long id) {
        requireCandidate();
        JobApplication application = applicationMapper.selectById(id);
        if (application == null || !currentUser.id().equals(application.getCandidateId())) {
            throw BusinessException.notFound("申请不存在");
        }
        return toApplicationView(application, true);
    }

    @Transactional
    public RecruitmentDtos.ApplicationView retryCandidateMatch(Long id) {
        requireCandidate();
        JobApplication application = applicationMapper.selectById(id);
        if (application == null || !currentUser.id().equals(application.getCandidateId())) {
            throw BusinessException.notFound("申请不存在");
        }
        return retryMatch(application, currentUser.id());
    }

    public RecruitmentDtos.MatchEvaluationView candidateMatch(Long id) {
        requireCandidate();
        JobApplication application = applicationMapper.selectById(id);
        if (application == null || !currentUser.id().equals(application.getCandidateId())) {
            throw BusinessException.notFound("申请不存在");
        }
        return matchView(application);
    }

    public PageResult<RecruitmentDtos.MatchEvaluationView> candidateMatchHistory(Long id, Long pageNo, Long pageSize) {
        requireCandidate();
        JobApplication application = applicationMapper.selectById(id);
        if (application == null || !currentUser.id().equals(application.getCandidateId())) {
            throw BusinessException.notFound("申请不存在");
        }
        return matchHistory(application, pageNo, pageSize);
    }

    public RecruitmentDtos.Dashboard companyDashboard() {
        Long companyId = companyAccess.requirePermission("analytics:read");
        long published = positionMapper.selectCount(new LambdaQueryWrapper<JobPosition>()
                .eq(JobPosition::getCompanyId, companyId).eq(JobPosition::getRecruitmentStatus, "PUBLISHED"));
        long drafts = positionMapper.selectCount(new LambdaQueryWrapper<JobPosition>()
                .eq(JobPosition::getCompanyId, companyId).eq(JobPosition::getRecruitmentStatus, "DRAFT"));
        List<JobApplication> applications = applicationMapper.selectList(new LambdaQueryWrapper<JobApplication>()
                .eq(JobApplication::getCompanyId, companyId));
        long pending = applications.stream().filter(item -> Set.of("SUBMITTED", "AI_INTERVIEW_PENDING", "AI_INTERVIEWING", "UNDER_REVIEW").contains(item.getStatus())).count();
        long offline = applications.stream().filter(item -> ApplicationStatus.OFFLINE_INTERVIEW.name().equals(item.getStatus())).count();
        long hired = applications.stream().filter(item -> ApplicationStatus.HIRED.name().equals(item.getStatus())).count();
        BigDecimal average = applications.stream().map(JobApplication::getMatchScore).filter(score -> score != null)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        long scored = applications.stream().filter(item -> item.getMatchScore() != null).count();
        if (scored > 0) average = average.divide(BigDecimal.valueOf(scored), 1, RoundingMode.HALF_UP);
        return new RecruitmentDtos.Dashboard(published, drafts, applications.size(), pending, offline, hired, average);
    }

    public PageResult<RecruitmentDtos.JobView> companyPositions(RecruitmentDtos.JobQuery query) {
        Long companyId = companyAccess.requirePermission("recruitment:position:read");
        long pageNo = pageNo(query.pageNo());
        long pageSize = pageSize(query.pageSize());
        LambdaQueryWrapper<JobPosition> wrapper = new LambdaQueryWrapper<JobPosition>()
                .eq(JobPosition::getCompanyId, companyId)
                .orderByDesc(JobPosition::getUpdatedAt).orderByDesc(JobPosition::getId);
        applyJobFilters(wrapper, query);
        Page<JobPosition> result = positionMapper.selectPage(new Page<>(pageNo, pageSize), wrapper);
        return PageResult.of(result.getRecords().stream().map(item -> toJobView(item, false)).toList(),
                result.getTotal(), pageNo, pageSize);
    }

    @Transactional
    public RecruitmentDtos.JobView createPosition(RecruitmentDtos.PositionRequest request) {
        Long companyId = companyAccess.requirePermission("recruitment:position:write");
        validatePositionRequest(request);
        if (StringUtils.hasText(request.recruitmentStatus())
                && !"DRAFT".equals(normalizeEnum(request.recruitmentStatus()))) {
            throw BusinessException.badRequest("岗位创建只能保存为草稿，请使用发布动作上线岗位");
        }
        if (positionMapper.exists(new LambdaQueryWrapper<JobPosition>().eq(JobPosition::getPositionCode, request.positionCode().trim()))) {
            throw BusinessException.conflict("岗位编码已存在");
        }
        JobPosition position = new JobPosition();
        position.setCompanyId(companyId);
        position.setCreatedBy(currentUser.id());
        position.setStatus(1);
        position.setRecruitmentStatus("DRAFT");
        applyPositionFields(position, request);
        positionMapper.insert(position);
        auditService.recordPositionOperation("POSITION_CREATED", companyId, position.getId(), position.getPositionCode(), "创建岗位草稿");
        return toJobView(position, false);
    }

    @Transactional
    public RecruitmentDtos.JobView updatePosition(Long id, RecruitmentDtos.PositionRequest request) {
        companyAccess.requirePermission("recruitment:position:write");
        validatePositionRequest(request);
        JobPosition position = companyAccess.requirePosition(id);
        if (StringUtils.hasText(request.recruitmentStatus())
                && !normalizeEnum(request.recruitmentStatus()).equals(normalizeEnum(position.getRecruitmentStatus()))) {
            throw BusinessException.badRequest("招聘状态必须通过发布或关闭动作修改");
        }
        if (!position.getPositionCode().equals(request.positionCode().trim())
                && positionMapper.exists(new LambdaQueryWrapper<JobPosition>().eq(JobPosition::getPositionCode, request.positionCode().trim()))) {
            throw BusinessException.conflict("岗位编码已存在");
        }
        applyPositionFields(position, request);
        positionMapper.updateById(position);
        auditService.recordPositionOperation("POSITION_UPDATED", position.getCompanyId(), position.getId(), position.getPositionCode(), "更新岗位内容");
        return toJobView(position, false);
    }

    public RecruitmentDtos.PositionDetail companyPositionDetail(Long id) {
        Long companyId = companyAccess.requirePermission("recruitment:position:read");
        JobPosition position = companyAccess.requirePosition(id);
        return new RecruitmentDtos.PositionDetail(toJobView(position, false), positionStatistics(companyId, position.getId()));
    }

    public RecruitmentDtos.PositionStatistics companyPositionStatistics(Long id) {
        Long companyId = companyAccess.requirePermission("recruitment:position:read");
        companyAccess.requirePosition(id);
        return positionStatistics(companyId, id);
    }

    @Transactional
    public RecruitmentDtos.JobView clonePosition(Long id) {
        Long companyId = companyAccess.requirePermission("recruitment:position:write");
        JobPosition source = companyAccess.requirePosition(id);
        JobPosition clone = new JobPosition();
        clone.setCompanyId(companyId);
        clone.setCreatedBy(currentUser.id());
        clone.setStatus(1);
        clone.setPositionCode(nextCloneCode(source.getPositionCode()));
        clone.setName(trimToLength(source.getName() + "（副本）", 128));
        clone.setDepartment(source.getDepartment());
        clone.setSalaryMin(source.getSalaryMin());
        clone.setSalaryMax(source.getSalaryMax());
        clone.setCity(source.getCity());
        clone.setExperienceRequirement(source.getExperienceRequirement());
        clone.setEducationRequirement(source.getEducationRequirement());
        clone.setJobType(source.getJobType());
        clone.setDescription(source.getDescription());
        clone.setRequirements(source.getRequirements());
        clone.setSkillTags(source.getSkillTags());
        clone.setRecruitmentStatus("DRAFT");
        clone.setPublishedAt(null);
        clone.setExpiresAt(null);
        positionMapper.insert(clone);
        auditService.recordPositionOperation("POSITION_CLONED", companyId, clone.getId(), clone.getPositionCode(),
                "复制岗位 sourcePositionId=" + source.getId());
        return toJobView(clone, false);
    }

    @Transactional
    public RecruitmentDtos.JobView updatePositionStatus(Long id, RecruitmentDtos.PositionStatusRequest request) {
        Long companyId = companyAccess.requirePermission("recruitment:position:write");
        JobPosition position = companyAccess.requirePosition(id);
        String current = normalizeEnum(position.getRecruitmentStatus());
        String target = normalizeEnum(request.status());
        if (!POSITION_STATUSES.contains(target)) throw BusinessException.badRequest("招聘状态不合法");
        if (current.equals(target)) return toJobView(position, false);
        if (!isAllowedPositionTransition(current, target)) {
            throw BusinessException.badRequest("岗位不能从 " + current + " 直接变更为 " + target);
        }
        if ("PUBLISHED".equals(target)) {
            companyAccess.requirePermission("recruitment:position:publish");
            validatePublishable(position);
            position.setPublishedAt(LocalDateTime.now());
        }
        position.setRecruitmentStatus(target);
        positionMapper.updateById(position);
        auditService.recordPositionOperation("POSITION_STATUS_CHANGED", companyId, position.getId(), position.getPositionCode(),
                current + " -> " + target + (StringUtils.hasText(request.note()) ? "; " + request.note().trim() : ""));
        return toJobView(position, false);
    }

    public PageResult<RecruitmentDtos.ApplicationView> companyApplications(RecruitmentDtos.ApplicationQuery query) {
        Long companyId = companyAccess.requirePermission("application:read");
        long pageNo = pageNo(query.pageNo());
        long pageSize = pageSize(query.pageSize());
        LambdaQueryWrapper<JobApplication> wrapper = new LambdaQueryWrapper<JobApplication>()
                .eq(JobApplication::getCompanyId, companyId);
        companyAccess.applyApplicationScope(wrapper);
        applyApplicationFilters(wrapper, query, companyId);
        applyApplicationSorting(wrapper, query.sort());
        Page<JobApplication> result = applicationMapper.selectPage(new Page<>(pageNo, pageSize), wrapper);
        ApplicationRelations relations = loadApplicationRelations(result.getRecords());
        return PageResult.of(result.getRecords().stream().map(item -> toApplicationView(item, false, relations)).toList(),
                result.getTotal(), pageNo, pageSize);
    }

    public RecruitmentDtos.ApplicationView companyApplicationDetail(Long id) {
        companyAccess.requirePermission("application:read");
        JobApplication application = companyAccess.requireApplication(id);
        return toApplicationView(application, true);
    }

    public RecruitmentDtos.ApplicationInterviewView companyInterviewDetail(Long id) {
        companyAccess.requirePermission("application:read");
        JobApplication application = companyAccess.requireApplication(id);
        Interview interview = application.getInterviewId() == null ? null : companyAccess.requireInterviewForApplication(application);
        OfflineInterview offline = offlineInterviewMapper.selectOne(new LambdaQueryWrapper<OfflineInterview>()
                .eq(OfflineInterview::getApplicationId, id)
                .eq(OfflineInterview::getCompanyId, application.getCompanyId())
                .orderByDesc(OfflineInterview::getCreatedAt)
                .last("LIMIT 1"));
        return new RecruitmentDtos.ApplicationInterviewView(application.getId(), application.getInterviewId(),
                interview == null ? null : new RecruitmentDtos.InterviewSummary(interview.getId(), interview.getTitle(),
                        interview.getScheduledAt(), interview.getDuration(), interview.getStatus(), interview.getType()),
                offline == null ? null : new RecruitmentDtos.OfflineInterviewView(offline.getId(), offline.getScheduledAt(),
                        offline.getDurationMinutes(), offline.getInterviewType(), offline.getLocation(), offline.getMeetingUrl(),
                        offline.getContactName(), offline.getContactPhone(), offline.getNote(), offline.getStatus()),
                interviewStatus(interview, offline));
    }

    public List<RecruitmentDtos.InterviewQuestionBankView> companyInterviewQuestionBanks() {
        companyAccess.requirePermission("interview:create");
        return questionBankMapper.selectList(new LambdaQueryWrapper<QuestionBank>()
                        .eq(QuestionBank::getStatus, 1).eq(QuestionBank::getVisibility, 2)
                        .orderByAsc(QuestionBank::getName).orderByAsc(QuestionBank::getId))
                .stream().map(item -> new RecruitmentDtos.InterviewQuestionBankView(item.getId(), item.getName(), item.getDescription())).toList();
    }

    @Transactional
    public RecruitmentDtos.ApplicationView updateApplicationStatus(Long id, RecruitmentDtos.StatusUpdateRequest request) {
        companyAccess.requirePermission("application:review");
        JobApplication application = companyAccess.requireApplication(id);
        ApplicationStatus target = ApplicationStatus.parse(request.status());
        if (target == ApplicationStatus.OFFLINE_INTERVIEW) {
            throw BusinessException.badRequest("进入线下面试请使用面试邀请接口");
        }
        if (request.interviewId() != null) validateInterviewLink(application, request.interviewId());
        transition(application, target, request.note(), request.interviewId());
        return toApplicationView(companyAccess.requireApplication(id), true);
    }

    @Transactional
    public RecruitmentDtos.ApplicationView retryCompanyMatch(Long id) {
        companyAccess.requirePermission("application:review");
        return retryMatch(companyAccess.requireApplication(id), currentUser.id());
    }

    public RecruitmentDtos.MatchEvaluationView companyMatch(Long id) {
        companyAccess.requirePermission("application:read");
        return matchView(companyAccess.requireApplication(id));
    }

    public PageResult<RecruitmentDtos.MatchEvaluationView> companyMatchHistory(Long id, Long pageNo, Long pageSize) {
        companyAccess.requirePermission("application:read");
        return matchHistory(companyAccess.requireApplication(id), pageNo, pageSize);
    }

    @Transactional
    public RecruitmentDtos.ApplicationView createAiInterview(Long id, RecruitmentDtos.AiInterviewRequest request) {
        Long companyId = companyAccess.requirePermission("interview:create");
        JobApplication application = lockCompanyApplication(id, companyId);
        if (!Set.of(ApplicationStatus.SUBMITTED.name(), ApplicationStatus.UNDER_REVIEW.name()).contains(application.getStatus())) {
            throw BusinessException.badRequest("当前申请状态不允许安排 AI 面试");
        }
        if (application.getInterviewId() != null) {
            throw BusinessException.conflict("该申请已经关联 AI 面试");
        }
        if (offlineInterviewMapper.exists(new LambdaQueryWrapper<OfflineInterview>()
                .eq(OfflineInterview::getApplicationId, id))) {
            throw BusinessException.conflict("该申请已经存在活动面试");
        }
        JobPosition position = companyAccess.requirePosition(application.getPositionId());
        InterviewDtos.CreateRequest interviewRequest = new InterviewDtos.CreateRequest(
                position.getName() + " · AI 面试", position.getId(), application.getCandidateId(), request.scheduledAt(),
                request.durationMinutes(), request.type().trim().toLowerCase(Locale.ROOT), null, trimToNull(request.remark()), null,
                request.questionBankId(), request.questionCount(), request.interviewerStyle());
        Interview interview = interviewService.createRecruitment(position.getId(), application.getCandidateId(), interviewRequest);
        auditService.recordInterviewOperation("INTERVIEW_CREATED", companyId, interview.getId(),
                "创建 AI 面试并关联申请 " + application.getId());
        transition(application, ApplicationStatus.AI_INTERVIEW_PENDING, "已安排 AI 面试，等待候选人进入", interview.getId());
        recordInterviewHistory(interview, application, null, "PENDING", "SCHEDULED", "已安排 AI 面试", "SENT");
        Company company = companyAccess.requireActiveCompany(companyId);
        notificationService.create(application.getCandidateId(), "INTERVIEW_CREATED", "收到 AI 面试邀请",
                company.getName() + "已为你安排“" + position.getName() + "”的 AI 面试，请在预约时间进入面试间。",
                "JOB_APPLICATION", application.getId(), "ai-interview-invite-" + interview.getId());
        return toApplicationView(companyAccess.requireApplication(id), true);
    }

    @Transactional
    public RecruitmentDtos.ApplicationView inviteOfflineInterview(Long id, RecruitmentDtos.OfflineInterviewRequest request) {
        Long companyId = companyAccess.requirePermission("interview:create");
        JobApplication application = lockCompanyApplication(id, companyId);
        if (application.getInterviewId() != null) {
            throw BusinessException.conflict("该申请已经存在活动面试");
        }
        if (offlineInterviewMapper.exists(new LambdaQueryWrapper<OfflineInterview>()
                .eq(OfflineInterview::getApplicationId, id))) {
            throw BusinessException.conflict("该申请已发送线下面试邀请");
        }
        OfflineInterview interview = new OfflineInterview();
        interview.setApplicationId(id);
        interview.setCompanyId(companyId);
        interview.setCandidateId(application.getCandidateId());
        interview.setScheduledAt(request.scheduledAt());
        interview.setDurationMinutes(request.durationMinutes());
        interview.setInterviewType(normalizeEnum(request.interviewType()));
        if (!Set.of("ONSITE", "VIDEO", "PHONE").contains(interview.getInterviewType())) {
            throw BusinessException.badRequest("线下面试形式不合法");
        }
        interview.setLocation(trimToNull(request.location()));
        interview.setMeetingUrl(trimToNull(request.meetingUrl()));
        if ("ONSITE".equals(interview.getInterviewType()) && !StringUtils.hasText(interview.getLocation())) {
            throw BusinessException.badRequest("现场面试必须填写地点");
        }
        if ("VIDEO".equals(interview.getInterviewType()) && !StringUtils.hasText(interview.getMeetingUrl())) {
            throw BusinessException.badRequest("视频面试必须填写会议链接");
        }
        interview.setContactName(trimToNull(request.contactName()));
        interview.setContactPhone(trimToNull(request.contactPhone()));
        interview.setNote(trimToNull(request.note()));
        interview.setStatus("SCHEDULED");
        interview.setCreatedBy(currentUser.id());
        offlineInterviewMapper.insert(interview);
        auditService.recordInterviewOperation("INTERVIEW_CREATED", companyId, interview.getId(),
                "创建线下面试并关联申请 " + application.getId());
        transition(application, ApplicationStatus.OFFLINE_INTERVIEW, "已发送线下面试邀请", null);
        recordInterviewHistory(null, application, interview, null, "SCHEDULED", "已发送线下面试邀请", "SENT");
        Company company = companyAccess.requireActiveCompany(companyId);
        notificationService.create(application.getCandidateId(), "INTERVIEW_CREATED", "收到线下面试邀请",
                company.getName() + "邀请你于 " + request.scheduledAt().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")) + " 参加面试，请查看详情。",
                "JOB_APPLICATION", application.getId(), "offline-interview-" + interview.getId());
        return toApplicationView(companyAccess.requireApplication(id), true);
    }

    private void applyJobFilters(LambdaQueryWrapper<JobPosition> wrapper, RecruitmentDtos.JobQuery query) {
        if (StringUtils.hasText(query.keyword())) {
            String keyword = query.keyword().trim();
            List<Long> companyIds = companyMapper.selectList(new LambdaQueryWrapper<Company>()
                            .like(Company::getName, keyword).or().like(Company::getShortName, keyword))
                    .stream().map(Company::getId).toList();
            wrapper.and(item -> {
                item.like(JobPosition::getPositionCode, keyword).or().like(JobPosition::getName, keyword)
                        .or().like(JobPosition::getDepartment, keyword)
                        .or().like(JobPosition::getDescription, keyword).or().like(JobPosition::getSkillTags, keyword);
                if (!companyIds.isEmpty()) item.or().in(JobPosition::getCompanyId, companyIds);
            });
        }
        if (StringUtils.hasText(query.city())) wrapper.like(JobPosition::getCity, query.city().trim());
        if (StringUtils.hasText(query.department())) wrapper.like(JobPosition::getDepartment, query.department().trim());
        if (StringUtils.hasText(query.status())) {
            String status = normalizeEnum(query.status());
            if (!POSITION_STATUSES.contains(status)) throw BusinessException.badRequest("招聘状态不合法");
            wrapper.eq(JobPosition::getRecruitmentStatus, status);
        }
        if (StringUtils.hasText(query.experience())) wrapper.eq(JobPosition::getExperienceRequirement, query.experience().trim());
        if (StringUtils.hasText(query.education())) wrapper.eq(JobPosition::getEducationRequirement, query.education().trim());
        if (StringUtils.hasText(query.jobType())) wrapper.eq(JobPosition::getJobType, normalizeEnum(query.jobType()));
        if (query.minSalary() != null) wrapper.ge(JobPosition::getSalaryMax, Math.max(0, query.minSalary()));
    }

    private void applyApplicationFilters(LambdaQueryWrapper<JobApplication> wrapper, RecruitmentDtos.ApplicationQuery query, Long companyId) {
        if (StringUtils.hasText(query.status())) wrapper.eq(JobApplication::getStatus, parseApplicationStatus(query.status()).name());
        if (query.positionId() != null) wrapper.eq(JobApplication::getPositionId, query.positionId());
        if (query.minMatchScore() != null) {
            validateMatchScore(query.minMatchScore());
            wrapper.ge(JobApplication::getMatchScore, query.minMatchScore());
        }
        if (query.maxMatchScore() != null) {
            validateMatchScore(query.maxMatchScore());
            wrapper.le(JobApplication::getMatchScore, query.maxMatchScore());
        }
        if (query.minMatchScore() != null && query.maxMatchScore() != null
                && query.maxMatchScore().compareTo(query.minMatchScore()) < 0) {
            throw BusinessException.badRequest("匹配度上限不能低于下限");
        }
        if (query.submittedFrom() != null) wrapper.ge(JobApplication::getSubmittedAt, query.submittedFrom());
        if (query.submittedTo() != null) wrapper.le(JobApplication::getSubmittedAt, query.submittedTo());
        applyInterviewStatusFilter(wrapper, query.interviewStatus(), companyId);
        if (!StringUtils.hasText(query.keyword())) return;
        String keyword = query.keyword().trim();
        List<Long> positionIds = positionMapper.selectList(new LambdaQueryWrapper<JobPosition>()
                        .eq(companyId != null, JobPosition::getCompanyId, companyId)
                        .like(JobPosition::getName, keyword))
                .stream().map(JobPosition::getId).toList();
        List<Long> candidateIds = companyId == null ? List.of() : userMapper.selectList(new LambdaQueryWrapper<UserAccount>()
                        .like(UserAccount::getRealName, keyword).or().like(UserAccount::getUsername, keyword))
                .stream().map(UserAccount::getId).toList();
        wrapper.and(item -> {
            item.like(JobApplication::getApplicationNo, keyword);
            if (!positionIds.isEmpty()) item.or().in(JobApplication::getPositionId, positionIds);
            if (!candidateIds.isEmpty()) item.or().in(JobApplication::getCandidateId, candidateIds);
        });
    }

    private void applyApplicationSorting(LambdaQueryWrapper<JobApplication> wrapper, String requestedSort) {
        String sort = StringUtils.hasText(requestedSort) ? normalizeEnum(requestedSort) : "LATEST";
        switch (sort) {
            case "LATEST" -> wrapper.orderByDesc(JobApplication::getSubmittedAt).orderByDesc(JobApplication::getId);
            case "MATCH_SCORE" -> wrapper.orderByDesc(JobApplication::getMatchScore)
                    .orderByDesc(JobApplication::getSubmittedAt).orderByDesc(JobApplication::getId);
            case "OLDEST_UNPROCESSED" -> wrapper.last("ORDER BY COALESCE(reviewed_at, submitted_at) ASC, id ASC");
            default -> throw BusinessException.badRequest("申请排序方式不合法");
        }
    }

    private void applyInterviewStatusFilter(LambdaQueryWrapper<JobApplication> wrapper, String requestedStatus,
                                             Long companyId) {
        if (!StringUtils.hasText(requestedStatus)) return;
        String status = normalizeEnum(requestedStatus);
        switch (status) {
            case "NONE" -> wrapper.isNull(JobApplication::getInterviewId)
                    .apply("NOT EXISTS (SELECT 1 FROM offline_interview oi "
                            + "WHERE oi.application_id = job_application.id AND oi.company_id = {0})", companyId);
            case "AI_PENDING" -> wrapper.eq(JobApplication::getStatus, ApplicationStatus.AI_INTERVIEW_PENDING.name());
            case "AI_IN_PROGRESS" -> wrapper.and(item -> item.eq(JobApplication::getStatus, ApplicationStatus.AI_INTERVIEWING.name())
                    .or().apply("EXISTS (SELECT 1 FROM interview i WHERE i.id = interview_id AND i.status = {0})", Interview.IN_PROGRESS));
            case "AI_COMPLETED" -> wrapper.apply("interview_id IS NOT NULL AND EXISTS "
                    + "(SELECT 1 FROM interview i WHERE i.id = interview_id AND i.status IN ({0}, {1}, {2}, {3}) )",
                    Interview.COMPLETED, Interview.PASSED, Interview.REPORT_GENERATING, Interview.REPORT_READY);
            case "OFFLINE_SCHEDULED", "OFFLINE_COMPLETED", "OFFLINE_CANCELLED" -> {
                String offlineStatus = status.substring("OFFLINE_".length());
                wrapper.apply("EXISTS (SELECT 1 FROM offline_interview oi WHERE oi.application_id = id "
                        + "AND oi.company_id = {0} AND oi.status = {1})", companyId, offlineStatus);
            }
            default -> throw BusinessException.badRequest("面试状态筛选不合法");
        }
    }

    private static void validateMatchScore(BigDecimal score) {
        if (score.compareTo(BigDecimal.ZERO) < 0 || score.compareTo(BigDecimal.valueOf(100)) > 0) {
            throw BusinessException.badRequest("匹配度必须在 0 到 100 之间");
        }
    }

    private RecruitmentDtos.JobView toJobView(JobPosition position, boolean applied) {
        Company company = companyAccess.requireActiveCompany(position.getCompanyId());
        return new RecruitmentDtos.JobView(position.getId(), position.getPositionCode(), toCompanyView(company), position.getName(),
                position.getDepartment(), position.getSalaryMin(), position.getSalaryMax(), position.getCity(),
                position.getExperienceRequirement(), position.getEducationRequirement(), position.getJobType(),
                position.getDescription(), position.getRequirements(), parseStringList(position.getSkillTags()),
                position.getRecruitmentStatus(), position.getPublishedAt(), position.getExpiresAt(), applied, position.getUpdatedAt());
    }

    private RecruitmentDtos.ApplicationView toApplicationView(JobApplication application, boolean includeHistory) {
        return toApplicationView(application, includeHistory, null);
    }

    private RecruitmentDtos.ApplicationView toApplicationView(JobApplication application, boolean includeHistory,
                                                               ApplicationRelations relations) {
        Company company = relations == null ? companyAccess.requireActiveCompany(application.getCompanyId())
                : relations.companies().get(application.getCompanyId());
        JobPosition position = relations == null ? positionMapper.selectById(application.getPositionId())
                : relations.positions().get(application.getPositionId());
        UserAccount candidate = relations == null ? userMapper.selectById(application.getCandidateId())
                : relations.candidates().get(application.getCandidateId());
        CandidateResume resume = application.getResumeId() == null ? null : relations == null
                ? resumeMapper.selectById(application.getResumeId()) : relations.resumes().get(application.getResumeId());
        Interview aiInterview = application.getInterviewId() == null ? null : relations == null
                ? interviewMapper.selectById(application.getInterviewId()) : relations.interviews().get(application.getInterviewId());
        OfflineInterview offline = relations == null
                ? offlineInterviewMapper.selectOne(new LambdaQueryWrapper<OfflineInterview>()
                        .eq(OfflineInterview::getApplicationId, application.getId()).last("LIMIT 1"))
                : relations.offlineInterviews().get(application.getId());
        List<RecruitmentDtos.HistoryView> history = includeHistory
                ? historyMapper.selectList(new LambdaQueryWrapper<JobApplicationStatusHistory>()
                        .eq(JobApplicationStatusHistory::getApplicationId, application.getId())
                        .orderByAsc(JobApplicationStatusHistory::getCreatedAt).orderByAsc(JobApplicationStatusHistory::getId))
                        .stream().map(item -> {
                            UserAccount operator = userMapper.selectById(item.getOperatorId());
                            return new RecruitmentDtos.HistoryView(item.getFromStatus(), item.getToStatus(),
                                    operator == null ? "系统" : operator.getRealName(), item.getNote(), item.getCreatedAt());
                        }).toList()
                : List.of();
        List<RecruitmentDtos.StatusTransition> allowedTransitions = statusService.allowedTransitions(application.getStatus());
        return new RecruitmentDtos.ApplicationView(application.getId(), application.getApplicationNo(), application.getCompanyId(),
                company.getName(), application.getPositionId(), position == null ? "岗位已失效" : position.getName(),
                application.getCandidateId(), candidate == null ? "候选人" : candidate.getRealName(),
                candidate == null ? null : candidate.getEmail(), candidate == null ? null : candidate.getPhone(),
                resume == null ? null : toResumeView(resume), application.getStatus(), application.getMatchScore(),
                 application.getMatchStatus(), application.getMatchVersion(), application.getMatchError(), application.getMatchCompletedAt(),
                 application.getMatchSummary(), application.getMatchDetails(), application.getCandidateMessage(),
                 application.getReviewNote(), application.getInterviewId(), toInterviewSummary(aiInterview), toOfflineInterviewView(offline), history,
                 application.getSubmittedAt(), application.getUpdatedAt(), allowedTransitions,
                 interviewStatus(aiInterview, offline), application.getUpdatedAt(), nextStep(allowedTransitions));
    }

    private ApplicationRelations loadApplicationRelations(List<JobApplication> applications) {
        if (applications.isEmpty()) return ApplicationRelations.empty();
        Set<Long> companyIds = applications.stream().map(JobApplication::getCompanyId).filter(java.util.Objects::nonNull)
                .collect(Collectors.toSet());
        Set<Long> positionIds = applications.stream().map(JobApplication::getPositionId).filter(java.util.Objects::nonNull)
                .collect(Collectors.toSet());
        Set<Long> candidateIds = applications.stream().map(JobApplication::getCandidateId).filter(java.util.Objects::nonNull)
                .collect(Collectors.toSet());
        Set<Long> resumeIds = applications.stream().map(JobApplication::getResumeId).filter(java.util.Objects::nonNull)
                .collect(Collectors.toSet());
        Set<Long> interviewIds = applications.stream().map(JobApplication::getInterviewId).filter(java.util.Objects::nonNull)
                .collect(Collectors.toSet());
        Set<Long> applicationIds = applications.stream().map(JobApplication::getId).filter(java.util.Objects::nonNull)
                .collect(Collectors.toSet());

        Map<Long, Company> companies = companyIds.stream().collect(Collectors.toMap(id -> id,
                companyAccess::requireActiveCompany));
        Map<Long, JobPosition> positions = positionIds.isEmpty() ? Map.of() : positionMapper.selectBatchIds(positionIds).stream()
                .collect(Collectors.toMap(JobPosition::getId, item -> item));
        Map<Long, UserAccount> candidates = candidateIds.isEmpty() ? Map.of() : userMapper.selectBatchIds(candidateIds).stream()
                .collect(Collectors.toMap(UserAccount::getId, item -> item));
        Map<Long, CandidateResume> resumes = resumeIds.isEmpty() ? Map.of() : resumeMapper.selectBatchIds(resumeIds).stream()
                .collect(Collectors.toMap(CandidateResume::getId, item -> item));
        Map<Long, Interview> interviews = interviewIds.isEmpty() ? Map.of() : interviewMapper.selectBatchIds(interviewIds).stream()
                .collect(Collectors.toMap(Interview::getId, item -> item));
        Map<Long, OfflineInterview> offlineInterviews = applicationIds.isEmpty() ? Map.of()
                : offlineInterviewMapper.selectList(new QueryWrapper<OfflineInterview>()
                        .in("application_id", applicationIds)).stream()
                        .collect(Collectors.toMap(OfflineInterview::getApplicationId, item -> item, (left, right) -> left));
        return new ApplicationRelations(companies, positions, candidates, resumes, interviews, offlineInterviews);
    }

    private record ApplicationRelations(Map<Long, Company> companies, Map<Long, JobPosition> positions,
                                        Map<Long, UserAccount> candidates, Map<Long, CandidateResume> resumes,
                                        Map<Long, Interview> interviews,
                                        Map<Long, OfflineInterview> offlineInterviews) {
        private static ApplicationRelations empty() {
            return new ApplicationRelations(Map.of(), Map.of(), Map.of(), Map.of(), Map.of(), Map.of());
        }
    }

    private static String interviewStatus(Interview aiInterview, OfflineInterview offlineInterview) {
        if (offlineInterview != null) return "OFFLINE_" + normalizeEnum(offlineInterview.getStatus());
        if (aiInterview == null) return "NONE";
        return switch (aiInterview.getStatus() == null ? -1 : aiInterview.getStatus()) {
            case Interview.PENDING -> "AI_PENDING";
            case Interview.IN_PROGRESS -> "AI_IN_PROGRESS";
            case Interview.COMPLETED, Interview.PASSED, Interview.REPORT_GENERATING, Interview.REPORT_READY -> "AI_COMPLETED";
            case Interview.CANCELLED, Interview.FAILED -> "AI_CANCELLED";
            default -> "AI_PENDING";
        };
    }

    private static String nextStep(List<RecruitmentDtos.StatusTransition> allowedTransitions) {
        if (allowedTransitions == null || allowedTransitions.isEmpty()) return "流程已结束";
        return allowedTransitions.stream().map(RecruitmentDtos.StatusTransition::label).collect(Collectors.joining(" / "));
    }

    private RecruitmentDtos.MatchEvaluationView matchView(JobApplication application) {
        JobMatchEvaluation evaluation = matchEvaluationMapper.selectOne(new LambdaQueryWrapper<JobMatchEvaluation>()
                .eq(JobMatchEvaluation::getApplicationId, application.getId())
                .orderByDesc(JobMatchEvaluation::getEvaluationVersion).orderByDesc(JobMatchEvaluation::getId)
                .last("LIMIT 1"));
        if (evaluation == null) throw BusinessException.notFound("岗位匹配结果尚未生成");
        return toMatchEvaluationView(application, evaluation);
    }

    private PageResult<RecruitmentDtos.MatchEvaluationView> matchHistory(JobApplication application, Long requestedPageNo,
                                                                           Long requestedPageSize) {
        long currentPageNo = pageNo(requestedPageNo);
        long currentPageSize = Math.min(20, pageSize(requestedPageSize));
        Page<JobMatchEvaluation> result = matchEvaluationMapper.selectPage(new Page<>(currentPageNo, currentPageSize),
                new LambdaQueryWrapper<JobMatchEvaluation>()
                        .eq(JobMatchEvaluation::getApplicationId, application.getId())
                        .orderByDesc(JobMatchEvaluation::getEvaluationVersion)
                        .orderByDesc(JobMatchEvaluation::getId));
        return PageResult.of(result.getRecords().stream().map(item -> toMatchEvaluationView(application, item)).toList(),
                result.getTotal(), currentPageNo, currentPageSize);
    }

    private RecruitmentDtos.MatchEvaluationView toMatchEvaluationView(JobApplication application,
                                                                        JobMatchEvaluation evaluation) {
        JsonNode details;
        try {
            details = objectMapper.readTree(application.getMatchDetails() == null ? "{}" : application.getMatchDetails());
        } catch (JsonProcessingException ignored) {
            details = objectMapper.createObjectNode();
        }
        return new RecruitmentDtos.MatchEvaluationView(evaluation.getId(), application.getId(), evaluation.getEvaluationVersion(),
                evaluation.getResumeVersion(), evaluation.getStatus(), evaluation.getRuleScore(), evaluation.getAiScore(),
                evaluation.getFinalScore(), StringUtils.hasText(evaluation.getSummary()) ? evaluation.getSummary() : application.getMatchSummary(),
                parseStringList(evaluation.getRuleMatchedSkills()), parseStringList(evaluation.getMatchedSkills()),
                parseStringList(evaluation.getStrengths()), parseStringList(evaluation.getGaps()), parseStringList(evaluation.getRisks()),
                parseStringList(evaluation.getEvidence()), evaluation.getConfidence(),
                evaluation.getProviderName(), evaluation.getModelName(), evaluation.getPromptVersion(),
                StringUtils.hasText(evaluation.getRecommendation()) ? evaluation.getRecommendation()
                        : details.path("recommendation").asText("建议人工复核"), evaluation.getCreatedAt(), evaluation.getFinishedAt());
    }

    private RecruitmentDtos.CompanyView toCompanyView(Company company) {
        return new RecruitmentDtos.CompanyView(company.getId(), company.getName(), company.getShortName(), company.getLogoUrl(),
                company.getIndustry(), company.getCompanySize(), company.getCity(), company.getDescription());
    }

    private RecruitmentDtos.ResumeView toResumeView(CandidateResume resume) {
        return new RecruitmentDtos.ResumeView(resume.getId(), resume.getTitle(), resume.getFileName(), resume.getSummary(),
                parseStringList(resume.getSkills()), Integer.valueOf(1).equals(resume.getIsDefault()), resume.getParseStatus(),
                resume.getParseVersion(), null, resume.getMediaId(), resume.getParsedAt(), resume.getUpdatedAt());
    }

    private RecruitmentDtos.OfflineInterviewView toOfflineInterviewView(OfflineInterview item) {
        if (item == null) return null;
        return new RecruitmentDtos.OfflineInterviewView(item.getId(), item.getScheduledAt(), item.getDurationMinutes(),
                item.getInterviewType(), item.getLocation(), item.getMeetingUrl(), item.getContactName(), item.getContactPhone(),
                item.getNote(), item.getStatus());
    }

    private RecruitmentDtos.InterviewSummary toInterviewSummary(Interview item) {
        if (item == null) return null;
        return new RecruitmentDtos.InterviewSummary(item.getId(), item.getTitle(), item.getScheduledAt(), item.getDuration(), item.getStatus(), item.getType());
    }

    private void applyPositionFields(JobPosition target, RecruitmentDtos.PositionRequest source) {
        target.setPositionCode(source.positionCode().trim());
        target.setName(source.name().trim());
        target.setDepartment(trimToNull(source.department()));
        target.setSalaryMin(source.salaryMin());
        target.setSalaryMax(source.salaryMax());
        target.setCity(trimToNull(source.city()));
        target.setExperienceRequirement(trimToNull(source.experienceRequirement()));
        target.setEducationRequirement(trimToNull(source.educationRequirement()));
        target.setJobType(normalizeEnum(source.jobType()));
        target.setDescription(trimToNull(source.description()));
        target.setRequirements(trimToNull(source.requirements()));
        target.setSkillTags(writeStringList(source.skillTags()));
        target.setExpiresAt(source.expiresAt());
    }

    private void validatePositionRequest(RecruitmentDtos.PositionRequest request) {
        String jobType = normalizeEnum(request.jobType());
        if (!JOB_TYPES.contains(jobType)) throw BusinessException.badRequest("岗位类型不合法");
        if (request.salaryMin() != null && request.salaryMax() != null && request.salaryMax() < request.salaryMin()) {
            throw BusinessException.badRequest("薪资上限不能低于下限");
        }
        if (request.expiresAt() != null && !request.expiresAt().isAfter(LocalDateTime.now())) {
            throw BusinessException.badRequest("招聘截止时间必须晚于当前时间");
        }
    }

    private RecruitmentDtos.PositionStatistics positionStatistics(Long companyId, Long positionId) {
        CompanyPositionStatisticsRow row = positionMapper.selectCompanyStatistics(companyId, positionId);
        if (row == null) return new RecruitmentDtos.PositionStatistics(0, BigDecimal.ZERO.setScale(1), 0, 0);
        BigDecimal average = row.getAverageMatchScore() == null ? BigDecimal.ZERO : row.getAverageMatchScore();
        return new RecruitmentDtos.PositionStatistics(
                nullSafe(row.getApplicationCount()), average.setScale(1, RoundingMode.HALF_UP),
                nullSafe(row.getInterviewCount()), nullSafe(row.getHiredCount()));
    }

    private void validatePublishable(JobPosition position) {
        if (!StringUtils.hasText(position.getPositionCode()) || !StringUtils.hasText(position.getName())) {
            throw BusinessException.badRequest("发布前请补充岗位编码和岗位名称");
        }
        if (!StringUtils.hasText(position.getCity())) throw BusinessException.badRequest("发布前请补充工作城市");
        if (!JOB_TYPES.contains(normalizeEnum(position.getJobType()))) throw BusinessException.badRequest("发布前请设置合法的岗位类型");
        if (!StringUtils.hasText(position.getDescription())) throw BusinessException.badRequest("发布前请补充岗位介绍");
        if (!StringUtils.hasText(position.getRequirements())) throw BusinessException.badRequest("发布前请补充任职要求");
        if (parseStringList(position.getSkillTags()).isEmpty()) throw BusinessException.badRequest("发布前至少添加一个技能标签");
        if (position.getSalaryMin() != null && position.getSalaryMax() != null
                && position.getSalaryMax() < position.getSalaryMin()) {
            throw BusinessException.badRequest("薪资上限不能低于下限");
        }
        if (position.getExpiresAt() != null && !position.getExpiresAt().isAfter(LocalDateTime.now())) {
            throw BusinessException.badRequest("招聘截止时间必须晚于当前时间");
        }
    }

    private static boolean isAllowedPositionTransition(String current, String target) {
        return ("DRAFT".equals(current) && "PUBLISHED".equals(target))
                || ("PUBLISHED".equals(current) && "CLOSED".equals(target))
                || ("CLOSED".equals(current) && "PUBLISHED".equals(target));
    }

    private String nextCloneCode(String sourceCode) {
        String suffix;
        String code;
        do {
            suffix = "-COPY-" + UUID.randomUUID().toString().substring(0, 6).toUpperCase(Locale.ROOT);
            code = trimToLength(StringUtils.hasText(sourceCode) ? sourceCode : "POSITION", 64 - suffix.length()) + suffix;
        } while (positionMapper.exists(new LambdaQueryWrapper<JobPosition>().eq(JobPosition::getPositionCode, code)));
        return code;
    }

    private void transition(JobApplication application, ApplicationStatus target, String note, Long interviewId) {
        String fromStatus = application.getStatus();
        statusService.transition(application, target, currentUser.id(), note, interviewId);
        auditService.recordApplicationOperation("APPLICATION_STATUS_CHANGED", application.getCompanyId(), application.getId(),
                "申请阶段由 " + fromStatus + " 变更为 " + target.name());
        if (target != ApplicationStatus.OFFLINE_INTERVIEW) {
            JobPosition position = positionMapper.selectById(application.getPositionId());
            notificationService.create(application.getCandidateId(), "APPLICATION_STATUS_CHANGED", "申请进度已更新",
                    "你投递的“" + (position == null ? "岗位" : position.getName()) + "”已进入“" + statusLabel(target) + "”。",
                    "JOB_APPLICATION", application.getId(), "status-" + target.name().toLowerCase(Locale.ROOT));
        }
    }

    private JobApplication lockCompanyApplication(Long id, Long companyId) {
        JobApplication application = applicationMapper.selectForUpdate(id);
        if (application == null || !companyId.equals(application.getCompanyId())) {
            throw BusinessException.notFound("申请不存在");
        }
        return application;
    }

    private void recordInterviewHistory(Interview aiInterview, JobApplication application, OfflineInterview offlineInterview,
                                        String from, String to, String reason, String notificationStatus) {
        if (interviewStatusHistoryMapper == null) return;
        InterviewStatusHistory history = new InterviewStatusHistory();
        history.setInterviewKind(aiInterview == null ? "OFFLINE" : "AI");
        history.setInterviewId(aiInterview == null ? null : aiInterview.getId());
        history.setOfflineInterviewId(offlineInterview == null ? null : offlineInterview.getId());
        history.setApplicationId(application.getId());
        history.setFromStatus(from);
        history.setToStatus(to);
        history.setOperatorId(currentUser.id());
        history.setReason(reason);
        history.setNotificationStatus(notificationStatus);
        history.setCreatedAt(LocalDateTime.now());
        interviewStatusHistoryMapper.insert(history);
    }

    private void validateInterviewLink(JobApplication application, Long interviewId) {
        Interview interview = interviewMapper.selectById(interviewId);
        if (interview == null || !application.getCandidateId().equals(interview.getCandidateId())
                || !application.getPositionId().equals(interview.getPositionId())) {
            throw BusinessException.badRequest("关联面试与当前申请不匹配");
        }
    }

    private void notifyCompanyManagers(Long companyId, String title, String content, Long applicationId, String suffix) {
        userMapper.selectList(new LambdaQueryWrapper<UserAccount>()
                        .eq(UserAccount::getCompanyId, companyId).eq(UserAccount::getStatus, 1))
                .forEach(user -> notificationService.create(user.getId(), "JOB_APPLICATION", title, content,
                        "JOB_APPLICATION", applicationId, "application-" + applicationId + "-" + suffix));
    }

    private MatchResult calculateMatch(JobPosition position, CandidateResume resume) {
        if (resume == null) return new MatchResult(null, "简历待补充，企业将结合申请信息进行审核。", null);
        Set<String> positionSkills = parseStringList(position.getSkillTags()).stream()
                .map(value -> value.toLowerCase(Locale.ROOT)).collect(Collectors.toSet());
        Set<String> resumeSkills = parseStringList(resume.getSkills()).stream()
                .map(value -> value.toLowerCase(Locale.ROOT)).collect(Collectors.toSet());
        List<String> matched = positionSkills.stream().filter(resumeSkills::contains).sorted().toList();
        BigDecimal score = BigDecimal.valueOf(Math.min(95, 55 + matched.size() * 10L));
        String summary = matched.isEmpty() ? "简历已提交，核心技能仍需在后续环节确认。"
                : "简历已匹配 " + matched.size() + " 项核心技能，建议结合 AI 面试进一步验证。";
        return new MatchResult(score, summary, writeObject(Map.of("matchedSkills", matched,
                "notice", "该分数为规则化初筛结果，不作为最终录用结论")));
    }

    private CandidateResume resolveResume(Long candidateId, Long resumeId) {
        CandidateResume resume = resumeId == null
                ? resumeMapper.selectOne(new LambdaQueryWrapper<CandidateResume>()
                    .eq(CandidateResume::getCandidateId, candidateId).eq(CandidateResume::getStatus, 1)
                    .orderByDesc(CandidateResume::getIsDefault).orderByDesc(CandidateResume::getUpdatedAt).last("LIMIT 1"))
                : resumeMapper.selectById(resumeId);
        if (resume == null) throw BusinessException.badRequest("请先上传一份简历再投递岗位");
        if (!candidateId.equals(resume.getCandidateId()) || !Integer.valueOf(1).equals(resume.getStatus())) {
            throw BusinessException.notFound("简历不存在");
        }
        return resume;
    }

    private RecruitmentDtos.ApplicationView retryMatch(JobApplication application, Long createdBy) {
        CandidateResume resume = application.getResumeId() == null ? null : resumeMapper.selectById(application.getResumeId());
        if (resume == null) throw BusinessException.badRequest("该申请没有可用于匹配的简历");
        boolean ready = resume.getParseStatus() == null || Set.of("SUCCESS", "MANUAL").contains(resume.getParseStatus());
        if (!ready) throw BusinessException.badRequest("简历尚未完成解析，暂不能重新匹配");
        int version = resumeVersion(resume);
        int currentVersion = application.getVersion() == null ? 0 : application.getVersion();
        int evaluationVersion = Math.max(0, application.getMatchEvaluationVersion() == null ? 0 : application.getMatchEvaluationVersion()) + 1;
        LambdaUpdateWrapper<JobApplication> update = new LambdaUpdateWrapper<JobApplication>()
                .eq(JobApplication::getId, application.getId())
                .eq(JobApplication::getVersion, currentVersion)
                .set(JobApplication::getMatchStatus, "PENDING")
                .set(JobApplication::getMatchVersion, version)
                .set(JobApplication::getMatchEvaluationVersion, evaluationVersion)
                .set(JobApplication::getMatchError, null)
                .set(JobApplication::getMatchScore, null)
                .set(JobApplication::getMatchSummary, "正在根据岗位 JD 生成新的 AI 匹配分析。")
                .set(JobApplication::getMatchDetails, null)
                .set(JobApplication::getMatchCompletedAt, null)
                .set(JobApplication::getVersion, currentVersion + 1);
        if (applicationMapper.update(null, update) == 0) {
            throw BusinessException.conflict("申请匹配状态已变化，请刷新后重试");
        }
        application.setMatchStatus("PENDING");
        application.setMatchVersion(version);
        application.setMatchEvaluationVersion(evaluationVersion);
        application.setMatchError(null);
        application.setMatchScore(null);
        application.setMatchSummary("正在根据岗位 JD 生成新的 AI 匹配分析。");
        application.setMatchDetails(null);
        application.setMatchCompletedAt(null);
        application.setVersion(currentVersion + 1);
        taskService.enqueueJobMatch(application.getId(), application.getPositionId(), resume.getId(), version,
                evaluationVersion, createdBy);
        return toApplicationView(applicationMapper.selectById(application.getId()), true);
    }

    private JobPosition requirePublishedPosition(Long id) {
        JobPosition position = positionMapper.selectById(id);
        LocalDateTime now = LocalDateTime.now();
        if (position == null || position.getCompanyId() == null || !Integer.valueOf(1).equals(position.getStatus())
                || !"PUBLISHED".equals(position.getRecruitmentStatus()) || position.getPublishedAt() == null
                || position.getPublishedAt().isAfter(now) || (position.getExpiresAt() != null && !position.getExpiresAt().isAfter(now))) {
            throw BusinessException.notFound("岗位不存在或已停止招聘");
        }
        Company company = companyMapper.selectById(position.getCompanyId());
        if (company == null || !Integer.valueOf(1).equals(company.getStatus())) {
            throw BusinessException.notFound("岗位不存在或已停止招聘");
        }
        return position;
    }

    private void requireCandidate() {
        if (!currentUser.hasRole("CANDIDATE")) throw BusinessException.forbidden("仅候选人可使用招聘岗位大厅");
    }

    private List<String> parseStringList(String json) {
        if (!StringUtils.hasText(json)) return List.of();
        try {
            return objectMapper.readValue(json, new TypeReference<>() {});
        } catch (JsonProcessingException ignored) {
            return List.of();
        }
    }

    private String writeStringList(List<String> values) {
        if (values == null || values.isEmpty()) return null;
        return writeObject(values.stream().filter(StringUtils::hasText).map(String::trim).distinct().toList());
    }

    private String writeObject(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("招聘数据序列化失败", exception);
        }
    }

    private static ApplicationStatus parseApplicationStatus(String value) {
        return ApplicationStatus.parse(normalizeEnum(value));
    }

    private static String statusLabel(ApplicationStatus status) {
        return status.label();
    }

    private static String nextApplicationNo() {
        return "APP-" + LocalDate.now().format(DateTimeFormatter.BASIC_ISO_DATE) + "-"
                + UUID.randomUUID().toString().substring(0, 8).toUpperCase(Locale.ROOT);
    }

    private static long pageNo(Long value) { return value == null ? 1 : Math.max(1, value); }
    private static long pageSize(Long value) { return value == null ? 20 : Math.min(100, Math.max(1, value)); }
    private static long nullSafe(Long value) { return value == null ? 0 : value; }
    private static String normalizeEnum(String value) { return value == null ? "" : value.trim().toUpperCase(Locale.ROOT); }
    private static String trimToNull(String value) { return StringUtils.hasText(value) ? value.trim() : null; }
    private static String trimToLength(String value, int maxLength) {
        if (value == null) return null;
        return value.length() <= maxLength ? value : value.substring(0, maxLength);
    }

    private record MatchResult(BigDecimal score, String summary, String details) {}

    private static int resumeVersion(CandidateResume resume) {
        return resume.getParseVersion() == null ? 0 : resume.getParseVersion();
    }
}
