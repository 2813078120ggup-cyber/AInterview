package com.tyut.aiinterview.recruitment;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.tyut.aiinterview.common.BusinessException;
import com.tyut.aiinterview.common.PageResult;
import com.tyut.aiinterview.domain.ApplicationNote;
import com.tyut.aiinterview.domain.CompanyCandidate;
import com.tyut.aiinterview.domain.CompanyCandidateTag;
import com.tyut.aiinterview.domain.CompanyCandidateTagRelation;
import com.tyut.aiinterview.domain.Interview;
import com.tyut.aiinterview.domain.JobApplication;
import com.tyut.aiinterview.domain.JobPosition;
import com.tyut.aiinterview.domain.OfflineInterview;
import com.tyut.aiinterview.domain.UserAccount;
import com.tyut.aiinterview.mapper.ApplicationNoteMapper;
import com.tyut.aiinterview.mapper.CompanyCandidateMapper;
import com.tyut.aiinterview.mapper.CompanyCandidateTagMapper;
import com.tyut.aiinterview.mapper.CompanyCandidateTagRelationMapper;
import com.tyut.aiinterview.mapper.CompanyTalentPoolMapper;
import com.tyut.aiinterview.mapper.CompanyTalentPoolRow;
import com.tyut.aiinterview.mapper.InterviewMapper;
import com.tyut.aiinterview.mapper.JobApplicationMapper;
import com.tyut.aiinterview.mapper.JobPositionMapper;
import com.tyut.aiinterview.mapper.OfflineInterviewMapper;
import com.tyut.aiinterview.mapper.UserMapper;
import com.tyut.aiinterview.observability.OperationAuditService;
import com.tyut.aiinterview.security.CurrentUser;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
public class CompanyTalentPoolService {
    private final CompanyTalentPoolMapper talentPoolMapper;
    private final CompanyCandidateMapper candidateMapper;
    private final ApplicationNoteMapper noteMapper;
    private final CompanyCandidateTagMapper tagMapper;
    private final CompanyCandidateTagRelationMapper relationMapper;
    private final JobApplicationMapper applicationMapper;
    private final JobPositionMapper positionMapper;
    private final InterviewMapper interviewMapper;
    private final OfflineInterviewMapper offlineInterviewMapper;
    private final UserMapper userMapper;
    private final CompanyAccessService companyAccess;
    private final CurrentUser currentUser;
    private final OperationAuditService auditService;

    public CompanyTalentPoolService(CompanyTalentPoolMapper talentPoolMapper,
                                    CompanyCandidateMapper candidateMapper,
                                    ApplicationNoteMapper noteMapper,
                                    CompanyCandidateTagMapper tagMapper,
                                    CompanyCandidateTagRelationMapper relationMapper,
                                    JobApplicationMapper applicationMapper,
                                    JobPositionMapper positionMapper,
                                    InterviewMapper interviewMapper,
                                    OfflineInterviewMapper offlineInterviewMapper,
                                    UserMapper userMapper,
                                    CompanyAccessService companyAccess,
                                    CurrentUser currentUser,
                                    OperationAuditService auditService) {
        this.talentPoolMapper = talentPoolMapper;
        this.candidateMapper = candidateMapper;
        this.noteMapper = noteMapper;
        this.tagMapper = tagMapper;
        this.relationMapper = relationMapper;
        this.applicationMapper = applicationMapper;
        this.positionMapper = positionMapper;
        this.interviewMapper = interviewMapper;
        this.offlineInterviewMapper = offlineInterviewMapper;
        this.userMapper = userMapper;
        this.companyAccess = companyAccess;
        this.currentUser = currentUser;
        this.auditService = auditService;
    }

    public PageResult<TalentPoolDtos.CandidateView> page(TalentPoolDtos.Query query) {
        Long companyId = companyAccess.requirePermission("application:read");
        long pageNo = pageNo(query == null ? null : query.pageNo());
        int pageSize = (int) pageSize(query == null ? null : query.pageSize());
        boolean restricted = companyAccess.isRestrictedInterviewer();
        Long userId = currentUser.id();
        List<CompanyTalentPoolRow> rows = talentPoolMapper.selectPage(companyId, userId, restricted, null,
                text(query == null ? null : query.keyword()), query == null ? null : query.tagId(),
                text(query == null ? null : query.skill()), query == null ? null : query.positionId(),
                query == null ? null : query.lastContactFrom(), query == null ? null : query.lastContactTo(),
                sort(query == null ? null : query.sort()), (int) ((pageNo - 1) * pageSize), pageSize);
        long total = talentPoolMapper.count(companyId, userId, restricted, null,
                text(query == null ? null : query.keyword()), query == null ? null : query.tagId(),
                text(query == null ? null : query.skill()), query == null ? null : query.positionId(),
                query == null ? null : query.lastContactFrom(), query == null ? null : query.lastContactTo());
        return PageResult.of(rows.stream().map(this::toCandidateView).toList(), total, pageNo, pageSize);
    }

    public TalentPoolDtos.Detail detail(Long candidateId, Long notePageNo, Long notePageSize) {
        Long companyId = companyAccess.requirePermission("application:read");
        companyAccess.requireCandidateAccess(candidateId);
        CompanyCandidate pool = requireActivePool(companyId, candidateId);
        CompanyTalentPoolRow row = selectSingle(companyId, candidateId);
        return new TalentPoolDtos.Detail(toCandidateView(row), tags(companyId, pool.getId()),
                notes(companyId, pool, notePageNo, notePageSize), applications(companyId, candidateId));
    }

    public TalentPoolDtos.MembershipView membership(Long candidateId) {
        Long companyId = companyAccess.requirePermission("application:read");
        companyAccess.requireCandidateAccess(candidateId);
        CompanyCandidate pool = candidateMapper.selectOne(new LambdaQueryWrapper<CompanyCandidate>()
                .eq(CompanyCandidate::getCompanyId, companyId)
                .eq(CompanyCandidate::getCandidateId, candidateId));
        if (pool == null) return new TalentPoolDtos.MembershipView(false, null, null, List.of());
        return new TalentPoolDtos.MembershipView("ACTIVE".equals(pool.getStatus()), pool.getId(), pool.getVersion(),
                "ACTIVE".equals(pool.getStatus()) ? tags(companyId, pool.getId()) : List.of());
    }

    public List<TalentPoolDtos.TagView> listTags() {
        Long companyId = companyAccess.requirePermission("application:read");
        return tagMapper.selectList(new LambdaQueryWrapper<CompanyCandidateTag>()
                        .eq(CompanyCandidateTag::getCompanyId, companyId)
                        .eq(CompanyCandidateTag::getStatus, 1)
                        .orderByAsc(CompanyCandidateTag::getName))
                .stream().map(this::toTagView).toList();
    }

    public PageResult<TalentPoolDtos.NoteView> notes(Long candidateId, Long notePageNo, Long notePageSize) {
        Long companyId = companyAccess.requirePermission("application:read");
        companyAccess.requireCandidateAccess(candidateId);
        CompanyCandidate pool = requireActivePool(companyId, candidateId);
        return notes(companyId, pool, notePageNo, notePageSize);
    }

    @Transactional
    public TalentPoolDtos.MembershipView add(Long candidateId) {
        Long companyId = companyAccess.requirePermission("application:review");
        companyAccess.requireCandidateAccess(candidateId);
        CompanyCandidate pool = candidateMapper.selectForUpdate(companyId, candidateId);
        if (pool == null) {
            pool = new CompanyCandidate();
            pool.setCompanyId(companyId);
            pool.setCandidateId(candidateId);
            pool.setStatus("ACTIVE");
            pool.setCreatedBy(currentUser.id());
            pool.setVersion(0);
            candidateMapper.insert(pool);
            auditService.success("RECRUITMENT", "TALENT_POOL_ADDED", "COMPANY_CANDIDATE", pool.getId(), companyId,
                    "加入人才库候选人 " + candidateId);
        } else if (!"ACTIVE".equals(pool.getStatus())) {
            pool.setStatus("ACTIVE");
            pool.setRemovedAt(null);
            pool.setRemovedBy(null);
            pool.setVersion(nextVersion(pool.getVersion()));
            candidateMapper.updateById(pool);
            auditService.success("RECRUITMENT", "TALENT_POOL_REACTIVATED", "COMPANY_CANDIDATE", pool.getId(), companyId,
                    "重新加入人才库候选人 " + candidateId);
        }
        return new TalentPoolDtos.MembershipView(true, pool.getId(), pool.getVersion(), tags(companyId, pool.getId()));
    }

    @Transactional
    public TalentPoolDtos.MembershipView remove(Long candidateId) {
        Long companyId = companyAccess.requirePermission("application:review");
        companyAccess.requireCandidateAccess(candidateId);
        CompanyCandidate pool = candidateMapper.selectForUpdate(companyId, candidateId);
        if (pool == null || !"ACTIVE".equals(pool.getStatus())) {
            throw BusinessException.notFound("候选人不在当前企业人才库");
        }
        pool.setStatus("REMOVED");
        pool.setRemovedAt(LocalDateTime.now());
        pool.setRemovedBy(currentUser.id());
        pool.setVersion(nextVersion(pool.getVersion()));
        candidateMapper.updateById(pool);
        auditService.success("RECRUITMENT", "TALENT_POOL_REMOVED", "COMPANY_CANDIDATE", pool.getId(), companyId,
                "移出人才库候选人 " + candidateId);
        return new TalentPoolDtos.MembershipView(false, pool.getId(), pool.getVersion(), List.of());
    }

    @Transactional
    public TalentPoolDtos.NoteView createNote(Long candidateId, TalentPoolDtos.NoteRequest request) {
        Long companyId = companyAccess.requirePermission("application:review");
        companyAccess.requireCandidateAccess(candidateId);
        CompanyCandidate pool = requireActivePool(companyId, candidateId);
        validateApplication(companyId, candidateId, request.applicationId());
        ApplicationNote note = new ApplicationNote();
        note.setCompanyId(companyId);
        note.setCompanyCandidateId(pool.getId());
        note.setCandidateId(candidateId);
        note.setApplicationId(request.applicationId());
        note.setAuthorId(currentUser.id());
        note.setUpdatedBy(currentUser.id());
        note.setContent(request.content().trim());
        note.setVersion(0);
        noteMapper.insert(note);
        auditService.success("RECRUITMENT", "TALENT_POOL_NOTE_CREATED", "APPLICATION_NOTE", note.getId(), companyId,
                "创建人才库共享备注");
        return toNoteView(note, userNames(List.of(currentUser.id())));
    }

    @Transactional
    public TalentPoolDtos.NoteView updateNote(Long candidateId, Long noteId, TalentPoolDtos.NoteUpdateRequest request) {
        Long companyId = companyAccess.requirePermission("application:review");
        companyAccess.requireCandidateAccess(candidateId);
        CompanyCandidate pool = requireActivePool(companyId, candidateId);
        ApplicationNote note = noteMapper.selectForUpdate(noteId, companyId);
        if (note == null || !Objects.equals(note.getCompanyCandidateId(), pool.getId())
                || !Objects.equals(note.getCandidateId(), candidateId)) {
            throw BusinessException.notFound("共享备注不存在");
        }
        int updated = noteMapper.update(null, new LambdaUpdateWrapper<ApplicationNote>()
                .eq(ApplicationNote::getId, noteId)
                .eq(ApplicationNote::getCompanyId, companyId)
                .eq(ApplicationNote::getVersion, request.version())
                .set(ApplicationNote::getContent, request.content().trim())
                .set(ApplicationNote::getUpdatedBy, currentUser.id())
                .set(ApplicationNote::getVersion, request.version() + 1));
        if (updated == 0) throw BusinessException.conflict("共享备注已被其他成员更新，请刷新后重试");
        note.setContent(request.content().trim());
        note.setUpdatedBy(currentUser.id());
        note.setVersion(request.version() + 1);
        note.setUpdatedAt(LocalDateTime.now());
        auditService.success("RECRUITMENT", "TALENT_POOL_NOTE_UPDATED", "APPLICATION_NOTE", noteId, companyId,
                "更新人才库共享备注");
        return toNoteView(note, userNames(List.of(note.getAuthorId(), note.getUpdatedBy())));
    }

    @Transactional
    public TalentPoolDtos.TagView createTag(TalentPoolDtos.TagRequest request) {
        Long companyId = companyAccess.requirePermission("application:review");
        String name = request.name().trim();
        CompanyCandidateTag tag = tagMapper.selectForUpdate(companyId, name);
        if (tag == null) {
            tag = new CompanyCandidateTag();
            tag.setCompanyId(companyId);
            tag.setName(name);
            tag.setColor(text(request.color()));
            tag.setStatus(1);
            tag.setCreatedBy(currentUser.id());
            tag.setVersion(0);
            tagMapper.insert(tag);
            auditService.success("RECRUITMENT", "TALENT_POOL_TAG_CREATED", "COMPANY_CANDIDATE_TAG", tag.getId(), companyId,
                    "创建人才库标签");
        } else if (!Integer.valueOf(1).equals(tag.getStatus())) {
            tag.setStatus(1);
            tag.setColor(text(request.color()));
            tag.setVersion(nextVersion(tag.getVersion()));
            tagMapper.updateById(tag);
            auditService.success("RECRUITMENT", "TALENT_POOL_TAG_REACTIVATED", "COMPANY_CANDIDATE_TAG", tag.getId(), companyId,
                    "恢复人才库标签");
        }
        return toTagView(tag);
    }

    @Transactional
    public List<TalentPoolDtos.TagView> addTag(Long candidateId, Long tagId) {
        Long companyId = companyAccess.requirePermission("application:review");
        CompanyCandidate pool = requireActivePool(companyId, candidateId);
        CompanyCandidateTag tag = tagMapper.selectOne(new LambdaQueryWrapper<CompanyCandidateTag>()
                .eq(CompanyCandidateTag::getId, tagId).eq(CompanyCandidateTag::getCompanyId, companyId)
                .eq(CompanyCandidateTag::getStatus, 1));
        if (tag == null) throw BusinessException.notFound("人才库标签不存在");
        CompanyCandidateTagRelation relation = relationMapper.selectForUpdate(companyId, pool.getId(), tagId);
        if (relation == null) {
            relation = new CompanyCandidateTagRelation();
            relation.setCompanyId(companyId);
            relation.setCompanyCandidateId(pool.getId());
            relation.setTagId(tagId);
            relation.setStatus(1);
            relation.setCreatedBy(currentUser.id());
            relation.setVersion(0);
            relationMapper.insert(relation);
            auditService.success("RECRUITMENT", "TALENT_POOL_TAG_ASSIGNED", "COMPANY_CANDIDATE_TAG_RELATION", relation.getId(), companyId,
                    "为人才库候选人添加标签");
        } else if (!Integer.valueOf(1).equals(relation.getStatus())) {
            relation.setStatus(1);
            relation.setVersion(nextVersion(relation.getVersion()));
            relationMapper.updateById(relation);
            auditService.success("RECRUITMENT", "TALENT_POOL_TAG_ASSIGNED", "COMPANY_CANDIDATE_TAG_RELATION", relation.getId(), companyId,
                    "恢复人才库候选人标签");
        }
        return tags(companyId, pool.getId());
    }

    @Transactional
    public List<TalentPoolDtos.TagView> removeTag(Long candidateId, Long tagId) {
        Long companyId = companyAccess.requirePermission("application:review");
        CompanyCandidate pool = requireActivePool(companyId, candidateId);
        CompanyCandidateTagRelation relation = relationMapper.selectForUpdate(companyId, pool.getId(), tagId);
        if (relation == null || !Integer.valueOf(1).equals(relation.getStatus())) return tags(companyId, pool.getId());
        relation.setStatus(0);
        relation.setVersion(nextVersion(relation.getVersion()));
        relationMapper.updateById(relation);
        auditService.success("RECRUITMENT", "TALENT_POOL_TAG_REMOVED", "COMPANY_CANDIDATE_TAG_RELATION", relation.getId(), companyId,
                "移除人才库候选人标签");
        return tags(companyId, pool.getId());
    }

    @Transactional
    public TalentPoolDtos.MembershipView markContacted(Long candidateId) {
        Long companyId = companyAccess.requirePermission("application:review");
        companyAccess.requireCandidateAccess(candidateId);
        CompanyCandidate pool = requireActivePool(companyId, candidateId);
        pool.setLastContactedAt(LocalDateTime.now());
        pool.setVersion(nextVersion(pool.getVersion()));
        candidateMapper.updateById(pool);
        auditService.success("RECRUITMENT", "TALENT_POOL_CONTACTED", "COMPANY_CANDIDATE", pool.getId(), companyId,
                "更新人才库最近联系时间");
        return new TalentPoolDtos.MembershipView(true, pool.getId(), pool.getVersion(), tags(companyId, pool.getId()));
    }

    private CompanyCandidate requireActivePool(Long companyId, Long candidateId) {
        CompanyCandidate pool = candidateMapper.selectOne(new LambdaQueryWrapper<CompanyCandidate>()
                .eq(CompanyCandidate::getCompanyId, companyId)
                .eq(CompanyCandidate::getCandidateId, candidateId)
                .eq(CompanyCandidate::getStatus, "ACTIVE"));
        if (pool == null) throw BusinessException.notFound("候选人不在当前企业人才库");
        return pool;
    }

    private CompanyTalentPoolRow selectSingle(Long companyId, Long candidateId) {
        List<CompanyTalentPoolRow> rows = talentPoolMapper.selectPage(companyId, currentUser.id(),
                companyAccess.isRestrictedInterviewer(), candidateId, null, null, null, null, null, null,
                "UPDATED", 0, 1);
        if (rows.isEmpty()) throw BusinessException.notFound("人才库候选人不存在");
        return rows.get(0);
    }

    private PageResult<TalentPoolDtos.NoteView> notes(Long companyId, CompanyCandidate pool,
                                                      Long notePageNo, Long notePageSize) {
        long pageNo = pageNo(notePageNo);
        long pageSize = Math.min(50, pageSize(notePageSize));
        Page<ApplicationNote> page = noteMapper.selectPage(new Page<>(pageNo, pageSize),
                new LambdaQueryWrapper<ApplicationNote>()
                        .eq(ApplicationNote::getCompanyId, companyId)
                        .eq(ApplicationNote::getCompanyCandidateId, pool.getId())
                        .orderByDesc(ApplicationNote::getUpdatedAt).orderByDesc(ApplicationNote::getId));
        Map<Long, String> names = userNames(page.getRecords().stream()
                .flatMap(note -> java.util.stream.Stream.of(note.getAuthorId(), note.getUpdatedBy()))
                .filter(Objects::nonNull).toList());
        return PageResult.of(page.getRecords().stream().map(note -> toNoteView(note, names)).toList(),
                page.getTotal(), pageNo, pageSize);
    }

    private List<TalentPoolDtos.HistoricalApplication> applications(Long companyId, Long candidateId) {
        List<JobApplication> applications = applicationMapper.selectList(new LambdaQueryWrapper<JobApplication>()
                .eq(JobApplication::getCompanyId, companyId)
                .eq(JobApplication::getCandidateId, candidateId)
                .orderByDesc(JobApplication::getSubmittedAt).orderByDesc(JobApplication::getId));
        if (applications.isEmpty()) return List.of();
        Map<Long, String> positionNames = positionMapper.selectBatchIds(applications.stream()
                        .map(JobApplication::getPositionId).filter(Objects::nonNull).toList()).stream()
                .collect(Collectors.toMap(JobPosition::getId, JobPosition::getName, (left, right) -> left));
        Map<Long, String> interviewStatuses = interviewStatuses(applications);
        return applications.stream().map(application -> new TalentPoolDtos.HistoricalApplication(
                application.getId(), application.getApplicationNo(), application.getPositionId(),
                positionNames.getOrDefault(application.getPositionId(), "岗位"), application.getStatus(),
                application.getMatchScore(), interviewStatuses.getOrDefault(application.getId(), "NONE"),
                application.getSubmittedAt(), application.getUpdatedAt())).toList();
    }

    private Map<Long, String> interviewStatuses(List<JobApplication> applications) {
        Map<Long, String> statuses = new HashMap<>();
        List<Long> interviewIds = applications.stream().map(JobApplication::getInterviewId)
                .filter(Objects::nonNull).toList();
        if (!interviewIds.isEmpty()) {
            Map<Long, Long> appByInterview = applications.stream().filter(item -> item.getInterviewId() != null)
                    .collect(Collectors.toMap(JobApplication::getInterviewId, JobApplication::getId, (left, right) -> left));
            interviewMapper.selectBatchIds(interviewIds).forEach(interview ->
                    statuses.put(appByInterview.get(interview.getId()), aiInterviewStatus(interview.getStatus())));
        }
        List<Long> applicationIds = applications.stream().map(JobApplication::getId).toList();
        offlineInterviewMapper.selectList(new LambdaQueryWrapper<OfflineInterview>()
                        .in(OfflineInterview::getApplicationId, applicationIds))
                .forEach(interview -> statuses.put(interview.getApplicationId(), "OFFLINE_" + interview.getStatus()));
        return statuses;
    }

    private List<TalentPoolDtos.TagView> tags(Long companyId, Long poolId) {
        List<CompanyCandidateTagRelation> relations = relationMapper.selectList(new LambdaQueryWrapper<CompanyCandidateTagRelation>()
                .eq(CompanyCandidateTagRelation::getCompanyId, companyId)
                .eq(CompanyCandidateTagRelation::getCompanyCandidateId, poolId)
                .eq(CompanyCandidateTagRelation::getStatus, 1));
        if (relations.isEmpty()) return List.of();
        return tagMapper.selectBatchIds(relations.stream().map(CompanyCandidateTagRelation::getTagId).toList()).stream()
                .filter(tag -> Objects.equals(tag.getCompanyId(), companyId) && Integer.valueOf(1).equals(tag.getStatus()))
                .sorted(java.util.Comparator.comparing(CompanyCandidateTag::getName))
                .map(this::toTagView).toList();
    }

    private Map<Long, String> userNames(List<Long> userIds) {
        List<Long> ids = userIds.stream().filter(Objects::nonNull).distinct().toList();
        if (ids.isEmpty()) return Map.of();
        return userMapper.selectBatchIds(ids).stream().collect(Collectors.toMap(UserAccount::getId,
                user -> StringUtils.hasText(user.getRealName()) ? user.getRealName() : user.getUsername(),
                (left, right) -> left));
    }

    private TalentPoolDtos.CandidateView toCandidateView(CompanyTalentPoolRow row) {
        return new TalentPoolDtos.CandidateView(row.poolId(), row.candidateId(), row.candidateName(), row.email(), row.phone(),
                row.candidateStatus(), row.poolStatus(), row.lastContactedAt(), row.addedAt(), row.updatedAt(),
                nullSafe(row.noteCount()), nullSafe(row.applicationCount()), row.lastApplicationAt(), row.lastActivityAt(),
                parseTags(row.tagSummary()));
    }

    private TalentPoolDtos.NoteView toNoteView(ApplicationNote note, Map<Long, String> names) {
        return new TalentPoolDtos.NoteView(note.getId(), note.getApplicationId(), note.getContent(), note.getAuthorId(),
                names.getOrDefault(note.getAuthorId(), "企业成员"), note.getUpdatedBy(),
                names.getOrDefault(note.getUpdatedBy(), "企业成员"), note.getVersion(), note.getCreatedAt(), note.getUpdatedAt());
    }

    private TalentPoolDtos.TagView toTagView(CompanyCandidateTag tag) {
        return new TalentPoolDtos.TagView(tag.getId(), tag.getName(), tag.getColor());
    }

    private List<TalentPoolDtos.TagView> parseTags(String summary) {
        if (!StringUtils.hasText(summary)) return List.of();
        return Arrays.stream(summary.split("\\|\\|"))
                .map(item -> item.split("::", 2))
                .filter(item -> item.length == 2)
                .map(item -> {
                    try {
                        return new TalentPoolDtos.TagView(Long.valueOf(item[0]), item[1], null);
                    } catch (NumberFormatException ignored) {
                        return null;
                    }
                }).filter(Objects::nonNull).toList();
    }

    private void validateApplication(Long companyId, Long candidateId, Long applicationId) {
        if (applicationId == null) return;
        boolean exists = applicationMapper.exists(new LambdaQueryWrapper<JobApplication>()
                .eq(JobApplication::getId, applicationId)
                .eq(JobApplication::getCompanyId, companyId)
                .eq(JobApplication::getCandidateId, candidateId));
        if (!exists) throw BusinessException.notFound("备注关联的申请不存在");
    }

    private static String aiInterviewStatus(Integer status) {
        if (status == null) return "AI_SCHEDULED";
        return switch (status) {
            case 3 -> "AI_CANCELLED";
            case 2, 4, 6 -> "AI_COMPLETED";
            case 1, 5 -> "AI_RUNNING";
            case 7 -> "AI_FAILED";
            default -> "AI_SCHEDULED";
        };
    }

    private static long pageNo(Long value) { return value == null ? 1 : Math.max(1, value); }
    private static long pageSize(Long value) { return value == null ? 20 : Math.min(100, Math.max(1, value)); }
    private static int nextVersion(Integer version) { return version == null ? 1 : version + 1; }
    private static long nullSafe(Long value) { return value == null ? 0 : value; }
    private static String text(String value) { return StringUtils.hasText(value) ? value.trim() : null; }
    private static String sort(String value) {
        return value != null && Set.of("LAST_CONTACTED", "NAME", "APPLICATIONS").contains(value)
                ? value : "UPDATED";
    }
}
