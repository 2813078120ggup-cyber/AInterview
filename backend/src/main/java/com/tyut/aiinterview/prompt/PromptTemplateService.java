package com.tyut.aiinterview.prompt;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.tyut.aiinterview.common.BusinessException;
import com.tyut.aiinterview.domain.AiPromptActivationLog;
import com.tyut.aiinterview.domain.AiPromptVersion;
import com.tyut.aiinterview.mapper.AiPromptActivationLogMapper;
import com.tyut.aiinterview.mapper.AiPromptVersionMapper;
import com.tyut.aiinterview.security.CurrentUser;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PromptTemplateService {
    private static final Pattern VARIABLE_PATTERN = Pattern.compile("\\$\\{([A-Za-z0-9_.-]+)}");

    private final AiPromptVersionMapper versionMapper;
    private final AiPromptActivationLogMapper logMapper;
    private final CurrentUser currentUser;

    public PromptTemplateService(AiPromptVersionMapper versionMapper, AiPromptActivationLogMapper logMapper,
                                 CurrentUser currentUser) {
        this.versionMapper = versionMapper;
        this.logMapper = logMapper;
        this.currentUser = currentUser;
    }

    public RenderedPrompt render(String code, Map<String, ?> variables) {
        AiPromptVersion version = requireSingleActiveVersion(code);
        return renderVersion(code, version, variables);
    }

    public RenderedPrompt renderVersion(String code, int versionNo, Map<String, ?> variables) {
        return renderVersion(code, requireVersion(code, versionNo), variables);
    }

    private RenderedPrompt renderVersion(String code, AiPromptVersion version, Map<String, ?> variables) {
        PromptCatalog.Definition definition = definition(code);
        return new RenderedPrompt(code, version.getVersionNo(),
                renderTemplate(version.getSystemTemplate(), definition, variables),
                renderTemplate(version.getUserTemplate(), definition, variables));
    }

    public int activeVersionNo(String code) {
        return requireSingleActiveVersion(code).getVersionNo();
    }

    public List<PromptDtos.PromptSummary> listSummaries() {
        requireAdmin();
        List<AiPromptVersion> all = versionMapper.selectList(new LambdaQueryWrapper<AiPromptVersion>()
                .orderByAsc(AiPromptVersion::getPromptCode)
                .orderByDesc(AiPromptVersion::getVersionNo));
        Map<String, List<AiPromptVersion>> byCode = new HashMap<>();
        all.forEach(item -> byCode.computeIfAbsent(item.getPromptCode(), ignored -> new ArrayList<>()).add(item));
        return PromptCatalog.definitions().stream().map(definition -> summary(definition, byCode.getOrDefault(definition.code(), List.of()))).toList();
    }

    public PromptDtos.PromptDetail detail(String code) {
        requireAdmin();
        PromptCatalog.Definition definition = definition(code);
        List<AiPromptVersion> versions = versions(code);
        List<PromptDtos.ActivationLogView> history = logMapper.selectList(new LambdaQueryWrapper<AiPromptActivationLog>()
                        .eq(AiPromptActivationLog::getPromptCode, code)
                        .orderByDesc(AiPromptActivationLog::getCreatedAt)
                        .orderByDesc(AiPromptActivationLog::getId))
                .stream().map(this::toLogView).toList();
        return new PromptDtos.PromptDetail(summary(definition, versions), versions.stream().map(this::toView).toList(), history);
    }

    @Transactional
    public PromptDtos.VersionView createVersion(String code, PromptDtos.CreateVersionRequest request) {
        requireAdmin();
        PromptCatalog.Definition definition = definition(code);
        validateTemplate(definition, request.systemTemplate());
        validateTemplate(definition, request.userTemplate());
        List<AiPromptVersion> versions = versions(code);
        int nextVersion = versions.stream().map(AiPromptVersion::getVersionNo).max(Integer::compareTo).orElse(0) + 1;
        AiPromptVersion version = new AiPromptVersion();
        version.setPromptCode(code);
        version.setPromptName(definition.name());
        version.setCategory(definition.category());
        version.setVersionNo(nextVersion);
        version.setSystemTemplate(request.systemTemplate().trim());
        version.setUserTemplate(request.userTemplate().trim());
        version.setActive(0);
        version.setChangeNote(normalizeNote(request.changeNote(), "创建版本 v" + nextVersion));
        version.setCreatedBy(currentUser.id());
        version.setCreatedAt(LocalDateTime.now());
        versionMapper.insert(version);
        if (request.activate()) switchVersion(code, version, "ACTIVATE", version.getChangeNote(), currentUser.id());
        return toView(versionMapper.selectById(version.getId()));
    }

    @Transactional
    public PromptDtos.VersionView activate(String code, int versionNo, PromptDtos.ActivationRequest request) {
        requireAdmin();
        definition(code);
        AiPromptVersion target = requireVersion(code, versionNo);
        switchVersion(code, target, "ACTIVATE", normalizeNote(request == null ? null : request.note(), "激活 v" + versionNo), currentUser.id());
        return toView(versionMapper.selectById(target.getId()));
    }

    @Transactional
    public PromptDtos.VersionView rollback(String code, int versionNo, PromptDtos.ActivationRequest request) {
        requireAdmin();
        definition(code);
        AiPromptVersion current = activeVersionEntity(code);
        AiPromptVersion target = requireVersion(code, versionNo);
        if (current == null) throw BusinessException.badRequest("当前没有已激活版本，请使用激活操作");
        if (target.getVersionNo() >= current.getVersionNo()) {
            throw BusinessException.badRequest("回滚目标必须早于当前版本；较新版本请使用激活操作");
        }
        switchVersion(code, target, "ROLLBACK", normalizeNote(request == null ? null : request.note(),
                "从 v" + current.getVersionNo() + " 回滚到 v" + versionNo), currentUser.id());
        return toView(versionMapper.selectById(target.getId()));
    }

    @Transactional
    public void ensureDefaults(Map<String, DefaultTemplate> defaults) {
        for (PromptCatalog.Definition definition : PromptCatalog.definitions()) {
            if (versionMapper.exists(new LambdaQueryWrapper<AiPromptVersion>()
                    .eq(AiPromptVersion::getPromptCode, definition.code()))) continue;
            DefaultTemplate template = defaults.get(definition.code());
            if (template == null) throw new IllegalStateException("缺少默认提示词资源：" + definition.code());
            validateTemplate(definition, template.systemTemplate());
            validateTemplate(definition, template.userTemplate());
            AiPromptVersion version = new AiPromptVersion();
            version.setPromptCode(definition.code());
            version.setPromptName(definition.name());
            version.setCategory(definition.category());
            version.setVersionNo(1);
            version.setSystemTemplate(template.systemTemplate().trim());
            version.setUserTemplate(template.userTemplate().trim());
            version.setActive(1);
            version.setChangeNote("系统内置初始版本");
            version.setCreatedAt(LocalDateTime.now());
            version.setActivatedAt(LocalDateTime.now());
            versionMapper.insert(version);
            insertLog(definition.code(), null, 1, "INITIAL", "系统初始化", null);
        }
    }

    private void switchVersion(String code, AiPromptVersion target, String action, String note, Long operatorId) {
        AiPromptVersion current = activeVersionEntity(code);
        if (current != null && current.getId().equals(target.getId())) return;
        versionMapper.update(null, new LambdaUpdateWrapper<AiPromptVersion>()
                .eq(AiPromptVersion::getPromptCode, code)
                .eq(AiPromptVersion::getActive, 1)
                .set(AiPromptVersion::getActive, 0)
                .set(AiPromptVersion::getActivatedAt, null));
        LocalDateTime now = LocalDateTime.now();
        versionMapper.update(null, new LambdaUpdateWrapper<AiPromptVersion>()
                .eq(AiPromptVersion::getId, target.getId())
                .set(AiPromptVersion::getActive, 1)
                .set(AiPromptVersion::getActivatedAt, now));
        insertLog(code, current == null ? null : current.getVersionNo(), target.getVersionNo(), action, note, operatorId);
        target.setActive(1);
        target.setActivatedAt(now);
    }

    private void insertLog(String code, Integer fromVersion, Integer toVersion, String action, String note, Long operatorId) {
        AiPromptActivationLog log = new AiPromptActivationLog();
        log.setPromptCode(code);
        log.setFromVersionNo(fromVersion);
        log.setToVersionNo(toVersion);
        log.setAction(action);
        log.setNote(note);
        log.setOperatorId(operatorId);
        log.setCreatedAt(LocalDateTime.now());
        logMapper.insert(log);
    }

    private List<AiPromptVersion> versions(String code) {
        return versionMapper.selectList(new LambdaQueryWrapper<AiPromptVersion>()
                .eq(AiPromptVersion::getPromptCode, code)
                .orderByDesc(AiPromptVersion::getVersionNo));
    }

    private AiPromptVersion activeVersionEntity(String code) {
        return versionMapper.selectOne(new LambdaQueryWrapper<AiPromptVersion>()
                .eq(AiPromptVersion::getPromptCode, code)
                .eq(AiPromptVersion::getActive, 1)
                .last("LIMIT 1"));
    }

    private AiPromptVersion requireSingleActiveVersion(String code) {
        definition(code);
        List<AiPromptVersion> active = versionMapper.selectList(new LambdaQueryWrapper<AiPromptVersion>()
                .eq(AiPromptVersion::getPromptCode, code)
                .eq(AiPromptVersion::getActive, 1));
        if (active.size() != 1) {
            throw new IllegalStateException("提示词 " + code + " 必须且只能有一个已激活版本，当前数量：" + active.size());
        }
        return active.get(0);
    }

    private AiPromptVersion requireVersion(String code, int versionNo) {
        AiPromptVersion version = versionMapper.selectOne(new LambdaQueryWrapper<AiPromptVersion>()
                .eq(AiPromptVersion::getPromptCode, code)
                .eq(AiPromptVersion::getVersionNo, versionNo));
        if (version == null) throw BusinessException.notFound("提示词版本不存在：" + code + " v" + versionNo);
        return version;
    }

    private PromptDtos.PromptSummary summary(PromptCatalog.Definition definition, List<AiPromptVersion> versions) {
        AiPromptVersion active = versions.stream().filter(item -> Integer.valueOf(1).equals(item.getActive())).findFirst().orElse(null);
        Integer latest = versions.stream().map(AiPromptVersion::getVersionNo).max(Comparator.naturalOrder()).orElse(null);
        return new PromptDtos.PromptSummary(definition.code(), definition.name(), definition.category(),
                definition.description(), definition.variables(), active == null ? null : active.getVersionNo(), latest,
                active == null ? null : active.getActivatedAt());
    }

    private PromptDtos.VersionView toView(AiPromptVersion version) {
        return new PromptDtos.VersionView(version.getId(), version.getPromptCode(), version.getPromptName(),
                version.getCategory(), version.getVersionNo(), version.getSystemTemplate(), version.getUserTemplate(),
                Integer.valueOf(1).equals(version.getActive()), version.getChangeNote(), version.getCreatedBy(),
                version.getCreatedAt(), version.getActivatedAt());
    }

    private PromptDtos.ActivationLogView toLogView(AiPromptActivationLog log) {
        return new PromptDtos.ActivationLogView(log.getId(), log.getPromptCode(), log.getFromVersionNo(),
                log.getToVersionNo(), log.getAction(), log.getNote(), log.getOperatorId(), log.getCreatedAt());
    }

    private String renderTemplate(String template, PromptCatalog.Definition definition, Map<String, ?> variables) {
        Matcher matcher = VARIABLE_PATTERN.matcher(template);
        StringBuffer output = new StringBuffer();
        while (matcher.find()) {
            String name = matcher.group(1);
            if (!definition.variables().contains(name)) {
                throw new IllegalStateException("提示词 " + definition.code() + " 包含未声明变量：" + name);
            }
            if (!variables.containsKey(name)) {
                throw new IllegalStateException("渲染提示词 " + definition.code() + " 时缺少变量：" + name);
            }
            Object value = variables.get(name);
            matcher.appendReplacement(output, Matcher.quoteReplacement(value == null ? "" : String.valueOf(value)));
        }
        matcher.appendTail(output);
        return output.toString();
    }

    private void validateTemplate(PromptCatalog.Definition definition, String template) {
        Matcher matcher = VARIABLE_PATTERN.matcher(template);
        while (matcher.find()) {
            if (!definition.variables().contains(matcher.group(1))) {
                throw BusinessException.badRequest("模板包含不可用变量 ${" + matcher.group(1) + "}，可用变量：" + definition.variables());
            }
        }
    }

    private PromptCatalog.Definition definition(String code) {
        try {
            return PromptCatalog.require(code);
        } catch (IllegalArgumentException exception) {
            throw BusinessException.notFound(exception.getMessage());
        }
    }

    private String normalizeNote(String note, String fallback) {
        return note == null || note.isBlank() ? fallback : note.trim();
    }

    private void requireAdmin() {
        if (!currentUser.hasRole("ADMIN")) throw BusinessException.forbidden("仅管理员可管理提示词版本");
    }

    public record RenderedPrompt(String code, int version, String systemPrompt, String userPrompt) {
    }

    public record DefaultTemplate(String systemTemplate, String userTemplate) {
    }
}
