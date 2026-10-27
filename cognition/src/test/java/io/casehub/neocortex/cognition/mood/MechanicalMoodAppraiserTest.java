package io.casehub.neocortex.cognition.mood;

import io.casehub.neocortex.caps.SituationActivation;
import io.casehub.neocortex.caps.SituationContext;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class MechanicalMoodAppraiserTest {

    @Test
    void positiveScenario_rewardAndMastery() {
        var ctx = SituationContext.from(List.of(
                new SituationActivation("reward", 0.8),
                new SituationActivation("mastery", 0.7)));
        var result = MechanicalMoodAppraiser.appraise(ctx);
        assertNotNull(result);
        assertTrue(result.pleasureDelta() > 0, "reward + mastery → positive pleasure");
        assertTrue(result.arousalDelta() > 0, "reward + mastery → positive arousal");
        assertTrue(result.dominanceDelta() > 0, "mastery → positive dominance");
        assertEquals("mechanical-appraisal", result.cause());
    }

    @Test
    void negativeScenario_betrayalAndPowerlessness() {
        var ctx = SituationContext.from(List.of(
                new SituationActivation("betrayal", 0.9),
                new SituationActivation("powerlessness", 0.8)));
        var result = MechanicalMoodAppraiser.appraise(ctx);
        assertNotNull(result);
        assertTrue(result.pleasureDelta() < 0, "betrayal → negative pleasure");
        assertTrue(result.arousalDelta() > 0, "betrayal → positive arousal");
        assertTrue(result.dominanceDelta() < 0, "powerlessness → negative dominance");
    }

    @Test
    void mixedScenario_competitionAndAcceptance() {
        var ctx = SituationContext.from(List.of(
                new SituationActivation("competition", 0.6),
                new SituationActivation("acceptance", 0.7)));
        var result = MechanicalMoodAppraiser.appraise(ctx);
        assertNotNull(result);
        assertTrue(result.pleasureDelta() > 0, "acceptance dominates pleasure");
        assertTrue(result.arousalDelta() > 0, "competition → arousal");
    }

    @Test
    void emptyContext_returnsNull() {
        assertNull(MechanicalMoodAppraiser.appraise(SituationContext.EMPTY));
        assertNull(MechanicalMoodAppraiser.appraise(null));
    }

    @Test
    void unknownNode_ignored() {
        var ctx = SituationContext.from(List.of(
                new SituationActivation("nonexistent_node", 1.0),
                new SituationActivation("reward", 0.5)));
        var result = MechanicalMoodAppraiser.appraise(ctx);
        assertNotNull(result);
        assertEquals(0.25 * 0.5, result.pleasureDelta(), 0.001);
    }

    @Test
    void outputClampedToRange() {
        var ctx = SituationContext.from(List.of(
                new SituationActivation("reward", 1.0),
                new SituationActivation("mastery", 1.0),
                new SituationActivation("competence_recognition", 1.0),
                new SituationActivation("acceptance", 1.0)));
        var result = MechanicalMoodAppraiser.appraise(ctx);
        assertNotNull(result);
        assertTrue(result.pleasureDelta() <= 0.3, "pleasure clamped to 0.3");
        assertTrue(result.pleasureDelta() >= -0.3, "pleasure clamped to -0.3");
        assertTrue(result.arousalDelta() <= 0.3);
        assertTrue(result.dominanceDelta() <= 0.3);
    }

    @Test
    void lowConfidence_belowThreshold_returnsNull() {
        var ctx = SituationContext.from(List.of(
                new SituationActivation("sharing", 0.01)));
        assertNull(MechanicalMoodAppraiser.appraise(ctx),
                "near-zero confidence → below 0.01 threshold → null");
    }

    @Test
    void singleThreatNode_correctPad() {
        var ctx = SituationContext.from(List.of(
                new SituationActivation("physical_threat", 1.0)));
        var result = MechanicalMoodAppraiser.appraise(ctx);
        assertNotNull(result);
        assertEquals(-0.15, result.pleasureDelta(), 0.001);
        assertEquals(0.25, result.arousalDelta(), 0.001);
        assertEquals(-0.10, result.dominanceDelta(), 0.001);
    }
}
