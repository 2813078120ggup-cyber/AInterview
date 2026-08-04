package com.tyut.aiinterview.ai;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class ChoiceAnswerScorerTest {
    private final ChoiceAnswerScorer scorer = new ChoiceAnswerScorer(new ObjectMapper());

    @Test
    void singleChoiceUsesExactAnswerInsteadOfAnswerLength() {
        ChoiceAnswerScorer.Result result = scorer.score(
                "single_choice", "[\"A\"]", "[\"A\"]", "A", "extends 用于类继承");

        assertTrue(result.correct());
        assertEquals(new BigDecimal("100.00"), result.score());
        assertTrue(result.comment().contains("回答正确"));
    }

    @Test
    void trueFalseAcceptsBooleanKeys() {
        ChoiceAnswerScorer.Result result = scorer.score(
                "true_false", "[false]", "[\"false\"]", "false", null);

        assertTrue(result.correct());
        assertEquals(new BigDecimal("100.00"), result.score());
    }

    @Test
    void multipleChoiceAwardsPartialCreditOnlyWhenNoWrongOptionIsSelected() {
        ChoiceAnswerScorer.Result partial = scorer.score(
                "multiple_choice", "[\"A\",\"B\",\"C\"]", "[\"A\",\"B\"]", "A, B", null);
        ChoiceAnswerScorer.Result wrong = scorer.score(
                "multiple_choice", "[\"A\",\"B\",\"C\"]", "[\"A\",\"D\"]", "A, D", null);

        assertFalse(partial.correct());
        assertEquals(new BigDecimal("66.67"), partial.score());
        assertEquals(new BigDecimal("0.00"), wrong.score());
    }
}
