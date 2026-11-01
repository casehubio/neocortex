package io.casehub.neocortex.cognitive.index;

import io.casehub.neocortex.cognitive.Confidence;
import io.casehub.neocortex.memory.CaseMemoryStore;
import io.casehub.neocortex.memory.EraseRequest;
import io.casehub.neocortex.memory.Memory;
import io.casehub.neocortex.memory.MemoryDomain;
import io.casehub.neocortex.memory.MemoryInput;
import io.casehub.neocortex.memory.MemoryQuery;
import io.casehub.neocortex.memory.Subject;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CognitiveMemoryRetrieverTest {

    private static final Instant NOW = Instant.parse("2026-06-01T12:00:00Z");
    private static final MemoryDomain EXPERIENCE = new MemoryDomain("experience");
    private static final Subject SUBJECT = Subject.of("unknown", "e1");

    @Test
    void recencyModulationPutsRecentFirst() {
        var old = memory("m1", NOW.minus(30, ChronoUnit.DAYS), Confidence.unknown(1.0));
        var recent = memory("m2", NOW.minus(1, ChronoUnit.HOURS), Confidence.unknown(1.0));

        var retriever = new CognitiveMemoryRetriever(
                stubStore(List.of(old, recent)),
                List.of(ModulationFactors.recencyDecay(Duration.ofDays(7), NOW)));

        var results = retriever.query(query());

        assertThat(results).extracting(Memory::memoryId)
                .containsExactly("m2", "m1");
    }

    @Test
    void confidenceModulationPutsHighConfidenceFirst() {
        var low = memory("m1", NOW, Confidence.unknown(0.1));
        var high = memory("m2", NOW, Confidence.unknown(0.9));

        var retriever = new CognitiveMemoryRetriever(
                stubStore(List.of(low, high)),
                List.of(ModulationFactors.confidenceWeight()));

        var results = retriever.query(query());

        assertThat(results).extracting(Memory::memoryId)
                .containsExactly("m2", "m1");
    }

    @Test
    void noFactorsPassesThrough() {
        var m1 = memory("m1", NOW.minus(30, ChronoUnit.DAYS), Confidence.unknown(0.1));
        var m2 = memory("m2", NOW.minus(1, ChronoUnit.HOURS), Confidence.unknown(0.9));

        var retriever = new CognitiveMemoryRetriever(
                stubStore(List.of(m1, m2)),
                List.of());

        var results = retriever.query(query());

        assertThat(results).extracting(Memory::memoryId)
                .containsExactly("m1", "m2");
    }

    @Test
    void emptyResultsPassesThrough() {
        var retriever = new CognitiveMemoryRetriever(
                stubStore(List.of()),
                List.of(ModulationFactors.confidenceWeight()));

        var results = retriever.query(query());

        assertThat(results).isEmpty();
    }

    @Test
    void modulationContextRequiresTenantId() {
        assertThatThrownBy(() -> new ModulationContext(null, null, null, null, null))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void modulationContextDefensiveCopiesEntityIds() {
        var ids = new java.util.HashSet<>(Set.of("n1", "n2"));
        var ctx = new ModulationContext("agent1", "t1", null, null, ids);
        ids.add("n3");
        assertThat(ctx.contextEntityNodeIds()).hasSize(2);
    }

    @Test
    void contextualMoodFactorChangesOrdering() {
        var happy = memory("m1", NOW, Confidence.unknown(1.0), 0.8, 0.3, 0.5);
        var sad   = memory("m2", NOW, Confidence.unknown(1.0), -0.7, -0.2, -0.3);

        ContextualModulationFactory<Memory> moodFactory = ctx -> {
            if (ctx.currentMood() == null) {return (item, profile) -> 1.0;}
            return ModulationFactors.moodCongruence(ctx.currentMood(), 0.5);
        };

        var retriever = new CognitiveMemoryRetriever(
                stubStore(List.of(happy, sad)),
                List.of(),
                List.of(moodFactory));

        var happyCtx = new ModulationContext(null, "t1",
                                             new io.casehub.neocortex.memory.mood.MoodState("a1", "t1", Instant.now(), 0.8, 0.3, 0.5, "test", null, Set.of(), Map.of()), null, null);
        var results = retriever.query(query(), happyCtx);

        assertThat(results).extracting(Memory::memoryId)
                           .containsExactly("m1", "m2");
    }

    @Test
    void nullMoodSkipsContextualFactor() {
        var m1 = memory("m1", NOW, Confidence.unknown(1.0));
        var m2 = memory("m2", NOW, Confidence.unknown(1.0));

        ContextualModulationFactory<Memory> moodFactory = ctx -> {
            if (ctx.currentMood() == null) {return null;}
            return ModulationFactors.moodCongruence(ctx.currentMood(), 0.5);
        };

        var retriever = new CognitiveMemoryRetriever(
                stubStore(List.of(m1, m2)),
                List.of(),
                List.of(moodFactory));

        var noMoodCtx = new ModulationContext(null, "t1", null, null, null);
        var results   = retriever.query(query(), noMoodCtx);

        assertThat(results).extracting(Memory::memoryId)
                           .containsExactly("m1", "m2");
    }

    @Test
    void staticAndContextualFactorsCompose() {
        var oldHighConf   = memory("m1", NOW.minus(30, ChronoUnit.DAYS), Confidence.unknown(0.9));
        var recentLowConf = memory("m2", NOW.minus(1, ChronoUnit.HOURS), Confidence.unknown(0.2));

        ContextualModulationFactory<Memory> neutralFactory = ctx ->
                                                                     (item, profile) -> 1.0;

        var retriever = new CognitiveMemoryRetriever(
                stubStore(List.of(oldHighConf, recentLowConf)),
                List.of(ModulationFactors.recencyDecay(Duration.ofDays(7), NOW)),
                List.of(neutralFactory));

        var ctx     = new ModulationContext(null, "t1", null, null, null);
        var results = retriever.query(query(), ctx);

        assertThat(results).extracting(Memory::memoryId)
                           .containsExactly("m2", "m1");
    }


    private static MemoryQuery query() {
        return MemoryQuery.forSubject(SUBJECT, EXPERIENCE, "t1");
    }

    @SuppressWarnings("deprecation")
    private static Memory memory(String id, Instant createdAt, Confidence confidence) {
        return new Memory(id, "e1", EXPERIENCE, "t1", null, "text-" + id,
                Map.of(), createdAt, confidence, null, null, null);
    }

    @SuppressWarnings("deprecation")
    private static Memory memory(String id, Instant createdAt, Confidence confidence,
                                 double pleasure, double arousal, double dominance) {
        return new Memory(id, "e1", EXPERIENCE, "t1", null, "text-" + id,
                          Map.of(), createdAt, confidence, pleasure, arousal, dominance);
    }


    private static CaseMemoryStore stubStore(List<Memory> fixedResults) {
        return new CaseMemoryStore() {
            @Override public String store(MemoryInput input) { throw new UnsupportedOperationException(); }
            @Override public List<Memory> query(MemoryQuery query) { return fixedResults; }
            @Override public int erase(EraseRequest request) { throw new UnsupportedOperationException(); }
        };
    }
}
