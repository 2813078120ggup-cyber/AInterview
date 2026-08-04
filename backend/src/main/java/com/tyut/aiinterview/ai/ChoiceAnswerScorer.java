package com.tyut.aiinterview.ai;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

@Component
public class ChoiceAnswerScorer {
    private final ObjectMapper objectMapper;

    public ChoiceAnswerScorer(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public Result score(String questionType, String correctAnswer, String answerData, String answerContent,
                        String explanation) {
        Set<String> correct = values(correctAnswer);
        if (correct.isEmpty()) {
            throw new IllegalStateException("选择题未配置标准答案，无法评分");
        }
        Set<String> selected = values(answerData);
        if (selected.isEmpty()) selected = values(answerContent);

        boolean exact = selected.equals(correct);
        BigDecimal score;
        String rule;
        if (exact) {
            score = BigDecimal.valueOf(100);
            rule = "回答正确";
        } else if ("multiple_choice".equals(questionType) && !selected.isEmpty()
                && correct.containsAll(selected)) {
            score = BigDecimal.valueOf(selected.size())
                    .multiply(BigDecimal.valueOf(100))
                    .divide(BigDecimal.valueOf(correct.size()), 2, RoundingMode.HALF_UP);
            rule = "答案不完整，按少选比例得分";
        } else {
            score = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
            rule = selected.isEmpty() ? "未作答" : "回答错误";
        }

        StringBuilder comment = new StringBuilder("客观题按标准答案规则判定：").append(rule)
                .append("。候选人选择：").append(display(selected))
                .append("；标准答案：").append(display(correct)).append('。');
        if (explanation != null && !explanation.isBlank()) {
            comment.append("解析：").append(explanation.trim());
        }
        return new Result(score.setScale(2, RoundingMode.HALF_UP), exact, comment.toString());
    }

    private Set<String> values(String raw) {
        if (raw == null || raw.isBlank()) return Set.of();
        String value = raw.trim();
        try {
            JsonNode node = objectMapper.readTree(value);
            if (node != null && node.isArray()) {
                Set<String> values = new LinkedHashSet<>();
                node.forEach(item -> add(values, item.asText()));
                return values;
            }
            if (node != null && node.isValueNode()) {
                Set<String> values = new LinkedHashSet<>();
                add(values, node.asText());
                return values;
            }
        } catch (JsonProcessingException ignored) {
            // Legacy answer_content stores comma-separated option keys instead of JSON.
        }
        return Arrays.stream(value.split("[,，\\s]+"))
                .map(this::normalize)
                .filter(item -> !item.isBlank())
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    private void add(Set<String> values, String value) {
        String normalized = normalize(value);
        if (!normalized.isBlank()) values.add(normalized);
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }

    private String display(Set<String> values) {
        if (values.isEmpty()) return "未作答";
        return values.stream().map(value -> switch (value) {
            case "true" -> "正确";
            case "false" -> "错误";
            default -> value.toUpperCase(Locale.ROOT);
        }).collect(Collectors.joining("、"));
    }

    public record Result(BigDecimal score, boolean correct, String comment) {
    }
}
