package com.tyut.aiinterview.recruitment;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tyut.aiinterview.ai.AiTaskService;
import com.tyut.aiinterview.common.BusinessException;
import com.tyut.aiinterview.domain.AiTask;
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
import java.io.IOException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

@Service
public class CandidateResumeService {
    private static final String PARSE_PENDING = "PENDING";
    private static final String PARSE_PROCESSING = "PROCESSING";
    private static final String PARSE_SUCCESS = "SUCCESS";

    private final CandidateResumeMapper resumeMapper;
    private final CandidateResumeAnalysisMapper analysisMapper;
    private final JobApplicationMapper applicationMapper;
    private final MediaService mediaService;
    private final AiTaskService taskService;
    private final CurrentUser currentUser;
    private final ObjectMapper objectMapper;
    private final CompanyAccessService companyAccess;

    public CandidateResumeService(CandidateResumeMapper resumeMapper, CandidateResumeAnalysisMapper analysisMapper,
                                  JobApplicationMapper applicationMapper, MediaService mediaService,
                                  AiTaskService taskService, CurrentUser currentUser, ObjectMapper objectMapper,
                                  CompanyAccessService companyAccess) {
        this.resumeMapper = resumeMapper;
        this.analysisMapper = analysisMapper;
        this.applicationMapper = applicationMapper;
        this.mediaService = mediaService;
        this.taskService = taskService;
        this.currentUser = currentUser;
        this.objectMapper = objectMapper;
        this.companyAccess = companyAccess;
    }

    public List<RecruitmentDtos.ResumeView> list() {
        requireCandidate();
        return resumeMapper.selectList(new LambdaQueryWrapper<CandidateResume>()
                        .eq(CandidateResume::getCandidateId, currentUser.id())
                        .eq(CandidateResume::getStatus, 1)
                        .orderByDesc(CandidateResume::getIsDefault)
                        .orderByDesc(CandidateResume::getUpdatedAt))
                .stream().map(this::view).toList();
    }

    @Transactional
    public RecruitmentDtos.ResumeView upload(MultipartFile file, String title, boolean defaultResume) {
        requireCandidate();
        MediaDtos.MediaVO media = mediaService.uploadResume(file);
        try {
            String fileName = media.originalName();
            CandidateResume resume = new CandidateResume();
            resume.setCandidateId(currentUser.id());
            resume.setMediaId(media.id());
            resume.setTitle(normalizeTitle(title, fileName));
            resume.setFileName(fileName);
            resume.setIsDefault(defaultResume ? 1 : 0);
            resume.setStatus(1);
            resume.setParseStatus(PARSE_PENDING);
            resume.setParseVersion(1);
            resume.setContentHash(mediaService.requireAvailable(media.id()).getChecksumSha256());
            resumeMapper.insert(resume);
            if (defaultResume || !hasDefaultResume()) {
                clearDefaultExcept(resume.getId());
                resume.setIsDefault(1);
                resumeMapper.updateById(resume);
            }
            CandidateResumeAnalysis analysis = newAnalysis(resume, 1);
            analysisMapper.insert(analysis);
            AiTask task = taskService.enqueueResumeAnalysis(resume.getId(), analysis.getId(), analysis.getAnalysisVersion());
            analysis.setAiTaskId(task.getId());
            analysisMapper.updateById(analysis);
            return view(resume);
        } catch (RuntimeException exception) {
            try {
                mediaService.deleteOwned(media.id());
            } catch (RuntimeException ignored) {
                // uploadResume already compensates storage failures; do not mask the original transaction error.
            }
            throw exception;
        }
    }

    public RecruitmentDtos.ResumeView detail(Long id) {
        return view(owned(id));
    }

    public RecruitmentDtos.ResumeAnalysisView analysis(Long id) {
        CandidateResume resume = owned(id);
        return analysisView(resume);
    }

    /**
     * Returns the structured allowlist for an HR application. The company is
     * resolved from the authenticated user and the resume is only followed
     * through the application relationship; raw extracted text and prompt
     * material never leave this service.
     */
    public RecruitmentDtos.ResumeAnalysisView companyAnalysis(Long applicationId) {
        companyAccess.requirePermission("application:read");
        JobApplication application = companyAccess.requireApplication(applicationId);
        if (application.getResumeId() == null) {
            return emptyAnalysis(null, null, "NOT_AVAILABLE", null, null);
        }
        CandidateResume resume = resumeMapper.selectById(application.getResumeId());
        if (resume == null || !Objects.equals(application.getCandidateId(), resume.getCandidateId())
                || !Integer.valueOf(1).equals(resume.getStatus())) {
            return emptyAnalysis(null, null, "NOT_AVAILABLE", null, null);
        }
        return analysisView(resume);
    }

    /**
     * Returns the exact allowlist used by the HR profile tab. The application
     * relationship is resolved before following the resume, so a resume id
     * can never be used as a cross-company lookup key.
     */
    public RecruitmentDtos.CompanyResumeAnalysisView companyStructuredAnalysis(Long applicationId) {
        companyAccess.requirePermission("application:read");
        JobApplication application = companyAccess.requireApplication(applicationId);
        if (application.getResumeId() == null) return emptyCompanyAnalysis(null, "NOT_AVAILABLE");
        CandidateResume resume = resumeMapper.selectById(application.getResumeId());
        if (resume == null || !Objects.equals(application.getCandidateId(), resume.getCandidateId())
                || !Integer.valueOf(1).equals(resume.getStatus())) {
            return emptyCompanyAnalysis(null, "NOT_AVAILABLE");
        }
        CandidateResumeAnalysis latest = latestAnalysis(resume.getId());
        if (latest == null) return emptyCompanyAnalysis(resume.getParseVersion(), resume.getParseStatus());
        JsonNode profile = parseProfile(latest.getProfileJson());
        return new RecruitmentDtos.CompanyResumeAnalysisView(
                stringsAny(profile, 20, 48, "skills"),
                stringsAny(profile, 6, 300, "workExperience", "workHistory", "experience", "experienceHighlights"),
                projects(profile.path("projects")),
                stringsAny(profile, 6, 240, "education", "educationExperience", "educationHistory"),
                stringsAny(profile, 6, 300, "strengths", "strength"),
                stringsAny(profile, 6, 300, "risks", "riskPoints"),
                stringsAny(profile, 8, 300, "followUpDirections", "recommendedFollowUp", "interviewFocus"),
                latest.getAnalysisVersion(), latest.getStatus());
    }

    @Transactional
    public RecruitmentDtos.ResumeParseRetryView companyRetryAnalysis(Long applicationId) {
        companyAccess.requirePermission("application:review");
        JobApplication application = companyAccess.requireApplication(applicationId);
        if (application.getResumeId() == null) throw BusinessException.notFound("该申请没有关联简历");
        CandidateResume resume = resumeMapper.selectById(application.getResumeId());
        if (resume == null || !Objects.equals(application.getCandidateId(), resume.getCandidateId())
                || !Integer.valueOf(1).equals(resume.getStatus())) {
            throw BusinessException.notFound("简历不存在");
        }
        return new RecruitmentDtos.ResumeParseRetryView(retryParseInternal(resume).status());
    }

    private RecruitmentDtos.ResumeAnalysisView analysisView(CandidateResume resume) {
        CandidateResumeAnalysis latest = latestAnalysis(resume.getId());
        if (latest == null) {
            return emptyAnalysis(resume.getId(), resume.getParseVersion(), resume.getParseStatus(), null, null);
        }
        JsonNode profile = parseProfile(latest.getProfileJson());
        return new RecruitmentDtos.ResumeAnalysisView(resume.getId(), latest.getAnalysisVersion(), latest.getStatus(),
                text(profile, "candidateProfile", "summary", 3000), strings(profile, "targetRoles", 2, 80),
                strings(profile, "skills", 20, 48), strings(profile, "experienceHighlights", 6, 300),
                projects(profile.path("projects")), strings(profile, "interviewFocus", 8, 300),
                strings(profile, "riskPoints", 6, 300), latest.getCreatedAt(), latest.getFinishedAt());
    }

    @Transactional
    public RecruitmentDtos.ResumeView setDefault(Long id) {
        CandidateResume resume = owned(id);
        clearDefaultExcept(id);
        resume.setIsDefault(1);
        resumeMapper.updateById(resume);
        return view(resumeMapper.selectById(id));
    }

    @Transactional
    public void delete(Long id) {
        CandidateResume resume = owned(id);
        if (applicationMapper.exists(new LambdaQueryWrapper<JobApplication>().eq(JobApplication::getResumeId, id))) {
            throw BusinessException.conflict("该简历已关联岗位申请，暂不能删除");
        }
        analysisMapper.delete(new LambdaQueryWrapper<CandidateResumeAnalysis>()
                .eq(CandidateResumeAnalysis::getResumeId, id));
        resumeMapper.deleteById(id);
        if (resume.getMediaId() != null) mediaService.deleteOwned(resume.getMediaId());
    }

    @Transactional
    public RecruitmentDtos.ResumeParseTaskView retryParse(Long id) {
        CandidateResume resume = owned(id);
        return retryParseInternal(resume);
    }

    private RecruitmentDtos.ResumeParseTaskView retryParseInternal(CandidateResume resume) {
        if (resume.getMediaId() == null) throw BusinessException.badRequest("该简历没有可解析的文件");
        CandidateResumeAnalysis latest = analysisMapper.selectOne(new LambdaQueryWrapper<CandidateResumeAnalysis>()
                .eq(CandidateResumeAnalysis::getResumeId, resume.getId())
                .in(CandidateResumeAnalysis::getStatus, PARSE_PENDING, PARSE_PROCESSING)
                .orderByDesc(CandidateResumeAnalysis::getAnalysisVersion)
                .last("LIMIT 1"));
        if (latest != null && latest.getAiTaskId() != null) return taskView(taskService.get(latest.getAiTaskId()));
        int version = resume.getParseVersion() == null ? 1 : resume.getParseVersion() + 1;
        resume.setParseVersion(version);
        resume.setParseStatus(PARSE_PENDING);
        resume.setParseError(null);
        resume.setParsedAt(null);
        resumeMapper.updateById(resume);
        CandidateResumeAnalysis analysis = newAnalysis(resume, version);
        analysisMapper.insert(analysis);
        AiTask task = taskService.enqueueResumeAnalysis(resume.getId(), analysis.getId(), version);
        analysis.setAiTaskId(task.getId());
        analysisMapper.updateById(analysis);
        return taskView(task);
    }

    private CandidateResumeAnalysis latestAnalysis(Long resumeId) {
        return analysisMapper.selectOne(new LambdaQueryWrapper<CandidateResumeAnalysis>()
                .eq(CandidateResumeAnalysis::getResumeId, resumeId)
                .orderByDesc(CandidateResumeAnalysis::getAnalysisVersion)
                .last("LIMIT 1"));
    }

    public RecruitmentDtos.ResumeParseTaskView parseTask(Long id) {
        CandidateResume resume = owned(id);
        CandidateResumeAnalysis latest = analysisMapper.selectOne(new LambdaQueryWrapper<CandidateResumeAnalysis>()
                .eq(CandidateResumeAnalysis::getResumeId, resume.getId())
                .orderByDesc(CandidateResumeAnalysis::getAnalysisVersion)
                .last("LIMIT 1"));
        if (latest == null || latest.getAiTaskId() == null) throw BusinessException.notFound("简历解析任务不存在");
        return taskView(taskService.get(latest.getAiTaskId()));
    }

    public ResumeContent content(Long id) throws IOException {
        CandidateResume resume = owned(id);
        if (resume.getMediaId() == null) throw BusinessException.notFound("简历文件不存在");
        MediaFile media = mediaService.requireAvailable(resume.getMediaId());
        if (!currentUser.id().equals(media.getOwnerId())) throw BusinessException.forbidden("无权读取该简历文件");
        return new ResumeContent(mediaService.view(media), mediaService.content(media));
    }

    public ResumeContent companyContent(Long applicationId) throws IOException {
        JobApplication application = companyAccess.requireApplication(applicationId);
        if (application.getResumeId() == null) throw BusinessException.notFound("该申请没有关联简历");
        CandidateResume resume = resumeMapper.selectById(application.getResumeId());
        if (resume == null || !Objects.equals(application.getCandidateId(), resume.getCandidateId())
                || !Integer.valueOf(1).equals(resume.getStatus()) || resume.getMediaId() == null) {
            throw BusinessException.notFound("简历文件不存在");
        }
        MediaFile media = mediaService.requireAvailable(resume.getMediaId());
        if (!Objects.equals(resume.getCandidateId(), media.getOwnerId())) {
            throw BusinessException.notFound("简历文件不存在");
        }
        return new ResumeContent(mediaService.view(media), mediaService.content(media));
    }

    private CandidateResumeAnalysis newAnalysis(CandidateResume resume, int version) {
        CandidateResumeAnalysis analysis = new CandidateResumeAnalysis();
        analysis.setResumeId(resume.getId());
        analysis.setAnalysisVersion(version);
        analysis.setStatus(PARSE_PENDING);
        analysis.setExtractorVersion("resume-extractor-v1");
        analysis.setCreatedAt(LocalDateTime.now());
        return analysis;
    }

    private CandidateResume owned(Long id) {
        requireCandidate();
        CandidateResume resume = resumeMapper.selectById(id);
        if (resume == null || !currentUser.id().equals(resume.getCandidateId()) || !Integer.valueOf(1).equals(resume.getStatus())) {
            throw BusinessException.notFound("简历不存在");
        }
        return resume;
    }

    private void clearDefaultExcept(Long id) {
        resumeMapper.update(null, new LambdaUpdateWrapper<CandidateResume>()
                .eq(CandidateResume::getCandidateId, currentUser.id())
                .eq(CandidateResume::getStatus, 1)
                .ne(CandidateResume::getId, id)
                .set(CandidateResume::getIsDefault, 0));
    }

    private boolean hasDefaultResume() {
        return resumeMapper.exists(new LambdaQueryWrapper<CandidateResume>()
                .eq(CandidateResume::getCandidateId, currentUser.id())
                .eq(CandidateResume::getStatus, 1)
                .eq(CandidateResume::getIsDefault, 1));
    }

    private String normalizeTitle(String title, String fileName) {
        String value = StringUtils.hasText(title) ? title.trim() : removeExtension(fileName);
        if (value.isBlank()) value = "我的简历";
        return value.substring(0, Math.min(160, value.length()));
    }

    private String removeExtension(String fileName) {
        String value = fileName == null ? "" : fileName;
        int index = value.lastIndexOf('.');
        return index > 0 ? value.substring(0, index) : value;
    }

    private RecruitmentDtos.ResumeView view(CandidateResume resume) {
        return new RecruitmentDtos.ResumeView(resume.getId(), resume.getTitle(), resume.getFileName(), resume.getSummary(),
                parseSkills(resume.getSkills()), Integer.valueOf(1).equals(resume.getIsDefault()), resume.getParseStatus(),
                resume.getParseVersion(), resume.getParseError(), resume.getMediaId(), resume.getParsedAt(), resume.getUpdatedAt());
    }

    private RecruitmentDtos.ResumeAnalysisView emptyAnalysis(Long resumeId, Integer version, String status,
                                                               LocalDateTime createdAt, LocalDateTime finishedAt) {
        return new RecruitmentDtos.ResumeAnalysisView(resumeId, version, status, null, List.of(), List.of(),
                List.of(), List.of(), List.of(), List.of(), createdAt, finishedAt);
    }

    private RecruitmentDtos.CompanyResumeAnalysisView emptyCompanyAnalysis(Integer version, String status) {
        return new RecruitmentDtos.CompanyResumeAnalysisView(List.of(), List.of(), List.of(), List.of(), List.of(),
                List.of(), List.of(), version, status);
    }

    private JsonNode parseProfile(String profileJson) {
        if (!StringUtils.hasText(profileJson)) return objectMapper.createObjectNode();
        try {
            JsonNode profile = objectMapper.readTree(profileJson);
            return profile != null && profile.isObject() ? profile : objectMapper.createObjectNode();
        } catch (JsonProcessingException ignored) {
            return objectMapper.createObjectNode();
        }
    }

    private String text(JsonNode node, String first, String second, int maxLength) {
        String value = StringUtils.hasText(node.path(first).asText(null)) ? node.path(first).asText() : node.path(second).asText("");
        value = value.trim();
        return value.length() <= maxLength ? value : value.substring(0, maxLength);
    }

    private List<String> strings(JsonNode node, String field, int maxItems, int maxLength) {
        JsonNode values = node.path(field);
        if (!values.isArray()) return List.of();
        return java.util.stream.StreamSupport.stream(values.spliterator(), false)
                .map(value -> value.isObject() ? value.path("name").asText("") : value.asText(""))
                .map(String::trim).filter(StringUtils::hasText)
                .map(value -> value.length() <= maxLength ? value : value.substring(0, maxLength))
                .distinct().limit(maxItems).toList();
    }

    private List<String> stringsAny(JsonNode node, int maxItems, int maxLength, String... fields) {
        for (String field : fields) {
            List<String> values = strings(node, field, maxItems, maxLength);
            if (!values.isEmpty()) return values;
        }
        return List.of();
    }

    private List<RecruitmentDtos.ResumeProjectView> projects(JsonNode values) {
        if (!values.isArray()) return List.of();
        return java.util.stream.StreamSupport.stream(values.spliterator(), false).filter(JsonNode::isObject).limit(6)
                .map(item -> new RecruitmentDtos.ResumeProjectView(text(item, "name", "project", 160),
                        text(item, "role", "responsibility", 160), text(item, "evidence", "achievement", 500)))
                .filter(item -> StringUtils.hasText(item.name())).toList();
    }

    private List<String> parseSkills(String value) {
        if (!StringUtils.hasText(value)) return List.of();
        try {
            return objectMapper.readValue(value, new TypeReference<>() {});
        } catch (JsonProcessingException exception) {
            return List.of();
        }
    }

    private RecruitmentDtos.ResumeParseTaskView taskView(AiTask task) {
        return new RecruitmentDtos.ResumeParseTaskView(task.getId(), task.getStatus(), task.getAttempts(), task.getMaxAttempts(),
                task.getErrorMessage(), task.getCreatedAt(), task.getFinishedAt());
    }

    private void requireCandidate() {
        if (!currentUser.hasRole("CANDIDATE")) throw BusinessException.forbidden("仅候选人可管理简历");
    }

    public record ResumeContent(MediaDtos.MediaVO metadata, Resource resource) {}
}
