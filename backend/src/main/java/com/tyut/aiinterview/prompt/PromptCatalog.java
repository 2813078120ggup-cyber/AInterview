package com.tyut.aiinterview.prompt;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class PromptCatalog {
    public static final String SIMULATION_FOLLOW_UP = "simulation.follow_up";
    public static final String SIMULATION_OPENING = "simulation.opening";
    public static final String FREE_INTERVIEW_FOLLOW_UP = "free_interview.follow_up";
    public static final String RESUME_ANALYSIS = "resume.analysis";
    public static final String ANSWER_EVALUATION = "report.answer_evaluation";
    public static final String SIMULATION_REPORT = "report.simulation_summary";
    public static final String FREE_INTERVIEW_REPORT = "report.free_summary";

    private static final Map<String, Definition> DEFINITIONS = definitionsByCode();

    private PromptCatalog() {
    }

    public static List<Definition> definitions() {
        return List.copyOf(DEFINITIONS.values());
    }

    public static Definition require(String code) {
        Definition definition = DEFINITIONS.get(code);
        if (definition == null) throw new IllegalArgumentException("未知提示词编码：" + code);
        return definition;
    }

    private static Map<String, Definition> definitionsByCode() {
        Map<String, Definition> definitions = new LinkedHashMap<>();
        add(definitions, SIMULATION_FOLLOW_UP, "模拟面试追问", "SIMULATION_INTERVIEW",
                "面试回答后的评价与单个追问", Set.of("interviewerStyle", "originalQuestion", "answer"));
        add(definitions, SIMULATION_OPENING, "模拟面试开场", "SIMULATION_INTERVIEW",
                "根据当前题目生成首个面试问题", Set.of("interviewerStyle", "question"));
        add(definitions, FREE_INTERVIEW_FOLLOW_UP, "自由面试追问", "FREE_INTERVIEW",
                "简历驱动自由面试的连续追问", Set.of("nextTurn", "resumeSummary", "transcript"));
        add(definitions, RESUME_ANALYSIS, "简历分析", "RESUME_ANALYSIS",
                "提炼简历事实、风险和首轮问题", Set.of("targetRole", "resumeText"));
        add(definitions, ANSWER_EVALUATION, "单题回答评分", "REPORT_SCORING",
                "模拟面试单题四维评分", Set.of("question", "referenceAnswer", "candidateAnswer"));
        add(definitions, SIMULATION_REPORT, "模拟面试报告", "REPORT_SCORING",
                "根据逐题评测生成综合报告", Set.of("evaluationContext"));
        add(definitions, FREE_INTERVIEW_REPORT, "自由面试报告", "REPORT_SCORING",
                "根据简历要点和十轮问答生成报告", Set.of("resumeSummary", "transcript"));
        return definitions;
    }

    private static void add(Map<String, Definition> definitions, String code, String name, String category,
                            String description, Set<String> variables) {
        definitions.put(code, new Definition(code, name, category, description, variables));
    }

    public record Definition(String code, String name, String category, String description, Set<String> variables) {
    }
}
