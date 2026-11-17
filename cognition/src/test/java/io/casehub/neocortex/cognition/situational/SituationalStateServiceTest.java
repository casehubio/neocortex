package io.casehub.neocortex.cognition.situational;

import io.casehub.neocortex.mindmap.SubgraphTypes;
import io.casehub.neocortex.mindmap.inmem.InMemoryMindMapStore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

class SituationalStateServiceTest {

    private InMemoryMindMapStore store;
    private SituationalStateService service;
    private static final Instant NOW = Instant.parse("2026-11-17T10:00:00Z");
    private static final java.util.Set<String> FACETS = java.util.Set.of(
            "Anxiousness", "Callousness", "Hostility", "Emotional Lability",
            "Grandiosity", "Impulsivity", "Risk Taking", "Suspiciousness",
            "Withdrawal", "Manipulativeness", "Deceitfulness",
            "Intimacy Avoidance", "Attention Seeking");

    @BeforeEach
    void setUp() {
        store = new InMemoryMindMapStore();
        service = new SituationalStateService(store, FACETS);
    }

    @Test
    void initialStateIsAtRest() {
        var state = service.loadOrCreate("t1", "agent1", NOW);
        assertThat(state.facets()).isNotEmpty();
        assertThat(state.facets()).allSatisfy((name, fs) -> {
            assertThat(fs.displacement()).isZero();
            assertThat(fs.homeOffset()).isZero();
            assertThat(fs.resilience()).isEqualTo(1.0);
        });
        for (var mode : SituationalMode.values()) {
            assertThat(state.intensity(mode)).isZero();
            assertThat(state.isEngaged(mode)).isFalse();
        }
    }

    @Test
    void persistenceRoundTrip() {
        var state = service.loadOrCreate("t1", "agent1", NOW);
        var updated = state.withFacets(Map.of(
                "Anxiousness", new FacetState(0.15, 0.02, 0.9, 1.5, 0.3),
                "Hostility", new FacetState(-0.1, 0.0, 1.0, 0.0, 0.0)));
        service.persist("t1", "agent1", updated);

        var service2 = new SituationalStateService(store, FACETS);
        var loaded = service2.loadOrCreate("t1", "agent1", NOW);
        assertThat(loaded.facets().get("Anxiousness").displacement()).isCloseTo(0.15, within(1e-9));
        assertThat(loaded.facets().get("Anxiousness").homeOffset()).isCloseTo(0.02, within(1e-9));
        assertThat(loaded.facets().get("Anxiousness").resilience()).isCloseTo(0.9, within(1e-9));
        assertThat(loaded.facets().get("Hostility").displacement()).isCloseTo(-0.1, within(1e-9));
    }

    @Test
    void deterministicNodeIdentity() {
        var name1 = SituationalStateService.nodeName("agent1");
        var name2 = SituationalStateService.nodeName("agent1");
        assertThat(name1).isEqualTo(name2);
        assertThat(name1).startsWith("sit-state:");
    }

    @Test
    void differentAgentsDifferentNodes() {
        var name1 = SituationalStateService.nodeName("agent1");
        var name2 = SituationalStateService.nodeName("agent2");
        assertThat(name1).isNotEqualTo(name2);
    }

    @Test
    void tenantIsolation() {
        service.loadOrCreate("t1", "agent1", NOW);
        service.loadOrCreate("t2", "agent1", NOW);

        var subgraphs = store.listSubgraphs("t1").stream()
                .filter(sg -> SubgraphTypes.PERSONALITY_STATE.equals(sg.type()))
                .toList();
        assertThat(subgraphs).hasSize(1);

        var subgraphs2 = store.listSubgraphs("t2").stream()
                .filter(sg -> SubgraphTypes.PERSONALITY_STATE.equals(sg.type()))
                .toList();
        assertThat(subgraphs2).hasSize(1);
        assertThat(subgraphs.getFirst().id()).isNotEqualTo(subgraphs2.getFirst().id());
    }

    @Test
    void secondLoadReturnsCached() {
        var state1 = service.loadOrCreate("t1", "agent1", NOW);
        var state2 = service.loadOrCreate("t1", "agent1", NOW);
        assertThat(state1).isSameAs(state2);
    }

    @Test
    void currentStateNullBeforeLoad() {
        assertThat(service.currentState("t1", "agent1")).isNull();
    }

    @Test
    void currentStateAvailableAfterLoad() {
        service.loadOrCreate("t1", "agent1", NOW);
        assertThat(service.currentState("t1", "agent1")).isNotNull();
    }

    @Test
    void propertiesRoundTrip() {
        var facets = new java.util.LinkedHashMap<String, FacetState>();
        facets.put("Anxiousness", new FacetState(0.12, 0.03, 0.85, 2.1, 0.4));
        facets.put("Hostility", FacetState.initial());
        var state = new PersonalityState(facets,
                Map.of(SituationalMode.STRESS, 0.75, SituationalMode.FLOW, 0.0),
                Map.of(SituationalMode.STRESS, true, SituationalMode.FLOW, false),
                Map.of(SituationalMode.Group.AFFECTIVE, SituationalMode.STRESS),
                NOW, PersonalityState.truncateToHour(NOW), NOW.plusSeconds(3600));

        var props = SituationalStateService.toProperties(state);
        var loaded = service.fromProperties(props, NOW);

        assertThat(loaded.facets().get("Anxiousness").displacement()).isCloseTo(0.12, within(1e-9));
        assertThat(loaded.facets().get("Anxiousness").resilience()).isCloseTo(0.85, within(1e-9));
        assertThat(loaded.intensity(SituationalMode.STRESS)).isCloseTo(0.75, within(1e-9));
        assertThat(loaded.isEngaged(SituationalMode.STRESS)).isTrue();
        assertThat(loaded.winners().get(SituationalMode.Group.AFFECTIVE)).isEqualTo(SituationalMode.STRESS);
        assertThat(loaded.cursorTime()).isEqualTo(NOW);
        assertThat(loaded.exposedUntil()).isEqualTo(NOW.plusSeconds(3600));
    }
}
