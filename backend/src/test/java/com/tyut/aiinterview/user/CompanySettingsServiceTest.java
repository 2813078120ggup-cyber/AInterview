package com.tyut.aiinterview.user;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.tyut.aiinterview.domain.Company;
import com.tyut.aiinterview.mapper.CompanyMapper;
import com.tyut.aiinterview.recruitment.CompanyAccessService;
import org.junit.jupiter.api.Test;

class CompanySettingsServiceTest {
    private final CompanyMapper companyMapper = mock(CompanyMapper.class);
    private final CompanyAccessService companyAccess = mock(CompanyAccessService.class);
    private final CompanySettingsService service = new CompanySettingsService(companyMapper, companyAccess);

    @Test
    void readsOnlyTheAuthenticatedCompany() {
        Company company = company(100L);
        when(companyAccess.requirePermission("company:read")).thenReturn(100L);
        when(companyMapper.selectById(100L)).thenReturn(company);

        CompanySettingsDtos.SettingsView view = service.view();

        assertEquals(100L, view.id());
        assertEquals("Xingyun", view.name());
        verify(companyMapper).selectById(100L);
    }

    @Test
    void updatesContactAndProfileFieldsWithoutCallerSuppliedCompanyId() {
        Company company = company(100L);
        when(companyAccess.requirePermission("company:write")).thenReturn(100L);
        when(companyMapper.selectById(100L)).thenReturn(company);

        CompanySettingsDtos.SettingsView view = service.update(new CompanySettingsDtos.UpdateRequest(
                " Xingyun Tech ", " XY ", " https://logo.example/logo.png ", "软件", "100-499", "太原",
                "面向企业提供智能招聘服务", "https://example.com", "林晓雯", "hr@example.com", "13800002001"));

        assertEquals("Xingyun Tech", company.getName());
        assertEquals("林晓雯", view.recruitmentContactName());
        assertEquals("hr@example.com", company.getRecruitmentContactEmail());
        verify(companyMapper).updateById(any(Company.class));
    }

    private Company company(Long id) {
        Company company = new Company();
        company.setId(id);
        company.setName("Xingyun");
        company.setStatus(1);
        return company;
    }
}
