package com.tyut.aiinterview.ai;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

class SimulationFollowUpPolicyTest {
    private final SimulationFollowUpPolicy policy = new SimulationFollowUpPolicy(new ObjectMapper());

    @Test
    void countsOnlyFollowUpsAndReturnsLatestCandidateAnswer() {
        SimulationFollowUpPolicy.State state = policy.inspect("""
                [
                  {"role":"assistant","content":"什么是 volatile？","kind":"question"},
                  {"role":"candidate","content":"它保证可见性。"},
                  {"role":"assistant","content":"它如何保证可见性？","kind":"follow-up"},
                  {"role":"candidate","content":"通过内存屏障。"},
                  {"role":"assistant","content":"内存屏障有什么作用？","kind":"follow-up"},
                  {"role":"assistant","content":"这道题到这里。","kind":"transition"}
                ]
                """, "什么是 volatile？");

        assertEquals(2, state.followUpCount());
        assertEquals(3, state.nextSequence());
        assertEquals("通过内存屏障。", state.latestCandidateAnswer());
        assertEquals(2, state.previousFollowUps().size());
        assertTrue(state.conversationContext().contains("候选人：通过内存屏障。"));
        assertFalse(state.limitReached());
    }

    @Test
    void thirdFollowUpReachesLimit() {
        SimulationFollowUpPolicy.State state = policy.inspect("""
                [
                  {"role":"assistant","content":"追问一","kind":"follow-up"},
                  {"role":"assistant","content":"追问二","kind":"follow-up"},
                  {"role":"assistant","content":"追问三","kind":"follow-up"}
                ]
                """, "主问题");

        assertEquals(3, state.followUpCount());
        assertTrue(state.limitReached());
    }

    @Test
    void legacyMessagesExcludeRepeatedMainQuestion() {
        SimulationFollowUpPolicy.State state = policy.inspect("""
                [
                  {"role":"assistant","content":"主问题"},
                  {"role":"candidate","content":"第一次回答"},
                  {"role":"assistant","content":"旧版追问"}
                ]
                """, "主问题");

        assertEquals(1, state.followUpCount());
        assertEquals("第一次回答", state.latestCandidateAnswer());
    }
}
