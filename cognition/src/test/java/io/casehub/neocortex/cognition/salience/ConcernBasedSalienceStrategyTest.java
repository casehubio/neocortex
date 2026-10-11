package io.casehub.neocortex.cognition.salience;

import io.casehub.neocortex.cognition.appraisal.Drive;
import io.casehub.neocortex.cognition.appraisal.DriveCategory;
import io.casehub.neocortex.cognition.appraisal.SalienceContext;
import io.casehub.neocortex.cognitive.Confidence;
import io.casehub.neocortex.cognitive.ConfidenceOrigin;
import io.casehub.neocortex.memory.Memory;
import io.casehub.neocortex.memory.MemoryDomain;
import io.casehub.neocortex.memory.Subject;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class ConcernBasedSalienceStrategyTest {

    private static final MemoryDomain CONCERN_DOMAIN = new MemoryDomain("concern");

    @Test
    void producesSalienceForMatchingConcern() {
        var concern = makeConcern("protect-clara", "Protect Clara from danger");
        var context = new SalienceContext(
                "Clara is in terrible danger from the villain",
                List.of(), null, List.of(concern), List.of());

        var strategy = new ConcernBasedSalienceStrategy();
        var result = strategy.perceive(context);

        assertThat(result.narrative()).isEqualTo(context.observation());
        assertThat(result.salience()).containsKey("protect-clara");
        assertThat(result.salience().get("protect-clara")).isGreaterThan(0.0);
    }

    @Test
    void noSalienceForUnrelatedObservation() {
        var concern = makeConcern("protect-clara", "Protect Clara from danger");
        var context = new SalienceContext(
                "The weather is pleasant today",
                List.of(), null, List.of(concern), List.of());

        var strategy = new ConcernBasedSalienceStrategy();
        var result = strategy.perceive(context);

        assertThat(result.salience()).isEmpty();
    }

    @Test
    void multipleConcernsProduceMultipleSalienceEntries() {
        var concern1 = makeConcern("protect-clara", "Protect Clara from danger");
        var concern2 = makeConcern("hatch-scheme", "Hatch an elaborate scheme to win");
        var context = new SalienceContext(
                "Clara is in danger and someone hatched a scheme",
                List.of(), null, List.of(concern1, concern2), List.of());

        var strategy = new ConcernBasedSalienceStrategy();
        var result = strategy.perceive(context);

        assertThat(result.salience()).containsKey("protect-clara");
        assertThat(result.salience()).containsKey("hatch-scheme");
    }

    @Test
    void narrativePassesThroughUnmodified() {
        var context = new SalienceContext(
                "Original observation text",
                List.of(), null, List.of(), List.of());

        var strategy = new ConcernBasedSalienceStrategy();
        var result = strategy.perceive(context);

        assertThat(result.narrative()).isEqualTo("Original observation text");
    }

    private static Memory makeConcern(String id, String text) {
        return new Memory(id, Subject.of("concern", id), CONCERN_DOMAIN,
                "t1", "", text, Map.of(), Instant.now(),
                new Confidence(ConfidenceOrigin.STATED, 0.8, Instant.now()),
                null, null, null, null, null);
    }
}
