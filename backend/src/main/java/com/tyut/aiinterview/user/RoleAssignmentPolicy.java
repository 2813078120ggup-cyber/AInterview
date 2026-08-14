package com.tyut.aiinterview.user;

import com.tyut.aiinterview.common.BusinessException;
import com.tyut.aiinterview.domain.Role;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Defines the mutually exclusive account identity domains used by every administrative role assignment entry point.
 */
public final class RoleAssignmentPolicy {
    public static final String CANDIDATE_ROLE = "CANDIDATE";
    public static final String COMPANY_ADMIN_ROLE = "COMPANY_ADMIN";
    public static final Set<String> COMPANY_ROLE_CODES = Set.of(
            COMPANY_ADMIN_ROLE, "COMPANY_RECRUITER", "COMPANY_INTERVIEWER");

    private RoleAssignmentPolicy() {
    }

    public static void validate(Long companyId, List<Role> roles) {
        Set<String> roleCodes = roles.stream().map(Role::getRoleCode).collect(Collectors.toSet());
        boolean hasCandidateRole = roleCodes.contains(CANDIDATE_ROLE);
        boolean hasCompanyRole = roleCodes.stream().anyMatch(COMPANY_ROLE_CODES::contains);

        if (hasCandidateRole && roleCodes.size() > 1) {
            throw BusinessException.badRequest("候选人角色必须单独分配，不能同时拥有平台或企业角色");
        }
        if (companyId == null && hasCompanyRole) {
            throw BusinessException.badRequest("企业角色必须绑定企业");
        }
        if (companyId != null && roleCodes.stream().anyMatch(code -> !COMPANY_ROLE_CODES.contains(code))) {
            throw BusinessException.badRequest("企业成员只能分配企业角色");
        }
    }
}
