package com.tyut.aiinterview.user;

import com.tyut.aiinterview.common.BusinessException;
import com.tyut.aiinterview.domain.Company;
import com.tyut.aiinterview.mapper.CompanyMapper;
import com.tyut.aiinterview.observability.OperationAuditService;
import com.tyut.aiinterview.recruitment.CompanyAccessService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
public class CompanySettingsService {
    private final CompanyMapper companyMapper;
    private final CompanyAccessService companyAccess;
    private final OperationAuditService auditService;

    public CompanySettingsService(CompanyMapper companyMapper, CompanyAccessService companyAccess) {
        this(companyMapper, companyAccess, null);
    }

    @Autowired
    public CompanySettingsService(CompanyMapper companyMapper, CompanyAccessService companyAccess,
                                  OperationAuditService auditService) {
        this.companyMapper = companyMapper;
        this.companyAccess = companyAccess;
        this.auditService = auditService;
    }

    public CompanySettingsDtos.SettingsView view() {
        Long companyId = companyAccess.requirePermission("company:read");
        return toView(requireCompany(companyId));
    }

    @Transactional
    public CompanySettingsDtos.SettingsView update(CompanySettingsDtos.UpdateRequest request) {
        Long companyId = companyAccess.requirePermission("company:write");
        Company company = requireCompany(companyId);
        company.setName(request.name().trim());
        company.setShortName(normalize(request.shortName()));
        company.setLogoUrl(normalize(request.logoUrl()));
        company.setIndustry(normalize(request.industry()));
        company.setCompanySize(normalize(request.companySize()));
        company.setCity(normalize(request.city()));
        company.setDescription(normalize(request.description()));
        company.setWebsiteUrl(normalize(request.websiteUrl()));
        company.setRecruitmentContactName(normalize(request.recruitmentContactName()));
        company.setRecruitmentContactEmail(normalize(request.recruitmentContactEmail()));
        company.setRecruitmentContactPhone(normalize(request.recruitmentContactPhone()));
        companyMapper.updateById(company);
        if (auditService != null) {
            auditService.success("COMPANY_SETTINGS", "COMPANY_SETTINGS_UPDATED", "COMPANY", companyId, companyId,
                    "更新企业资料字段");
        }
        return toView(companyMapper.selectById(companyId));
    }

    private Company requireCompany(Long companyId) {
        Company company = companyMapper.selectById(companyId);
        if (company == null || !Integer.valueOf(1).equals(company.getStatus())) {
            throw BusinessException.notFound("企业不存在或已停用");
        }
        return company;
    }

    private CompanySettingsDtos.SettingsView toView(Company company) {
        return new CompanySettingsDtos.SettingsView(company.getId(), company.getCompanyCode(), company.getName(),
                company.getShortName(), company.getLogoUrl(), company.getIndustry(), company.getCompanySize(),
                company.getCity(), company.getDescription(), company.getWebsiteUrl(),
                company.getRecruitmentContactName(), company.getRecruitmentContactEmail(),
                company.getRecruitmentContactPhone(), company.getUpdatedAt());
    }

    private static String normalize(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }
}
