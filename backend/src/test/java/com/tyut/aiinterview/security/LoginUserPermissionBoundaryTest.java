package com.tyut.aiinterview.security;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

class LoginUserPermissionBoundaryTest {
    @Test
    void candidateDoesNotReceivePermissionAuthoritiesFromStaleRelations() {
        LoginUser candidate = new LoginUser(7L, "candidate", "", true,
                List.of("CANDIDATE"), null, List.of("company:read", "ai:execute"));

        assertFalse(candidate.hasPermission("company:read"));
        assertFalse(candidate.hasPermission("ai:execute"));
        assertTrue(candidate.getAuthorities().stream().anyMatch(item -> "ROLE_CANDIDATE".equals(item.getAuthority())));
        assertFalse(candidate.getAuthorities().stream().anyMatch(item -> item.getAuthority().startsWith("PERM_")));
    }

    @Test
    void invalidMixedCandidateIdentityFailsClosedForCompanyPermissions() {
        LoginUser mixed = new LoginUser(10L, "legacy", "", true,
                List.of("CANDIDATE", "COMPANY_ADMIN"), 100L, List.of("company:read"));

        assertEquals(List.of("CANDIDATE"), mixed.getRoles());
        assertNull(mixed.getCompanyId());
        assertFalse(mixed.hasPermission("company:read"));
        assertFalse(mixed.getAuthorities().stream().anyMatch(item -> "ROLE_COMPANY_ADMIN".equals(item.getAuthority())));
        assertFalse(mixed.getAuthorities().stream().anyMatch(item -> item.getAuthority().startsWith("PERM_")));
    }

    @Test
    void companyRoleKeepsItsConfiguredPermissionAuthorities() {
        LoginUser recruiter = new LoginUser(8L, "recruiter", "", true,
                List.of("COMPANY_RECRUITER"), 100L, List.of("application:read"));

        assertTrue(recruiter.hasPermission("application:read"));
        assertTrue(recruiter.getAuthorities().stream().anyMatch(item -> "PERM_application:read".equals(item.getAuthority())));
    }
}
