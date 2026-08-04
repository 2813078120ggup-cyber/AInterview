package com.tyut.aiinterview.algorithm;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.tyut.aiinterview.common.BusinessException;
import com.tyut.aiinterview.common.PageResult;
import com.tyut.aiinterview.domain.AlgorithmFavorite;
import com.tyut.aiinterview.domain.AlgorithmNote;
import com.tyut.aiinterview.domain.AlgorithmProblem;
import com.tyut.aiinterview.domain.AlgorithmProblemTag;
import com.tyut.aiinterview.domain.AlgorithmTag;
import com.tyut.aiinterview.domain.AlgorithmTestCase;
import com.tyut.aiinterview.domain.AlgorithmUserProgress;
import com.tyut.aiinterview.mapper.AlgorithmFavoriteMapper;
import com.tyut.aiinterview.mapper.AlgorithmNoteMapper;
import com.tyut.aiinterview.mapper.AlgorithmProblemMapper;
import com.tyut.aiinterview.mapper.AlgorithmProblemTagMapper;
import com.tyut.aiinterview.mapper.AlgorithmTagMapper;
import com.tyut.aiinterview.mapper.AlgorithmTestCaseMapper;
import com.tyut.aiinterview.mapper.AlgorithmUserProgressMapper;
import com.tyut.aiinterview.mapper.AdminProblemStatRow;
import com.tyut.aiinterview.mapper.ProblemStatRow;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
public class AlgorithmProblemService {
    private final AlgorithmProblemMapper problemMapper;
    private final AlgorithmTagMapper tagMapper;
    private final AlgorithmProblemTagMapper problemTagMapper;
    private final AlgorithmTestCaseMapper testCaseMapper;
    private final AlgorithmFavoriteMapper favoriteMapper;
    private final AlgorithmNoteMapper noteMapper;
    private final AlgorithmUserProgressMapper progressMapper;

    public AlgorithmProblemService(AlgorithmProblemMapper problemMapper,
                                   AlgorithmTagMapper tagMapper,
                                   AlgorithmProblemTagMapper problemTagMapper,
                                   AlgorithmTestCaseMapper testCaseMapper,
                                   AlgorithmFavoriteMapper favoriteMapper,
                                   AlgorithmNoteMapper noteMapper,
                                   AlgorithmUserProgressMapper progressMapper) {
        this.problemMapper = problemMapper;
        this.tagMapper = tagMapper;
        this.problemTagMapper = problemTagMapper;
        this.testCaseMapper = testCaseMapper;
        this.favoriteMapper = favoriteMapper;
        this.noteMapper = noteMapper;
        this.progressMapper = progressMapper;
    }

    public PageResult<AlgorithmDtos.ProblemListItem> listProblems(Long userId, String keyword, String difficulty,
                                                                   Long tagId, String progressStatus,
                                                                   int pageNo, int pageSize) {
        pageNo = Math.max(1, pageNo);
        pageSize = Math.max(1, Math.min(100, pageSize));
        if (StringUtils.hasText(difficulty) && !isEnum(difficulty, AlgorithmDifficulty.class)) {
            throw BusinessException.badRequest("难度参数不正确");
        }
        if (StringUtils.hasText(progressStatus) && !isEnum(progressStatus, AlgorithmProgressStatus.class)) {
            throw BusinessException.badRequest("完成状态参数不正确");
        }
        List<ProblemStatRow> rows = problemMapper.selectProblemPage(userId, trimToNull(keyword), difficulty,
                tagId, progressStatus, (pageNo - 1) * pageSize, pageSize);
        long total = problemMapper.countProblemPage(userId, trimToNull(keyword), difficulty, tagId, progressStatus);
        if (!rows.isEmpty()) {
            Map<Long, ProblemStatRow> counts = problemMapper.selectSubmissionCounts(
                            rows.stream().map(ProblemStatRow::getId).toList())
                    .stream().collect(Collectors.toMap(ProblemStatRow::getId, Function.identity()));
            rows.forEach(row -> {
                ProblemStatRow count = counts.get(row.getId());
                row.setSubmissionCount(count == null ? 0 : count.getSubmissionCount());
                row.setAcceptedCount(count == null ? 0 : count.getAcceptedCount());
            });
        }
        return PageResult.of(decorate(userId, rows), total, pageNo, pageSize);
    }

    public List<AlgorithmDtos.ProblemListItem> decorate(Long userId, List<ProblemStatRow> rows) {
        if (rows == null || rows.isEmpty()) return List.of();
        List<Long> problemIds = rows.stream().map(ProblemStatRow::getId).toList();
        Map<Long, List<Long>> tagIdsByProblem = new HashMap<>();
        problemTagMapper.selectList(new LambdaQueryWrapper<AlgorithmProblemTag>()
                        .in(AlgorithmProblemTag::getProblemId, problemIds))
                .forEach(relation -> tagIdsByProblem
                        .computeIfAbsent(relation.getProblemId(), key -> new ArrayList<>())
                        .add(relation.getTagId()));
        Set<Long> allTagIds = tagIdsByProblem.values().stream().flatMap(List::stream).collect(Collectors.toSet());
        Map<Long, AlgorithmTag> tagById = allTagIds.isEmpty()
                ? Map.of()
                : tagMapper.selectBatchIds(allTagIds).stream()
                        .collect(Collectors.toMap(AlgorithmTag::getId, Function.identity()));
        Set<Long> favorites = favoriteIds(userId, problemIds);
        Set<Long> notes = noteIds(userId, problemIds);
        return rows.stream().map(row -> toListItem(row, tagIdsByProblem, tagById, favorites, notes)).toList();
    }

    public AlgorithmDtos.ProblemDetailView detail(Long userId, Long problemId) {
        AlgorithmProblem problem = requireEnabled(problemId);
        List<AlgorithmDtos.TagView> tags = tagsOf(problemId);
        List<AlgorithmDtos.TestCaseView> samples = testCaseMapper.selectList(
                        new LambdaQueryWrapper<AlgorithmTestCase>()
                                .eq(AlgorithmTestCase::getProblemId, problemId)
                                .eq(AlgorithmTestCase::getCaseType, AlgorithmCaseType.SAMPLE.name())
                                .eq(AlgorithmTestCase::getEnabled, 1)
                                .orderByAsc(AlgorithmTestCase::getSortNo)
                                .orderByAsc(AlgorithmTestCase::getId))
                .stream().map(caseItem -> new AlgorithmDtos.TestCaseView(caseItem.getId(),
                        caseItem.getInputData(), caseItem.getExpectedOutput(), caseItem.getScore(),
                        caseItem.getSortNo())).toList();
        AlgorithmUserProgress progress = progressMapper.selectOne(new LambdaQueryWrapper<AlgorithmUserProgress>()
                .eq(AlgorithmUserProgress::getUserId, userId)
                .eq(AlgorithmUserProgress::getProblemId, problemId));
        boolean favorited = favoriteMapper.selectCount(new LambdaQueryWrapper<AlgorithmFavorite>()
                .eq(AlgorithmFavorite::getUserId, userId)
                .eq(AlgorithmFavorite::getProblemId, problemId)) > 0;
        AlgorithmNote note = noteMapper.selectOne(new LambdaQueryWrapper<AlgorithmNote>()
                .eq(AlgorithmNote::getUserId, userId)
                .eq(AlgorithmNote::getProblemId, problemId));
        return new AlgorithmDtos.ProblemDetailView(
                problem.getId(), problem.getTitle(), problem.getSlug(), problem.getDifficulty(),
                difficultyLabel(problem.getDifficulty()), problem.getDescriptionMd(),
                problem.getInputDescription(), problem.getOutputDescription(),
                problem.getConstraintsDescription(), problem.getHintContent(),
                problem.getTimeLimitMs(), problem.getMemoryLimitMb(), problem.getDefaultLanguage(),
                problem.getStarterCode(),
                progress == null ? null : progress.getProgressStatus(),
                progress == null ? 0 : progress.getSubmitCount(),
                favorited, note == null ? null : note.getContentMd(), tags, samples);
    }

    public List<AlgorithmDtos.TagView> tags() {
        return tagMapper.selectList(new LambdaQueryWrapper<AlgorithmTag>()
                        .orderByAsc(AlgorithmTag::getSortNo).orderByAsc(AlgorithmTag::getId))
                .stream().map(tag -> new AlgorithmDtos.TagView(tag.getId(), tag.getName(), tag.getCode())).toList();
    }

    public void toggleFavorite(Long userId, Long problemId, boolean favorite) {
        requireEnabled(problemId);
        if (favorite) {
            AlgorithmFavorite item = new AlgorithmFavorite();
            item.setUserId(userId);
            item.setProblemId(problemId);
            try {
                favoriteMapper.insert(item);
            } catch (DuplicateKeyException ignored) {
                // 已收藏
            }
        } else {
            favoriteMapper.delete(new LambdaQueryWrapper<AlgorithmFavorite>()
                    .eq(AlgorithmFavorite::getUserId, userId)
                    .eq(AlgorithmFavorite::getProblemId, problemId));
        }
    }

    public void saveNote(Long userId, Long problemId, String content) {
        requireEnabled(problemId);
        String normalized = content == null ? "" : content.trim();
        AlgorithmNote note = noteMapper.selectOne(new LambdaQueryWrapper<AlgorithmNote>()
                .eq(AlgorithmNote::getUserId, userId)
                .eq(AlgorithmNote::getProblemId, problemId));
        if (!StringUtils.hasText(normalized)) {
            if (note != null) noteMapper.deleteById(note.getId());
            return;
        }
        if (normalized.length() > 20_000) {
            throw BusinessException.badRequest("笔记内容过长");
        }
        if (note == null) {
            note = new AlgorithmNote();
            note.setUserId(userId);
            note.setProblemId(problemId);
            note.setContentMd(normalized);
            noteMapper.insert(note);
        } else {
            note.setContentMd(normalized);
            noteMapper.updateById(note);
        }
    }

    public List<AlgorithmDtos.AdminProblemView> adminList() {
        return problemMapper.selectAdminStats().stream()
                .map(row -> new AlgorithmDtos.AdminProblemView(
                        row.getId(), row.getTitle(), row.getSlug(), row.getDifficulty(),
                        row.getStatus(), row.getSortNo(),
                        row.getSubmissionCount() == null ? 0 : row.getSubmissionCount(),
                        row.getAcceptedCount() == null ? 0 : row.getAcceptedCount(),
                        row.getCreatedAt()))
                .toList();
    }

    public AlgorithmDtos.AdminProblemDetailView adminDetail(Long problemId) {
        AlgorithmProblem problem = require(problemId);
        List<AlgorithmDtos.TagView> tags = tagsOf(problemId);
        List<AlgorithmDtos.AdminTestCaseInput> cases = testCaseMapper.selectList(
                        new LambdaQueryWrapper<AlgorithmTestCase>()
                                .eq(AlgorithmTestCase::getProblemId, problemId)
                                .orderByAsc(AlgorithmTestCase::getSortNo)
                                .orderByAsc(AlgorithmTestCase::getId))
                .stream().map(caseItem -> new AlgorithmDtos.AdminTestCaseInput(
                        caseItem.getId(), caseItem.getInputData(), caseItem.getExpectedOutput(),
                        caseItem.getCaseType(), caseItem.getScore(), caseItem.getSortNo(),
                        caseItem.getEnabled() == null || caseItem.getEnabled() == 1))
                .toList();
        return new AlgorithmDtos.AdminProblemDetailView(
                problem.getId(), problem.getTitle(), problem.getSlug(), problem.getDifficulty(),
                problem.getDescriptionMd(), problem.getInputDescription(), problem.getOutputDescription(),
                problem.getConstraintsDescription(), problem.getHintContent(),
                problem.getTimeLimitMs(), problem.getMemoryLimitMb(), problem.getDefaultLanguage(),
                problem.getStarterCode(), problem.getSolutionCode(), problem.getStatus(), problem.getSortNo(),
                tags, cases, problem.getCreatedAt(), problem.getUpdatedAt());
    }

    @Transactional
    public Long adminSave(Long creatorId, AlgorithmDtos.AdminProblemSaveRequest request) {
        validateSave(request);
        AlgorithmProblem problem = new AlgorithmProblem();
        apply(problem, request);
        problem.setCreatedBy(creatorId);
        problemMapper.insert(problem);
        replaceTags(problem.getId(), request.tagIds());
        replaceCases(problem.getId(), request.testCases());
        return problem.getId();
    }

    @Transactional
    public void adminUpdate(Long problemId, AlgorithmDtos.AdminProblemSaveRequest request) {
        AlgorithmProblem problem = require(problemId);
        validateSave(request);
        apply(problem, request);
        problemMapper.updateById(problem);
        replaceTags(problemId, request.tagIds());
        replaceCases(problemId, request.testCases());
    }

    public void adminUpdateStatus(Long problemId, Integer status) {
        if (status == null || (status != 0 && status != 1)) {
            throw BusinessException.badRequest("状态参数不正确");
        }
        AlgorithmProblem problem = require(problemId);
        problem.setStatus(status);
        problemMapper.updateById(problem);
    }

    private void apply(AlgorithmProblem problem, AlgorithmDtos.AdminProblemSaveRequest request) {
        problem.setTitle(request.title().trim());
        problem.setSlug(trimToNull(request.slug()));
        problem.setDifficulty(request.difficulty());
        problem.setDescriptionMd(request.descriptionMd());
        problem.setInputDescription(trimToNull(request.inputDescription()));
        problem.setOutputDescription(trimToNull(request.outputDescription()));
        problem.setConstraintsDescription(trimToNull(request.constraintsDescription()));
        problem.setHintContent(trimToNull(request.hintContent()));
        problem.setTimeLimitMs(request.timeLimitMs());
        problem.setMemoryLimitMb(request.memoryLimitMb());
        problem.setDefaultLanguage(request.defaultLanguage() == null ? "JAVA17" : request.defaultLanguage());
        problem.setStarterCode(request.starterCode());
        problem.setSolutionCode(request.solutionCode());
        problem.setStatus(request.status() == null ? 1 : request.status());
        problem.setSortNo(request.sortNo() == null ? 0 : request.sortNo());
    }

    private void validateSave(AlgorithmDtos.AdminProblemSaveRequest request) {
        if (!StringUtils.hasText(request.title()) || request.title().length() > 200) {
            throw BusinessException.badRequest("题目名称不能为空且不超过 200 字");
        }
        if (!isEnum(request.difficulty(), AlgorithmDifficulty.class)) {
            throw BusinessException.badRequest("难度参数不正确");
        }
        if (!StringUtils.hasText(request.descriptionMd())) {
            throw BusinessException.badRequest("题目描述不能为空");
        }
        if (request.timeLimitMs() == null || request.timeLimitMs() < 100 || request.timeLimitMs() > 60_000) {
            throw BusinessException.badRequest("时间限制须在 100-60000ms");
        }
        if (request.memoryLimitMb() == null || request.memoryLimitMb() < 16 || request.memoryLimitMb() > 1024) {
            throw BusinessException.badRequest("内存限制须在 16-1024MB");
        }
        if (!StringUtils.hasText(request.starterCode())) {
            throw BusinessException.badRequest("代码模板不能为空");
        }
        if (request.testCases() == null || request.testCases().isEmpty()) {
            throw BusinessException.badRequest("至少需要一个测试用例");
        }
        for (AlgorithmDtos.AdminTestCaseInput caseItem : request.testCases()) {
            if (caseItem.expectedOutput() == null || caseItem.expectedOutput().isBlank()) {
                throw BusinessException.badRequest("测试用例期望输出不能为空");
            }
            if (!isEnum(caseItem.caseType(), AlgorithmCaseType.class)) {
                throw BusinessException.badRequest("测试用例类型不正确");
            }
        }
        if (StringUtils.hasText(request.slug())) {
            boolean slugExists = problemMapper.selectCount(new LambdaQueryWrapper<AlgorithmProblem>()
                    .eq(AlgorithmProblem::getSlug, request.slug().trim())) > 0;
            if (slugExists) {
                throw BusinessException.badRequest("slug 已存在，请更换");
            }
        }
    }

    private void replaceTags(Long problemId, List<Long> tagIds) {
        problemTagMapper.delete(new LambdaQueryWrapper<AlgorithmProblemTag>()
                .eq(AlgorithmProblemTag::getProblemId, problemId));
        if (tagIds == null) return;
        for (Long tagId : tagIds.stream().filter(Objects::nonNull).distinct().toList()) {
            AlgorithmProblemTag relation = new AlgorithmProblemTag();
            relation.setProblemId(problemId);
            relation.setTagId(tagId);
            problemTagMapper.insert(relation);
        }
    }

    private void replaceCases(Long problemId, List<AlgorithmDtos.AdminTestCaseInput> inputs) {
        testCaseMapper.delete(new LambdaQueryWrapper<AlgorithmTestCase>()
                .eq(AlgorithmTestCase::getProblemId, problemId));
        int order = 1;
        for (AlgorithmDtos.AdminTestCaseInput input : inputs) {
            AlgorithmTestCase caseItem = new AlgorithmTestCase();
            caseItem.setProblemId(problemId);
            caseItem.setInputData(input.inputData());
            caseItem.setExpectedOutput(input.expectedOutput());
            caseItem.setCaseType(input.caseType());
            caseItem.setScore(input.score() == null ? 10 : input.score());
            caseItem.setSortNo(input.sortNo() == null ? order : input.sortNo());
            caseItem.setEnabled(input.enabled() == null || input.enabled() ? 1 : 0);
            testCaseMapper.insert(caseItem);
            order++;
        }
    }

    private AlgorithmDtos.ProblemListItem toListItem(ProblemStatRow row,
                                                     Map<Long, List<Long>> tagIdsByProblem,
                                                     Map<Long, AlgorithmTag> tagById,
                                                     Set<Long> favorites,
                                                     Set<Long> notes) {
        List<AlgorithmDtos.TagView> tags = tagIdsByProblem.getOrDefault(row.getId(), List.of()).stream()
                .map(tagById::get).filter(Objects::nonNull)
                .map(tag -> new AlgorithmDtos.TagView(tag.getId(), tag.getName(), tag.getCode()))
                .toList();
        int submissions = row.getSubmissionCount() == null ? 0 : row.getSubmissionCount();
        int accepted = row.getAcceptedCount() == null ? 0 : row.getAcceptedCount();
        double rate = submissions == 0 ? 0.0
                : BigDecimal.valueOf(accepted * 100.0 / submissions).setScale(1, RoundingMode.HALF_UP).doubleValue();
        return new AlgorithmDtos.ProblemListItem(
                row.getId(), row.getTitle(), row.getSlug(), row.getDifficulty(),
                difficultyLabel(row.getDifficulty()), row.getProgressStatus(),
                row.getMySubmitCount() == null ? 0 : row.getMySubmitCount(),
                submissions, accepted, rate, tags,
                favorites.contains(row.getId()), notes.contains(row.getId()));
    }

    private Set<Long> favoriteIds(Long userId, List<Long> problemIds) {
        return favoriteMapper.selectList(new LambdaQueryWrapper<AlgorithmFavorite>()
                        .eq(AlgorithmFavorite::getUserId, userId)
                        .in(AlgorithmFavorite::getProblemId, problemIds))
                .stream().map(AlgorithmFavorite::getProblemId).collect(Collectors.toSet());
    }

    private Set<Long> noteIds(Long userId, List<Long> problemIds) {
        return noteMapper.selectList(new LambdaQueryWrapper<AlgorithmNote>()
                        .eq(AlgorithmNote::getUserId, userId)
                        .in(AlgorithmNote::getProblemId, problemIds))
                .stream().map(AlgorithmNote::getProblemId).collect(Collectors.toSet());
    }

    private List<AlgorithmDtos.TagView> tagsOf(Long problemId) {
        List<Long> tagIds = problemTagMapper.selectList(new LambdaQueryWrapper<AlgorithmProblemTag>()
                        .eq(AlgorithmProblemTag::getProblemId, problemId))
                .stream().map(AlgorithmProblemTag::getTagId).toList();
        if (tagIds.isEmpty()) return List.of();
        return tagMapper.selectBatchIds(new HashSet<>(tagIds)).stream()
                .map(tag -> new AlgorithmDtos.TagView(tag.getId(), tag.getName(), tag.getCode())).toList();
    }

    private AlgorithmProblem requireEnabled(Long problemId) {
        AlgorithmProblem problem = require(problemId);
        if (problem.getStatus() == null || problem.getStatus() != 1) {
            throw BusinessException.notFound("题目不存在或已停用");
        }
        return problem;
    }

    private AlgorithmProblem require(Long problemId) {
        if (problemId == null) throw BusinessException.badRequest("题目 ID 不能为空");
        AlgorithmProblem problem = problemMapper.selectById(problemId);
        if (problem == null) throw BusinessException.notFound("题目不存在");
        return problem;
    }

    private static boolean isEnum(String value, Class<? extends Enum<?>> enumType) {
        for (Enum<?> constant : enumType.getEnumConstants()) {
            if (constant.name().equals(value)) return true;
        }
        return false;
    }

    private static String trimToNull(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    private static String difficultyLabel(String difficulty) {
        try {
            return AlgorithmDifficulty.valueOf(difficulty).getLabel();
        } catch (IllegalArgumentException exception) {
            return difficulty;
        }
    }
}
