package io.casehub.neocortex.cognition.situational;

import io.casehub.neocortex.cognition.journal.CognitiveEntry;
import io.casehub.neocortex.cognition.journal.CognitiveJournal;
import io.casehub.neocortex.cognitive.index.EffectiveCognitiveDefaults;
import io.casehub.neocortex.mindmap.inmem.InMemoryMindMapStore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class SituationalDeformationPhaseTest {

    private static final String TENANT = "test-tenant";
    private static final String AGENT = "frida";
    private static final Instant NOW = Instant.parse("2026-11-17T10:00:00Z");
    private static final Set<String> FACETS = Set.of("Anxiousness", "Emotional Lability", "Hostility");

    private SituationalDeformationPhase phase;
    private PersonalityStateService     stateService;
    private EffectiveCognitiveDefaults  effectiveDefaults;
    private List<CognitiveEntry> journalEntries;
    private Clock clock;

    @BeforeEach
    void setUp() {
        clock = Clock.fixed(NOW.plus(Duration.ofHours(3)), ZoneId.of("UTC"));
        var store = new InMemoryMindMapStore();
        stateService = new PersonalityStateService(store, FACETS);
        effectiveDefaults = EffectiveCognitiveDefaults.forTesting();
        journalEntries = new ArrayList<>();
        CognitiveJournal journal = journalEntries::add;

        var config = DynamicsConfig.defaults();
        var integrator = new PersonalityStateIntegrator(config);

        phase = new SituationalDeformationPhase(
                stateService,
                integrator,
                effectiveDefaults,
                journal,
                clock
        );
    }

    @Test
    void advancesIdleAgentState() {
        var state = stateService.loadOrCreate(TENANT, AGENT, NOW);
        var displaced = new LinkedHashMap<>(state.facets());
        displaced.put("Anxiousness", new FacetState(0.15, 0.02, 0.9, 1.5, 0.3));
        state = state.withFacets(displaced).withCursorTime(NOW);
        stateService.persist(TENANT, AGENT, state);

        double initialDisplacement = stateService.currentState(TENANT, AGENT)
                .facet("Anxiousness").displacement();
        assertThat(initialDisplacement).isEqualTo(0.15);

        phase.run(TENANT, List.of());

        var advanced = stateService.currentState(TENANT, AGENT);
        assertThat(advanced.facet("Anxiousness").displacement())
                .isLessThan(initialDisplacement);
    }

    @Test
    void journalsConsolidation() {
        var state = stateService.loadOrCreate(TENANT, AGENT, NOW);
        var displaced = new LinkedHashMap<>(state.facets());
        displaced.put("Anxiousness", new FacetState(0.15, 0.02, 0.9, 1.5, 0.3));
        state = state.withFacets(displaced).withCursorTime(NOW);
        stateService.persist(TENANT, AGENT, state);

        phase.run(TENANT, List.of());

        var consolidations = journalEntries.stream()
                .filter(e -> e instanceof CognitiveEntry.SituationalDeformationConsolidated)
                .map(e -> (CognitiveEntry.SituationalDeformationConsolidated) e)
                .toList();
        assertThat(consolidations).hasSize(1);
        assertThat(consolidations.getFirst().tenantId()).isEqualTo(TENANT);
        assertThat(consolidations.getFirst().agentId()).isEqualTo(AGENT);
        assertThat(consolidations.getFirst().hoursAdvanced()).isGreaterThan(0);
    }

    @Test
    void noStateNoAction() {
        phase.run(TENANT, List.of());
        assertThat(journalEntries).isEmpty();
    }

    @Test
    void alreadyAdvancedStateSkips() {
        var advancedClock = Clock.fixed(NOW, ZoneId.of("UTC"));
        var store = new InMemoryMindMapStore();
        var svc = new PersonalityStateService(store, FACETS);
        var journal2 = new ArrayList<CognitiveEntry>();
        var phase2 = new SituationalDeformationPhase(
                svc, new PersonalityStateIntegrator(DynamicsConfig.defaults()),
                effectiveDefaults, journal2::add, advancedClock);

        var state = svc.loadOrCreate(TENANT, AGENT, NOW);
        svc.persist(TENANT, AGENT, state);

        phase2.run(TENANT, List.of());

        var consolidations = journal2.stream()
                .filter(e -> e instanceof CognitiveEntry.SituationalDeformationConsolidated)
                .toList();
        assertThat(consolidations).hasSize(1);
        assertThat(((CognitiveEntry.SituationalDeformationConsolidated) consolidations.getFirst()).hoursAdvanced())
                .isEqualTo(0);
    }

    @Test
    void name() {
        assertThat(phase.name()).isEqualTo("situational-deformation");
    }
}
