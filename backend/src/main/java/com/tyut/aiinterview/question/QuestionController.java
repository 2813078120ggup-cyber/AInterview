package com.tyut.aiinterview.question;

import com.tyut.aiinterview.common.ApiResponse;
import com.tyut.aiinterview.common.PageResult;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/v1/question-banks")
@PreAuthorize("hasRole('ADMIN')")
public class QuestionController {
    private final QuestionService service;

    public QuestionController(QuestionService service) {
        this.service = service;
    }

    @GetMapping
    public ApiResponse<PageResult<QuestionDtos.BankVO>> page(QuestionDtos.BankQuery query) {
        return ApiResponse.ok(service.pageBanks(query));
    }

    @GetMapping("/options")
    public ApiResponse<List<QuestionDtos.QuestionOption>> options(@org.springframework.web.bind.annotation.RequestParam(required = false) String keyword) {
        return ApiResponse.ok(service.publishedOptions(keyword));
    }

    @GetMapping("/{id}")
    public ApiResponse<QuestionDtos.BankVO> detail(@PathVariable Long id) {
        return ApiResponse.ok(service.bankDetail(id));
    }

    @PostMapping
    public ApiResponse<QuestionDtos.BankVO> create(@Valid @RequestBody QuestionDtos.BankRequest request) {
        return ApiResponse.ok(service.createBank(request));
    }

    @PutMapping("/{id}")
    public ApiResponse<QuestionDtos.BankVO> update(@PathVariable Long id, @Valid @RequestBody QuestionDtos.BankRequest request) {
        return ApiResponse.ok(service.updateBank(id, request));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        service.deleteBank(id);
        return ApiResponse.ok();
    }

    @GetMapping("/{bankId}/questions")
    public ApiResponse<PageResult<QuestionDtos.QuestionVO>> pageQuestions(@PathVariable Long bankId, QuestionDtos.QuestionQuery query) {
        return ApiResponse.ok(service.pageQuestions(bankId, query));
    }

    @GetMapping("/{bankId}/questions/{id}")
    public ApiResponse<QuestionDtos.QuestionVO> questionDetail(@PathVariable Long bankId, @PathVariable Long id) {
        return ApiResponse.ok(service.questionDetail(bankId, id));
    }

    @PostMapping("/{bankId}/questions")
    public ApiResponse<QuestionDtos.QuestionVO> createQuestion(@PathVariable Long bankId, @Valid @RequestBody QuestionDtos.QuestionRequest request) {
        return ApiResponse.ok(service.createQuestion(bankId, request));
    }

    @PutMapping("/{bankId}/questions/{id}")
    public ApiResponse<QuestionDtos.QuestionVO> updateQuestion(@PathVariable Long bankId, @PathVariable Long id,
                                                                @Valid @RequestBody QuestionDtos.QuestionRequest request) {
        return ApiResponse.ok(service.updateQuestion(bankId, id, request));
    }

    @DeleteMapping("/{bankId}/questions/{id}")
    public ApiResponse<Void> deleteQuestion(@PathVariable Long bankId, @PathVariable Long id) {
        service.deleteQuestion(bankId, id);
        return ApiResponse.ok();
    }

    @GetMapping("/categories/tree")
    public ApiResponse<List<QuestionDtos.CategoryVO>> categoryTree() {
        return ApiResponse.ok(service.categoryTree());
    }

    @GetMapping("/categories/{id}")
    public ApiResponse<QuestionDtos.CategoryVO> categoryDetail(@PathVariable Long id) {
        return ApiResponse.ok(service.categoryDetail(id));
    }

    @PostMapping("/categories")
    public ApiResponse<QuestionDtos.CategoryVO> createCategory(@Valid @RequestBody QuestionDtos.CategoryRequest request) {
        return ApiResponse.ok(service.createCategory(request));
    }

    @PutMapping("/categories/{id}")
    public ApiResponse<QuestionDtos.CategoryVO> updateCategory(@PathVariable Long id,
                                                                @Valid @RequestBody QuestionDtos.CategoryRequest request) {
        return ApiResponse.ok(service.updateCategory(id, request));
    }

    @DeleteMapping("/categories/{id}")
    public ApiResponse<Void> deleteCategory(@PathVariable Long id) {
        service.deleteCategory(id);
        return ApiResponse.ok();
    }
}
