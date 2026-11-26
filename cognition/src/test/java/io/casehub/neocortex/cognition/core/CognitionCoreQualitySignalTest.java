package io.casehub.neocortex.cognition.core;

import io.casehub.neocortex.cognition.mood.MoodConfig;
import io.casehub.neocortex.cognition.mood.MoodOrchestrator;
import io.casehub.neocortex.cognition.mood.MoodSignal;
import io.casehub.neocortex.memory.relationship.QualitySignal;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class CognitionCoreQualitySignalTest {

    private MoodOrchestrator mood;
    private CognitionCore core;

    @BeforeEach
    void setUp() {
        mood = new MoodOrchestrator(MoodConfig.defaults());
        core = new CognitionCore(mood, null, null, null, null, null, null, null);
    }

    @Test
    void positiveDelta_yieldsPositiveQuality() {
        core.seedMoodFallback("agent1", 0.2, 0.15, 0.1);

        var signal = core.deriveQualitySignal("agent1", "t1", "hello");

        assertThat(signal.quality()).isEqualTo(QualitySignal.POSITIVE);
    }

    @Test
    void negativeDelta_yieldsNegativeQuality() {
        core.seedMoodFallback("agent1", -0.2, -0.15, -0.1);

        var signal = core.deriveQualitySignal("agent1", "t1", "hello");

        assertThat(signal.quality()).isEqualTo(QualitySignal.NEGATIVE);
    }

    @Test
    void noDelta_yieldsNeutral() {
        var signal = core.deriveQualitySignal("agent1", "t1", "hello");

        assertThat(signal.quality()).isEqualTo(QualitySignal.NEUTRAL);
    }

    @Test
    void negativeCumulativeMood_positiveDelta_yieldsPositive() {
        mood.record(new MoodSignal.InteractionAppraisal(-0.3, -0.3, -0.1, "bad"),
                    "agent1", "t1");
        mood.tick("agent1", "t1");
        assertThat(mood.currentMood("agent1", "t1")).isPresent();
        var cumulativeMood = mood.currentMood("agent1", "t1").get();
        assertThat(cumulativeMood.pleasure()).isLessThan(0);

        core.seedMoodFallback("agent1", 0.2, 0.15, 0.1);

        var signal = core.deriveQualitySignal("agent1", "t1", "hello");

        assertThat(signal.quality())
                .as("quality should reflect the positive interaction delta, not the stale negative cumulative mood")
                .isEqualTo(QualitySignal.POSITIVE);
    }

    @Test
    void impactMoodSignal_populatesDeltaForQualityDerivation() {
        var impact = new CognitiveImpact(
                null,
                new MoodSignal.InteractionAppraisal(0.2, 0.15, 0.1, "good"),
                false, null, null);
        core.recordInteraction("agent1", "t1", null, "hello", "hi", impact);

        var signal = core.deriveQualitySignal("agent1", "t1", "next msg");

        assertThat(signal.quality()).isEqualTo(QualitySignal.POSITIVE);
    }
}
