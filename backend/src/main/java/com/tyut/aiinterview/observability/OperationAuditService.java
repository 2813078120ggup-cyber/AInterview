package com.tyut.aiinterview.observability;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.tyut.aiinterview.common.PageResult;
import com.tyut.aiinterview.domain.OperationAuditLog;
import com.tyut.aiinterview.mapper.OperationAuditLogMapper;
import com.tyut.aiinterview.security.CurrentUser;
import jakarta.servlet.http.HttpServletRequest;
import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.stream.Collectors;
import org.slf4j.MDC;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

@Service
public class OperationAuditService {
    private static final String SYSTEM_ROLE = "SYSTEM";
    private final OperationAuditLogMapper mapper;
    private final CurrentUser currentUser;

    public OperationAuditService(OperationAuditLogMapper mapper, CurrentUser currentUser) {
        this.mapper = mapper;
        this.currentUser = currentUser;
    }

    public void success(String module, String action, String resourceType, Object resourceId,
                        Long companyId, String summary) {
        record(module, action, resourceType, resourceId, "SUCCESS", summary, currentActorId(),
                currentActorRole(), companyId);
    }

    public void failure(String module, String action, String resourceType, Object resourceId,
                        Long companyId, String summary) {
        record(module, action, resourceType, resourceId, "FAILURE", summary, currentActorId(),
                currentActorRole(), companyId);
    }

    public void denied(String module, String action, String resourceType, Object resourceId,
                       Long companyId, String summary) {
        record(module, action, resourceType, resourceId, "DENIED", summary, currentActorId(),
                currentActorRole(), companyId);
    }

    public void record(String module, String action, String resourceType, Object resourceId,
                       String result, String summary, Long actorId, String actorRole, Long companyId) {
        OperationAuditLog log = new OperationAuditLog();
        log.setRequestId(sanitizeSummary(requestId()));
        log.setActorId(actorId);
        log.setActorRole(safe(actorRole, 256));
        log.setCompanyId(companyId);
        log.setModule(safe(module, 64));
        log.setAction(safe(action, 64));
        log.setResourceType(safe(resourceType, 64));
        log.setResourceId(resourceId == null ? null : safe(String.valueOf(resourceId), 128));
        log.setResult(normalizeResult(result));
        log.setSummary(sanitizeSummary(summary));
        RequestContext request = requestContext();
        log.setIpAddress(safe(request.ipAddress(), 64));
        log.setUserAgent(safe(sanitizeSummary(request.userAgent()), 512));
        log.setCreatedAt(LocalDateTime.now());
        mapper.insert(log);
    }

    public PageResult<OperationAuditDtos.View> page(OperationAuditDtos.Query query) {
        long pageNo = query == null || query.pageNo() == null ? 1 : Math.max(1, query.pageNo());
        long pageSize = query == null || query.pageSize() == null ? 20 : Math.min(100, Math.max(1, query.pageSize()));
        LambdaQueryWrapper<OperationAuditLog> wrapper = filters(query).orderByDesc(OperationAuditLog::getId);
        Page<OperationAuditLog> result = mapper.selectPage(new Page<>(pageNo, pageSize), wrapper);
        return PageResult.of(result.getRecords().stream().map(this::view).toList(), result.getTotal(), pageNo, pageSize);
    }

    public List<OperationAuditDtos.View> export(OperationAuditDtos.Query query) {
        LambdaQueryWrapper<OperationAuditLog> wrapper = filters(query).orderByDesc(OperationAuditLog::getId)
                .last("LIMIT 5000");
        return mapper.selectList(wrapper).stream().map(this::view).toList();
    }

    private LambdaQueryWrapper<OperationAuditLog> filters(OperationAuditDtos.Query query) {
        LambdaQueryWrapper<OperationAuditLog> wrapper = new LambdaQueryWrapper<>();
        if (query == null) return wrapper;
        if (hasText(query.module())) wrapper.eq(OperationAuditLog::getModule, query.module().trim());
        if (hasText(query.action())) wrapper.eq(OperationAuditLog::getAction, query.action().trim());
        if (hasText(query.resourceType())) wrapper.eq(OperationAuditLog::getResourceType, query.resourceType().trim());
        if (hasText(query.result())) wrapper.eq(OperationAuditLog::getResult, query.result().trim().toUpperCase(Locale.ROOT));
        if (query.actorId() != null) wrapper.eq(OperationAuditLog::getActorId, query.actorId());
        if (query.companyId() != null) wrapper.eq(OperationAuditLog::getCompanyId, query.companyId());
        if (hasText(query.from())) wrapper.ge(OperationAuditLog::getCreatedAt, parseTime(query.from()));
        if (hasText(query.to())) wrapper.le(OperationAuditLog::getCreatedAt, parseTime(query.to()));
        if (hasText(query.keyword())) {
            String keyword = query.keyword().trim();
            wrapper.and(item -> item.like(OperationAuditLog::getRequestId, keyword)
                    .or().like(OperationAuditLog::getAction, keyword)
                    .or().like(OperationAuditLog::getModule, keyword)
                    .or().like(OperationAuditLog::getResourceId, keyword)
                    .or().like(OperationAuditLog::getSummary, keyword));
        }
        return wrapper;
    }

    private OperationAuditDtos.View view(OperationAuditLog item) {
        return new OperationAuditDtos.View(item.getId(), item.getRequestId(), item.getActorId(), item.getActorRole(),
                item.getCompanyId(), item.getModule(), item.getAction(), item.getResourceType(), item.getResourceId(),
                item.getResult(), item.getSummary(), item.getIpAddress(), item.getUserAgent(), item.getCreatedAt());
    }

    private Long currentActorId() {
        try {
            return currentUser.id();
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    private String currentActorRole() {
        try {
            var user = currentUser.require();
            if (user.getRoles() == null || user.getRoles().isEmpty()) return SYSTEM_ROLE;
            return user.getRoles().stream().filter(Objects::nonNull).sorted().collect(Collectors.joining(","));
        } catch (RuntimeException ignored) {
            return SYSTEM_ROLE;
        }
    }

    private String requestId() {
        String requestId = MDC.get("requestId");
        return safe(StringUtils.hasText(requestId) ? requestId : "unknown", 64);
    }

    private RequestContext requestContext() {
        var attributes = RequestContextHolder.getRequestAttributes();
        if (attributes instanceof ServletRequestAttributes servletAttributes) {
            HttpServletRequest request = servletAttributes.getRequest();
            String forwarded = request.getHeader("X-Forwarded-For");
            String ip = StringUtils.hasText(forwarded) ? forwarded.split(",", 2)[0].trim() : request.getRemoteAddr();
            return new RequestContext(ip, request.getHeader("User-Agent"));
        }
        return new RequestContext(null, null);
    }

    private String sanitizeSummary(String value) {
        String result = value == null ? "" : value.replaceAll("[\\r\\n]+", " ").trim();
        result = result.replaceAll("(?i)(password|passwd|token|authorization|api[-_]?key|api[-_]?secret|secret|prompt|provider(?:[-_ ]?(?:raw|original))?(?:[-_ ]?(?:response|result))?)\\s*[:=：]\\s*[^,; ]+", "$1=[REDACTED]");
        result = result.replaceAll("(?i)(code|captcha|verification)\\s*[:=：]\\s*[^,; ]+", "$1=[REDACTED]");
        result = result.replaceAll("(?i)Bearer\\s+[A-Za-z0-9._~+/=-]+", "Bearer [REDACTED]");
        result = result.replaceAll("(?i)\\beyJ[A-Za-z0-9_-]+\\.[A-Za-z0-9_-]+\\.[A-Za-z0-9_-]+\\b", "[REDACTED_JWT]");
        result = result.replaceAll("(?i)\\b[A-Z0-9._%+-]+@[A-Z0-9.-]+\\.[A-Z]{2,}\\b", "[REDACTED_EMAIL]");
        result = result.replaceAll("(?<!\\d)1\\d{10}(?!\\d)", "[REDACTED_PHONE]");
        result = result.replaceAll("(?i)(resume|cv|answer|response)\\s*(?:raw|original|full|content|text)?\\s*[:=：]\\s*\\S+", "$1=[REDACTED]");
        return safe(result, 2000);
    }

    private String normalizeResult(String result) {
        String value = result == null ? "FAILURE" : result.trim().toUpperCase(Locale.ROOT);
        return switch (value) {
            case "SUCCESS", "FAILURE", "DENIED" -> value;
            default -> "FAILURE";
        };
    }

    private String safe(String value, int max) {
        if (value == null) return null;
        String trimmed = value.trim();
        return trimmed.length() <= max ? trimmed : trimmed.substring(0, max);
    }

    private boolean hasText(String value) {
        return StringUtils.hasText(value);
    }

    private LocalDateTime parseTime(String value) {
        try {
            return LocalDateTime.parse(value.trim());
        } catch (DateTimeParseException exception) {
            throw new IllegalArgumentException("审计时间筛选格式无效");
        }
    }

    private record RequestContext(String ipAddress, String userAgent) {
    }
}
