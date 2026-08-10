package com.tyut.aiinterview.learning;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tyut.aiinterview.common.BusinessException;
import com.tyut.aiinterview.common.PageResult;
import com.tyut.aiinterview.domain.LearningResource;
import com.tyut.aiinterview.domain.LearningResourceAnnotation;
import com.tyut.aiinterview.domain.LearningResourcePermission;
import com.tyut.aiinterview.domain.LearningResourceVersion;
import com.tyut.aiinterview.domain.MediaFile;
import com.tyut.aiinterview.domain.Role;
import com.tyut.aiinterview.domain.UserAccount;
import com.tyut.aiinterview.mapper.LearningResourceAnnotationMapper;
import com.tyut.aiinterview.mapper.LearningResourceMapper;
import com.tyut.aiinterview.mapper.LearningResourcePermissionMapper;
import com.tyut.aiinterview.mapper.LearningResourceVersionMapper;
import com.tyut.aiinterview.mapper.MediaFileMapper;
import com.tyut.aiinterview.mapper.RoleMapper;
import com.tyut.aiinterview.mapper.UserMapper;
import com.tyut.aiinterview.mapper.UserRoleMapper;
import com.tyut.aiinterview.media.LocalObjectStorage;
import com.tyut.aiinterview.media.MediaDtos;
import com.tyut.aiinterview.media.MediaService;
import com.tyut.aiinterview.security.CurrentUser;
import com.tyut.aiinterview.security.LoginUser;
import java.io.IOException;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

@Service
public class LearningResourceService {
    private static final int MAX_PAGES = 500;
    private static final Set<String> STATUSES = Set.of(LearningResource.DRAFT, LearningResource.PUBLISHED, LearningResource.OFFLINE);
    private static final Set<String> ANNOTATION_TYPES = Set.of("HIGHLIGHT", "UNDERLINE", "STRIKEOUT", "NOTE", "RECTANGLE", "INK");
    private static final Set<String> ANCHOR_TYPES = Set.of("POSITION", "TEXT", "PAGE");

    private final LearningResourceMapper resourceMapper;
    private final LearningResourceVersionMapper versionMapper;
    private final LearningResourcePermissionMapper permissionMapper;
    private final LearningResourceAnnotationMapper annotationMapper;
    private final MediaFileMapper mediaMapper;
    private final UserMapper userMapper;
    private final UserRoleMapper userRoleMapper;
    private final RoleMapper roleMapper;
    private final MediaService mediaService;
    private final LocalObjectStorage storage;
    private final CurrentUser currentUser;
    private final ObjectMapper objectMapper;

    public LearningResourceService(LearningResourceMapper resourceMapper,
            LearningResourceVersionMapper versionMapper,
            LearningResourcePermissionMapper permissionMapper,
            LearningResourceAnnotationMapper annotationMapper,
            MediaFileMapper mediaMapper,
            UserMapper userMapper,
            UserRoleMapper userRoleMapper,
            RoleMapper roleMapper,
            MediaService mediaService,
            LocalObjectStorage storage,
            CurrentUser currentUser,
            ObjectMapper objectMapper) {
        this.resourceMapper = resourceMapper;
        this.versionMapper = versionMapper;
        this.permissionMapper = permissionMapper;
        this.annotationMapper = annotationMapper;
        this.mediaMapper = mediaMapper;
        this.userMapper = userMapper;
        this.userRoleMapper = userRoleMapper;
        this.roleMapper = roleMapper;
        this.mediaService = mediaService;
        this.storage = storage;
        this.currentUser = currentUser;
        this.objectMapper = objectMapper;
    }

    public List<LearningResourceDtos.ResourceSummary> visibleResources() {
        List<LearningResource> resources = resourceMapper.selectList(new LambdaQueryWrapper<LearningResource>()
                .eq(LearningResource::getStatus, LearningResource.PUBLISHED)
                .orderByDesc(LearningResource::getUpdatedAt));
        return resources.stream().map(resource -> new ResourceAccess(resource, access(resource)))
                .filter(item -> item.access().canView())
                .map(item -> toSummary(item.resource(), item.access())).toList();
    }

    public PageResult<LearningResourceDtos.ResourceSummary> visiblePage(LearningResourceDtos.ResourceQuery query) {
        int pageNo = query.pageNo() == null ? 1 : Math.max(1, query.pageNo());
        int pageSize = query.pageSize() == null ? 12 : Math.min(50, Math.max(1, query.pageSize()));
        LambdaQueryWrapper<LearningResource> wrapper = new LambdaQueryWrapper<LearningResource>()
                .eq(LearningResource::getStatus, LearningResource.PUBLISHED)
                .orderByDesc(LearningResource::getUpdatedAt);
        if (!currentUser.hasRole("ADMIN")) {
            LoginUser login = currentUser.require();
            LambdaQueryWrapper<LearningResourcePermission> permissionWrapper = new LambdaQueryWrapper<LearningResourcePermission>()
                    .eq(LearningResourcePermission::getCanView, 1)
                    .and(group -> {
                        group.eq(LearningResourcePermission::getSubjectType, LearningResourcePermission.USER)
                                .eq(LearningResourcePermission::getSubjectId, String.valueOf(login.getId()));
                        if (!login.getRoles().isEmpty()) {
                            group.or(item -> item.eq(LearningResourcePermission::getSubjectType, LearningResourcePermission.ROLE)
                                    .in(LearningResourcePermission::getSubjectId, login.getRoles()));
                        }
                    })
                    .and(expiry -> expiry.isNull(LearningResourcePermission::getExpiresAt)
                            .or().gt(LearningResourcePermission::getExpiresAt, LocalDateTime.now()));
            List<Long> resourceIds = permissionMapper.selectList(permissionWrapper).stream()
                    .map(LearningResourcePermission::getResourceId).distinct().toList();
            if (resourceIds.isEmpty()) return PageResult.of(List.of(), 0, pageNo, pageSize);
            wrapper.in(LearningResource::getId, resourceIds);
        }
        Page<LearningResource> page = resourceMapper.selectPage(new Page<>(pageNo, pageSize), wrapper);
        List<LearningResourceDtos.ResourceSummary> records = page.getRecords().stream()
                .map(resource -> new ResourceAccess(resource, access(resource)))
                .filter(item -> item.access().canView())
                .map(item -> toSummary(item.resource(), item.access())).toList();
        return PageResult.of(records, page.getTotal(), pageNo, pageSize);
    }

    public PageResult<LearningResourceDtos.ResourceSummary> adminPage(LearningResourceDtos.ResourceQuery query) {
        int pageNo = query.pageNo() == null ? 1 : Math.max(1, query.pageNo());
        int pageSize = query.pageSize() == null ? 20 : Math.min(100, Math.max(1, query.pageSize()));
        LambdaQueryWrapper<LearningResource> wrapper = new LambdaQueryWrapper<LearningResource>().orderByDesc(LearningResource::getUpdatedAt);
        if (StringUtils.hasText(query.keyword())) wrapper.and(item -> item.like(LearningResource::getTitle, query.keyword().trim()).or().like(LearningResource::getDescription, query.keyword().trim()));
        if (StringUtils.hasText(query.status())) wrapper.eq(LearningResource::getStatus, normalizeStatus(query.status()));
        Page<LearningResource> page = resourceMapper.selectPage(new Page<>(pageNo, pageSize), wrapper);
        return PageResult.of(page.getRecords().stream().map(resource -> toSummary(resource, Access.admin())).toList(), page.getTotal(), pageNo, pageSize);
    }

    public LearningResourceDtos.ResourceSummary detail(String publicId) {
        LearningResource resource = requireResource(publicId);
        Access access = requireView(resource);
        return toSummary(resource, access);
    }

    public LearningResourceDtos.ResourceSummary adminDetail(String publicId) {
        return toSummary(requireResource(publicId), Access.admin());
    }

    @Transactional
    public LearningResourceDtos.ResourceSummary create(LearningResourceDtos.CreateRequest request, MultipartFile file) {
        String status = normalizeStatus(request.status());
        MediaDtos.MediaVO uploaded = mediaService.upload(file);
        MediaFile media = mediaMapper.selectById(uploaded.id());
        if (media == null) throw BusinessException.badRequest("PDF 文件保存失败");
        int pageCount;
        try {
            pageCount = countPages(media);
        } catch (RuntimeException exception) {
            storage.delete(media.getObjectKey());
            mediaMapper.deleteById(media.getId());
            throw BusinessException.badRequest("PDF 文件无法解析，请确认文件没有损坏或加密");
        }
        LearningResource resource = new LearningResource();
        resource.setPublicId(UUID.randomUUID().toString());
        resource.setTitle(request.title().trim());
        resource.setDescription(normalize(request.description()));
        resource.setStatus(status);
        resource.setAllowDownload(Boolean.TRUE.equals(request.allowDownload()) ? 1 : 0);
        resource.setCreatedBy(currentUser.id());
        resourceMapper.insert(resource);

        LearningResourceVersion version = new LearningResourceVersion();
        version.setResourceId(resource.getId());
        version.setVersionNo(1);
        version.setMediaId(media.getId());
        version.setOriginalName(media.getOriginalName());
        version.setFileSize(media.getSizeBytes());
        version.setChecksumSha256(media.getChecksumSha256());
        version.setPageCount(pageCount);
        version.setCreatedBy(currentUser.id());
        versionMapper.insert(version);
        resource.setCurrentVersionId(version.getId());
        resourceMapper.updateById(resource);
        return toSummary(resource, Access.admin());
    }

    @Transactional
    public LearningResourceDtos.ResourceSummary update(String publicId, LearningResourceDtos.UpdateRequest request) {
        LearningResource resource = requireResource(publicId);
        resource.setTitle(request.title().trim());
        resource.setDescription(normalize(request.description()));
        resource.setStatus(normalizeStatus(request.status()));
        resource.setAllowDownload(Boolean.TRUE.equals(request.allowDownload()) ? 1 : 0);
        resourceMapper.updateById(resource);
        return toSummary(resource, Access.admin());
    }

    @Transactional
    public void delete(String publicId) {
        LearningResource resource = requireResource(publicId);
        List<LearningResourceVersion> versions = versionMapper.selectList(new LambdaQueryWrapper<LearningResourceVersion>()
                .eq(LearningResourceVersion::getResourceId, resource.getId()));
        resourceMapper.deleteById(resource.getId());
        for (LearningResourceVersion version : versions) {
            if (mediaMapper.selectById(version.getMediaId()) != null) mediaMapper.deleteById(version.getMediaId());
        }
    }

    public FileContent content(String publicId) {
        return fileContent(publicId, false);
    }

    public FileContent download(String publicId) {
        return fileContent(publicId, true);
    }

    private FileContent fileContent(String publicId, boolean download) {
        LearningResource resource = requireResource(publicId);
        requireView(resource);
        if (download && !currentUser.hasRole("ADMIN") && resource.getAllowDownload() != 1) throw BusinessException.forbidden("当前资料不允许下载");
        LearningResourceVersion version = requireCurrentVersion(resource);
        MediaFile media = requireMedia(version.getMediaId());
        return new FileContent(storage.resource(media.getObjectKey()), media.getOriginalName(), download);
    }

    public List<LearningResourceDtos.PermissionView> permissions(String publicId) {
        LearningResource resource = requireResource(publicId);
        return permissionMapper.selectList(new LambdaQueryWrapper<LearningResourcePermission>().eq(LearningResourcePermission::getResourceId, resource.getId())
                .orderByAsc(LearningResourcePermission::getSubjectType).orderByAsc(LearningResourcePermission::getSubjectId)).stream().map(this::toPermissionView).toList();
    }

    @Transactional
    public LearningResourceDtos.PermissionResponse replacePermissions(String publicId, List<LearningResourceDtos.PermissionRequest> requests) {
        LearningResource resource = requireResource(publicId);
        List<LearningResourceDtos.PermissionRequest> safeRequests = requests == null ? List.of() : requests;
        Set<String> keys = new HashSet<>();
        for (LearningResourceDtos.PermissionRequest request : safeRequests) validatePermission(request, keys);
        permissionMapper.delete(new LambdaQueryWrapper<LearningResourcePermission>().eq(LearningResourcePermission::getResourceId, resource.getId()));
        for (LearningResourceDtos.PermissionRequest request : safeRequests) {
            LearningResourcePermission permission = new LearningResourcePermission();
            permission.setResourceId(resource.getId());
            permission.setSubjectType(request.subjectType().trim().toUpperCase(Locale.ROOT));
            permission.setSubjectId(request.subjectId().trim());
            permission.setCanView(Boolean.FALSE.equals(request.canView()) ? 0 : 1);
            permission.setCanAnnotate(Boolean.FALSE.equals(request.canAnnotate()) ? 0 : 1);
            if (permission.getCanAnnotate() == 1 && permission.getCanView() == 0) throw BusinessException.badRequest("有批注权限时必须同时拥有查看权限");
            permission.setExpiresAt(request.expiresAt());
            permission.setGrantedBy(currentUser.id());
            permissionMapper.insert(permission);
        }
        return new LearningResourceDtos.PermissionResponse(publicId, permissions(publicId));
    }

    public List<LearningResourceDtos.AnnotationView> annotations(String publicId) {
        LearningResource resource = requireResource(publicId);
        Access access = requireView(resource);
        LearningResourceVersion version = requireCurrentVersion(resource);
        return annotationMapper.selectList(new LambdaQueryWrapper<LearningResourceAnnotation>()
                .eq(LearningResourceAnnotation::getResourceId, resource.getId())
                .eq(LearningResourceAnnotation::getVersionId, version.getId())
                .eq(LearningResourceAnnotation::getOwnerUserId, currentUser.id())
                .orderByAsc(LearningResourceAnnotation::getPageIndex).orderByAsc(LearningResourceAnnotation::getCreatedAt))
                .stream().map(this::toAnnotationView).toList();
    }

    @Transactional
    public LearningResourceDtos.AnnotationView createAnnotation(String publicId, LearningResourceDtos.AnnotationRequest request) {
        LearningResource resource = requireResource(publicId);
        Access access = requireView(resource);
        if (!access.canAnnotate()) throw BusinessException.forbidden("当前资料没有批注权限");
        LearningResourceVersion version = requireCurrentVersion(resource);
        validateAnnotation(request, version);
        LearningResourceAnnotation annotation = new LearningResourceAnnotation();
        annotation.setPublicId(UUID.randomUUID().toString());
        annotation.setResourceId(resource.getId());
        annotation.setVersionId(version.getId());
        annotation.setOwnerUserId(currentUser.id());
        annotation.setPageIndex(request.pageIndex());
        annotation.setAnnotationType(normalizeAnnotationType(request.annotationType()));
        annotation.setAnchorType(normalizeAnchorType(request.anchorType()));
        annotation.setGeometryJson(writeJson(request.geometry(), "批注坐标格式不正确"));
        annotation.setSelectedText(normalize(request.selectedText()));
        annotation.setNoteContent(normalize(request.noteContent()));
        annotation.setStyleJson(request.style() == null ? null : writeJson(request.style(), "批注样式格式不正确"));
        annotation.setVisibility(currentUser.hasRole("ADMIN") && StringUtils.hasText(request.visibility()) ? normalizeVisibility(request.visibility()) : LearningResourceAnnotation.PRIVATE);
        annotation.setVersion(1);
        annotationMapper.insert(annotation);
        return toAnnotationView(annotation);
    }

    @Transactional
    public LearningResourceDtos.AnnotationView updateAnnotation(String annotationPublicId, LearningResourceDtos.AnnotationRequest request) {
        LearningResourceAnnotation annotation = requireAnnotation(annotationPublicId);
        LearningResource resource = requireResourceById(annotation.getResourceId());
        Access access = requireView(resource);
        if (!access.canAnnotate() || !currentUser.id().equals(annotation.getOwnerUserId())) throw BusinessException.forbidden("无权修改该批注");
        if (request.version() == null || !request.version().equals(annotation.getVersion())) throw BusinessException.conflict("批注已被更新，请刷新后重试");
        LearningResourceVersion version = requireCurrentVersion(resource);
        validateAnnotation(request, version);
        LearningResourceAnnotation update = new LearningResourceAnnotation();
        update.setPageIndex(request.pageIndex());
        update.setAnnotationType(normalizeAnnotationType(request.annotationType()));
        update.setAnchorType(normalizeAnchorType(request.anchorType()));
        update.setGeometryJson(writeJson(request.geometry(), "批注坐标格式不正确"));
        update.setSelectedText(normalize(request.selectedText()));
        update.setNoteContent(normalize(request.noteContent()));
        update.setStyleJson(request.style() == null ? null : writeJson(request.style(), "批注样式格式不正确"));
        update.setVisibility(LearningResourceAnnotation.PRIVATE);
        update.setVersion(annotation.getVersion() + 1);
        int updated = annotationMapper.update(update, new LambdaUpdateWrapper<LearningResourceAnnotation>()
                .eq(LearningResourceAnnotation::getId, annotation.getId())
                .eq(LearningResourceAnnotation::getOwnerUserId, currentUser.id())
                .eq(LearningResourceAnnotation::getVersion, annotation.getVersion()));
        if (updated == 0) throw BusinessException.conflict("批注已被更新，请刷新后重试");
        annotation.setPageIndex(update.getPageIndex());
        annotation.setAnnotationType(update.getAnnotationType());
        annotation.setAnchorType(update.getAnchorType());
        annotation.setGeometryJson(update.getGeometryJson());
        annotation.setSelectedText(update.getSelectedText());
        annotation.setNoteContent(update.getNoteContent());
        annotation.setStyleJson(update.getStyleJson());
        annotation.setVisibility(update.getVisibility());
        annotation.setVersion(update.getVersion());
        return toAnnotationView(annotation);
    }

    @Transactional
    public void deleteAnnotation(String annotationPublicId) {
        LearningResourceAnnotation annotation = requireAnnotation(annotationPublicId);
        LearningResource resource = requireResourceById(annotation.getResourceId());
        requireView(resource);
        if (!currentUser.id().equals(annotation.getOwnerUserId())) throw BusinessException.forbidden("无权删除该批注");
        annotationMapper.deleteById(annotation.getId());
    }

    private LearningResourceDtos.ResourceSummary toSummary(LearningResource resource, Access access) {
        LearningResourceVersion version = resource.getCurrentVersionId() == null ? null : versionMapper.selectById(resource.getCurrentVersionId());
        MediaFile media = version == null ? null : mediaMapper.selectById(version.getMediaId());
        return new LearningResourceDtos.ResourceSummary(resource.getId(), resource.getPublicId(), resource.getTitle(), resource.getDescription(), resource.getStatus(), resource.getAllowDownload() != null && resource.getAllowDownload() == 1,
                media == null ? null : media.getOriginalName(), media == null ? null : media.getSizeBytes(), version == null ? null : version.getPageCount(), media == null ? null : media.getChecksumSha256(), resource.getCreatedAt(), resource.getUpdatedAt(), access.canView(), access.canAnnotate());
    }

    private LearningResourceDtos.PermissionView toPermissionView(LearningResourcePermission permission) {
        String label = permission.getSubjectId();
        if (LearningResourcePermission.USER.equals(permission.getSubjectType())) {
            UserAccount user = userMapper.selectById(Long.valueOf(permission.getSubjectId()));
            if (user != null) label = StringUtils.hasText(user.getRealName()) ? user.getRealName() + "（" + user.getUsername() + "）" : user.getUsername();
        } else if (LearningResourcePermission.ROLE.equals(permission.getSubjectType())) {
            Role role = roleMapper.selectOne(new LambdaQueryWrapper<Role>().eq(Role::getRoleCode, permission.getSubjectId()));
            if (role != null && StringUtils.hasText(role.getRoleName())) label = role.getRoleName();
        }
        return new LearningResourceDtos.PermissionView(permission.getId(), permission.getSubjectType(), permission.getSubjectId(), label, permission.getCanView() == 1, permission.getCanAnnotate() == 1, permission.getExpiresAt());
    }

    private LearningResourceDtos.AnnotationView toAnnotationView(LearningResourceAnnotation annotation) {
        return new LearningResourceDtos.AnnotationView(annotation.getPublicId(), annotation.getResourceId(), annotation.getVersionId(), annotation.getOwnerUserId(), annotation.getPageIndex(), annotation.getAnnotationType(), annotation.getAnchorType(), readJson(annotation.getGeometryJson()), annotation.getSelectedText(), annotation.getNoteContent(), readJson(annotation.getStyleJson()), annotation.getVisibility(), annotation.getVersion(), annotation.getCreatedAt(), annotation.getUpdatedAt());
    }

    private Access requireView(LearningResource resource) {
        Access access = access(resource);
        if (!access.canView()) throw BusinessException.notFound("资料不存在或没有查看权限");
        return access;
    }

    private Access access(LearningResource resource) {
        if (currentUser.hasRole("ADMIN")) return Access.admin();
        if (!LearningResource.PUBLISHED.equals(resource.getStatus())) return Access.none();
        LocalDateTime now = LocalDateTime.now();
        LoginUser login = currentUser.require();
        List<LearningResourcePermission> permissions = permissionMapper.selectList(new LambdaQueryWrapper<LearningResourcePermission>().eq(LearningResourcePermission::getResourceId, resource.getId()));
        boolean view = permissions.stream().filter(item -> item.getExpiresAt() == null || item.getExpiresAt().isAfter(now)).anyMatch(item -> item.getCanView() == 1 && ((LearningResourcePermission.USER.equals(item.getSubjectType()) && String.valueOf(login.getId()).equals(item.getSubjectId())) || (LearningResourcePermission.ROLE.equals(item.getSubjectType()) && login.getRoles().contains(item.getSubjectId()))));
        boolean annotate = permissions.stream().filter(item -> item.getExpiresAt() == null || item.getExpiresAt().isAfter(now)).anyMatch(item -> item.getCanAnnotate() == 1 && ((LearningResourcePermission.USER.equals(item.getSubjectType()) && String.valueOf(login.getId()).equals(item.getSubjectId())) || (LearningResourcePermission.ROLE.equals(item.getSubjectType()) && login.getRoles().contains(item.getSubjectId()))));
        return new Access(resource, view, view && annotate);
    }

    private LearningResource requireResource(String publicId) {
        LearningResource resource = resourceMapper.selectOne(new LambdaQueryWrapper<LearningResource>().eq(LearningResource::getPublicId, publicId));
        if (resource == null) throw BusinessException.notFound("学习资料不存在");
        return resource;
    }

    private LearningResource requireResourceById(Long id) {
        LearningResource resource = resourceMapper.selectById(id);
        if (resource == null) throw BusinessException.notFound("学习资料不存在");
        return resource;
    }

    private LearningResourceVersion requireCurrentVersion(LearningResource resource) {
        if (resource.getCurrentVersionId() == null) throw BusinessException.notFound("学习资料暂无可用版本");
        LearningResourceVersion version = versionMapper.selectById(resource.getCurrentVersionId());
        if (version == null) throw BusinessException.notFound("学习资料版本不存在");
        return version;
    }

    private MediaFile requireMedia(Long mediaId) {
        MediaFile media = mediaMapper.selectById(mediaId);
        if (media == null || media.getStatus() != MediaFile.AVAILABLE) throw BusinessException.notFound("PDF 文件不存在");
        return media;
    }

    private LearningResourceAnnotation requireAnnotation(String publicId) {
        LearningResourceAnnotation annotation = annotationMapper.selectOne(new LambdaQueryWrapper<LearningResourceAnnotation>().eq(LearningResourceAnnotation::getPublicId, publicId));
        if (annotation == null) throw BusinessException.notFound("批注不存在");
        return annotation;
    }

    private int countPages(MediaFile media) {
        try (PDDocument document = Loader.loadPDF(storage.path(media.getObjectKey()).toFile())) {
            int pages = document.getNumberOfPages();
            if (pages < 1 || pages > MAX_PAGES) throw new IllegalArgumentException("PDF 页数超出限制");
            return pages;
        } catch (IOException exception) {
            throw new IllegalArgumentException("PDF 无法解析", exception);
        }
    }

    private void validatePermission(LearningResourceDtos.PermissionRequest request, Set<String> keys) {
        if (request == null || !StringUtils.hasText(request.subjectType()) || !StringUtils.hasText(request.subjectId())) throw BusinessException.badRequest("资料权限项不完整");
        String type = request.subjectType().trim().toUpperCase(Locale.ROOT);
        String id = request.subjectId().trim();
        if (!LearningResourcePermission.USER.equals(type) && !LearningResourcePermission.ROLE.equals(type)) throw BusinessException.badRequest("资料权限主体类型不合法");
        if (!keys.add(type + ":" + id)) throw BusinessException.badRequest("资料权限不能重复");
        if (LearningResourcePermission.USER.equals(type)) {
            try { if (userMapper.selectById(Long.valueOf(id)) == null) throw BusinessException.badRequest("授权用户不存在"); }
            catch (NumberFormatException exception) { throw BusinessException.badRequest("授权用户编号不合法"); }
        } else if (roleMapper.selectOne(new LambdaQueryWrapper<Role>().eq(Role::getRoleCode, id).eq(Role::getStatus, 1)) == null) throw BusinessException.badRequest("授权角色不存在或已停用");
    }

    private void validateAnnotation(LearningResourceDtos.AnnotationRequest request, LearningResourceVersion version) {
        if (request.geometry() == null || !request.geometry().isObject() && !request.geometry().isArray()) throw BusinessException.badRequest("批注坐标必须是 JSON 对象或数组");
        if (request.pageIndex() < 0 || request.pageIndex() >= version.getPageCount()) throw BusinessException.badRequest("批注页码超出 PDF 范围");
        normalizeAnnotationType(request.annotationType());
        normalizeAnchorType(request.anchorType());
        if ("NOTE".equalsIgnoreCase(request.annotationType()) && !StringUtils.hasText(request.noteContent())) throw BusinessException.badRequest("便签内容不能为空");
    }

    private String normalizeStatus(String status) {
        String value = StringUtils.hasText(status) ? status.trim().toUpperCase(Locale.ROOT) : LearningResource.DRAFT;
        if (!STATUSES.contains(value)) throw BusinessException.badRequest("资料状态不合法");
        return value;
    }

    private String normalizeAnnotationType(String type) {
        String value = StringUtils.hasText(type) ? type.trim().toUpperCase(Locale.ROOT) : "HIGHLIGHT";
        if (!ANNOTATION_TYPES.contains(value)) throw BusinessException.badRequest("批注类型不合法");
        return value;
    }

    private String normalizeAnchorType(String type) {
        String value = StringUtils.hasText(type) ? type.trim().toUpperCase(Locale.ROOT) : "POSITION";
        if (!ANCHOR_TYPES.contains(value)) throw BusinessException.badRequest("批注锚点类型不合法");
        return value;
    }

    private String normalizeVisibility(String visibility) {
        String value = visibility.trim().toUpperCase(Locale.ROOT);
        if (!Set.of(LearningResourceAnnotation.PRIVATE, LearningResourceAnnotation.ADMIN_VISIBLE, LearningResourceAnnotation.PUBLIC).contains(value)) throw BusinessException.badRequest("批注可见性不合法");
        return value;
    }

    private String writeJson(JsonNode node, String message) {
        try { return objectMapper.writeValueAsString(node); } catch (JsonProcessingException exception) { throw BusinessException.badRequest(message); }
    }

    private JsonNode readJson(String value) {
        if (!StringUtils.hasText(value)) return null;
        try { return objectMapper.readTree(value); } catch (JsonProcessingException exception) { return objectMapper.createObjectNode(); }
    }

    private static String normalize(String value) { return StringUtils.hasText(value) ? value.trim() : null; }

    private record Access(LearningResource resource, boolean canView, boolean canAnnotate) {
        static Access admin() { return new Access(null, true, true); }
        static Access none() { return new Access(null, false, false); }
    }

    public record FileContent(Resource resource, String originalName, boolean download) {}

    private record ResourceAccess(LearningResource resource, Access access) {}
}
