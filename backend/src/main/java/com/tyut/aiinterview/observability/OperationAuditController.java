package com.tyut.aiinterview.observability;

import com.tyut.aiinterview.common.ApiResponse;
import com.tyut.aiinterview.common.PageResult;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/v1/admin/operation-audit-logs")
@PreAuthorize("hasRole('ADMIN')")
public class OperationAuditController {
    private final OperationAuditService service;

    public OperationAuditController(OperationAuditService service) {
        this.service = service;
    }

    @GetMapping
    public ApiResponse<PageResult<OperationAuditDtos.View>> page(OperationAuditDtos.Query query) {
        return ApiResponse.ok(service.page(query));
    }

    @GetMapping("/export")
    public ResponseEntity<byte[]> export(OperationAuditDtos.Query query) {
        String csv = toCsv(service.export(query));
        String filename = "operation-audit-logs-" + LocalDate.now().format(DateTimeFormatter.BASIC_ISO_DATE) + ".csv";
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                .contentType(new MediaType("text", "csv", StandardCharsets.UTF_8))
                .header(HttpHeaders.CACHE_CONTROL, "no-store")
                .header("X-Content-Type-Options", "nosniff")
                .body(("\uFEFF" + csv).getBytes(StandardCharsets.UTF_8));
    }

    private String toCsv(List<OperationAuditDtos.View> records) {
        StringBuilder builder = new StringBuilder();
        builder.append("id,request_id,actor_id,actor_role,company_id,module,action,resource_type,resource_id,result,summary,ip_address,user_agent,created_at\n");
        for (OperationAuditDtos.View item : records) {
            builder.append(row(item.id())).append(',').append(row(item.requestId())).append(',')
                    .append(row(item.actorId())).append(',').append(row(item.actorRole())).append(',')
                    .append(row(item.companyId())).append(',').append(row(item.module())).append(',')
                    .append(row(item.action())).append(',').append(row(item.resourceType())).append(',')
                    .append(row(item.resourceId())).append(',').append(row(item.result())).append(',')
                    .append(row(item.summary())).append(',').append(row(item.ipAddress())).append(',')
                    .append(row(item.userAgent())).append(',').append(row(item.createdAt())).append('\n');
        }
        return builder.toString();
    }

    private String row(Object value) {
        if (value == null) return "";
        String text = String.valueOf(value).replace("\"", "\"\"");
        return "\"" + text + "\"";
    }
}
