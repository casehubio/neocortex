package io.casehub.neocortex.cognition.dissonance;

import io.casehub.neocortex.mindmap.MindMapNode;
import io.casehub.neocortex.mindmap.NodeInput;
import io.casehub.neocortex.mindmap.SubgraphInput;
import io.casehub.neocortex.mindmap.SubgraphTypes;
import io.casehub.neocortex.mindmap.inmem.InMemoryMindMapStore;
import io.casehub.neocortex.session.testing.StubPipelines;
import io.casehub.platform.agent.AgentEvent;
import io.casehub.platform.agent.AgentSession;
import io.casehub.platform.api.identity.PrincipalId;
import io.smallrye.mutiny.Multi;
import io.smallrye.mutiny.Uni;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

class DissonanceDetectionPhaseTest {

    private InMemoryMindMapStore store;
    private DissonanceDetectionPhase phase;
    private AtomicReference<String> response;
    private static final String TENANT = "test-tenant";
    private static final String AGENT = "agent-1";
    private String cognitiveSubgraphId;

    @BeforeEach
    void setUp() {
        store = new InMemoryMindMapStore();
        response = new AtomicReference<>("{\"contradictions\": []}");
        var config = new DissonanceDetectionConfig(true, 0.1, 0.15, 0.3, 3,
            0.15, 0.3, 0.10, 0.2, 0.05, 0.1, 0.5);
        phase = new DissonanceDetectionPhase(store, config);
        phase.setSessionPipeline(StubPipelines.create(init -> new AgentSession() {
            @Override
            public Multi<AgentEvent> query(String prompt) {
                return Multi.createFrom().item(new AgentEvent.TextDelta(response.get()));
            }
            @Override
            public Uni<Void> interrupt() { return Uni.createFrom().voidItem(); }
            @Override
            public void close(Duration timeout) {}
        }));
        cognitiveSubgraphId = store.createSubgraph(
            new SubgraphInput("Cognitive", SubgraphTypes.COGNITIVE, null), TENANT);
        store.createSubgraph(
            new SubgraphInput("Behavioral", SubgraphTypes.BEHAVIORAL, null), TENANT);
    }

    @Test
    void skipsWhenDisabled() {
        var disabled = new DissonanceDetectionPhase(store,
            DissonanceDetectionConfig.defaults());
        disabled.setSessionPipeline(StubPipelines.returningJson("{}"));
        seedBeliefs("I am kind", "I enjoy cruelty");
        disabled.run(TENANT, List.of());
        assertThat(tensionNodes()).isEmpty();
    }

    @Test
    void skipsWhenNoSessionPipeline() {
        var noLlm = new DissonanceDetectionPhase(store,
            new DissonanceDetectionConfig(true, 0.1, 0.15, 0.3, 3,
                0.15, 0.3, 0.10, 0.2, 0.05, 0.1, 0.5));
        seedBeliefs("I am kind", "I enjoy cruelty");
        noLlm.run(TENANT, List.of());
        assertThat(tensionNodes()).isEmpty();
    }

    @Test
    void createsTensionNodeWhenContradictionDetected() {
        seedBeliefs("I am kind", "I enjoy cruelty");
        response.set(contradictionJson(
            "belief-1", "I am kind", "belief-2", "I enjoy cruelty", 0.85));
        phase.run(TENANT, List.of());

        var tensions = tensionNodes();
        assertThat(tensions).hasSize(1);
        var t = tensions.getFirst();
        assertThat(t.traits()).contains("DissonanceTension");
        assertThat(t.property("strength")).hasValue("0.85");
        assertThat(t.property("tension-type")).hasValue("belief-belief");
        assertThat(t.property("agent-id")).hasValue(AGENT);
    }

    @Test
    void confirmsTensionWhenRedetected() {
        seedBeliefs("I am kind", "I enjoy cruelty");
        response.set(contradictionJson(
            "belief-1", "I am kind", "belief-2", "I enjoy cruelty", 0.85));
        phase.run(TENANT, List.of());
        String firstConfirmed = tensionNodes().getFirst()
            .property("last-confirmed").orElse("");

        response.set(contradictionJson(
            "belief-1", "I am kind", "belief-2", "I enjoy cruelty", 0.90));
        phase.run(TENANT, List.of());

        var tensions = tensionNodes();
        assertThat(tensions).hasSize(1);
        String secondConfirmed = tensions.getFirst()
            .property("last-confirmed").orElse("");
        assertThat(secondConfirmed).isNotEqualTo(firstConfirmed);
        assertThat(tensions.getFirst().property("decay-count")).hasValue("0");
    }

    @Test
    void decaysAdditivelyWhenNotRedetected() {
        seedBeliefs("I am kind", "I enjoy cruelty");
        response.set(contradictionJson(
            "belief-1", "I am kind", "belief-2", "I enjoy cruelty", 0.50));
        phase.run(TENANT, List.of());

        response.set("{\"contradictions\": []}");
        phase.run(TENANT, List.of());

        var tensions = tensionNodes();
        assertThat(tensions).hasSize(1);
        double strength = Double.parseDouble(
            tensions.getFirst().property("strength").orElse("0"));
        assertThat(strength).isCloseTo(0.40, org.assertj.core.data.Offset.offset(0.001));
        assertThat(tensions.getFirst().property("decay-count")).hasValue("1");
    }

    @Test
    void supersedesWhenBelowThreshold() {
        seedBeliefs("I am kind", "I enjoy cruelty");
        response.set(contradictionJson(
            "belief-1", "I am kind", "belief-2", "I enjoy cruelty", 0.20));
        phase.run(TENANT, List.of());
        assertThat(tensionNodes()).hasSize(1);

        response.set("{\"contradictions\": []}");
        phase.run(TENANT, List.of());
        // 0.20 - 0.10 = 0.10, below threshold 0.15 → superseded
        // nodesIn excludes superseded nodes
        assertThat(tensionNodes()).isEmpty();
    }

    @Test
    void deduplicatesOrderIndependentPairs() {
        seedBeliefs("I am kind", "I enjoy cruelty");
        response.set(contradictionJson(
            "belief-2", "I enjoy cruelty", "belief-1", "I am kind", 0.85));
        phase.run(TENANT, List.of());
        // Same pair, reversed order — should match existing
        response.set(contradictionJson(
            "belief-1", "I am kind", "belief-2", "I enjoy cruelty", 0.90));
        phase.run(TENANT, List.of());

        assertThat(tensionNodes()).hasSize(1);
    }

    @Test
    void populatesInMemoryCache() {
        seedBeliefs("I am kind", "I enjoy cruelty");
        response.set(contradictionJson(
            "belief-1", "I am kind", "belief-2", "I enjoy cruelty", 0.85));
        phase.run(TENANT, List.of());

        var cached = phase.activeTensions(AGENT, TENANT);
        assertThat(cached).hasSize(1);
        assertThat(cached.getFirst().strength()).isCloseTo(0.85,
            org.assertj.core.data.Offset.offset(0.001));
    }

    // --- helpers ---

    private void seedBeliefs(String... beliefs) {
        for (int i = 0; i < beliefs.length; i++) {
            store.addNode(
                NodeInput.of(beliefs[i], cognitiveSubgraphId)
                    .withTraits(Set.of("Belieflike"))
                    .withProperties(Map.of("agent-id", AGENT))
                    .withPrincipalId(PrincipalId.agent(AGENT)),
                TENANT);
        }
    }

    private List<MindMapNode> tensionNodes() {
        return store.nodesIn(cognitiveSubgraphId, TENANT).stream()
            .filter(n -> n.traits().contains("DissonanceTension"))
            .toList();
    }

    private String contradictionJson(String idA, String textA,
                                      String idB, String textB,
                                      double strength) {
        return """
            {"contradictions": [{
              "elementA": {"nodeId": "%s", "type": "belief", "text": "%s"},
              "elementB": {"nodeId": "%s", "type": "belief", "text": "%s"},
              "strength": %s,
              "capsActivationsA": {"acceptance": 0.7},
              "capsActivationsB": {"dominance": 0.8},
              "reasoning": "test contradiction"
            }]}""".formatted(idA, textA, idB, textB, strength);
    }
}
