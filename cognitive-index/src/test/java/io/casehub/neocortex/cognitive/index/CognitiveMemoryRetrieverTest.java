package io.casehub.neocortex.cognitive.index;

import io.casehub.neocortex.cognitive.Confidence;
import io.casehub.neocortex.cognitive.ModulationFactor;
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

import static org.assertj.core.api.Assertions.assertThat;

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

    private static MemoryQuery query() {
        return MemoryQuery.forSubject(SUBJECT, EXPERIENCE, "t1");
    }

    @SuppressWarnings("deprecation")
    private static Memory memory(String id, Instant createdAt, Confidence confidence) {
        return new Memory(id, "e1", EXPERIENCE, "t1", null, "text-" + id,
                Map.of(), createdAt, confidence, null, null, null);
    }

    private static CaseMemoryStore stubStore(List<Memory> fixedResults) {
        return new CaseMemoryStore() {
            @Override public String store(MemoryInput input) { throw new UnsupportedOperationException(); }
            @Override public List<Memory> query(MemoryQuery query) { return fixedResults; }
            @Override public int erase(EraseRequest request) { throw new UnsupportedOperationException(); }
        };
    }
}
