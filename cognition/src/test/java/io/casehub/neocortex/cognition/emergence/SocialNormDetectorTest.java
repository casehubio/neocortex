package io.casehub.neocortex.cognition.emergence;

import io.casehub.neocortex.memory.cbr.CbrMatch;
import io.casehub.neocortex.memory.cbr.CbrRecord;
import io.casehub.neocortex.memory.cbr.CbrRecordStore;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class SocialNormDetectorTest {

    private static final Instant NOW = Instant.parse("2026-09-15T12:00:00Z");

    private CbrRecordStore cbrStore;
    private SocialNormDetector detector;
    private NormDetectionConfig config;

    @BeforeEach
    void setUp() {
        cbrStore = mock(CbrRecordStore.class);
        config = new NormDetectionConfig(3, 0.7, 0.4, 2,
                "norm-detection", "norm-observation");
        detector = new SocialNormDetector(cbrStore, config,
                Clock.fixed(NOW, ZoneId.of("UTC")));
    }

    @Test
    void returnsNoChange_whenNoObservations() {
        when(cbrStore.retrieveSimilar(any(), eq(CbrRecord.class)))
                .thenReturn(List.of());

        var tick = detector.tick("t1");

        assertThat(tick).isInstanceOf(NormDetectionTick.NoChange.class);
    }

    @Test
    void detectsEstablishedNorm_whenAdherenceAboveThreshold() {
        var observations = createObservations("greeting-norm", 10, 8,
                Set.of("agent-1", "agent-2"));
        stubObservations(observations);

        var tick = detector.tick("t1");

        assertThat(tick).isInstanceOf(NormDetectionTick.Updated.class);
        var updated = (NormDetectionTick.Updated) tick;
        assertThat(updated.current().norms()).hasSize(1);
        assertThat(updated.current().norms().get(0).strength()).isEqualTo(NormStrength.ESTABLISHED);
        assertThat(updated.current().norms().get(0).adherenceRate()).isEqualTo(0.8);
        assertThat(updated.newNormIds()).containsExactly("greeting-norm");
    }

    @Test
    void detectsEmergingNorm_whenAdherenceBelowEstablished() {
        var observations = createObservations("turn-taking", 10, 5,
                Set.of("agent-1", "agent-2"));
        stubObservations(observations);

        var tick = detector.tick("t1");

        assertThat(tick).isInstanceOf(NormDetectionTick.Updated.class);
        var updated = (NormDetectionTick.Updated) tick;
        assertThat(updated.current().norms()).hasSize(1);
        assertThat(updated.current().norms().get(0).strength()).isEqualTo(NormStrength.EMERGING);
    }

    @Test
    void filtersOutPatternsWithTooFewObservations() {
        var observations = createObservations("rare-pattern", 2, 2,
                Set.of("agent-1", "agent-2"));
        stubObservations(observations);

        var tick = detector.tick("t1");

        assertThat(tick).isInstanceOf(NormDetectionTick.NoChange.class);
    }

    @Test
    void filtersOutPatternsWithTooFewAgents() {
        var observations = createObservations("single-agent", 5, 5,
                Set.of("agent-1"));
        stubObservations(observations);

        var tick = detector.tick("t1");

        assertThat(tick).isInstanceOf(NormDetectionTick.NoChange.class);
    }

    @Test
    void detectsDecliningNorm_whenAdherenceDrops() {
        var initial = createObservations("politeness", 10, 9,
                Set.of("agent-1", "agent-2"));
        stubObservations(initial);
        detector.tick("t1");

        var declining = createObservations("politeness", 10, 3,
                Set.of("agent-1", "agent-2"));
        stubObservations(declining);

        var tick = detector.tick("t1");

        assertThat(tick).isInstanceOf(NormDetectionTick.Updated.class);
        var updated = (NormDetectionTick.Updated) tick;
        assertThat(updated.current().norms()).hasSize(1);
        assertThat(updated.current().norms().get(0).strength()).isEqualTo(NormStrength.DECLINING);
        assertThat(updated.declinedNormIds()).containsExactly("politeness");
    }

    @Test
    void returnsNoChange_whenNormsUnchanged() {
        var observations = createObservations("stable-norm", 10, 8,
                Set.of("agent-1", "agent-2"));
        stubObservations(observations);
        detector.tick("t1");

        stubObservations(observations);
        var tick = detector.tick("t1");

        assertThat(tick).isInstanceOf(NormDetectionTick.NoChange.class);
    }

    @Test
    void cachesNormsPerTenant() {
        var obs = createObservations("norm-a", 10, 8, Set.of("agent-1", "agent-2"));
        stubObservations(obs);
        detector.tick("t1");

        assertThat(detector.currentNorms("t1")).isPresent();
        assertThat(detector.currentNorms("t2")).isEmpty();
    }

    private List<CbrMatch<CbrRecord>> createObservations(String pattern, int count,
                                                          int followedCount, Set<String> agents) {
        var matches = new ArrayList<CbrMatch<CbrRecord>>();
        for (int i = 0; i < count; i++) {
            boolean followed = i < followedCount;
            var observation = new NormObservation("obs-" + i, "t1", pattern,
                    agents, "conv-" + i,
                    NOW.minusSeconds(3600L * (count - i)), followed);
            var cbrCase = NormObservationSchema.toCbrCase(observation);
            matches.add(new CbrMatch<>(cbrCase, "norm-observation", 1.0));
        }
        return matches;
    }

    @SuppressWarnings("unchecked")
    private void stubObservations(List<CbrMatch<CbrRecord>> observations) {
        when(cbrStore.retrieveSimilar(any(), eq(CbrRecord.class)))
                .thenReturn((List) observations);
    }
}
