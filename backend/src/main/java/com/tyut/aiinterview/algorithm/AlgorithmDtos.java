package com.tyut.aiinterview.algorithm;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

public final class AlgorithmDtos {
    private AlgorithmDtos() {}

    public record TagView(Long id, String name, String code) {}

    public record TestCaseView(Long id, String inputData, String expectedOutput, Integer score, Integer sortNo) {}

    public record ProblemListItem(
            Long id, String title, String slug, String difficulty, String difficultyLabel,
            String progressStatus, Integer mySubmitCount, Integer submissionCount, Integer acceptedCount,
            Double acceptanceRate, List<TagView> tags, Boolean favorited, Boolean hasNote) {}

    public record ProblemDetailView(
            Long id, String title, String slug, String difficulty, String difficultyLabel,
            String descriptionMd, String inputDescription, String outputDescription,
            String constraintsDescription, String hintContent,
            Integer timeLimitMs, Integer memoryLimitMb, String defaultLanguage, String starterCode,
            String progressStatus, Integer mySubmitCount, Boolean favorited, String note,
            List<TagView> tags, List<TestCaseView> sampleCases) {}

    public record RunRequest(Long problemId, String language, String sourceCode, String input) {}

    public record RunResponse(Long submissionId, String status, String output, String errorMessage,
                              Long executionTimeMs, Long memoryUsageKb) {}

    public record SubmitRequest(Long problemId, String language, String sourceCode) {}

    public record SubmissionListItem(
            Long id, Long problemId, String problemTitle, String language, String submitType, String status,
            Integer passedCount, Integer totalCount, Long executionTimeMs, Long memoryUsageKb,
            LocalDateTime createdAt) {}

    public record CaseResultView(Long testCaseId, String caseType, String status, String actualOutput,
                                 Long executionTimeMs, Long memoryUsageKb) {}

    public record SubmissionDetailView(
            Long id, Long problemId, String problemTitle, String language, String submitType, String status,
            Integer score, Integer passedCount, Integer totalCount, Long executionTimeMs, Long memoryUsageKb,
            String sourceCode, String compileMessage, String runtimeMessage,
            LocalDateTime createdAt, List<CaseResultView> caseResults) {}

    public record NoteRequest(String content) {}

    public record StatusRequest(Integer status) {}

    public record DifficultyProgress(int accepted, int total) {}

    public record RecentPractice(
            Long id, Long problemId, String problemTitle, String status, String submitType, String language,
            Integer passedCount, Integer totalCount, Long executionTimeMs, LocalDateTime createdAt) {}

    public record DashboardView(
            int acceptedProblemCount, int todayAcceptedCount, int submissionCount, double acceptanceRate,
            int continuousPracticeDays, Map<String, DifficultyProgress> difficultyProgress,
            List<RecentPractice> recentPractice, List<ProblemListItem> recommended, List<ProblemListItem> hot) {}

    public record WrongProblemView(
            Long id, String title, String slug, String difficulty, String difficultyLabel,
            Integer mySubmitCount, Boolean favorited, Boolean hasNote) {}

    public record AdminProblemView(
            Long id, String title, String slug, String difficulty,
            Integer status, Integer sortNo, Integer submissionCount, Integer acceptedCount,
            LocalDateTime createdAt) {}

    public record AdminTestCaseInput(
            Long id, String inputData, String expectedOutput, String caseType,
            Integer score, Integer sortNo, Boolean enabled) {}

    public record AdminProblemSaveRequest(
            String title, String slug, String difficulty, String descriptionMd,
            String inputDescription, String outputDescription, String constraintsDescription, String hintContent,
            Integer timeLimitMs, Integer memoryLimitMb, String defaultLanguage, String starterCode,
            String solutionCode,
            Integer status, Integer sortNo, List<Long> tagIds, List<AdminTestCaseInput> testCases) {}

    public record AdminProblemDetailView(
            Long id, String title, String slug, String difficulty, String descriptionMd,
            String inputDescription, String outputDescription, String constraintsDescription, String hintContent,
            Integer timeLimitMs, Integer memoryLimitMb, String defaultLanguage, String starterCode,
            String solutionCode,
            Integer status, Integer sortNo, List<TagView> tags, List<AdminTestCaseInput> testCases,
            LocalDateTime createdAt, LocalDateTime updatedAt) {}
}
