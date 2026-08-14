package com.tyut.aiinterview.user;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.tyut.aiinterview.common.BusinessException;
import com.tyut.aiinterview.domain.Role;
import java.util.List;
import org.junit.jupiter.api.Test;

class RoleAssignmentPolicyTest {

    @Test
    void candidateIdentityMustRemainExclusive() {
        BusinessException exception = assertThrows(BusinessException.class,
                () -> RoleAssignmentPolicy.validate(null, List.of(role("CANDIDATE"), role("ADMIN"))));

        assertEquals(400, exception.getStatus().value());
        assertEquals("候选人角色必须单独分配，不能同时拥有平台或企业角色", exception.getMessage());
    }

    @Test
    void establishedPlatformStaffCombinationsRemainValid() {
        assertDoesNotThrow(() -> RoleAssignmentPolicy.validate(null, List.of(role("ADMIN"), role("HR"))));
    }

    @Test
    void companyRolesCanBeCombinedOnlyOnCompanyAccounts() {
        assertDoesNotThrow(() -> RoleAssignmentPolicy.validate(100L,
                List.of(role("COMPANY_ADMIN"), role("COMPANY_RECRUITER"))));
        assertThrows(BusinessException.class,
                () -> RoleAssignmentPolicy.validate(null, List.of(role("COMPANY_ADMIN"))));
        assertThrows(BusinessException.class,
                () -> RoleAssignmentPolicy.validate(100L, List.of(role("COMPANY_ADMIN"), role("HR"))));
    }

    private Role role(String code) {
        Role role = new Role();
        role.setRoleCode(code);
        return role;
    }
}
