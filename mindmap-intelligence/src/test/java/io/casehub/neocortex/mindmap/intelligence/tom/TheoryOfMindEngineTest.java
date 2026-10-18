package io.casehub.neocortex.mindmap.intelligence.tom;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class TheoryOfMindEngineTest {

    private TheoryOfMindEngine engine;

    @BeforeEach
    void setUp() {
        engine = new TheoryOfMindEngine();
    }

    @Test
    void priorWithNoEvidenceReflectsBaseline() {
        double[] state = engine.inferEmotionalState();
        assertEquals(1.0, state[0] + state[1] + state[2], 0.001, "posterior sums to 1");
        assertTrue(state[EmotionalValence.NEUTRAL.ordinal()] > state[EmotionalValence.DISTRESSED.ordinal()],
            "with no evidence, neutral should be more likely than distressed");
    }

    @Test
    void avoidantWithNoThreatElevatesDistress() {
        engine.observeBehavior(BehaviorType.AVOIDANT);
        engine.observeThreat(ThreatLevel.NONE);

        double[] state = engine.inferEmotionalState();
        assertTrue(state[EmotionalValence.DISTRESSED.ordinal()] > 0.4,
            "avoidant + no threat should elevate distress above 0.4, was " + state[EmotionalValence.DISTRESSED.ordinal()]);
        assertTrue(state[EmotionalValence.DISTRESSED.ordinal()] > state[EmotionalValence.POSITIVE.ordinal()],
            "distressed should be more likely than positive when avoidant");
    }

    @Test
    void approachWithNoThreatElevatesPositive() {
        engine.observeBehavior(BehaviorType.APPROACH);
        engine.observeThreat(ThreatLevel.NONE);

        double[] state = engine.inferEmotionalState();
        assertTrue(state[EmotionalValence.POSITIVE.ordinal()] > state[EmotionalValence.DISTRESSED.ordinal()],
            "approach + no threat should make positive more likely than distressed");
        assertTrue(state[EmotionalValence.POSITIVE.ordinal()] > 0.4,
            "positive should be above 0.4, was " + state[EmotionalValence.POSITIVE.ordinal()]);
    }

    @Test
    void avoidantWithSevereThreatProducesHighDistress() {
        engine.observeBehavior(BehaviorType.AVOIDANT);
        engine.observeThreat(ThreatLevel.SEVERE);

        double[] state = engine.inferEmotionalState();
        assertTrue(state[EmotionalValence.DISTRESSED.ordinal()] > 0.85,
            "avoidant + severe threat should produce distress > 0.85, was " + state[EmotionalValence.DISTRESSED.ordinal()]);
    }

    @Test
    void incrementalUpdateChangesState() {
        engine.observeBehavior(BehaviorType.AVOIDANT);
        engine.observeThreat(ThreatLevel.NONE);
        double[] first = engine.inferEmotionalState();
        double firstDistress = first[EmotionalValence.DISTRESSED.ordinal()];

        engine.observeBehavior(BehaviorType.APPROACH);
        double[] second = engine.inferEmotionalState();
        double secondDistress = second[EmotionalValence.DISTRESSED.ordinal()];

        assertTrue(secondDistress < firstDistress,
            "switching to approach behavior should reduce distress: " + firstDistress + " -> " + secondDistress);
    }

    @Test
    void resetReturnsToBaseline() {
        engine.observeBehavior(BehaviorType.AVOIDANT);
        engine.observeThreat(ThreatLevel.SEVERE);
        engine.inferEmotionalState();

        engine.reset();
        double[] baseline = engine.inferEmotionalState();
        double[] fresh = new TheoryOfMindEngine().inferEmotionalState();

        for (int i = 0; i < 3; i++) {
            assertEquals(fresh[i], baseline[i], 0.001,
                "reset should return to fresh baseline for " + EmotionalValence.values()[i]);
        }
    }

    @Test
    void softEvidenceShiftsPosteriorComparedToHard() {
        engine.observeBehavior(BehaviorType.APPROACH);
        engine.observeThreat(ThreatLevel.NONE);
        double[] hardApproach = engine.inferEmotionalState();

        engine.reset();
        engine.observeBehavior(new double[]{0.2, 0.3, 0.5});
        engine.observeThreat(ThreatLevel.NONE);
        double[] softAvoidant = engine.inferEmotionalState();

        assertEquals(1.0, softAvoidant[0] + softAvoidant[1] + softAvoidant[2], 0.001, "posterior sums to 1");
        assertTrue(softAvoidant[EmotionalValence.DISTRESSED.ordinal()] > hardApproach[EmotionalValence.DISTRESSED.ordinal()],
            "soft avoidant evidence should raise distress compared to hard approach");
        assertTrue(softAvoidant[EmotionalValence.POSITIVE.ordinal()] < hardApproach[EmotionalValence.POSITIVE.ordinal()],
            "soft avoidant evidence should lower positive compared to hard approach");
    }
}
