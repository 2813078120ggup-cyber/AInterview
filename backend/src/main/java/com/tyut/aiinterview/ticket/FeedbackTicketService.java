package com.tyut.aiinterview.ticket;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.tyut.aiinterview.common.BusinessException;
import com.tyut.aiinterview.common.PageResult;
import com.tyut.aiinterview.config.StorageProperties;
import com.tyut.aiinterview.domain.FeedbackTicket;
import com.tyut.aiinterview.domain.FeedbackTicketActivity;
import com.tyut.aiinterview.domain.FeedbackTicketAttachment;
import com.tyut.aiinterview.domain.FeedbackTicketReadState;
import com.tyut.aiinterview.domain.MediaFile;
import com.tyut.aiinterview.domain.Role;
import com.tyut.aiinterview.domain.UserAccount;
import com.tyut.aiinterview.domain.UserRole;
import com.tyut.aiinterview.mapper.FeedbackTicketActivityMapper;
import com.tyut.aiinterview.mapper.FeedbackTicketAttachmentMapper;
import com.tyut.aiinterview.mapper.FeedbackTicketMapper;
import com.tyut.aiinterview.mapper.FeedbackTicketReadStateMapper;
import com.tyut.aiinterview.mapper.MediaFileMapper;
import com.tyut.aiinterview.mapper.RoleMapper;
import com.tyut.aiinterview.mapper.UserMapper;
import com.tyut.aiinterview.mapper.UserRoleMapper;
import com.tyut.aiinterview.media.LocalObjectStorage;
import com.tyut.aiinterview.media.UploadSecurityValidator;
import com.tyut.aiinterview.notification.SiteNotificationService;
import com.tyut.aiinterview.security.CurrentUser;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.core.io.Resource;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

@Service
public class FeedbackTicketService {
    private static final DateTimeFormatter TICKET_DATE = DateTimeFormatter.ofPattern("yyyyMMdd");
    private static final long MAX_SCREENSHOT_BYTES = 10L * 1024 * 1024;
    private static final int MAX_SCREENSHOTS = 5;
    private static final long MAX_SCREENSHOT_TOTAL_BYTES = 30L * 1024 * 1024;

    private final FeedbackTicketMapper ticketMapper;
    private final FeedbackTicketActivityMapper activityMapper;
    private final FeedbackTicketAttachmentMapper attachmentMapper;
    private final FeedbackTicketReadStateMapper readStateMapper;
    private final UserMapper userMapper;
    private final UserRoleMapper userRoleMapper;
    private final RoleMapper roleMapper;
    private final MediaFileMapper mediaMapper;
    private final CurrentUser currentUser;
    private final SiteNotificationService notificationService;
    private final LocalObjectStorage storage;
    private final StorageProperties storageProperties;
    private final UploadSecurityValidator uploadSecurity;

    public FeedbackTicketService(FeedbackTicketMapper ticketMapper,
                                 FeedbackTicketActivityMapper activityMapper,
                                 FeedbackTicketAttachmentMapper attachmentMapper,
                                 FeedbackTicketReadStateMapper readStateMapper,
                                 UserMapper userMapper,
                                 UserRoleMapper userRoleMapper,
                                 RoleMapper roleMapper,
                                 MediaFileMapper mediaMapper,
                                 CurrentUser currentUser,
                                 SiteNotificationService notificationService,
                                 LocalObjectStorage storage,
                                 StorageProperties storageProperties,
                                 UploadSecurityValidator uploadSecurity) {
        this.ticketMapper = ticketMapper;
        this.activityMapper = activityMapper;
        this.attachmentMapper = attachmentMapper;
        this.readStateMapper = readStateMapper;
        this.userMapper = userMapper;
        this.userRoleMapper = userRoleMapper;
        this.roleMapper = roleMapper;
        this.mediaMapper = mediaMapper;
        this.currentUser = currentUser;
        this.notificationService = notificationService;
        this.storage = storage;
        this.storageProperties = storageProperties;
        this.uploadSecurity = uploadSecurity;
    }

    @Transactional
    public FeedbackTicketDtos.Detail create(FeedbackTicketDtos.CreateRequest request) {
        requireCandidate();
        validateType(request.ticketType());
        LocalDateTime now = LocalDateTime.now();
        FeedbackTicket ticket = new FeedbackTicket();
        ticket.setCreatorId(currentUser.id());
        ticket.setTicketType(request.ticketType());
        ticket.setTitle(trimToEmpty(request.title()));
        ticket.setDescription(trimToEmpty(request.description()));
        ticket.setStatus(FeedbackTicket.DRAFT);
        // ticket_no is non-null, while the final sequential number depends on the auto-generated id.
        ticket.setTicketNo("TMP-" + UUID.randomUUID().toString().replace("-", "").substring(0, 20));
        ticket.setVersion(0);
        ticket.setLastActivityAt(now);
        ticket.setCreatedAt(now);
        ticket.setUpdatedAt(now);
        ticketMapper.insert(ticket);
        ticket.setTicketNo(ticketNo(ticket.getId(), now.toLocalDate()));
        ticketMapper.updateById(ticket);
        return detail(ticket);
    }

    @Transactional
    public FeedbackTicketDtos.Detail updateDraft(Long id, FeedbackTicketDtos.UpdateDraftRequest request) {
        FeedbackTicket ticket = requireTicketForUpdate(id);
        validateType(request.ticketType());
        ticket.setTicketType(request.ticketType());
        ticket.setTitle(request.title().trim());
        ticket.setDescription(request.description().trim());
        ticket.setUpdatedAt(LocalDateTime.now());
        ticketMapper.updateById(ticket);
        return detail(ticket);
    }

    @Transactional
    public void deleteDraft(Long id) {
        FeedbackTicket ticket = requireTicketForUpdate(id);
        if (!FeedbackTicket.DRAFT.equals(ticket.getStatus())) throw BusinessException.badRequest("只有草稿可以删除");
        ticketMapper.deleteById(ticket.getId());
    }

    @Transactional
    public FeedbackTicketDtos.Detail submit(Long id) {
        FeedbackTicket ticket = ticketMapper.selectForUpdate(id);
        requireOwner(ticket);
        if (!FeedbackTicket.DRAFT.equals(ticket.getStatus())) throw BusinessException.badRequest("只有草稿可以提交");
        validateSubmission(ticket);
        LocalDateTime now = LocalDateTime.now();
        String oldStatus = ticket.getStatus();
        ticket.setStatus(FeedbackTicket.PENDING);
        ticket.setSubmittedAt(now);
        ticket.setLastActivityAt(now);
        ticket.setUpdatedAt(now);
        ticket.setVersion(ticket.getVersion() + 1);
        ticketMapper.updateById(ticket);
        FeedbackTicketActivity activity = activity(ticket, FeedbackTicketActivity.SUBMITTED, currentUser.id(),
                "候选人提交了反馈工单", oldStatus, ticket.getStatus(), null, null, null);
        notifyAdmins(ticket, activity, "新的反馈工单", "有新的反馈工单等待处理");
        return detail(ticket);
    }

    public PageResult<FeedbackTicketDtos.TicketSummary> mine(FeedbackTicketDtos.TicketQuery query) {
        requireCandidate();
        long pageNo = pageNo(query.pageNo());
        long pageSize = pageSize(query.pageSize());
        LambdaQueryWrapper<FeedbackTicket> wrapper = baseQuery(query)
                .eq(FeedbackTicket::getCreatorId, currentUser.id())
                .orderByDesc(FeedbackTicket::getLastActivityAt)
                .orderByDesc(FeedbackTicket::getId);
        Page<FeedbackTicket> result = ticketMapper.selectPage(new Page<>(pageNo, pageSize), wrapper);
        return PageResult.of(toSummaries(result.getRecords()), result.getTotal(), pageNo, pageSize);
    }

    public PageResult<FeedbackTicketDtos.TicketSummary> adminPage(FeedbackTicketDtos.TicketQuery query) {
        requireAdmin();
        long pageNo = pageNo(query.pageNo());
        long pageSize = pageSize(query.pageSize());
        LambdaQueryWrapper<FeedbackTicket> wrapper = baseQuery(query)
                .orderByDesc(FeedbackTicket::getLastActivityAt)
                .orderByDesc(FeedbackTicket::getId);
        applyAssigneeFilter(wrapper, query.assigneeId());
        Page<FeedbackTicket> result = ticketMapper.selectPage(new Page<>(pageNo, pageSize), wrapper);
        return PageResult.of(toSummaries(result.getRecords()), result.getTotal(), pageNo, pageSize);
    }

    public FeedbackTicketDtos.Detail get(Long id) {
        FeedbackTicket ticket = requireReadable(id);
        return detail(ticket);
    }

    public List<FeedbackTicketDtos.Activity> activities(Long id, FeedbackTicketDtos.ActivityQuery query) {
        FeedbackTicket ticket = requireReadable(id);
        long afterId = query.afterId() == null ? 0 : query.afterId();
        int limit = query.limit() == null ? 100 : Math.min(200, Math.max(1, query.limit()));
        List<FeedbackTicketActivity> records = activityMapper.selectAfter(ticket.getId(), afterId == 0 ? null : afterId, limit);
        return toActivities(records, ticket.getId());
    }

    @Transactional
    public FeedbackTicketDtos.Activity message(Long id, FeedbackTicketDtos.MessageRequest request) {
        FeedbackTicket ticket = ticketMapper.selectForUpdate(id);
        requireReadable(ticket);
        if (FeedbackTicket.CLOSED.equals(ticket.getStatus())) throw BusinessException.badRequest("工单已关闭，不能继续留言");
        FeedbackTicketActivity duplicate = activityMapper.selectOne(new LambdaQueryWrapper<FeedbackTicketActivity>()
                .eq(FeedbackTicketActivity::getTicketId, id)
                .eq(FeedbackTicketActivity::getClientRequestId, request.clientRequestId())
                .last("LIMIT 1"));
        if (duplicate != null) return toActivities(List.of(duplicate), id).get(0);
        LocalDateTime now = LocalDateTime.now();
        FeedbackTicketActivity activity = activity(ticket, FeedbackTicketActivity.COMMENT, currentUser.id(),
                request.content().trim(), null, null, null, null, request.clientRequestId());
        ticket.setLastActivityAt(now);
        ticket.setUpdatedAt(now);
        ticketMapper.updateById(ticket);
        notifyCounterpart(ticket, activity);
        return toActivities(List.of(activity), id).get(0);
    }

    @Transactional
    public FeedbackTicketDtos.Detail assign(Long id, FeedbackTicketDtos.AssigneeRequest request) {
        requireAdmin();
        FeedbackTicket ticket = ticketMapper.selectForUpdate(id);
        requireReadable(ticket);
        if (FeedbackTicket.CLOSED.equals(ticket.getStatus())) throw BusinessException.badRequest("已关闭工单不能转派");
        ensureVersion(ticket, request.version());
        if (request.assigneeId() != null) requireActiveAdmin(request.assigneeId());
        Long oldAssignee = ticket.getAssigneeId();
        ticket.setAssigneeId(request.assigneeId());
        ticket.setLastActivityAt(LocalDateTime.now());
        ticket.setUpdatedAt(LocalDateTime.now());
        ticket.setVersion(ticket.getVersion() + 1);
        ticketMapper.updateById(ticket);
        FeedbackTicketActivity activity = activity(ticket, FeedbackTicketActivity.ASSIGNMENT, currentUser.id(),
                assignmentText(oldAssignee, request.assigneeId()), null, null, oldAssignee, request.assigneeId(), null);
        if (request.assigneeId() != null) {
            notificationService.create(request.assigneeId(), "FEEDBACK_TICKET_ASSIGNED", "新的反馈工单",
                    "工单 " + ticket.getTicketNo() + " 已转派给你", "FEEDBACK_TICKET", ticket.getId(), "assign:" + activity.getId());
        }
        if (ticket.getCreatorId() != null) {
            notificationService.create(ticket.getCreatorId(), "FEEDBACK_TICKET_ASSIGNED", "工单处理人已更新",
                    "工单 " + ticket.getTicketNo() + " 的处理人已更新", "FEEDBACK_TICKET", ticket.getId(), "assign-candidate:" + activity.getId());
        }
        return detail(ticket);
    }

    @Transactional
    public FeedbackTicketDtos.Detail changeStatus(Long id, FeedbackTicketDtos.StatusRequest request) {
        requireAdmin();
        FeedbackTicket ticket = ticketMapper.selectForUpdate(id);
        requireReadable(ticket);
        ensureVersion(ticket, request.version());
        String target = request.targetStatus().trim().toUpperCase();
        ensureTransition(ticket.getStatus(), target);
        if ((FeedbackTicket.RESOLVED.equals(target) || FeedbackTicket.CLOSED.equals(target))
                && !StringUtils.hasText(request.resolution())) {
            throw BusinessException.badRequest("解决或关闭工单时必须填写处理说明");
        }
        LocalDateTime now = LocalDateTime.now();
        String oldStatus = ticket.getStatus();
        ticket.setStatus(target);
        if (FeedbackTicket.PROCESSING.equals(target) && ticket.getProcessingAt() == null) ticket.setProcessingAt(now);
        if (FeedbackTicket.RESOLVED.equals(target)) ticket.setResolvedAt(now);
        if (FeedbackTicket.CLOSED.equals(target)) ticket.setClosedAt(now);
        if (StringUtils.hasText(request.resolution())) ticket.setResolution(request.resolution().trim());
        ticket.setLastActivityAt(now);
        ticket.setUpdatedAt(now);
        ticket.setVersion(ticket.getVersion() + 1);
        ticketMapper.updateById(ticket);
        FeedbackTicketActivity activity = activity(ticket, FeedbackTicketActivity.STATUS_CHANGE, currentUser.id(),
                "管理员将工单状态从“" + oldStatus + "”改为“" + target + "”" +
                        (StringUtils.hasText(request.resolution()) ? "：" + request.resolution().trim() : ""),
                oldStatus, target, null, null, null);
        notificationService.create(ticket.getCreatorId(), "FEEDBACK_TICKET_STATUS_CHANGED", "反馈工单状态已更新",
                "工单 " + ticket.getTicketNo() + " 的状态已更新为“" + target + "”", "FEEDBACK_TICKET", ticket.getId(), "status:" + activity.getId());
        return detail(ticket);
    }

    @Transactional
    public void markRead(Long id, Long activityId) {
        FeedbackTicket ticket = requireReadable(id);
        Long cursor = activityId;
        if (cursor == null) {
            List<FeedbackTicketActivity> latest = activityMapper.selectAfter(id, null, 200);
            if (!latest.isEmpty()) cursor = latest.get(latest.size() - 1).getId();
        } else {
            FeedbackTicketActivity activity = activityMapper.selectById(cursor);
            if (activity == null || !id.equals(activity.getTicketId())) throw BusinessException.badRequest("阅读位置不属于该工单");
        }
        if (cursor == null) return;
        FeedbackTicketReadState state = readStateMapper.selectOne(new LambdaQueryWrapper<FeedbackTicketReadState>()
                .eq(FeedbackTicketReadState::getTicketId, id)
                .eq(FeedbackTicketReadState::getUserId, currentUser.id())
                .last("LIMIT 1"));
        if (state == null) {
            state = new FeedbackTicketReadState();
            state.setTicketId(ticket.getId());
            state.setUserId(currentUser.id());
            state.setLastReadActivityId(cursor);
            state.setUpdatedAt(LocalDateTime.now());
            readStateMapper.insert(state);
        } else if (state.getLastReadActivityId() == null || state.getLastReadActivityId() < cursor) {
            state.setLastReadActivityId(cursor);
            state.setUpdatedAt(LocalDateTime.now());
            readStateMapper.update(state, new LambdaQueryWrapper<FeedbackTicketReadState>()
                    .eq(FeedbackTicketReadState::getTicketId, id)
                    .eq(FeedbackTicketReadState::getUserId, currentUser.id()));
        }
    }

    public List<FeedbackTicketDtos.Assignee> assignees() {
        requireAdmin();
        List<Long> ids = activeAdminIds();
        if (ids.isEmpty()) return List.of();
        return userMapper.selectBatchIds(ids).stream()
                .filter(user -> user.getStatus() != null && user.getStatus() == 1)
                .sorted((left, right) -> left.getRealName().compareToIgnoreCase(right.getRealName()))
                .map(user -> new FeedbackTicketDtos.Assignee(user.getId(), user.getUsername(), user.getRealName()))
                .toList();
    }

    @Transactional
    public FeedbackTicketDtos.Attachment uploadAttachment(Long id, MultipartFile file) {
        FeedbackTicket ticket = requireReadable(id);
        if (FeedbackTicket.CLOSED.equals(ticket.getStatus())) throw BusinessException.badRequest("已关闭工单不能上传附件");
        if (!currentUser.hasRole("ADMIN") && !currentUser.id().equals(ticket.getCreatorId())) {
            throw BusinessException.forbidden("无权上传该工单附件");
        }
        if (file == null || file.isEmpty()) throw BusinessException.badRequest("截图不能为空");
        if (file.getSize() > MAX_SCREENSHOT_BYTES) throw BusinessException.badRequest("单张截图不能超过 10MB");
        List<FeedbackTicketAttachment> existing = attachmentMapper.selectList(new LambdaQueryWrapper<FeedbackTicketAttachment>().eq(FeedbackTicketAttachment::getTicketId, id));
        if (existing.size() >= MAX_SCREENSHOTS) throw BusinessException.badRequest("每个工单最多上传 5 张截图");
        long totalBytes = existing.stream().map(FeedbackTicketAttachment::getMediaId).map(mediaMapper::selectById)
                .filter(Objects::nonNull).mapToLong(MediaFile::getSizeBytes).sum();
        if (totalBytes + file.getSize() > MAX_SCREENSHOT_TOTAL_BYTES) throw BusinessException.badRequest("工单截图总大小不能超过 30MB");
        UploadSecurityValidator.ValidatedUpload validated = uploadSecurity.validateMedia(file);
        if (!validated.contentType().startsWith("image/")) throw BusinessException.badRequest("工单附件只能是图片");
        String key = null;
        try (InputStream input = file.getInputStream()) {
            key = storage.save(validated.extension(), input);
            MediaFile media = new MediaFile();
            media.setOwnerId(currentUser.id());
            media.setBucketName("local");
            media.setObjectKey(key);
            media.setOriginalName(validated.originalName());
            media.setContentType(validated.contentType());
            media.setMediaType("image");
            media.setSizeBytes(file.getSize());
            media.setChecksumSha256(sha256(storage.path(key)));
            media.setStatus(MediaFile.AVAILABLE);
            mediaMapper.insert(media);
            FeedbackTicketAttachment attachment = new FeedbackTicketAttachment();
            attachment.setTicketId(id);
            attachment.setMediaId(media.getId());
            attachment.setUploaderId(currentUser.id());
            attachment.setCreatedAt(LocalDateTime.now());
            attachmentMapper.insert(attachment);
            return toAttachment(attachment, media);
        } catch (IOException exception) {
            if (key != null) storage.delete(key);
            throw new IllegalStateException("保存工单截图失败", exception);
        } catch (RuntimeException exception) {
            if (key != null) storage.delete(key);
            throw exception;
        }
    }

    public AttachmentContent attachmentContent(Long ticketId, Long attachmentId) {
        requireReadable(ticketId);
        FeedbackTicketAttachment attachment = attachmentMapper.selectById(attachmentId);
        if (attachment == null || !ticketId.equals(attachment.getTicketId())) throw BusinessException.notFound("工单附件不存在");
        MediaFile media = mediaMapper.selectById(attachment.getMediaId());
        if (media == null || media.getStatus() != MediaFile.AVAILABLE) throw BusinessException.notFound("工单附件不可用");
        return new AttachmentContent(storage.resource(media.getObjectKey()), media.getContentType(), media.getOriginalName());
    }

    private FeedbackTicketDtos.Detail detail(FeedbackTicket ticket) {
        List<FeedbackTicketActivity> activities = activityMapper.selectAfter(ticket.getId(), null, 200);
        List<FeedbackTicketAttachment> attachments = attachmentMapper.selectList(new LambdaQueryWrapper<FeedbackTicketAttachment>()
                .eq(FeedbackTicketAttachment::getTicketId, ticket.getId()).orderByAsc(FeedbackTicketAttachment::getId));
        Map<Long, MediaFile> media = mediaMap(attachments.stream().map(FeedbackTicketAttachment::getMediaId).toList());
        List<FeedbackTicketDtos.Attachment> initialAttachments = attachments.stream()
                .filter(item -> item.getActivityId() == null)
                .map(item -> toAttachment(item, media.get(item.getMediaId())))
                .toList();
        List<FeedbackTicketDtos.Activity> activityViews = toActivities(activities, ticket.getId(), attachments, media);
        return new FeedbackTicketDtos.Detail(summary(ticket), ticket.getDescription(), ticket.getResolution(), ticket.getVersion(),
                initialAttachments, activityViews, permissions(ticket));
    }

    private List<FeedbackTicketDtos.Activity> toActivities(List<FeedbackTicketActivity> activities, Long ticketId) {
        List<FeedbackTicketAttachment> attachments = attachmentMapper.selectList(new LambdaQueryWrapper<FeedbackTicketAttachment>()
                .eq(FeedbackTicketAttachment::getTicketId, ticketId));
        Map<Long, MediaFile> media = mediaMap(attachments.stream().map(FeedbackTicketAttachment::getMediaId).toList());
        return toActivities(activities, ticketId, attachments, media);
    }

    private List<FeedbackTicketDtos.Activity> toActivities(List<FeedbackTicketActivity> activities, Long ticketId,
                                                           List<FeedbackTicketAttachment> attachments,
                                                           Map<Long, MediaFile> media) {
        Map<Long, String> names = userNames(activities.stream().map(FeedbackTicketActivity::getActorId).toList());
        Map<Long, List<FeedbackTicketAttachment>> byActivity = attachments.stream()
                .filter(item -> item.getActivityId() != null)
                .collect(Collectors.groupingBy(FeedbackTicketAttachment::getActivityId));
        return activities.stream().map(item -> new FeedbackTicketDtos.Activity(item.getId(), item.getTicketId(), item.getActorId(),
                names.getOrDefault(item.getActorId(), "系统"), item.getActivityType(), item.getContent(), item.getFromStatus(),
                item.getToStatus(), item.getFromAssigneeId(), item.getToAssigneeId(), item.getCreatedAt(),
                byActivity.getOrDefault(item.getId(), List.of()).stream().map(attachment -> toAttachment(attachment, media.get(attachment.getMediaId()))).toList())).toList();
    }

    private List<FeedbackTicketDtos.TicketSummary> toSummaries(List<FeedbackTicket> records) {
        if (records.isEmpty()) return List.of();
        Map<Long, String> names = userNames(records.stream().flatMap(item -> java.util.stream.Stream.of(item.getCreatorId(), item.getAssigneeId())).toList());
        return records.stream().map(item -> new FeedbackTicketDtos.TicketSummary(item.getId(), item.getTicketNo(), item.getCreatorId(),
                names.getOrDefault(item.getCreatorId(), "未知用户"), item.getTicketType(), item.getTitle(), item.getStatus(), item.getAssigneeId(),
                names.get(item.getAssigneeId()), item.getLastActivityAt(), item.getCreatedAt(), activityMapper.countUnread(item.getId(), currentUser.id()))).toList();
    }

    private FeedbackTicketDtos.TicketSummary summary(FeedbackTicket ticket) {
        Map<Long, String> names = userNames(java.util.stream.Stream.of(ticket.getCreatorId(), ticket.getAssigneeId()).filter(Objects::nonNull).toList());
        return new FeedbackTicketDtos.TicketSummary(ticket.getId(), ticket.getTicketNo(), ticket.getCreatorId(),
                names.getOrDefault(ticket.getCreatorId(), "未知用户"), ticket.getTicketType(), ticket.getTitle(), ticket.getStatus(),
                ticket.getAssigneeId(), names.get(ticket.getAssigneeId()), ticket.getLastActivityAt(), ticket.getCreatedAt(),
                activityMapper.countUnread(ticket.getId(), currentUser.id()));
    }

    private FeedbackTicketDtos.Permissions permissions(FeedbackTicket ticket) {
        boolean owner = currentUser.id().equals(ticket.getCreatorId());
        boolean admin = currentUser.hasRole("ADMIN");
        boolean draft = FeedbackTicket.DRAFT.equals(ticket.getStatus());
        boolean closed = FeedbackTicket.CLOSED.equals(ticket.getStatus());
        return new FeedbackTicketDtos.Permissions(owner && draft, owner && draft, !closed && (owner || admin),
                admin && !closed, admin && !closed, admin && !closed);
    }

    private FeedbackTicketActivity activity(FeedbackTicket ticket, String type, Long actorId, String content,
                                            String fromStatus, String toStatus, Long fromAssignee, Long toAssignee,
                                            String clientRequestId) {
        FeedbackTicketActivity item = new FeedbackTicketActivity();
        item.setTicketId(ticket.getId());
        item.setActorId(actorId);
        item.setActivityType(type);
        item.setContent(content);
        item.setFromStatus(fromStatus);
        item.setToStatus(toStatus);
        item.setFromAssigneeId(fromAssignee);
        item.setToAssigneeId(toAssignee);
        item.setClientRequestId(clientRequestId);
        item.setCreatedAt(LocalDateTime.now());
        activityMapper.insert(item);
        return item;
    }

    private void notifyAdmins(FeedbackTicket ticket, FeedbackTicketActivity activity, String title, String content) {
        for (Long adminId : activeAdminIds()) {
            notificationService.create(adminId, "FEEDBACK_TICKET_CREATED", title, content,
                    "FEEDBACK_TICKET", ticket.getId(), "activity:" + activity.getId() + ":" + adminId);
        }
    }

    private void notifyCounterpart(FeedbackTicket ticket, FeedbackTicketActivity activity) {
        if (currentUser.id().equals(ticket.getCreatorId())) {
            if (ticket.getAssigneeId() != null) {
                notificationService.create(ticket.getAssigneeId(), "FEEDBACK_TICKET_MESSAGE", "候选人回复了工单",
                        "工单 " + ticket.getTicketNo() + " 有新的候选人回复", "FEEDBACK_TICKET", ticket.getId(), "message:" + activity.getId());
            } else {
                notifyAdmins(ticket, activity, "候选人回复了工单", "有未分配工单收到新的候选人回复");
            }
        } else {
            notificationService.create(ticket.getCreatorId(), "FEEDBACK_TICKET_MESSAGE", "管理员回复了工单",
                    "工单 " + ticket.getTicketNo() + " 有新的管理员回复", "FEEDBACK_TICKET", ticket.getId(), "message:" + activity.getId());
        }
    }

    private void requireCandidate() {
        if (!currentUser.hasRole("CANDIDATE")) throw BusinessException.forbidden("仅候选人可以提交反馈工单");
    }

    private void requireAdmin() {
        if (!currentUser.hasRole("ADMIN")) throw BusinessException.forbidden("仅管理员可以处理反馈工单");
    }

    private FeedbackTicket requireTicketForUpdate(Long id) {
        FeedbackTicket ticket = ticketMapper.selectForUpdate(id);
        requireOwner(ticket);
        if (!FeedbackTicket.DRAFT.equals(ticket.getStatus())) throw BusinessException.badRequest("只有草稿可以编辑");
        return ticket;
    }

    private FeedbackTicket requireReadable(Long id) {
        FeedbackTicket ticket = ticketMapper.selectById(id);
        return requireReadable(ticket);
    }

    private FeedbackTicket requireReadable(FeedbackTicket ticket) {
        if (ticket == null) throw BusinessException.notFound("反馈工单不存在");
        if (!currentUser.hasRole("ADMIN") && !currentUser.id().equals(ticket.getCreatorId())) {
            throw BusinessException.forbidden("无权访问该反馈工单");
        }
        return ticket;
    }

    private void requireOwner(FeedbackTicket ticket) {
        if (ticket == null) throw BusinessException.notFound("反馈工单不存在");
        if (!currentUser.id().equals(ticket.getCreatorId())) throw BusinessException.forbidden("无权操作该反馈工单");
    }

    private void ensureVersion(FeedbackTicket ticket, Integer version) {
        if (!Objects.equals(ticket.getVersion(), version)) throw BusinessException.conflict("工单已被其他管理员更新，请刷新后重试");
    }

    private void ensureTransition(String from, String to) {
        Set<String> allowed = switch (from) {
            case FeedbackTicket.PENDING -> Set.of(FeedbackTicket.PROCESSING, FeedbackTicket.CLOSED);
            case FeedbackTicket.PROCESSING -> Set.of(FeedbackTicket.RESOLVED, FeedbackTicket.CLOSED);
            case FeedbackTicket.RESOLVED -> Set.of(FeedbackTicket.PROCESSING, FeedbackTicket.CLOSED);
            default -> Set.of();
        };
        if (!allowed.contains(to)) throw BusinessException.badRequest("不允许从“" + from + "”变更为“" + to + "”");
    }

    private void validateSubmission(FeedbackTicket ticket) {
        if (!StringUtils.hasText(ticket.getTitle())) throw BusinessException.badRequest("请填写工单标题");
        if (!StringUtils.hasText(ticket.getDescription())) throw BusinessException.badRequest("请填写问题描述");
        validateType(ticket.getTicketType());
    }

    private void validateType(String type) {
        if (!Set.of(FeedbackTicket.TYPE_INTERVIEW_FAILURE, FeedbackTicket.TYPE_FEATURE_SUGGESTION, FeedbackTicket.TYPE_BUG_REPORT).contains(type)) {
            throw BusinessException.badRequest("工单类型不合法");
        }
    }

    private LambdaQueryWrapper<FeedbackTicket> baseQuery(FeedbackTicketDtos.TicketQuery query) {
        LambdaQueryWrapper<FeedbackTicket> wrapper = new LambdaQueryWrapper<>();
        if (query.keyword() != null && !query.keyword().isBlank()) {
            String keyword = query.keyword().trim();
            wrapper.and(item -> item.like(FeedbackTicket::getTicketNo, keyword)
                    .or().like(FeedbackTicket::getTitle, keyword)
                    .or().like(FeedbackTicket::getDescription, keyword));
        }
        if (query.ticketType() != null && !query.ticketType().isBlank()) wrapper.eq(FeedbackTicket::getTicketType, query.ticketType());
        if (query.status() != null && !query.status().isBlank()) wrapper.eq(FeedbackTicket::getStatus, query.status());
        return wrapper;
    }

    private void applyAssigneeFilter(LambdaQueryWrapper<FeedbackTicket> wrapper, String value) {
        if (value == null || value.isBlank()) return;
        if ("unassigned".equalsIgnoreCase(value)) wrapper.isNull(FeedbackTicket::getAssigneeId);
        else if ("me".equalsIgnoreCase(value)) wrapper.eq(FeedbackTicket::getAssigneeId, currentUser.id());
        else {
            try { wrapper.eq(FeedbackTicket::getAssigneeId, Long.valueOf(value)); }
            catch (NumberFormatException exception) { throw BusinessException.badRequest("处理人参数不合法"); }
        }
    }

    private void requireActiveAdmin(Long id) {
        if (!activeAdminIds().contains(id)) throw BusinessException.badRequest("处理人不存在或不是启用的管理员");
    }

    private List<Long> activeAdminIds() {
        Role admin = roleMapper.selectOne(new LambdaQueryWrapper<Role>().eq(Role::getRoleCode, "ADMIN").last("LIMIT 1"));
        if (admin == null) return List.of();
        return userRoleMapper.selectList(new LambdaQueryWrapper<UserRole>().eq(UserRole::getRoleId, admin.getId())).stream()
                .map(UserRole::getUserId).distinct().filter(Objects::nonNull).filter(id -> {
                    UserAccount user = userMapper.selectById(id);
                    return user != null && Objects.equals(user.getStatus(), 1);
                }).toList();
    }

    private Map<Long, String> userNames(Collection<Long> ids) {
        List<Long> distinct = ids.stream().filter(Objects::nonNull).distinct().toList();
        if (distinct.isEmpty()) return Map.of();
        return userMapper.selectBatchIds(distinct).stream().collect(Collectors.toMap(UserAccount::getId, UserAccount::getRealName, (left, right) -> left));
    }

    private Map<Long, MediaFile> mediaMap(Collection<Long> ids) {
        List<Long> distinct = ids.stream().filter(Objects::nonNull).distinct().toList();
        if (distinct.isEmpty()) return Map.of();
        return mediaMapper.selectBatchIds(distinct).stream().collect(Collectors.toMap(MediaFile::getId, item -> item));
    }

    private FeedbackTicketDtos.Attachment toAttachment(FeedbackTicketAttachment attachment, MediaFile media) {
        if (attachment == null || media == null) return new FeedbackTicketDtos.Attachment(attachment == null ? null : attachment.getId(), null, "附件不可用", "application/octet-stream", 0L, null, attachment == null ? null : attachment.getCreatedAt());
        return new FeedbackTicketDtos.Attachment(attachment.getId(), media.getId(), media.getOriginalName(), media.getContentType(), media.getSizeBytes(),
                "/v1/tickets/" + attachment.getTicketId() + "/attachments/" + attachment.getId() + "/content", attachment.getCreatedAt());
    }

    private String assignmentText(Long oldAssignee, Long newAssignee) {
        if (newAssignee == null) return "管理员取消了工单分配";
        return oldAssignee == null ? "管理员将工单分配给了其他管理员" : "管理员变更了工单处理人";
    }

    private String ticketNo(Long id, LocalDate date) {
        return "FB-" + TICKET_DATE.format(date) + "-" + String.format("%06d", id);
    }

    private long pageNo(Long value) { return value == null ? 1 : Math.max(1, value); }
    private long pageSize(Long value) { return value == null ? 20 : Math.min(100, Math.max(1, value)); }
    private String trimToEmpty(String value) { return value == null ? "" : value.trim(); }

    private String sha256(java.nio.file.Path path) {
        try (InputStream input = Files.newInputStream(path)) {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] buffer = new byte[8192];
            int read;
            while ((read = input.read(buffer)) != -1) digest.update(buffer, 0, read);
            StringBuilder result = new StringBuilder();
            for (byte item : digest.digest()) result.append(String.format("%02x", item));
            return result.toString();
        } catch (IOException | NoSuchAlgorithmException exception) {
            throw new IllegalStateException("计算附件摘要失败", exception);
        }
    }

    public record AttachmentContent(Resource resource, String contentType, String originalName) {}
}
