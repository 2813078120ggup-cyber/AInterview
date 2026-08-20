package com.tyut.aiinterview.admin;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.tyut.aiinterview.common.BusinessException;
import com.tyut.aiinterview.domain.Company;
import com.tyut.aiinterview.domain.JobPosition;
import com.tyut.aiinterview.domain.RecruitmentRequisition;
import com.tyut.aiinterview.domain.RecruitmentRequisitionEvent;
import com.tyut.aiinterview.mapper.CompanyMapper;
import com.tyut.aiinterview.mapper.JobPositionMapper;
import com.tyut.aiinterview.mapper.RecruitmentRequisitionEventMapper;
import com.tyut.aiinterview.mapper.RecruitmentRequisitionMapper;
import com.tyut.aiinterview.mapper.UserMapper;
import com.tyut.aiinterview.recruitment.RecruitmentAuditService;
import com.tyut.aiinterview.recruitment.RecruitmentService;
import com.tyut.aiinterview.security.CurrentUser;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import java.math.BigDecimal;
import java.util.List;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class AdminRecruitmentRequisitionServiceTest {
    private final RecruitmentRequisitionMapper requisitionMapper = mock(RecruitmentRequisitionMapper.class);
    private final RecruitmentRequisitionEventMapper eventMapper = mock(RecruitmentRequisitionEventMapper.class);
    private final JobPositionMapper positionMapper = mock(JobPositionMapper.class);
    private final CompanyMapper companyMapper = mock(CompanyMapper.class);
    private final UserMapper userMapper = mock(UserMapper.class);
    private final RecruitmentService recruitmentService = mock(RecruitmentService.class);
    private final RecruitmentAuditService auditService = mock(RecruitmentAuditService.class);
    private final CurrentUser currentUser = mock(CurrentUser.class);
    private AdminRecruitmentRequisitionService service;

    @BeforeEach
    void setUp() {
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), "requisition-test"),
                RecruitmentRequisition.class);
        service = new AdminRecruitmentRequisitionService(requisitionMapper, eventMapper, positionMapper,
                companyMapper, userMapper, recruitmentService, auditService, currentUser);
        when(currentUser.hasRole("ADMIN")).thenReturn(true);
        when(currentUser.id()).thenReturn(900L);
        when(eventMapper.selectList(any())).thenReturn(List.of());
        when(companyMapper.selectById(100L)).thenReturn(company());
        when(positionMapper.selectById(1L)).thenReturn(position());
    }

    @Test
    void approvePublishesPositionInSameWorkflow() {
        RecruitmentRequisition pending = requisition("PENDING_APPROVAL", 0);
        RecruitmentRequisition approved = requisition("APPROVED", 0);
        approved.setApprovedHeadcount(2);
        when(requisitionMapper.selectById(10L)).thenReturn(pending, approved);
        when(requisitionMapper.update(any(), any())).thenReturn(1);

        AdminRecruitmentDtos.RequisitionDetail result = service.approve(10L,
                new AdminRecruitmentDtos.ApprovalRequest(2, "预算与编制核验通过"));

        JobPosition position = positionMapper.selectById(1L);
        assertEquals("PUBLISHED", position.getRecruitmentStatus());
        assertNotNull(position.getPublishedAt());
        assertEquals("APPROVED", result.requisition().approvalStatus());
        verify(recruitmentService).validatePositionForPublication(position);
        verify(positionMapper).updateById(position);
        verify(eventMapper).insert(any(RecruitmentRequisitionEvent.class));
    }

    @Test
    void approvalCannotExceedRequestedHeadcount() {
        when(requisitionMapper.selectById(10L)).thenReturn(requisition("PENDING_APPROVAL", 0));

        BusinessException exception = assertThrows(BusinessException.class,
                () -> service.approve(10L, new AdminRecruitmentDtos.ApprovalRequest(4, null)));

        assertEquals("批准人数不能超过申请人数", exception.getMessage());
    }

    @Test
    void freezeRequiresApprovedRequisition() {
        when(requisitionMapper.selectById(10L)).thenReturn(requisition("PENDING_APPROVAL", 0));

        BusinessException exception = assertThrows(BusinessException.class,
                () -> service.freeze(10L, new AdminRecruitmentDtos.DecisionRequest("预算临时冻结")));

        assertEquals("只有已批准的招聘需求可以冻结", exception.getMessage());
    }

    private RecruitmentRequisition requisition(String status, int frozen) {
        RecruitmentRequisition item = new RecruitmentRequisition();
        item.setId(10L);
        item.setRequisitionNo("REQ-20260814-TEST");
        item.setCompanyId(100L);
        item.setPositionId(1L);
        item.setHeadcountCode("HC-RD-01");
        item.setRequestedHeadcount(3);
        item.setCostCenterCode("CC-RD");
        item.setBudgetAmount(new BigDecimal("50000.00"));
        item.setBudgetCurrency("CNY");
        item.setBusinessJustification("补充研发岗位");
        item.setApprovalStatus(status);
        item.setFrozen(frozen);
        return item;
    }

    private JobPosition position() {
        JobPosition position = new JobPosition();
        position.setId(1L);
        position.setCompanyId(100L);
        position.setPositionCode("POS-1");
        position.setName("Java 工程师");
        position.setRecruitmentStatus("DRAFT");
        return position;
    }

    private Company company() {
        Company company = new Company();
        company.setId(100L);
        company.setCompanyCode("COMP-1");
        company.setName("测试企业");
        return company;
    }
}
