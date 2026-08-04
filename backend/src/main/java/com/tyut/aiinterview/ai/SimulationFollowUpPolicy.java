package com.tyut.aiinterview.ai;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.List;

public final class SimulationFollowUpPolicy {
    public static final int MAX_FOLLOW_UPS = 3;

    private final ObjectMapper objectMapper;

    public SimulationFollowUpPolicy(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public State inspect(String answerData, String currentQuestion) {
        if (answerData == null || answerData.isBlank()) return new State(0, "", "", List.of());
        try {
            JsonNode messages = objectMapper.readTree(answerData);
            if (messages == null || !messages.isArray()) return new State(0, "", "", List.of());
            int followUps = 0;
            String latestCandidateAnswer = "";
            List<String> transcript = new ArrayList<>();
            List<String> previousFollowUps = new ArrayList<>();
            for (JsonNode message : messages) {
                String role = message.path("role").asText("").trim();
                String content = message.path("content").asText("").trim();
                if (("candidate".equals(role) || "assistant".equals(role)) && !content.isBlank()) {
                    transcript.add(("candidate".equals(role) ? "候选人：" : "面试官：") + clip(content, 240));
                }
                if ("candidate".equals(role) && !content.isBlank()) {
                    latestCandidateAnswer = content;
                    continue;
                }
                if (!"assistant".equals(role) || content.isBlank()) continue;
                String kind = message.path("kind").asText("").trim();
                if ("follow-up".equals(kind)) {
                    followUps += 1;
                    previousFollowUps.add(content);
                } else if (kind.isBlank() && !content.equals(currentQuestion == null ? "" : currentQuestion.trim())) {
                    // Compatibility with conversations saved before message kinds were introduced.
                    followUps += 1;
                    previousFollowUps.add(content);
                }
            }
            int from = Math.max(0, transcript.size() - 8);
            return new State(followUps, latestCandidateAnswer,
                    String.join("\n", transcript.subList(from, transcript.size())), List.copyOf(previousFollowUps));
        } catch (JsonProcessingException ignored) {
            // Historical answer data can be absent or use a non-conversation JSON shape.
            return new State(0, "", "", List.of());
        }
    }

    private String clip(String value, int maxLength) {
        return value.length() <= maxLength ? value : value.substring(0, maxLength) + "…";
    }

    public record State(int followUpCount, String latestCandidateAnswer, String conversationContext,
                        List<String> previousFollowUps) {
        public boolean limitReached() {
            return followUpCount >= MAX_FOLLOW_UPS;
        }

        public int nextSequence() {
            return Math.min(followUpCount + 1, MAX_FOLLOW_UPS);
        }
    }
}
