package io.casehub.neocortex.cognitive.index;

import io.casehub.neocortex.cognitive.ModulationFactor;
import io.casehub.neocortex.cognitive.RetrievalModulator;
import io.casehub.neocortex.memory.Memory;
import io.casehub.neocortex.memory.MemoryDomain;
import io.casehub.neocortex.memory.MemoryInput;
import io.casehub.neocortex.memory.MemoryQuery;
import io.casehub.neocortex.memory.Subject;
import io.casehub.neocortex.memory.inmem.InMemoryMemoryStore;
import io.casehub.platform.api.identity.CurrentPrincipal;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class ImportanceScoringIntegrationTest {

    private static final CurrentPrincipal PRINCIPAL = new CurrentPrincipal() {
        @Override public String actorId() { return "test"; }
        @Override public Set<String> groups() { return Set.of(); }
        @Override public String tenancyId() { return "t1"; }
        @Override public boolean isCrossTenantAdmin() { return false; }
    };

    @Test
    void enrichedImportanceModulatesRetrieval() {
        var store = new InMemoryMemoryStore(PRINCIPAL);
        var subject = Subject.of("agent", "entity1");
        var domain = new MemoryDomain("experience");

        String critical = store.store(MemoryInput.of(subject, domain, "t1",
            "Enemy rushed with 6-pool — critical threat"));
        String trivial = store.store(MemoryInput.of(subject, domain, "t1",
            "Built another supply depot"));

        store.enrichAttributes(critical, Map.of("importance", "9"), "t1");
        store.enrichAttributes(trivial, Map.of("importance", "2"), "t1");

        List<Memory> memories = store.query(MemoryQuery.forSubject(subject, domain, "t1"));

        ModulationFactor<Memory> factor = new ImportanceModulationFactor();
        var modulated = RetrievalModulator.modulate(memories, ModulationProfiles.MEMORY, List.of(factor));

        assertThat(modulated).hasSize(2);
        assertThat(modulated.get(0).text()).contains("critical");
        assertThat(modulated.get(1).text()).contains("supply depot");
    }

    @Test
    void unscoredMemoriesDefaultToOneAndRankEqually() {
        var store = new InMemoryMemoryStore(PRINCIPAL);
        var subject = Subject.of("agent", "entity1");
        var domain = new MemoryDomain("experience");

        store.store(MemoryInput.of(subject, domain, "t1", "First memory"));
        store.store(MemoryInput.of(subject, domain, "t1", "Second memory"));

        List<Memory> memories = store.query(MemoryQuery.forSubject(subject, domain, "t1"));

        ModulationFactor<Memory> factor = new ImportanceModulationFactor();
        var modulated = RetrievalModulator.modulate(memories, ModulationProfiles.MEMORY, List.of(factor));

        assertThat(modulated).hasSize(2);
        assertThat(factor.apply(modulated.get(0), ModulationProfiles.MEMORY)).isEqualTo(1.0);
        assertThat(factor.apply(modulated.get(1), ModulationProfiles.MEMORY)).isEqualTo(1.0);
    }
}
