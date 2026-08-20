package com.tyut.aiinterview.governance;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tyut.aiinterview.ai.AiGenerationContext;
import com.tyut.aiinterview.domain.RecruitmentAiCostReservation;
import com.tyut.aiinterview.domain.RecruitmentAiGovernanceEvent;
import com.tyut.aiinterview.domain.RecruitmentAiPolicy;
import com.tyut.aiinterview.mapper.JobApplicationMapper;
import com.tyut.aiinterview.mapper.RecruitmentAiCostReservationMapper;
import com.tyut.aiinterview.mapper.RecruitmentAiEvalRunMapper;
import com.tyut.aiinterview.mapper.RecruitmentAiEvalSuiteMapper;
import com.tyut.aiinterview.mapper.RecruitmentAiGovernanceEventMapper;
import com.tyut.aiinterview.mapper.RecruitmentAiPolicyMapper;
import java.math.BigDecimal;
import org.mockito.ArgumentCaptor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.SimpleTransactionStatus;

class RecruitmentAiGovernanceServiceTest {
    private RecruitmentAiPolicyMapper policyMapper;
    private RecruitmentAiEvalSuiteMapper suiteMapper;
    private RecruitmentAiCostReservationMapper costMapper;
    private RecruitmentAiGovernanceEventMapper eventMapper;
    private RecruitmentAiGovernanceService service;

    @BeforeEach
    void setUp() {
        policyMapper = mock(RecruitmentAiPolicyMapper.class);
        suiteMapper = mock(RecruitmentAiEvalSuiteMapper.class);
        costMapper = mock(RecruitmentAiCostReservationMapper.class);
        eventMapper = mock(RecruitmentAiGovernanceEventMapper.class);
        service = new RecruitmentAiGovernanceService(policyMapper, suiteMapper,
                mock(RecruitmentAiEvalRunMapper.class), costMapper, eventMapper,
                mock(JobApplicationMapper.class), new RecruitmentSensitiveDataRedactor(new ObjectMapper()),
                noOpTransactionManager());
    }

    @Test
    void emergencyStopBlocksBeforeAnyCostReservation() {
        RecruitmentAiPolicy global = policy(true, true, true);
        when(policyMapper.selectForUpdate("GLOBAL")).thenReturn(global);
        when(policyMapper.selectForUpdate("COMPANY:8")).thenReturn(null);

        assertThatThrownBy(() -> service.authorize(
                AiGenerationContext.recruitment(1L, null, "RECRUITMENT_JOB_MATCH", 2L, 8L),
                "recruitment.job_match", 3, "deepseek", "model-a", 600, 700))
                .isInstanceOf(AiGovernanceException.class)
                .hasMessageContaining("紧急停用");

        verify(costMapper, never()).insert(any(RecruitmentAiCostReservation.class));
        verify(eventMapper).insert(any(RecruitmentAiGovernanceEvent.class));
    }

    @Test
    void missingVersionMatchedEvaluationGateBlocksProductionCall() {
        RecruitmentAiPolicy global = policy(true, false, true);
        when(policyMapper.selectForUpdate("GLOBAL")).thenReturn(global);
        when(policyMapper.selectForUpdate("COMPANY:8")).thenReturn(null);
        when(suiteMapper.selectOne(any())).thenReturn(null);

        assertThatThrownBy(() -> service.authorize(
                AiGenerationContext.recruitment(1L, null, "RECRUITMENT_JOB_MATCH", 2L, 8L),
                "recruitment.job_match", 3, "deepseek", "model-a", 600, 700))
                .isInstanceOf(AiGovernanceException.class)
                .hasMessageContaining("评测集");

        verify(costMapper, never()).insert(any(RecruitmentAiCostReservation.class));
    }

    @Test
    void allowedCallReservesBudgetAndReturnsTraceablePermit() {
        RecruitmentAiPolicy global = policy(true, false, false);
        when(policyMapper.selectForUpdate("GLOBAL")).thenReturn(global);
        when(policyMapper.selectForUpdate("COMPANY:8")).thenReturn(null);
        when(costMapper.sumActiveCostSince(any(), any())).thenReturn(BigDecimal.ZERO);
        when(costMapper.insert(any(RecruitmentAiCostReservation.class))).thenAnswer(invocation -> {
            RecruitmentAiCostReservation item = invocation.getArgument(0);
            item.setId(91L);
            return 1;
        });

        RecruitmentAiGovernanceService.Permit permit = service.authorize(
                AiGenerationContext.recruitment(1L, null, "RECRUITMENT_JOB_MATCH", 2L, 8L),
                "recruitment.job_match", 3, "deepseek", "model-a", 600, 700);

        assertThat(permit.governed()).isTrue();
        assertThat(permit.companyId()).isEqualTo(8L);
        assertThat(permit.policyId()).isEqualTo(10L);
        assertThat(permit.reservationId()).isEqualTo(91L);
        assertThat(permit.estimatedCostUsd()).isPositive();
        verify(costMapper).releaseExpired(any());
        verify(costMapper).insert(any(RecruitmentAiCostReservation.class));
    }

    @Test
    void tenantPolicyCannotLowerGlobalCostRates() {
        RecruitmentAiPolicy global = policy(true, false, false);
        global.setInputCostPerMillionUsd(BigDecimal.ONE);
        global.setOutputCostPerMillionUsd(BigDecimal.valueOf(2));
        RecruitmentAiPolicy tenant = policy(true, false, false);
        tenant.setId(11L);
        tenant.setScopeKey("COMPANY:8");
        tenant.setCompanyId(8L);
        tenant.setInputCostPerMillionUsd(BigDecimal.valueOf(0.01));
        tenant.setOutputCostPerMillionUsd(BigDecimal.valueOf(0.01));
        when(policyMapper.selectForUpdate("GLOBAL")).thenReturn(global);
        when(policyMapper.selectForUpdate("COMPANY:8")).thenReturn(tenant);
        when(costMapper.sumActiveCostSince(any(), any())).thenReturn(BigDecimal.ZERO);
        when(costMapper.insert(any(RecruitmentAiCostReservation.class))).thenAnswer(invocation -> {
            RecruitmentAiCostReservation item = invocation.getArgument(0);
            item.setId(92L);
            return 1;
        });

        service.authorize(AiGenerationContext.recruitment(1L, null, "RECRUITMENT_JOB_MATCH", 2L, 8L),
                "recruitment.job_match", 3, "deepseek", "model-a", 300, 100);

        ArgumentCaptor<RecruitmentAiCostReservation> captor = ArgumentCaptor.forClass(RecruitmentAiCostReservation.class);
        verify(costMapper).insert(captor.capture());
        assertThat(captor.getValue().getEstimatedCostUsd()).isEqualByComparingTo("0.000500");
    }

    @Test
    void blockOnDetectionPolicyRejectsSensitiveInputBeforeModelCall() {
        RecruitmentAiPolicy global = policy(true, false, false);
        global.setSensitiveDataMode("BLOCK_ON_DETECTION");
        when(policyMapper.selectOne(any())).thenReturn(global);

        assertThatThrownBy(() -> service.prepareSensitiveInput(null, "姓名：张伟，5 年 Java 经验"))
                .isInstanceOf(AiGovernanceException.class)
                .hasMessageContaining("禁止模型处理");

        verify(eventMapper).insert(any(RecruitmentAiGovernanceEvent.class));
    }

    private RecruitmentAiPolicy policy(boolean enabled, boolean emergency, boolean gateRequired) {
        RecruitmentAiPolicy policy = new RecruitmentAiPolicy();
        policy.setId(10L);
        policy.setScopeKey("GLOBAL");
        policy.setAiEnabled(enabled ? 1 : 0);
        policy.setEmergencyStop(emergency ? 1 : 0);
        policy.setEmergencyReason(emergency ? "人工触发" : null);
        policy.setEvaluationGateRequired(gateRequired ? 1 : 0);
        policy.setEvaluationValidDays(30);
        policy.setMinimumPassRate(BigDecimal.valueOf(90));
        policy.setMaximumScoreDrift(BigDecimal.TEN);
        policy.setMaximumFairnessGap(BigDecimal.valueOf(5));
        policy.setHumanReviewMode("ALL");
        policy.setAdverseScoreThreshold(BigDecimal.valueOf(60));
        policy.setSensitiveDataMode("REDACT");
        policy.setDailyCostLimitUsd(BigDecimal.TEN);
        policy.setMonthlyCostLimitUsd(BigDecimal.valueOf(200));
        policy.setInputCostPerMillionUsd(BigDecimal.valueOf(0.27));
        policy.setOutputCostPerMillionUsd(BigDecimal.valueOf(1.10));
        policy.setPerRequestTokenLimit(4096);
        return policy;
    }

    private PlatformTransactionManager noOpTransactionManager() {
        return new PlatformTransactionManager() {
            @Override
            public TransactionStatus getTransaction(TransactionDefinition definition) {
                return new SimpleTransactionStatus();
            }
            @Override public void commit(TransactionStatus status) { }
            @Override public void rollback(TransactionStatus status) { }
        };
    }
}
