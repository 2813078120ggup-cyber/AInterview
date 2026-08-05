package com.tyut.aiinterview.notification;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.tyut.aiinterview.common.BusinessException;
import com.tyut.aiinterview.domain.SiteNotification;
import com.tyut.aiinterview.mapper.SiteNotificationMapper;
import com.tyut.aiinterview.security.CurrentUser;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SiteNotificationService {
    private final SiteNotificationMapper mapper;
    private final CurrentUser currentUser;

    public SiteNotificationService(SiteNotificationMapper mapper, CurrentUser currentUser) {
        this.mapper = mapper;
        this.currentUser = currentUser;
    }

    @Transactional
    public void create(Long recipientId, String type, String title, String content,
                       String businessType, Long businessId, String dedupeKey) {
        if (recipientId == null || Objects.equals(recipientId, currentUser.id())) return;
        SiteNotification item = new SiteNotification();
        item.setRecipientId(recipientId);
        item.setNotificationType(type);
        item.setTitle(title);
        item.setContent(content);
        item.setBusinessType(businessType);
        item.setBusinessId(businessId);
        item.setDedupeKey(dedupeKey);
        item.setCreatedAt(LocalDateTime.now());
        try {
            mapper.insert(item);
        } catch (DuplicateKeyException ignored) {
            // A retried business request should not duplicate the same notification.
        }
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
        List<NotificationDtos.Notification> records = result.getRecords().stream()
                .map(item -> new NotificationDtos.Notification(item.getId(), item.getNotificationType(), item.getTitle(),
                        item.getContent(), item.getBusinessType(), item.getBusinessId(), item.getReadAt() != null,
                        item.getCreatedAt()))
                .toList();
        return new NotificationDtos.NotificationPage(records, result.getTotal(), pageNo, pageSize);
    }

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
