package com.tyut.aiinterview.question;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tyut.aiinterview.common.BusinessException;
import com.tyut.aiinterview.common.PageResult;
import com.tyut.aiinterview.domain.JobPosition;
import com.tyut.aiinterview.domain.Question;
import com.tyut.aiinterview.domain.QuestionBank;
import com.tyut.aiinterview.domain.QuestionCategory;
import com.tyut.aiinterview.mapper.JobPositionMapper;
import com.tyut.aiinterview.mapper.QuestionBankMapper;
import com.tyut.aiinterview.mapper.QuestionCategoryMapper;
import com.tyut.aiinterview.mapper.QuestionMapper;
import com.tyut.aiinterview.security.CurrentUser;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class QuestionService {
    private static final Set<String> QUESTION_TYPES = Set.of("single_choice", "multiple_choice", "true_false", "short_answer", "coding");
    private static final Set<String> QUESTION_SOURCES = Set.of("manual", "ai");

    private final QuestionCategoryMapper categoryMapper;
    private final QuestionBankMapper bankMapper;
    private final QuestionMapper questionMapper;
    private final JobPositionMapper positionMapper;
    private final CurrentUser currentUser;
    private final ObjectMapper objectMapper;

    public QuestionService(QuestionCategoryMapper categoryMapper, QuestionBankMapper bankMapper, QuestionMapper questionMapper,
                           JobPositionMapper positionMapper, CurrentUser currentUser, ObjectMapper objectMapper) {
        this.categoryMapper = categoryMapper;
        this.bankMapper = bankMapper;
        this.questionMapper = questionMapper;
        this.positionMapper = positionMapper;
        this.currentUser = currentUser;
        this.objectMapper = objectMapper;
    }

    public List<QuestionDtos.CategoryVO> categoryTree() {
        List<QuestionCategory> categories = categoryMapper.selectList(new LambdaQueryWrapper<QuestionCategory>()
                .orderByAsc(QuestionCategory::getSortOrder).orderByAsc(QuestionCategory::getId));
        Map<Long, List<QuestionDtos.CategoryVO>> children = new LinkedHashMap<>();
        Map<Long, QuestionDtos.CategoryVO> nodes = new LinkedHashMap<>();
        for (QuestionCategory category : categories) {
            nodes.put(category.getId(), new QuestionDtos.CategoryVO(category.getId(), category.getParentId(), category.getCategoryCode(),
                    category.getName(), category.getSortOrder(), category.getStatus(), new ArrayList<>()));
        }
        List<QuestionDtos.CategoryVO> roots = new ArrayList<>();
        for (QuestionDtos.CategoryVO node : nodes.values()) {
            if (node.parentId() == null || !nodes.containsKey(node.parentId())) {
                roots.add(node);
            } else {
                children.computeIfAbsent(node.parentId(), ignored -> new ArrayList<>()).add(node);
            }
        }
        return attachChildren(roots, children);
    }

    public QuestionDtos.CategoryVO categoryDetail(Long id) {
        return toCategoryVO(requireCategory(id), List.of());
    }

    @Transactional
    public QuestionDtos.CategoryVO createCategory(QuestionDtos.CategoryRequest request) {
        validateCategory(request, null);
        if (categoryMapper.exists(new LambdaQueryWrapper<QuestionCategory>().eq(QuestionCategory::getCategoryCode, request.categoryCode()))) {
            throw BusinessException.badRequest("题库分类编码已存在");
        }
        QuestionCategory category = new QuestionCategory();
        apply(category, request);
        categoryMapper.insert(category);
        return toCategoryVO(category, List.of());
    }

    @Transactional
    public QuestionDtos.CategoryVO updateCategory(Long id, QuestionDtos.CategoryRequest request) {
        QuestionCategory category = requireCategory(id);
        validateCategory(request, id);
        if (!category.getCategoryCode().equals(request.categoryCode()) && categoryMapper.exists(
                new LambdaQueryWrapper<QuestionCategory>().eq(QuestionCategory::getCategoryCode, request.categoryCode()))) {
            throw BusinessException.badRequest("题库分类编码已存在");
        }
        apply(category, request);
        categoryMapper.updateById(category);
        return toCategoryVO(category, List.of());
    }

    @Transactional
    public void deleteCategory(Long id) {
        requireCategory(id);
        if (categoryMapper.exists(new LambdaQueryWrapper<QuestionCategory>().eq(QuestionCategory::getParentId, id))) {
            throw BusinessException.badRequest("分类下存在子分类，不能删除");
        }
        if (bankMapper.exists(new LambdaQueryWrapper<QuestionBank>().eq(QuestionBank::getCategoryId, id))) {
            throw BusinessException.badRequest("分类已关联题库，不能删除");
        }
        categoryMapper.deleteById(id);
    }

    public PageResult<QuestionDtos.BankVO> pageBanks(QuestionDtos.BankQuery query) {
        long pageNo = pageNo(query.pageNo());
        long pageSize = pageSize(query.pageSize());
        LambdaQueryWrapper<QuestionBank> wrapper = new LambdaQueryWrapper<QuestionBank>().orderByDesc(QuestionBank::getUpdatedAt);
        if (hasText(query.keyword())) {
            wrapper.and(item -> item.like(QuestionBank::getBankCode, query.keyword()).or().like(QuestionBank::getName, query.keyword()));
        }
        if (query.categoryId() != null) wrapper.eq(QuestionBank::getCategoryId, query.categoryId());
        if (query.positionId() != null) wrapper.eq(QuestionBank::getPositionId, query.positionId());
        if (query.status() != null) wrapper.eq(QuestionBank::getStatus, query.status());
        Page<QuestionBank> result = bankMapper.selectPage(new Page<>(pageNo, pageSize), wrapper);
        return PageResult.of(result.getRecords().stream().map(this::toBankVO).toList(), result.getTotal(), pageNo, pageSize);
    }

    public QuestionDtos.BankVO bankDetail(Long id) {
        return toBankVO(requireBank(id));
    }

    @Transactional
    public QuestionDtos.BankVO createBank(QuestionDtos.BankRequest request) {
        validateBank(request, null);
        if (bankMapper.exists(new LambdaQueryWrapper<QuestionBank>().eq(QuestionBank::getBankCode, request.bankCode()))) {
            throw BusinessException.badRequest("题库编码已存在");
        }
        QuestionBank bank = new QuestionBank();
        apply(bank, request);
        bank.setCreatedBy(currentUser.id());
        bankMapper.insert(bank);
        return toBankVO(bank);
    }

    @Transactional
    public QuestionDtos.BankVO updateBank(Long id, QuestionDtos.BankRequest request) {
        QuestionBank bank = requireBank(id);
        validateBank(request, id);
        if (!bank.getBankCode().equals(request.bankCode()) && bankMapper.exists(
                new LambdaQueryWrapper<QuestionBank>().eq(QuestionBank::getBankCode, request.bankCode()))) {
            throw BusinessException.badRequest("题库编码已存在");
        }
        apply(bank, request);
        bankMapper.updateById(bank);
        return toBankVO(bank);
    }

    @Transactional
    public void deleteBank(Long id) {
        requireBank(id);
        if (questionMapper.exists(new LambdaQueryWrapper<Question>().eq(Question::getBankId, id))) {
            throw BusinessException.badRequest("题库下仍有题目，不能删除");
        }
        bankMapper.deleteById(id);
    }

    public PageResult<QuestionDtos.QuestionVO> pageQuestions(Long bankId, QuestionDtos.QuestionQuery query) {
        requireBank(bankId);
        long pageNo = pageNo(query.pageNo());
        long pageSize = pageSize(query.pageSize());
        LambdaQueryWrapper<Question> wrapper = new LambdaQueryWrapper<Question>().eq(Question::getBankId, bankId)
                .orderByAsc(Question::getSortOrder).orderByAsc(Question::getId);
        if (hasText(query.keyword())) wrapper.like(Question::getContent, query.keyword());
        if (hasText(query.questionType())) wrapper.eq(Question::getQuestionType, query.questionType());
        if (query.difficulty() != null) wrapper.eq(Question::getDifficulty, query.difficulty());
        if (query.status() != null) wrapper.eq(Question::getStatus, query.status());
        if (hasText(query.source())) wrapper.eq(Question::getSource, query.source());
        Page<Question> result = questionMapper.selectPage(new Page<>(pageNo, pageSize), wrapper);
        return PageResult.of(result.getRecords().stream().map(this::toQuestionVO).toList(), result.getTotal(), pageNo, pageSize);
    }

    public List<QuestionDtos.QuestionOption> publishedOptions(String keyword) {
        LambdaQueryWrapper<Question> wrapper = new LambdaQueryWrapper<Question>().eq(Question::getStatus, 1).orderByAsc(Question::getBankId).orderByAsc(Question::getId);
        if (hasText(keyword)) wrapper.like(Question::getContent, keyword);
        return questionMapper.selectList(wrapper).stream().map(item -> new QuestionDtos.QuestionOption(item.getId(), item.getBankId(), item.getContent(), item.getQuestionType(), item.getDifficulty(), item.getScore())).toList();
    }

    public QuestionDtos.QuestionVO questionDetail(Long bankId, Long id) {
        Question question = requireQuestion(id);
        if (!question.getBankId().equals(bankId)) throw BusinessException.notFound("题目不属于该题库");
        return toQuestionVO(question);
    }

    @Transactional
    public QuestionDtos.QuestionVO createQuestion(Long bankId, QuestionDtos.QuestionRequest request) {
        requireBank(bankId);
        validateQuestion(request);
        Question question = new Question();
        apply(question, bankId, request);
        question.setCreatedBy(currentUser.id());
        questionMapper.insert(question);
        return toQuestionVO(question);
    }

    @Transactional
    public QuestionDtos.QuestionVO updateQuestion(Long bankId, Long id, QuestionDtos.QuestionRequest request) {
        Question question = requireQuestion(id);
        if (!question.getBankId().equals(bankId)) throw BusinessException.notFound("题目不属于该题库");
        validateQuestion(request);
        apply(question, bankId, request);
        questionMapper.updateById(question);
        return toQuestionVO(question);
    }

    @Transactional
    public void deleteQuestion(Long bankId, Long id) {
        Question question = requireQuestion(id);
        if (!question.getBankId().equals(bankId)) throw BusinessException.notFound("题目不属于该题库");
        questionMapper.deleteById(id);
    }

    private List<QuestionDtos.CategoryVO> attachChildren(Collection<QuestionDtos.CategoryVO> parents,
                                                         Map<Long, List<QuestionDtos.CategoryVO>> children) {
        return parents.stream().map(parent -> toCategoryVO(parent, attachChildren(children.getOrDefault(parent.id(), List.of()), children))).toList();
    }

    private void validateCategory(QuestionDtos.CategoryRequest request, Long categoryId) {
        validateBinaryStatus(request.status(), "分类状态");
        if (request.parentId() == null) return;
        if (request.parentId().equals(categoryId)) throw BusinessException.badRequest("分类不能设置为自身的父级");
        QuestionCategory cursor = requireCategory(request.parentId());
        int depth = 0;
        while (cursor.getParentId() != null) {
            if (cursor.getParentId().equals(categoryId)) throw BusinessException.badRequest("分类父级不能形成循环引用");
            cursor = requireCategory(cursor.getParentId());
            if (++depth > 32) throw BusinessException.badRequest("分类层级超过允许深度");
        }
    }

    private void validateBank(QuestionDtos.BankRequest request, Long ignoredId) {
        validateVisibility(request.visibility());
        validateBinaryStatus(request.status(), "题库状态");
        if (request.categoryId() != null) requireCategory(request.categoryId());
        if (request.positionId() != null) {
            JobPosition position = positionMapper.selectById(request.positionId());
            if (position == null) throw BusinessException.badRequest("关联岗位不存在");
        }
    }

    private void validateQuestion(QuestionDtos.QuestionRequest request) {
        if (!QUESTION_TYPES.contains(request.questionType())) throw BusinessException.badRequest("题目类型不合法");
        if (request.status() != null && (request.status() < 0 || request.status() > 2)) throw BusinessException.badRequest("题目状态不合法");
        String source = request.source() == null ? "manual" : request.source();
        if (!QUESTION_SOURCES.contains(source)) throw BusinessException.badRequest("题目来源不合法");
        validateJson(request.options(), "选项");
        validateJson(request.correctAnswer(), "标准答案");
        validateJson(request.tags(), "标签");
        if (Set.of("single_choice", "multiple_choice", "true_false").contains(request.questionType())
                && (!hasText(request.options()) || !hasText(request.correctAnswer()))) {
            throw BusinessException.badRequest("选择题和判断题必须填写选项与标准答案");
        }
    }

    private void validateJson(String value, String fieldName) {
        if (!hasText(value)) return;
        try {
            JsonNode ignored = objectMapper.readTree(value);
        } catch (JsonProcessingException exception) {
            throw BusinessException.badRequest(fieldName + "必须是有效 JSON");
        }
    }

    private void validateVisibility(Integer visibility) {
        if (visibility == null || visibility < 0 || visibility > 2) throw BusinessException.badRequest("题库可见性不合法");
    }

    private void validateBinaryStatus(Integer status, String fieldName) {
        if (status == null || (status != 0 && status != 1)) throw BusinessException.badRequest(fieldName + "不合法");
    }

    private void apply(QuestionCategory target, QuestionDtos.CategoryRequest source) {
        target.setParentId(source.parentId());
        target.setCategoryCode(source.categoryCode());
        target.setName(source.name());
        target.setSortOrder(source.sortOrder() == null ? 0 : source.sortOrder());
        target.setStatus(source.status());
    }

    private void apply(QuestionBank target, QuestionDtos.BankRequest source) {
        target.setCategoryId(source.categoryId());
        target.setPositionId(source.positionId());
        target.setBankCode(source.bankCode());
        target.setName(source.name());
        target.setDescription(source.description());
        target.setVisibility(source.visibility());
        target.setStatus(source.status());
    }

    private void apply(Question target, Long bankId, QuestionDtos.QuestionRequest source) {
        target.setBankId(bankId);
        target.setQuestionType(source.questionType());
        target.setDifficulty(source.difficulty());
        target.setContent(source.content());
        target.setOptions(source.options());
        target.setCorrectAnswer(source.correctAnswer());
        target.setAnswerTemplate(source.answerTemplate());
        target.setExplanation(source.explanation());
        target.setTags(source.tags());
        target.setScore(source.score());
        target.setSource(source.source() == null ? "manual" : source.source());
        target.setSortOrder(source.sortOrder() == null ? 0 : source.sortOrder());
        target.setStatus(source.status() == null ? 1 : source.status());
    }

    private QuestionCategory requireCategory(Long id) {
        QuestionCategory category = categoryMapper.selectById(id);
        if (category == null) throw BusinessException.notFound("题库分类不存在");
        return category;
    }

    private QuestionBank requireBank(Long id) {
        QuestionBank bank = bankMapper.selectById(id);
        if (bank == null) throw BusinessException.notFound("题库不存在");
        return bank;
    }

    private Question requireQuestion(Long id) {
        Question question = questionMapper.selectById(id);
        if (question == null) throw BusinessException.notFound("题目不存在");
        return question;
    }

    private QuestionDtos.CategoryVO toCategoryVO(QuestionCategory item, List<QuestionDtos.CategoryVO> children) {
        return new QuestionDtos.CategoryVO(item.getId(), item.getParentId(), item.getCategoryCode(), item.getName(), item.getSortOrder(), item.getStatus(), children);
    }

    private QuestionDtos.CategoryVO toCategoryVO(QuestionDtos.CategoryVO item, List<QuestionDtos.CategoryVO> children) {
        return new QuestionDtos.CategoryVO(item.id(), item.parentId(), item.categoryCode(), item.name(), item.sortOrder(), item.status(), children);
    }

    private QuestionDtos.BankVO toBankVO(QuestionBank item) {
        return new QuestionDtos.BankVO(item.getId(), item.getCategoryId(), item.getPositionId(), item.getBankCode(), item.getName(),
                item.getDescription(), item.getVisibility(), item.getStatus(), item.getCreatedBy(), item.getCreatedAt(), item.getUpdatedAt());
    }

    private QuestionDtos.QuestionVO toQuestionVO(Question item) {
        return new QuestionDtos.QuestionVO(item.getId(), item.getBankId(), item.getQuestionType(), item.getDifficulty(), item.getContent(),
                item.getOptions(), item.getCorrectAnswer(), item.getAnswerTemplate(), item.getExplanation(), item.getTags(), item.getScore(),
                item.getSource(), item.getSortOrder(), item.getStatus(), item.getCreatedBy(), item.getCreatedAt(), item.getUpdatedAt());
    }

    private long pageNo(Long requested) {
        return requested == null ? 1 : Math.max(1, requested);
    }

    private long pageSize(Long requested) {
        return requested == null ? 20 : Math.min(100, Math.max(1, requested));
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
