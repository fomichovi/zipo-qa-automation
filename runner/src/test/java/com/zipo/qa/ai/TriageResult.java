package com.zipo.qa.ai;

import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import java.util.List;

/**
 * The structured answer Claude must return (enforced with structured outputs, so it can't ramble
 * or return free text). Advisory only: it never changes a test's pass/fail result.
 */
public record TriageResult(
        @JsonPropertyDescription("Most likely cause of the failure.")
        Category category,
        @JsonPropertyDescription("How sure the analysis is, given the evidence provided.")
        Confidence confidence,
        @JsonPropertyDescription("One or two plain sentences: what went wrong.")
        String summary,
        @JsonPropertyDescription("2-4 short facts from the provided inputs that support the category. Quote, don't invent.")
        List<String> evidence,
        @JsonPropertyDescription("One concrete next step for the engineer.")
        String nextStep) {

    public enum Category { APP_BUG, TEST_ISSUE, ENVIRONMENT, UNKNOWN }

    public enum Confidence { HIGH, MEDIUM, LOW }
}
