package com.tyut.aiinterview.admin;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.tyut.aiinterview.common.BusinessException;
import com.tyut.aiinterview.common.PageResult;
import com.tyut.aiinterview.domain.Company;
import com.tyut.aiinterview.domain.JobPosition;
import com.tyut.aiinterview.domain.RecruitmentRequisition;
import com.tyut.aiinterview.domain.RecruitmentRequisitionEvent;
import com.tyut.aiinterview.domain.UserAccount;
import com.tyut.aiinterview.mapper.CompanyMapper;
import com.tyut.aiinterview.mapper.JobPositionMapper;
import com.tyut.aiinterview.mapper.RecruitmentRequisitionEventMapper;
import com.tyut.aiinterview.mapper.RecruitmentRequisitionMapper;
import com.tyut.aiinterview.mapper.UserMapper;
import com.tyut.aiinterview.recruitment.RecruitmentAuditService;
import com.tyut.aiinterview.recruitment.RecruitmentService;
import com.tyut.aiinterview.security.CurrentUser;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
public class AdminRecruitmentRequisitionService {
    private static final Set<String> APPROVAL_STATUSES = Set.of("DRAFT", "PENDING_APPROVAL", "APPROVED", "REJECTED");

    private final RecruitmentRequisitionMapper requisitionMapper;
    private final RecruitmentRequisitionEventMapper eventMapper;
    private final JobPositionMapper positionMapper;
    private final CompanyMapper companyMapper;
    private final UserMapper userMapper;
    private final RecruitmentService recruitmentService;
    private final RecruitmentAuditService auditService;
    private final CurrentUser currentUser;

    public AdminRecruitmentRequisitionService(RecruitmentRequisitionMapper requisitionMapper,
                                              RecruitmentRequisitionEventMapper eventMapper,
                                              JobPositionMapper positionMapper, CompanyMapper companyMapper,
                                              UserMapper userMapper, RecruitmentService recruitmentService,
                                              RecruitmentAuditService auditService, CurrentUser currentUser) {
        this.requisitionMapper = requisitionMapper;
        this.eventMapper = eventMapper;
        this.positionMapper = positionMapper;
        this.companyMapper = companyMapper;
        this.userMapper = userMapper;
        this.recruitmentService = recruitmentService;
        this.auditService = auditService;
        this.currentUser = currentUser;
    }

    public PageResult<AdminRecruitmentDtos.RequisitionView> page(AdminRecruitmentDtos.RequisitionQuery query) {
        requireAdmin();
        long pageNo = query.pageNo() == null ? 1 : Math.max(1, query.pageNo());
        long pageSize = query.pageSize() == null ? 20 : Math.min(100, Math.max(1, query.pageSize()));
        LambdaQueryWrapper<RecruitmentRequisition> wrapper = new LambdaQueryWrapper<>();
        if (query.companyId() != null) wrapper.eq(RecruitmentRequisition::getCompanyId, query.companyId());
        if (StringUtils.hasText(query.approvalStatus())) {
            String status = normalize(query.approvalStatus());
            if (!APPROVAL_STATUSES.contains(status)) throw BusinessException.badRequest("审批状态不合法");
            wrapper.eq(RecruitmentRequisition::getApprovalStatus, status);
        }
        if (query.frozen() != null) wrapper.eq(RecruitmentRequisition::getFrozen, Boolean.TRUE.equals(query.frozen()) ? 1 : 0);
        if (StringUtils.hasText(query.keyword())) {
            String keyword = query.keyword().trim();
            wrapper.and(item -> item.like(RecruitmentRequisition::getRequisitionNo, keyword)
                    .or().like(RecruitmentRequisition::getHeadcountCode, keyword)
                    .or().like(RecruitmentRequisition::getCostCenterCode, keyword)
                    .or().apply("EXISTS (SELECT 1 FROM company c WHERE c.id = recruitment_requisition.company_id AND c.name LIKE CONCAT('%', {0}, '%'))", keyword)
                    .or().apply("EXISTS (SELECT 1 FROM job_position p WHERE p.id = recruitment_requisition.position_id AND p.name LIKE CONCAT('%', {0}, '%'))", keyword));
        }
        wrapper.orderByDesc(RecruitmentRequisition::getSubmittedAt)
                .orderByDesc(RecruitmentRequisition::getUpdatedAt)
                .orderByDesc(RecruitmentRequisition::getId);
        Page<RecruitmentRequisition> result = requisitionMapper.selectPage(new Page<>(pageNo, pageSize), wrapper);
        return PageResult.of(result.getRecords().stream().map(this::toView).toList(), result.getTotal(), pageNo, pageSize);
    }

    public AdminRecruitmentDtos.RequisitionDetail detail(Long id) {
        requireAdmin();
        RecruitmentRequisition requisition = requireRequisition(id);
        List<AdminRecruitmentDtos.RequisitionEvent> history = eventMapper.selectList(
                        new LambdaQueryWrapper<RecruitmentRequisitionEvent>()
                                .eq(RecruitmentRequisitionEvent::getRequisitionId, id)
                                .orderByAsc(RecruitmentRequisitionEvent::getCreatedAt)
                                .orderByAsc(RecruitmentRequisitionEvent::getId))
                .stream().map(this::toEvent).toList();
        return new AdminRecruitmentDtos.RequisitionDetail(toView(requisition), history);
    }

    @Transactional
    public AdminRecruitmentDtos.RequisitionDetail approve(Long id, AdminRecruitmentDtos.ApprovalRequest request) {
        requireAdmin();
        RecruitmentRequisition requisition = requirePending(id);
        if (request.approvedHeadcount() > requisition.getRequestedHeadcount()) {
            throw BusinessException.badRequest("批准人数不能超过申请人数");
        }
        JobPosition position = requirePosition(requisition);
        recruitmentService.validatePositionForPublication(position);
        LocalDateTime now = LocalDateTime.now();
        int updated = requisitionMapper.update(null, new LambdaUpdateWrapper<RecruitmentRequisition>()
                .eq(RecruitmentRequisition::getId, id)
                .eq(RecruitmentRequisition::getApprovalStatus, "PENDING_APPROVAL")
                .eq(RecruitmentRequisition::getFrozen, 0)
                .set(RecruitmentRequisition::getApprovalStatus, "APPROVED")
                .set(RecruitmentRequisition::getApprovedHeadcount, request.approvedHeadcount())
                .set(RecruitmentRequisition::getReviewedBy, currentUser.id())
                .set(RecruitmentRequisition::getReviewedAt, now)
                .set(RecruitmentRequisition::getReviewNote, trimToNull(request.note()))
                .setSql("version = version + 1"));
        if (updated != 1) throw BusinessException.conflict("招聘需求状态已变化，请刷新后重试");
        position.setRecruitmentStatus("PUBLISHED");
        position.setPublishedAt(now);
        positionMapper.updateById(position);
        recordEvent(id, "APPROVED_AND_PUBLISHED", "PENDING_APPROVAL", "APPROVED",
                StringUtils.hasText(request.note()) ? request.note().trim() : "超级管理员批准并发布岗位");
        auditService.recordPositionOperation("REQUISITION_APPROVED", requisition.getCompanyId(), position.getId(),
                position.getPositionCode(), "requisitionNo=" + requisition.getRequisitionNo()
                        + "; approvedHeadcount=" + request.approvedHeadcount());
        return detail(id);
    }

    @Transactional
    public AdminRecruitmentDtos.RequisitionDetail reject(Long id, AdminRecruitmentDtos.DecisionRequest request) {
        requireAdmin();
        RecruitmentRequisition requisition = requirePending(id);
        LocalDateTime now = LocalDateTime.now();
        int updated = requisitionMapper.update(null, new LambdaUpdateWrapper<RecruitmentRequisition>()
                .eq(RecruitmentRequisition::getId, id)
                .eq(RecruitmentRequisition::getApprovalStatus, "PENDING_APPROVAL")
                .eq(RecruitmentRequisition::getFrozen, 0)
                .set(RecruitmentRequisition::getApprovalStatus, "REJECTED")
                .set(RecruitmentRequisition::getApprovedHeadcount, null)
                .set(RecruitmentRequisition::getReviewedBy, currentUser.id())
                .set(RecruitmentRequisition::getReviewedAt, now)
                .set(RecruitmentRequisition::getReviewNote, request.note().trim())
                .setSql("version = version + 1"));
        if (updated != 1) throw BusinessException.conflict("招聘需求状态已变化，请刷新后重试");
        JobPosition position = requirePosition(requisition);
        position.setRecruitmentStatus("DRAFT");
        position.setPublishedAt(null);
        positionMapper.updateById(position);
        recordEvent(id, "REJECTED", "PENDING_APPROVAL", "REJECTED", request.note().trim());
        auditService.recordPositionOperation("REQUISITION_REJECTED", requisition.getCompanyId(), position.getId(),
                position.getPositionCode(), "requisitionNo=" + requisition.getRequisitionNo() + "; " + request.note().trim());
        return detail(id);
    }

    @Transactional
    public AdminRecruitmentDtos.RequisitionDetail freeze(Long id, AdminRecruitmentDtos.DecisionRequest request) {
        requireAdmin();
        RecruitmentRequisition requisition = requireRequisition(id);
        if (!"APPROVED".equals(normalize(requisition.getApprovalStatus()))) {
            throw BusinessException.conflict("只有已批准的招聘需求可以冻结");
        }
        if (Integer.valueOf(1).equals(requisition.getFrozen())) return detail(id);
        LocalDateTime now = LocalDateTime.now();
        int updated = requisitionMapper.update(null, new LambdaUpdateWrapper<RecruitmentRequisition>()
                .eq(RecruitmentRequisition::getId, id)
                .eq(RecruitmentRequisition::getApprovalStatus, "APPROVED")
                .eq(RecruitmentRequisition::getFrozen, 0)
                .set(RecruitmentRequisition::getFrozen, 1)
                .set(RecruitmentRequisition::getFrozenBy, currentUser.id())
                .set(RecruitmentRequisition::getFrozenAt, now)
                .set(RecruitmentRequisition::getFreezeReason, request.note().trim())
                .setSql("version = version + 1"));
        if (updated != 1) throw BusinessException.conflict("招聘需求状态已变化，请刷新后重试");
        JobPosition position = requirePosition(requisition);
        recordEvent(id, "FROZEN", "APPROVED", "APPROVED", request.note().trim());
        auditService.recordPositionOperation("REQUISITION_FROZEN", requisition.getCompanyId(), position.getId(),
                position.getPositionCode(), "requisitionNo=" + requisition.getRequisitionNo() + "; " + request.note().trim());
        return detail(id);
    }

    @Transactional
    public AdminRecruitmentDtos.RequisitionDetail unfreeze(Long id, AdminRecruitmentDtos.DecisionRequest request) {
        requireAdmin();
        RecruitmentRequisition requisition = requireRequisition(id);
        if (!Integer.valueOf(1).equals(requisition.getFrozen())) return detail(id);
        if (!"APPROVED".equals(normalize(requisition.getApprovalStatus()))) {
            throw BusinessException.conflict("只有已批准的招聘需求可以解除冻结");
        }
        int updated = requisitionMapper.update(null, new LambdaUpdateWrapper<RecruitmentRequisition>()
                .eq(RecruitmentRequisition::getId, id)
                .eq(RecruitmentRequisition::getApprovalStatus, "APPROVED")
                .eq(RecruitmentRequisition::getFrozen, 1)
                .set(RecruitmentRequisition::getFrozen, 0)
                .set(RecruitmentRequisition::getFrozenBy, null)
                .set(RecruitmentRequisition::getFrozenAt, null)
                .set(RecruitmentRequisition::getFreezeReason, null)
                .setSql("version = version + 1"));
        if (updated != 1) throw BusinessException.conflict("招聘需求状态已变化，请刷新后重试");
        JobPosition position = requirePosition(requisition);
        recordEvent(id, "UNFROZEN", "APPROVED", "APPROVED", request.note().trim());
        auditService.recordPositionOperation("REQUISITION_UNFROZEN", requisition.getCompanyId(), position.getId(),
                position.getPositionCode(), "requisitionNo=" + requisition.getRequisitionNo() + "; " + request.note().trim());
        return detail(id);
    }

    private RecruitmentRequisition requirePending(Long id) {
        RecruitmentRequisition requisition = requireRequisition(id);
        if (!"PENDING_APPROVAL".equals(normalize(requisition.getApprovalStatus()))
                || Integer.valueOf(1).equals(requisition.getFrozen())) {
            throw BusinessException.conflict("该招聘需求已不在待审核状态");
        }
        return requisition;
    }

    private RecruitmentRequisition requireRequisition(Long id) {
        RecruitmentRequisition requisition = requisitionMapper.selectById(id);
        if (requisition == null) throw BusinessException.notFound("招聘需求不存在");
        return requisition;
    }

    private JobPosition requirePosition(RecruitmentRequisition requisition) {
        JobPosition position = positionMapper.selectById(requisition.getPositionId());
        if (position == null || !requisition.getCompanyId().equals(position.getCompanyId())) {
            throw BusinessException.notFound("招聘需求关联岗位不存在");
        }
        return position;
    }

    private AdminRecruitmentDtos.RequisitionView toView(RecruitmentRequisition item) {
        Company company = companyMapper.selectById(item.getCompanyId());
        JobPosition position = positionMapper.selectById(item.getPositionId());
        AdminRecruitmentDtos.Ref companyRef = new AdminRecruitmentDtos.Ref(item.getCompanyId(),
                company == null ? null : company.getCompanyCode(), company == null ? "企业已失效" : company.getName(),
                company == null ? null : company.getCity());
        AdminRecruitmentDtos.Ref positionRef = new AdminRecruitmentDtos.Ref(item.getPositionId(),
                position == null ? null : position.getPositionCode(), position == null ? "岗位已失效" : position.getName(),
                position == null ? null : position.getDepartment());
        return new AdminRecruitmentDtos.RequisitionView(item.getId(), item.getRequisitionNo(), companyRef, positionRef,
                item.getHeadcountCode(), item.getRequestedHeadcount(), item.getApprovedHeadcount(), item.getCostCenterCode(),
                item.getCostCenterName(), item.getBudgetAmount(), item.getBudgetCurrency(), item.getBusinessJustification(),
                item.getApprovalStatus(), item.getSubmittedBy(), item.getSubmittedAt(), item.getReviewedBy(), item.getReviewedAt(),
                item.getReviewNote(), Integer.valueOf(1).equals(item.getFrozen()), item.getFrozenBy(), item.getFrozenAt(),
                item.getFreezeReason(), item.getUpdatedAt());
    }

    private AdminRecruitmentDtos.RequisitionEvent toEvent(RecruitmentRequisitionEvent item) {
        UserAccount operator = item.getOperatorId() == null ? null : userMapper.selectById(item.getOperatorId());
        String operatorName = operator == null ? "系统" : StringUtils.hasText(operator.getRealName())
                ? operator.getRealName() : operator.getUsername();
        return new AdminRecruitmentDtos.RequisitionEvent(item.getId(), item.getEventType(), item.getFromStatus(),
                item.getToStatus(), item.getOperatorId(), operatorName, item.getNote(), item.getCreatedAt());
    }

    private void recordEvent(Long requisitionId, String type, String fromStatus, String toStatus, String note) {
        RecruitmentRequisitionEvent event = new RecruitmentRequisitionEvent();
        event.setRequisitionId(requisitionId);
        event.setEventType(type);
        event.setFromStatus(fromStatus);
        event.setToStatus(toStatus);
        event.setOperatorId(currentUser.id());
        event.setNote(note);
        event.setCreatedAt(LocalDateTime.now());
        eventMapper.insert(event);
    }

    private void requireAdmin() {
        if (!currentUser.hasRole("ADMIN")) throw BusinessException.forbidden("仅超级管理员可以审核招聘需求");
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
    }

    private static String trimToNull(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }
}
