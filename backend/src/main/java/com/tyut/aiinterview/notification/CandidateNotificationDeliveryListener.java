package com.tyut.aiinterview.notification;

import com.tyut.aiinterview.account.AccountNotificationPreferenceService;
import com.tyut.aiinterview.domain.UserAccount;
import com.tyut.aiinterview.mapper.UserMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
public class CandidateNotificationDeliveryListener {
    private static final Logger log = LoggerFactory.getLogger(CandidateNotificationDeliveryListener.class);
    private final UserMapper userMapper;
    private final AccountNotificationPreferenceService preferenceService;
    private final CandidateNotificationDeliveryExecutor executor;

    public CandidateNotificationDeliveryListener(UserMapper userMapper,
                                                 AccountNotificationPreferenceService preferenceService,
                                                 CandidateNotificationDeliveryExecutor executor) {
        this.userMapper = userMapper;
        this.preferenceService = preferenceService;
        this.executor = executor;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void deliver(CandidateNotificationRequested request) {
        if (request == null || request.recipientId() == null) return;
        try {
            UserAccount user = userMapper.selectById(request.recipientId());
            if (user == null || !Integer.valueOf(1).equals(user.getStatus()) || user.getDeletedAt() != null) return;

            if (request.event() == null) {
                deliverSite(request, false);
                return;
            }
            boolean candidate = userMapper.hasActiveRole(user.getId(), "CANDIDATE");
            AccountNotificationPreferenceService.DeliveryPreference preference = candidate
                    ? preferenceService.deliveryPreference(user, request.event())
                    : new AccountNotificationPreferenceService.DeliveryPreference(true, false, false);
            if (preference.siteEnabled()) deliverSite(request, true);
            if (!candidate) return;
            if (preference.emailEnabled()) deliverEmail(user, request);
            if (preference.smsEnabled()) deliverSms(user, request);
        } catch (RuntimeException exception) {
            log.warn("Candidate notification dispatch failed event={} recipientId={}",
                    request.notificationType(), request.recipientId());
        }
    }

    private void deliverSite(CandidateNotificationRequested request, boolean audited) {
        try {
            executor.createSite(request);
            if (audited) safeAudit(request, "SITE", "SUCCESS");
        } catch (RuntimeException exception) {
            log.warn("Candidate notification site delivery failed event={} recipientId={}",
                    request.notificationType(), request.recipientId());
            if (audited) safeAudit(request, "SITE", "FAILURE");
        }
    }

    private void deliverEmail(UserAccount user, CandidateNotificationRequested request) {
        try {
            executor.sendEmail(user, request);
            safeAudit(request, "EMAIL", "SUCCESS");
        } catch (RuntimeException exception) {
            log.warn("Candidate notification email delivery failed event={} recipientId={}",
                    request.event(), request.recipientId());
            safeAudit(request, "EMAIL", "FAILURE");
        }
    }

    private void deliverSms(UserAccount user, CandidateNotificationRequested request) {
        try {
            executor.sendSms(user, request);
            safeAudit(request, "SMS", "SUCCESS");
        } catch (RuntimeException exception) {
            log.warn("Candidate notification sms delivery failed event={} recipientId={}",
                    request.event(), request.recipientId());
            safeAudit(request, "SMS", "FAILURE");
        }
    }

    private void safeAudit(CandidateNotificationRequested request, String channel, String result) {
        try {
            executor.audit(request, channel, result);
        } catch (RuntimeException exception) {
            log.warn("Candidate notification audit failed event={} channel={} result={}",
                    request.event(), channel, result);
        }
    }
}
