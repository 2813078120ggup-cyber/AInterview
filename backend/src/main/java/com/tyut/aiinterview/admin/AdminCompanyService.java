package com.tyut.aiinterview.admin;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.tyut.aiinterview.common.BusinessException;
import com.tyut.aiinterview.common.PageResult;
import com.tyut.aiinterview.domain.Company;
import com.tyut.aiinterview.domain.Role;
import com.tyut.aiinterview.domain.UserAccount;
import com.tyut.aiinterview.domain.UserRole;
import com.tyut.aiinterview.mapper.AdminCompanyMapper;
import com.tyut.aiinterview.mapper.AdminCompanyRow;
import com.tyut.aiinterview.mapper.CompanyMapper;
import com.tyut.aiinterview.mapper.RoleMapper;
import com.tyut.aiinterview.mapper.UserMapper;
import com.tyut.aiinterview.mapper.UserRoleMapper;
import com.tyut.aiinterview.observability.OperationAuditService;
import com.tyut.aiinterview.security.CurrentUser;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
public class AdminCompanyService {
    private static final Set<String> COMPANY_ROLE_CODES = Set.of(
            "COMPANY_ADMIN", "COMPANY_RECRUITER", "COMPANY_INTERVIEWER");

    private final AdminCompanyMapper adminCompanyMapper;
    private final CompanyMapper companyMapper;
    private final UserMapper userMapper;
    private final UserRoleMapper userRoleMapper;
    private final RoleMapper roleMapper;
    private final PasswordEncoder passwordEncoder;
    private final CurrentUser currentUser;
    private final OperationAuditService auditService;

    public AdminCompanyService(AdminCompanyMapper adminCompanyMapper, CompanyMapper companyMapper,
                               UserMapper userMapper, UserRoleMapper userRoleMapper, RoleMapper roleMapper,
                               PasswordEncoder passwordEncoder, CurrentUser currentUser,
                               OperationAuditService auditService) {
        this.adminCompanyMapper = adminCompanyMapper;
        this.companyMapper = companyMapper;
        this.userMapper = userMapper;
        this.userRoleMapper = userRoleMapper;
        this.roleMapper = roleMapper;
        this.passwordEncoder = passwordEncoder;
        this.currentUser = currentUser;
        this.auditService = auditService;
    }

    public PageResult<AdminCompanyDtos.CompanyView> page(AdminCompanyDtos.Query query) {
        long pageNo = safePageNo(query == null ? null : query.pageNo());
        long pageSize = safePageSize(query == null ? null : query.pageSize());
        String keyword = normalizeOptional(query == null ? null : query.keyword());
        Integer status = query == null ? null : query.status();
        if (status != null && status != 0 && status != 1) throw BusinessException.badRequest("企业状态不合法");
        List<AdminCompanyDtos.CompanyView> records = adminCompanyMapper
                .selectPage(keyword, status, (pageNo - 1) * pageSize, pageSize)
                .stream().map(this::toView).toList();
        return PageResult.of(records, adminCompanyMapper.count(keyword, status), pageNo, pageSize);
    }

    public AdminCompanyDtos.CompanyDetailView detail(Long companyId) {
        AdminCompanyRow row = requireRow(companyId);
        return new AdminCompanyDtos.CompanyDetailView(toView(row), new AdminCompanyDtos.Overview(
                value(row.getRecruitingPositionCount()), value(row.getApplicationCount()), value(row.getMemberCount()),
                adminCompanyMapper.countInProgressInterviews(companyId)));
    }

    @Transactional
    public AdminCompanyDtos.CompanyDetailView create(AdminCompanyDtos.CreateRequest request) {
        String code = request.companyCode().trim().toUpperCase(Locale.ROOT);
        if (companyMapper.selectOne(new LambdaQueryWrapper<Company>().eq(Company::getCompanyCode, code)) != null) {
            throw BusinessException.conflict("企业编码已存在");
        }
        Company company = new Company();
        company.setCompanyCode(code);
        apply(company, request);
        company.setStatus(1);
        company.setCreatedBy(currentUser.id());
        companyMapper.insert(company);
        audit("COMPANY_CREATED", company.getId(), company.getId(), "创建企业 " + company.getName());
        return detail(company.getId());
    }

    @Transactional
    public AdminCompanyDtos.CompanyDetailView update(Long companyId, AdminCompanyDtos.UpdateRequest request) {
        Company company = requireCompany(companyId);
        apply(company, request);
        companyMapper.updateById(company);
        audit("COMPANY_UPDATED", companyId, companyId, "更新企业资料");
        return detail(companyId);
    }

    @Transactional
    public AdminCompanyDtos.CompanyDetailView updateStatus(Long companyId, AdminCompanyDtos.StatusRequest request) {
        Company company = requireCompany(companyId);
        if (Integer.valueOf(request.status()).equals(company.getStatus())) return detail(companyId);
        if (request.status() == 0) {
            long positions = adminCompanyMapper.countPublishedPositions(companyId);
            long interviews = adminCompanyMapper.countInProgressInterviews(companyId);
            if ((positions > 0 || interviews > 0) && !Boolean.TRUE.equals(request.confirm())) {
                auditService.failure("ADMIN_COMPANY", "COMPANY_DISABLE_BLOCKED", "COMPANY", companyId, companyId,
                        "停用前检查阻止：" + positions + " 个招聘中岗位、" + interviews + " 场进行中面试");
                throw BusinessException.conflict("停用前请确认：当前有 " + positions + " 个招聘中岗位、" + interviews + " 场进行中面试");
            }
        }
        company.setStatus(request.status());
        companyMapper.updateById(company);
        if (request.status() == 0) userMapper.bumpSecurityVersionForCompany(companyId);
        audit(request.status() == 1 ? "COMPANY_ENABLED" : "COMPANY_DISABLED", companyId, companyId,
                request.status() == 1 ? "启用企业" : "停用企业，历史招聘数据保留");
        return detail(companyId);
    }

    public List<AdminCompanyDtos.MemberView> members(Long companyId) {
        requireCompany(companyId);
        return userMapper.selectList(new LambdaQueryWrapper<UserAccount>()
                        .eq(UserAccount::getCompanyId, companyId)
                        .orderByDesc(UserAccount::getStatus)
                        .orderByAsc(UserAccount::getRealName)
                        .orderByAsc(UserAccount::getId))
                .stream().map(this::toMemberView).toList();
    }

    @Transactional
    public AdminCompanyDtos.MemberView createMember(Long companyId, AdminCompanyDtos.MemberCreateRequest request) {
        requireCompany(companyId);
        String username = request.username().trim();
        if (userMapper.exists(new LambdaQueryWrapper<UserAccount>().eq(UserAccount::getUsername, username))) {
            throw BusinessException.conflict("用户名已存在");
        }
        RoleSelection selection = resolveRoles(request.roleCodes());
        UserAccount member = new UserAccount();
        member.setUsername(username);
        member.setPasswordHash(passwordEncoder.encode(request.password()));
        member.setRealName(request.realName().trim());
        member.setEmail(normalizeOptional(request.email()));
        member.setPhone(normalizeOptional(request.phone()));
        member.setCompanyId(companyId);
        member.setStatus(1);
        userMapper.insert(member);
        for (Role role : selection.roles()) {
            UserRole relation = new UserRole();
            relation.setUserId(member.getId());
            relation.setRoleId(role.getId());
            relation.setAssignedBy(currentUser.id());
            relation.setAssignedAt(LocalDateTime.now());
            userRoleMapper.insert(relation);
        }
        audit(selection.roleCodes().contains("COMPANY_ADMIN") ? "COMPANY_ADMIN_ASSIGNED" : "COMPANY_MEMBER_CREATED",
                member.getId(), companyId, "为企业创建成员并分配 " + String.join("、", selection.roleCodes()));
        return toMemberView(member);
    }

    private AdminCompanyRow requireRow(Long companyId) {
        AdminCompanyRow row = adminCompanyMapper.selectById(companyId);
        if (row == null) throw BusinessException.notFound("企业不存在");
        return row;
    }

    private Company requireCompany(Long companyId) {
        Company company = companyId == null ? null : companyMapper.selectById(companyId);
        if (company == null) throw BusinessException.notFound("企业不存在");
        return company;
    }

    private void apply(Company company, AdminCompanyDtos.CreateRequest request) {
        company.setName(request.name().trim());
        company.setShortName(normalizeOptional(request.shortName()));
        company.setLogoUrl(normalizeOptional(request.logoUrl()));
        company.setIndustry(normalizeOptional(request.industry()));
        company.setCompanySize(normalizeOptional(request.companySize()));
        company.setCity(normalizeOptional(request.city()));
        company.setDescription(normalizeOptional(request.description()));
        company.setWebsiteUrl(normalizeOptional(request.websiteUrl()));
        company.setRecruitmentContactName(normalizeOptional(request.recruitmentContactName()));
        company.setRecruitmentContactEmail(normalizeOptional(request.recruitmentContactEmail()));
        company.setRecruitmentContactPhone(normalizeOptional(request.recruitmentContactPhone()));
    }

    private void apply(Company company, AdminCompanyDtos.UpdateRequest request) {
        company.setName(request.name().trim());
        company.setShortName(normalizeOptional(request.shortName()));
        company.setLogoUrl(normalizeOptional(request.logoUrl()));
        company.setIndustry(normalizeOptional(request.industry()));
        company.setCompanySize(normalizeOptional(request.companySize()));
        company.setCity(normalizeOptional(request.city()));
        company.setDescription(normalizeOptional(request.description()));
        company.setWebsiteUrl(normalizeOptional(request.websiteUrl()));
        company.setRecruitmentContactName(normalizeOptional(request.recruitmentContactName()));
        company.setRecruitmentContactEmail(normalizeOptional(request.recruitmentContactEmail()));
        company.setRecruitmentContactPhone(normalizeOptional(request.recruitmentContactPhone()));
    }

    private RoleSelection resolveRoles(List<String> roleCodes) {
        List<String> normalized = roleCodes.stream().map(this::normalizeRole).distinct().toList();
        if (normalized.stream().anyMatch(code -> !COMPANY_ROLE_CODES.contains(code))) {
            throw BusinessException.badRequest("只能分配企业团队角色");
        }
        List<Role> roles = roleMapper.selectList(new LambdaQueryWrapper<Role>()
                .in(Role::getRoleCode, normalized).eq(Role::getStatus, 1));
        if (roles.size() != normalized.size()) throw BusinessException.badRequest("角色不存在或已停用");
        Map<String, Role> byCode = roles.stream().collect(Collectors.toMap(Role::getRoleCode, Function.identity()));
        return new RoleSelection(normalized.stream().map(byCode::get).toList(), normalized);
    }

    private AdminCompanyDtos.CompanyView toView(AdminCompanyRow row) {
        return new AdminCompanyDtos.CompanyView(row.getId(), row.getCompanyCode(), row.getName(), row.getShortName(),
                row.getLogoUrl(), row.getIndustry(), row.getCompanySize(), row.getCity(), row.getDescription(),
                row.getWebsiteUrl(), row.getRecruitmentContactName(), row.getRecruitmentContactEmail(),
                row.getRecruitmentContactPhone(), row.getStatus(), value(row.getRecruitingPositionCount()),
                value(row.getApplicationCount()), value(row.getMemberCount()), row.getCreatedAt(), row.getUpdatedAt());
    }

    private AdminCompanyDtos.MemberView toMemberView(UserAccount user) {
        List<Long> roleIds = userRoleMapper.selectList(new LambdaQueryWrapper<UserRole>()
                .eq(UserRole::getUserId, user.getId())).stream().map(UserRole::getRoleId).toList();
        List<String> roles = roleIds.isEmpty() ? List.of() : roleMapper.selectBatchIds(roleIds).stream()
                .map(Role::getRoleCode).toList();
        return new AdminCompanyDtos.MemberView(user.getId(), user.getUsername(), user.getRealName(), user.getEmail(),
                user.getPhone(), user.getStatus(), roles, user.getLastLoginAt(), user.getCreatedAt());
    }

    private void audit(String action, Long resourceId, Long companyId, String summary) {
        auditService.success("ADMIN_COMPANY", action, "COMPANY", resourceId, companyId, summary);
    }

    private static String normalizeOptional(String value) { return StringUtils.hasText(value) ? value.trim() : null; }
    private String normalizeRole(String value) { return value == null ? "" : value.trim().toUpperCase(Locale.ROOT); }
    private static long safePageNo(Long value) { return value == null ? 1 : Math.max(1, value); }
    private static long safePageSize(Long value) { return value == null ? 20 : Math.min(100, Math.max(1, value)); }
    private static long value(Long value) { return value == null ? 0 : value; }
    private record RoleSelection(List<Role> roles, List<String> roleCodes) {}
}
