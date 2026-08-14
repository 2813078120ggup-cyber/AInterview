package com.tyut.aiinterview.notification;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.tyut.aiinterview.account.CandidateNotificationEvent;
import com.tyut.aiinterview.common.BusinessException;
import com.tyut.aiinterview.domain.SiteNotification;
import com.tyut.aiinterview.domain.Report;
import com.tyut.aiinterview.mapper.ReportMapper;
import com.tyut.aiinterview.mapper.SiteNotificationMapper;
import com.tyut.aiinterview.security.CurrentUser;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SiteNotificationService {
    private final SiteNotificationMapper mapper;
    private final ReportMapper reportMapper;
    private final CurrentUser currentUser;
    private final ApplicationEventPublisher eventPublisher;

    public SiteNotificationService(SiteNotificationMapper mapper, ReportMapper reportMapper, CurrentUser currentUser,
                                   ApplicationEventPublisher eventPublisher) {
        this.mapper = mapper;
        this.reportMapper = reportMapper;
        this.currentUser = currentUser;
        this.eventPublisher = eventPublisher;
    }

    @Transactional
    public void create(Long recipientId, String type, String title, String content,
                       String businessType, Long businessId, String dedupeKey) {
        CandidateNotificationEvent candidateEvent = CandidateNotificationEvent.parse(type);
        if (recipientId == null || candidateEvent == null && Objects.equals(recipientId, currentUser.id())) return;
        eventPublisher.publishEvent(new CandidateNotificationRequested(recipientId, type, candidateEvent, title, content,
                businessType, businessId, dedupeKey));
    }

    public long unreadCount() {
        return mapper.countUnread(currentUser.id());
    }

    public NotificationDtos.NotificationPage page(NotificationDtos.Query query) {
        long pageNo = query.pageNo() == null ? 1 : Math.max(1, query.pageNo());
        long pageSize = query.pageSize() == null ? 20 : Math.min(100, Math.max(1, query.pageSize()));
        LambdaQueryWrapper<SiteNotification> wrapper = new LambdaQueryWrapper<SiteNotification>()
                .eq(SiteNotification::getRecipientId, currentUser.id())
                .orderByDesc(SiteNotification::getCreatedAt)
                .orderByDesc(SiteNotification::getId);
        Page<SiteNotification> result = mapper.selectPage(new Page<>(pageNo, pageSize), wrapper);
        Audience audience = audience();
        Map<Long, Report> reports = reportsById(result.getRecords());
        List<NotificationDtos.Notification> records = result.getRecords().stream()
                .map(item -> new NotificationDtos.Notification(item.getId(), item.getNotificationType(), item.getTitle(),
                        item.getContent(), item.getBusinessType(), item.getBusinessId(),
                        actionPath(item, audience, reports), item.getReadAt() != null, item.getCreatedAt()))
                .toList();
        return new NotificationDtos.NotificationPage(records, result.getTotal(), pageNo, pageSize);
    }

    private Map<Long, Report> reportsById(List<SiteNotification> notifications) {
        List<Long> ids = notifications.stream()
                .filter(item -> item.getBusinessId() != null && item.getBusinessType() != null
                        && "REPORT".equalsIgnoreCase(item.getBusinessType().trim()))
                .map(SiteNotification::getBusinessId)
                .distinct()
                .toList();
        if (ids.isEmpty()) return Collections.emptyMap();
        return reportMapper.selectBatchIds(ids).stream()
                .collect(Collectors.toMap(Report::getId, Function.identity()));
    }

    private String actionPath(SiteNotification item, Audience audience, Map<Long, Report> reports) {
        Long businessId = item.getBusinessId();
        if (businessId == null || audience == Audience.UNSUPPORTED || item.getBusinessType() == null) return null;
        return switch (item.getBusinessType().trim().toUpperCase(Locale.ROOT)) {
            case "JOB_APPLICATION" -> switch (audience) {
                case CANDIDATE -> "/applications?applicationId=" + businessId;
                case COMPANY -> "/company/applications/" + businessId;
                case ADMIN -> "/admin/recruitment/applications/" + businessId;
                default -> null;
            };
            case "INTERVIEW" -> switch (audience) {
                case CANDIDATE -> "/candidate/interviews/" + businessId + "/room";
                case COMPANY -> "/company/interviews/AI-" + businessId;
                case ADMIN -> "/admin/interviews/" + businessId + "/review";
                default -> null;
            };
            case "REPORT" -> reportActionPath(reports.get(businessId), audience);
            case "FEEDBACK_TICKET" -> switch (audience) {
                case CANDIDATE -> "/candidate/tickets/" + businessId;
                case ADMIN -> "/admin/tickets/" + businessId;
                default -> null;
            };
            case "USER" -> switch (audience) {
                case CANDIDATE -> "/candidate/settings/security";
                case COMPANY -> "/company/account/security";
                case ADMIN -> "/admin/account/security";
                default -> null;
            };
            default -> null;
        };
    }

    private String reportActionPath(Report report, Audience audience) {
        if (report == null || report.getInterviewId() == null) return null;
        return switch (audience) {
            case CANDIDATE -> "/candidate/interviews/" + report.getInterviewId() + "/report";
            case COMPANY -> "/company/interviews/AI-" + report.getInterviewId();
            case ADMIN -> "/admin/interviews/" + report.getInterviewId() + "/review";
            default -> null;
        };
    }

    private Audience audience() {
        if (currentUser.hasRole("ADMIN")) return Audience.ADMIN;
        if (currentUser.hasCompanyRole()) return Audience.COMPANY;
        if (currentUser.hasRole("CANDIDATE")) return Audience.CANDIDATE;
        return Audience.UNSUPPORTED;
    }

    private enum Audience { CANDIDATE, COMPANY, ADMIN, UNSUPPORTED }

    @Transactional
    public void markRead(Long id) {
        SiteNotification item = mapper.selectById(id);
        if (item == null || !currentUser.id().equals(item.getRecipientId())) {
            throw BusinessException.notFound("通知不存在");
        }
        if (item.getReadAt() == null) {
            item.setReadAt(LocalDateTime.now());
            mapper.updateById(item);
        }
    }

    @Transactional
    public void markAllRead() {
        mapper.update(null, new com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper<SiteNotification>()
                .eq(SiteNotification::getRecipientId, currentUser.id())
                .isNull(SiteNotification::getReadAt)
                .set(SiteNotification::getReadAt, LocalDateTime.now()));
    }
}
