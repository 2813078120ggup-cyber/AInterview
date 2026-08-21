package com.tyut.aiinterview.admin.dictionary;

import com.tyut.aiinterview.common.ApiResponse;
import com.tyut.aiinterview.common.PageResult;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/v1/admin/operations/data-dictionary")
@PreAuthorize("hasRole('ADMIN')")
public class AdminDataDictionaryController {
    private final AdminDataDictionaryService service;

    public AdminDataDictionaryController(AdminDataDictionaryService service) {
        this.service = service;
    }

    @GetMapping("/overview")
    public ApiResponse<AdminDataDictionaryDtos.Overview> overview() {
        return ApiResponse.ok(service.overview());
    }

    @GetMapping("/tables")
    public ApiResponse<PageResult<AdminDataDictionaryDtos.TableSummary>> tables(
            AdminDataDictionaryDtos.TableQuery query) {
        return ApiResponse.ok(service.tables(query));
    }

    @GetMapping("/tables/{tableName}")
    public ApiResponse<AdminDataDictionaryDtos.TableDetail> table(@PathVariable String tableName) {
        return ApiResponse.ok(service.table(tableName));
    }
}
