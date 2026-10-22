package io.casehub.neocortex.cognition.core;

import io.casehub.neocortex.cognition.mood.MoodConfig;
import io.casehub.neocortex.cognition.mood.MoodOrchestrator;
import io.casehub.neocortex.cognition.mood.MoodSignal;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

class CognitionCoreMoodFallbackTest {

    private MoodOrchestrator mood;
    private CognitionCore core;

    @BeforeEach
    void setUp() {
        mood = new MoodOrchestrator(MoodConfig.defaults());
        core = new CognitionCore(mood, null, null, null, null, null, null, null);
    }

    @Test
    void noPriorDelta_skipped() {
        core.recordInteraction("agent1", "t1", "user1", "hello", "hi there", null);

        var m = core.tickMetrics();
        assertThat(m.moodSkipped()).isEqualTo(1);
        assertThat(m.moodFallback()).isZero();
        assertThat(m.moodSuccess()).isZero();
    }

    @Test
    void withPriorDelta_fallbackAppliesDecayedSignal() {
        mood.record(new MoodSignal.InteractionAppraisal(0.2, 0.1, -0.1, "test"),
                    "agent1", "t1");
        mood.tick("agent1", "t1");
        var baselineMood = mood.currentMood("agent1", "t1");
        assertThat(baselineMood).isPresent();

        core.seedMoodFallback("agent1", 0.2, 0.1, -0.1);

        core.recordInteraction("agent1", "t1", "user1", "hello", "hi", null);

        var m = core.tickMetrics();
        assertThat(m.moodFallback()).isEqualTo(1);
        assertThat(m.moodSkipped()).isZero();
    }

    @Test
    void fallbackDecaysExponentially() {
        core.seedMoodFallback("agent1", 0.2, 0.1, -0.1);

        core.recordInteraction("agent1", "t1", "user1", "msg1", "resp1", null);
        core.recordInteraction("agent1", "t1", "user1", "msg2", "resp2", null);

        var m = core.tickMetrics();
        assertThat(m.moodFallback()).isEqualTo(2);
    }

    @Test
    void fallbackStopsWhenDecayedBelowThreshold() {
        core.seedMoodFallback("agent1", 0.019, 0.009, -0.009);

        core.recordInteraction("agent1", "t1", "user1", "msg", "resp", null);

        var m = core.tickMetrics();
        assertThat(m.moodSkipped()).isEqualTo(1);
        assertThat(m.moodFallback()).isZero();
    }

    @Test
    void metricsRecordSuccessRate() {
        var m = new CognitionCore.CognitiveTickMetrics(7, 2, 1, 0, 5, 3, 2);

        assertThat(m.moodSuccessRate()).isCloseTo(0.7, within(0.01));
        assertThat(m.moodSignalRate()).isCloseTo(0.9, within(0.01));
        assertThat(m.bdiSuccessRate()).isCloseTo(0.5, within(0.01));
    }
}
