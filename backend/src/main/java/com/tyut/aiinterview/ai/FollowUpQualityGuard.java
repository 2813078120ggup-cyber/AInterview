package com.tyut.aiinterview.ai;

import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

final class FollowUpQualityGuard {
    private static final List<String> TRANSITION_PHRASES = List.of(
            "下一题", "下一个问题", "下一道题", "换一道", "进入下一", "过渡到");
    private static final List<String> FALLBACKS = List.of(
            "你刚才提到的关键机制，在什么边界条件下可能失效或需要额外处理？",
            "沿着刚才的思路，如果运行环境或输入规模发生变化，你会如何验证结论仍然成立？",
            "结合一个真实实现，你会用什么指标或现象判断这个方案达到了预期效果？",
            "这个方案出现异常时，你会先观察哪一个信号来定位问题？");

    String rejectionReason(String candidate, List<String> previousFollowUps) {
        String value = candidate == null ? "" : candidate.trim();
        if (value.length() < 8) return "追问内容过短";
        if (value.length() > 180) return "追问内容过长";
        long questionMarks = value.chars().filter(character -> character == '?' || character == '？').count();
        if (questionMarks != 1) return "必须且只能提出一个问题";
        if (TRANSITION_PHRASES.stream().anyMatch(value::contains)) return "追问不得切换到下一题";
        if (previousFollowUps != null && previousFollowUps.stream()
                .filter(previous -> previous != null && !previous.isBlank())
                .anyMatch(previous -> similarity(value, previous) >= 0.68d)) {
            return "追问与本题已有追问过于相似";
        }
        return "";
    }

    String fallback(List<String> previousFollowUps) {
        for (String fallback : FALLBACKS) {
            if (rejectionReason(fallback, previousFollowUps).isBlank()) return fallback;
        }
        return FALLBACKS.get(Math.floorMod(previousFollowUps == null ? 0 : previousFollowUps.size(), FALLBACKS.size()));
    }

    private double similarity(String left, String right) {
        Set<String> leftPairs = pairs(normalize(left));
        Set<String> rightPairs = pairs(normalize(right));
        if (leftPairs.isEmpty() || rightPairs.isEmpty()) return normalize(left).equals(normalize(right)) ? 1d : 0d;
        Set<String> intersection = new HashSet<>(leftPairs);
        intersection.retainAll(rightPairs);
        Set<String> union = new HashSet<>(leftPairs);
        union.addAll(rightPairs);
        return union.isEmpty() ? 0d : (double) intersection.size() / union.size();
    }

    private Set<String> pairs(String value) {
        Set<String> result = new HashSet<>();
        for (int index = 0; index + 1 < value.length(); index++) {
            result.add(value.substring(index, index + 2));
        }
        return result;
    }

    private String normalize(String value) {
        return value == null ? "" : value.toLowerCase(Locale.ROOT)
                .replaceAll("[^\\p{IsHan}a-z0-9]", "");
    }
}
