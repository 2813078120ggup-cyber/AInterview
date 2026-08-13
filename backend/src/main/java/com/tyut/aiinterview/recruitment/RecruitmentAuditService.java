package com.tyut.aiinterview.recruitment;

import com.tyut.aiinterview.observability.OperationAuditService;
import com.tyut.aiinterview.security.CurrentUser;
import org.springframework.beans.factory.annotation.Autowired;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Server-side audit boundary for recruitment operations.
 *
 * <p>Compatibility boundary for recruitment callers. The sink is now the
 * append-only server-side operation audit table; callers remain decoupled from
 * its storage details.</p>
 */
@Service
public class RecruitmentAuditService {
    private static final Logger log = LoggerFactory.getLogger(RecruitmentAuditService.class);
    private final CurrentUser currentUser;
    private final OperationAuditService operationAuditService;

    public RecruitmentAuditService(CurrentUser currentUser) {
        this(currentUser, null);
    }

    @Autowired
    public RecruitmentAuditService(CurrentUser currentUser, OperationAuditService operationAuditService) {
        this.currentUser = currentUser;
        this.operationAuditService = operationAuditService;
    }

    public void recordPositionOperation(String action, Long companyId, Long positionId,
                                         String positionCode, String detail) {
        if (operationAuditService != null) {
            operationAuditService.success("RECRUITMENT", action, "JOB_POSITION", positionId, companyId,
                    "positionCode=" + positionCode + "; " + detail);
            return;
        }
        log.info("recruitment_audit action={} operatorId={} companyId={} positionId={} positionCode={} detail={}",
                action, currentUser.id(), companyId, positionId, positionCode, detail);
    }

    public void recordApplicationOperation(String action, Long companyId, Long applicationId, String detail) {
        recordOperation("RECRUITMENT", action, "JOB_APPLICATION", applicationId, companyId, detail, "SUCCESS");
    }

    public void recordInterviewOperation(String action, Long companyId, Long interviewId, String detail) {
        recordOperation("INTERVIEW", action, "INTERVIEW", interviewId, companyId, detail, "SUCCESS");
    }

    public void recordOperation(String module, String action, String resourceType, Object resourceId,
                                Long companyId, String detail, String result) {
        if (operationAuditService != null) {
            switch (result == null ? "SUCCESS" : result.toUpperCase()) {
                case "DENIED" -> operationAuditService.denied(module, action, resourceType, resourceId, companyId, detail);
                case "FAILURE" -> operationAuditService.failure(module, action, resourceType, resourceId, companyId, detail);
                default -> operationAuditService.success(module, action, resourceType, resourceId, companyId, detail);
            }
            return;
        }
        log.info("operation_audit action={} operatorId={} companyId={} resourceType={} resourceId={} result={} detail={}",
                action, currentUser.id(), companyId, resourceType, resourceId, result, detail);
    }
}
