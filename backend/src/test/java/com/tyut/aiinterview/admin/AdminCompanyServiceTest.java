package com.tyut.aiinterview.admin;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.tyut.aiinterview.common.BusinessException;
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
import java.util.List;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.apache.ibatis.session.Configuration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.password.PasswordEncoder;

class AdminCompanyServiceTest {
    private final AdminCompanyMapper adminMapper = org.mockito.Mockito.mock(AdminCompanyMapper.class);
    private final CompanyMapper companyMapper = org.mockito.Mockito.mock(CompanyMapper.class);
    private final UserMapper userMapper = org.mockito.Mockito.mock(UserMapper.class);
    private final UserRoleMapper userRoleMapper = org.mockito.Mockito.mock(UserRoleMapper.class);
    private final RoleMapper roleMapper = org.mockito.Mockito.mock(RoleMapper.class);
    private final PasswordEncoder passwordEncoder = org.mockito.Mockito.mock(PasswordEncoder.class);
    private final CurrentUser currentUser = org.mockito.Mockito.mock(CurrentUser.class);
    private final OperationAuditService auditService = org.mockito.Mockito.mock(OperationAuditService.class);
    private final AdminCompanyService service = new AdminCompanyService(adminMapper, companyMapper, userMapper,
            userRoleMapper, roleMapper, passwordEncoder, currentUser, auditService);

    @BeforeEach
    void initializeMyBatisPlusLambdaMetadata() {
        Configuration configuration = new Configuration();
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(configuration, "test");
        for (Class<?> entityType : List.of(Company.class, Role.class, UserAccount.class, UserRole.class)) {
            if (TableInfoHelper.getTableInfo(entityType) == null) TableInfoHelper.initTableInfo(assistant, entityType);
        }
    }

    @Test
    void pagesCompaniesWithDatabaseAggregatedRecruitmentCounts() {
        AdminCompanyRow row = new AdminCompanyRow();
        row.setId(10L);
        row.setCompanyCode("ACME");
        row.setName("示例企业");
        row.setStatus(1);
        row.setRecruitingPositionCount(3L);
        row.setApplicationCount(18L);
        row.setMemberCount(4L);
        when(adminMapper.selectPage("示例", 1, 0, 20)).thenReturn(List.of(row));
        when(adminMapper.count("示例", 1)).thenReturn(1L);

        var result = service.page(new AdminCompanyDtos.Query(1L, 20L, "示例", 1));

        assertEquals(1L, result.total());
        assertEquals(3L, result.records().get(0).recruitingPositionCount());
        assertEquals(18L, result.records().get(0).applicationCount());
        assertEquals(4L, result.records().get(0).memberCount());
    }

    @Test
    void refusesToDisableCompanyWithoutExplicitRiskConfirmation() {
        Company company = company(10L, 1);
        when(companyMapper.selectById(10L)).thenReturn(company);
        when(adminMapper.countPublishedPositions(10L)).thenReturn(2L);
        when(adminMapper.countInProgressInterviews(10L)).thenReturn(1L);

        BusinessException exception = assertThrows(BusinessException.class,
                () -> service.updateStatus(10L, new AdminCompanyDtos.StatusRequest(0, false)));

        assertEquals(409, exception.getStatus().value());
        verify(companyMapper, never()).updateById(any(Company.class));
        verify(auditService).failure("ADMIN_COMPANY", "COMPANY_DISABLE_BLOCKED", "COMPANY", 10L, 10L,
                "停用前检查阻止：2 个招聘中岗位、1 场进行中面试");
    }

    @Test
    void confirmedDisablePreservesCompanyAndWritesAudit() {
        Company company = company(10L, 1);
        AdminCompanyRow row = new AdminCompanyRow();
        row.setId(10L);
        row.setName("示例企业");
        row.setStatus(0);
        when(companyMapper.selectById(10L)).thenReturn(company);
        when(adminMapper.countPublishedPositions(10L)).thenReturn(2L);
        when(adminMapper.countInProgressInterviews(10L)).thenReturn(1L);
        when(adminMapper.selectById(10L)).thenReturn(row);

        service.updateStatus(10L, new AdminCompanyDtos.StatusRequest(0, true));

        assertEquals(0, company.getStatus());
        verify(companyMapper).updateById(company);
        verify(auditService).success("ADMIN_COMPANY", "COMPANY_DISABLED", "COMPANY", 10L, 10L,
                "停用企业，历史招聘数据保留");
    }

    @Test
    void createsMemberWithCompanyAdminRoleAndNeverAcceptsCompanyIdFromRequest() {
        when(currentUser.id()).thenReturn(1L);
        when(userMapper.exists(any())).thenReturn(false);
        when(companyMapper.selectById(10L)).thenReturn(company(10L, 1));
        Role adminRole = role(30L, "COMPANY_ADMIN");
        when(roleMapper.selectList(any())).thenReturn(List.of(adminRole));
        when(passwordEncoder.encode("Password123")).thenReturn("encoded");
        when(userRoleMapper.selectList(any())).thenReturn(List.of(new UserRole()));
        when(roleMapper.selectBatchIds(any())).thenReturn(List.of(adminRole));
        doAnswer(invocation -> {
            ((UserAccount) invocation.getArgument(0)).setId(99L);
            return 1;
        }).when(userMapper).insert(any(UserAccount.class));

        var result = service.createMember(10L, new AdminCompanyDtos.MemberCreateRequest(
                "company_admin", "Password123", "企业管理员", "admin@example.test", "13800000000",
                List.of("COMPANY_ADMIN")));

        assertEquals(99L, result.id());
        assertEquals(List.of("COMPANY_ADMIN"), result.roles());
        verify(auditService).success("ADMIN_COMPANY", "COMPANY_ADMIN_ASSIGNED", "COMPANY", 99L, 10L,
                "为企业创建成员并分配 COMPANY_ADMIN");
    }

    private Company company(Long id, Integer status) {
        Company company = new Company();
        company.setId(id);
        company.setStatus(status);
        company.setName("示例企业");
        return company;
    }

    private Role role(Long id, String code) {
        Role role = new Role();
        role.setId(id);
        role.setRoleCode(code);
        role.setStatus(1);
        return role;
    }
}
