package com.mybank.autostone;

import com.mybank.autostone.util.ConditionEvaluator;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class ConditionEvaluatorTest {
    @Test
    void parsesAndEvaluatesAutosysSuccessCondition() {
        assertTrue(ConditionEvaluator.evaluate("s(JOB_A)", name ->
                "JOB_A".equals(name) ? ConditionEvaluator.Outcome.SUCCESS : ConditionEvaluator.Outcome.PENDING));
    }

    @Test
    void extractsMultipleConditionReferences() {
        assertEquals(
                java.util.Set.of("JOB_A", "JOB_B"),
                ConditionEvaluator.extractReferencedJobNames("s(JOB_A) AND (s(JOB_B) OR f(JOB_A))")
        );
    }

    @Test
    void rejectsMalformedCondition() {
        assertThrows(IllegalArgumentException.class, () -> ConditionEvaluator.extractReferencedJobNames("s(JOB_A"));
    }
}
