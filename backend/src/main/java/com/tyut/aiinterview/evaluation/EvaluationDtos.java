package com.tyut.aiinterview.evaluation;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;

public final class EvaluationDtos {
    private EvaluationDtos() {}
    public record HumanEvaluationRequest(
            @NotNull @DecimalMin("0") @DecimalMax("100") BigDecimal professionalScore,
            @NotNull @DecimalMin("0") @DecimalMax("100") BigDecimal expressionScore,
            @NotNull @DecimalMin("0") @DecimalMax("100") BigDecimal logicScore,
            @NotNull @DecimalMin("0") @DecimalMax("100") BigDecimal adaptabilityScore,
            @NotNull @DecimalMin("0") @DecimalMax("100") BigDecimal overallScore,
            String comment) {}
}
