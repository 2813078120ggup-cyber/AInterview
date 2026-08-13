package com.tyut.aiinterview.account;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.tyut.aiinterview.auth.VerificationCodeService;
import com.tyut.aiinterview.common.BusinessException;
import com.tyut.aiinterview.domain.UserAccount;
import com.tyut.aiinterview.domain.UserNotificationPreference;
import com.tyut.aiinterview.mapper.UserMapper;
import com.tyut.aiinterview.mapper.UserNotificationPreferenceMapper;
import com.tyut.aiinterview.observability.OperationAuditService;
import com.tyut.aiinterview.security.CurrentUser;
import java.time.LocalDateTime;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
public class AccountNotificationPreferenceService {
    private static final String SITE_POLICY = "关键业务与账户安全通知必须保留站内信。";
    private static final String EMAIL_SECURITY_POLICY = "已验证邮箱可用时，账户安全邮件不可关闭。";

    private final UserNotificationPreferenceMapper preferenceMapper;
    private final UserMapper userMapper;
    private final CurrentUser currentUser;
    private final VerificationCodeService verificationCodeService;
    private final OperationAuditService auditService;

    public AccountNotificationPreferenceService(UserNotificationPreferenceMapper preferenceMapper,
                                                UserMapper userMapper,
                                                CurrentUser currentUser,
                                                VerificationCodeService verificationCodeService) {
        this(preferenceMapper, userMapper, currentUser, verificationCodeService, null);
    }

    @Autowired
    public AccountNotificationPreferenceService(UserNotificationPreferenceMapper preferenceMapper,
                                                UserMapper userMapper,
                                                CurrentUser currentUser,
                                                VerificationCodeService verificationCodeService,
                                                OperationAuditService auditService) {
        this.preferenceMapper = preferenceMapper;
        this.userMapper = userMapper;
        this.currentUser = currentUser;
        this.verificationCodeService = verificationCodeService;
        this.auditService = auditService;
    }

    public AccountNotificationPreferenceDtos.Preferences get() {
        UserAccount user = requireCandidate();
        return response(user, rows(user.getId()));
    }

    @Transactional
    public AccountNotificationPreferenceDtos.Preferences update(
            AccountNotificationPreferenceDtos.UpdateRequest request) {
        UserAccount user = requireCandidate();
        ChannelState channels = channels(user);
        Map<CandidateNotificationEvent, UserNotificationPreference> current = rows(user.getId());
        Set<CandidateNotificationEvent> seen = new HashSet<>();

        for (AccountNotificationPreferenceDtos.UpdatePreference submitted : request.preferences()) {
            CandidateNotificationEvent event = CandidateNotificationEvent.parse(submitted.eventType());
            if (event == null || !seen.add(event)) {
                throw BusinessException.badRequest("通知事件不正确或重复");
            }
            if (submitted.version() < 0) throw BusinessException.badRequest("通知偏好版本不正确");

            UserNotificationPreference existing = current.get(event);
            boolean siteEnabled = event.siteForced() || Boolean.TRUE.equals(submitted.siteEnabled());
            boolean emailEnabled = channels.emailAvailable()
                    ? event.emailForced() || Boolean.TRUE.equals(submitted.emailEnabled())
                    : existing == null ? event.defaultEmail() : enabled(existing.getEmailEnabled());
            boolean smsEnabled = channels.smsAvailable()
                    ? Boolean.TRUE.equals(submitted.smsEnabled())
                    : existing == null ? event.defaultSms() : enabled(existing.getSmsEnabled());

            if (existing == null) {
                if (submitted.version() != 0) throw conflict();
                insert(user.getId(), event, siteEnabled, emailEnabled, smsEnabled);
            } else {
                int changed = preferenceMapper.updateWithVersion(user.getId(), event.name(), flag(siteEnabled),
                        flag(emailEnabled), flag(smsEnabled), submitted.version());
                if (changed != 1) throw conflict();
            }
        }

        if (auditService != null) {
            auditService.success("ACCOUNT", "NOTIFICATION_PREFERENCES_UPDATED", "USER", user.getId(), null,
                    "更新候选人通知偏好，共 " + seen.size() + " 个事件；未记录联系方式");
        }
        return response(user, rows(user.getId()));
    }

    public DeliveryPreference deliveryPreference(UserAccount user, CandidateNotificationEvent event) {
        if (user == null || user.getId() == null || !Integer.valueOf(1).equals(user.getStatus())
                || user.getDeletedAt() != null) {
            return new DeliveryPreference(event.defaultSite(), false, false);
        }
        ChannelState channels = channels(user);
        UserNotificationPreference row = preferenceMapper.selectOne(
                new LambdaQueryWrapper<UserNotificationPreference>()
                        .eq(UserNotificationPreference::getUserId, user.getId())
                        .eq(UserNotificationPreference::getEventType, event.name())
                        .last("LIMIT 1"));
        return effective(event, row, channels);
    }

    private UserAccount requireCandidate() {
        if (!currentUser.hasRole("CANDIDATE")) throw BusinessException.forbidden("仅候选人可以管理通知偏好");
        UserAccount user = userMapper.selectById(currentUser.id());
        if (user == null || !Integer.valueOf(1).equals(user.getStatus()) || user.getDeletedAt() != null) {
            throw BusinessException.forbidden("账户不可用");
        }
        return user;
    }

    private Map<CandidateNotificationEvent, UserNotificationPreference> rows(Long userId) {
        Map<CandidateNotificationEvent, UserNotificationPreference> result =
                new EnumMap<>(CandidateNotificationEvent.class);
        preferenceMapper.selectList(new LambdaQueryWrapper<UserNotificationPreference>()
                        .eq(UserNotificationPreference::getUserId, userId))
                .forEach(row -> {
                    CandidateNotificationEvent event = CandidateNotificationEvent.parse(row.getEventType());
                    if (event != null) result.put(event, row);
                });
        return result;
    }

    private AccountNotificationPreferenceDtos.Preferences response(
            UserAccount user, Map<CandidateNotificationEvent, UserNotificationPreference> rows) {
        ChannelState channels = channels(user);
        List<AccountNotificationPreferenceDtos.Preference> preferences =
                java.util.Arrays.stream(CandidateNotificationEvent.values())
                        .map(event -> view(event, rows.get(event), channels))
                        .toList();
        return new AccountNotificationPreferenceDtos.Preferences(
                new AccountNotificationPreferenceDtos.ChannelAvailability(true, channels.emailAvailable(),
                        channels.emailReason(), channels.smsAvailable(), channels.smsReason()), preferences);
    }

    private AccountNotificationPreferenceDtos.Preference view(
            CandidateNotificationEvent event, UserNotificationPreference row, ChannelState channels) {
        DeliveryPreference effective = effective(event, row, channels);
        return new AccountNotificationPreferenceDtos.Preference(event.name(), event.label(), event.description(),
                event.group(), effective.siteEnabled(), effective.emailEnabled(), effective.smsEnabled(),
                event.siteForced(), event.emailForced() && channels.emailAvailable(),
                event.siteForced() ? SITE_POLICY : null,
                event.emailForced() && channels.emailAvailable() ? EMAIL_SECURITY_POLICY : null,
                row == null || row.getVersion() == null ? 0 : row.getVersion());
    }

    private DeliveryPreference effective(CandidateNotificationEvent event, UserNotificationPreference row,
                                         ChannelState channels) {
        boolean configuredSite = row == null ? event.defaultSite() : enabled(row.getSiteEnabled());
        boolean configuredEmail = row == null ? event.defaultEmail() : enabled(row.getEmailEnabled());
        boolean configuredSms = row == null ? event.defaultSms() : enabled(row.getSmsEnabled());
        return new DeliveryPreference(event.siteForced() || configuredSite,
                channels.emailAvailable() && (event.emailForced() || configuredEmail),
                channels.smsAvailable() && configuredSms);
    }

    private ChannelState channels(UserAccount user) {
        boolean emailBound = StringUtils.hasText(user.getEmail());
        boolean emailVerified = emailBound && user.getEmailVerifiedAt() != null;
        boolean emailProvider = verificationCodeService.isNotificationChannelAvailable("email");
        boolean phoneBound = StringUtils.hasText(user.getPhone());
        boolean phoneVerified = phoneBound && user.getPhoneVerifiedAt() != null;
        boolean smsProvider = verificationCodeService.isNotificationChannelAvailable("sms");
        return new ChannelState(emailVerified && emailProvider,
                !emailBound ? "尚未绑定邮箱" : !emailVerified ? "邮箱尚未验证" : !emailProvider ? "邮件渠道暂不可用" : null,
                phoneVerified && smsProvider,
                !phoneBound ? "尚未绑定手机号" : !phoneVerified ? "手机号尚未验证" : !smsProvider ? "短信渠道暂不可用" : null);
    }

    private void insert(Long userId, CandidateNotificationEvent event, boolean site, boolean email, boolean sms) {
        UserNotificationPreference row = new UserNotificationPreference();
        row.setUserId(userId);
        row.setEventType(event.name());
        row.setSiteEnabled(flag(site));
        row.setEmailEnabled(flag(email));
        row.setSmsEnabled(flag(sms));
        row.setVersion(0);
        row.setCreatedAt(LocalDateTime.now());
        row.setUpdatedAt(LocalDateTime.now());
        try {
            preferenceMapper.insert(row);
        } catch (DuplicateKeyException exception) {
            throw conflict();
        }
    }

    private static int flag(boolean enabled) { return enabled ? 1 : 0; }
    private static boolean enabled(Integer value) { return Integer.valueOf(1).equals(value); }
    private static BusinessException conflict() {
        return BusinessException.conflict("通知偏好已被其他会话修改，请重新加载");
    }

    public record DeliveryPreference(boolean siteEnabled, boolean emailEnabled, boolean smsEnabled) {}
    private record ChannelState(boolean emailAvailable, String emailReason,
                                boolean smsAvailable, String smsReason) {}
}
