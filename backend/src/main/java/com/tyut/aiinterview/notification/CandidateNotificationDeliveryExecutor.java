package com.tyut.aiinterview.notification;

import com.tyut.aiinterview.auth.VerificationCodeService;
import com.tyut.aiinterview.domain.SiteNotification;
import com.tyut.aiinterview.domain.UserAccount;
import com.tyut.aiinterview.mapper.SiteNotificationMapper;
import com.tyut.aiinterview.observability.OperationAuditService;
import java.time.LocalDateTime;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CandidateNotificationDeliveryExecutor {
    private final SiteNotificationMapper siteNotificationMapper;
    private final VerificationCodeService verificationCodeService;
    private final OperationAuditService auditService;

    public CandidateNotificationDeliveryExecutor(SiteNotificationMapper siteNotificationMapper,
                                                 VerificationCodeService verificationCodeService,
                                                 OperationAuditService auditService) {
        this.siteNotificationMapper = siteNotificationMapper;
        this.verificationCodeService = verificationCodeService;
        this.auditService = auditService;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void createSite(CandidateNotificationRequested request) {
        SiteNotification item = new SiteNotification();
        item.setRecipientId(request.recipientId());
        item.setNotificationType(request.notificationType());
        item.setTitle(request.title());
        item.setContent(request.content());
        item.setBusinessType(request.businessType());
        item.setBusinessId(request.businessId());
        item.setDedupeKey(request.dedupeKey());
        item.setCreatedAt(LocalDateTime.now());
        try {
            siteNotificationMapper.insert(item);
        } catch (DuplicateKeyException ignored) {
            // Retried business actions must not duplicate an already delivered notification.
        }
    }

    public void sendEmail(UserAccount user, CandidateNotificationRequested request) {
        verificationCodeService.sendSecurityNotification("email", user.getEmail(), request.title(), request.content());
    }

    public void sendSms(UserAccount user, CandidateNotificationRequested request) {
        verificationCodeService.sendSecurityNotification("sms", user.getPhone(), request.title(), request.content());
    }

    public void audit(CandidateNotificationRequested request, String channel, String result) {
        String summary = "候选人通知投递；event=" + request.event().name() + "; channel=" + channel
                + "; result=" + result + "；未记录联系方式";
        if ("SUCCESS".equals(result)) {
            auditService.success("NOTIFICATION", "CANDIDATE_NOTIFICATION_DELIVERED", request.businessType(),
                    request.businessId(), null, summary);
        } else {
            auditService.failure("NOTIFICATION", "CANDIDATE_NOTIFICATION_DELIVERY_FAILED", request.businessType(),
                    request.businessId(), null, summary);
        }
    }
}
