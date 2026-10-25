package io.casehub.neocortex.cognition.belief;

import io.casehub.neocortex.session.testing.StubPipelines;
import io.casehub.neocortex.cognitive.Confidence;
import io.casehub.neocortex.mindmap.NodeInput;
import io.casehub.neocortex.mindmap.SubgraphInput;
import io.casehub.neocortex.mindmap.SubgraphTypes;
import io.casehub.neocortex.mindmap.inmem.InMemoryMindMapStore;
import io.casehub.platform.agent.AgentEvent;
import io.casehub.platform.agent.AgentSession;
import io.casehub.platform.api.identity.PrincipalId;
import io.smallrye.mutiny.Multi;
import io.smallrye.mutiny.Uni;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

class BeliefRevisionPhaseTest {

    private static final String TENANT = "test-tenant";
    private InMemoryMindMapStore store;
    private AtomicInteger callCount;
    private AtomicReference<String> response;
    private BeliefRevisionPhase phase;
    private String cognitiveSubgraphId;

    @BeforeEach
    void setUp() {
        store = new InMemoryMindMapStore();
        callCount = new AtomicInteger();
        response = new AtomicReference<>("{\"contradictions\": []}");

        phase = new BeliefRevisionPhase(store, BeliefRevisionConfig.defaults());
        phase.setSessionPipeline(StubPipelines.create(init -> new AgentSession() {
            @Override
            public Multi<AgentEvent> query(String prompt) {
                callCount.incrementAndGet();
                return Multi.createFrom().item(new AgentEvent.TextDelta(response.get()));
            }
            @Override
            public Uni<Void> interrupt() { return Uni.createFrom().voidItem(); }
            @Override
            public void close(Duration timeout) {}
        }));
        cognitiveSubgraphId = store.createSubgraph(
                new SubgraphInput("cognitive-agent1", SubgraphTypes.COGNITIVE, null), TENANT);
    }

    @Test
    void noNewEvidence_skipsLlmCall() {
        addBelief("People always abandon me", "agent1");
        addEvidence("Observed: the housekeeper made a cake", "agent1");

        phase.run(TENANT, List.of());
        int firstPassCalls = callCount.get();
        assertThat(firstPassCalls).isGreaterThan(0);

        phase.run(TENANT, List.of());
        assertThat(callCount.get())
                .as("Second pass with no new evidence should not call LLM")
                .isEqualTo(firstPassCalls);
    }

    @Test
    void newEvidence_triggersRevision() {
        addBelief("People always abandon me", "agent1");
        addEvidence("Observed: guardian left", "agent1");

        phase.run(TENANT, List.of());
        int firstPassCalls = callCount.get();

        addEvidence("Observed: Peter-Perfect stayed and helped", "agent1");

        phase.run(TENANT, List.of());
        assertThat(callCount.get())
                .as("New evidence should trigger another LLM call")
                .isGreaterThan(firstPassCalls);
    }

    @Test
    void sameEvidenceAcrossMultiplePasses_onlyCallsOnce() {
        addBelief("The world is unsafe", "agent1");
        addEvidence("Observed: someone was kind", "agent1");

        phase.run(TENANT, List.of());
        phase.run(TENANT, List.of());
        phase.run(TENANT, List.of());

        assertThat(callCount.get())
                .as("Three passes with same evidence should produce exactly one LLM call")
                .isEqualTo(1);
    }

    @Test
    void contradictionFound_revisesBelief() {
        addBeliefWithConfidence("Nobody cares about me", "agent1", 0.25);
        addEvidence("The housekeeper made a birthday cake for you", "agent1");

        response.set("""
                {"contradictions": [{
                    "beliefNodeId": "any",
                    "beliefText": "Nobody cares about me",
                    "contradictingEvidence": "The housekeeper made a birthday cake",
                    "reasoning": "Someone clearly cared enough to make a cake",
                    "contradictionStrength": 0.8,
                    "revisedBelief": "Some people show care in quiet ways"
                }]}
                """);

        phase.run(TENANT, List.of());

        var nodes = store.nodesIn(cognitiveSubgraphId, TENANT);
        var revisedBeliefs = nodes.stream()
                .filter(n -> "belief-revision".equals(n.provenance()))
                .toList();
        assertThat(revisedBeliefs)
                .as("Contradiction should produce a revised belief node")
                .isNotEmpty();
    }

    @Test
    void afterRevision_cursorAdvances_nextPassSkipsSameEvidence() {
        addBeliefWithConfidence("Nobody cares about me", "agent1", 0.25);
        addEvidence("The housekeeper made a birthday cake for you", "agent1");

        response.set("""
                {"contradictions": [{
                    "beliefNodeId": "any",
                    "beliefText": "Nobody cares about me",
                    "contradictingEvidence": "The housekeeper made a birthday cake",
                    "reasoning": "Someone clearly cared",
                    "contradictionStrength": 0.8,
                    "revisedBelief": "Some people show care in quiet ways"
                }]}
                """);

        phase.run(TENANT, List.of());
        int callsAfterRevision = callCount.get();

        response.set("{\"contradictions\": []}");

        phase.run(TENANT, List.of());
        assertThat(callCount.get())
                .as("After a revision, cursor advances — next pass does not re-process same evidence")
                .isEqualTo(callsAfterRevision);
    }

    @Test
    void beliefOscillation_trustRevisedThenRebuilt() {
        var aggressiveConfig = new BeliefRevisionConfig(true, 0.5, 0.3, 0.4);
        phase = new BeliefRevisionPhase(store, aggressiveConfig);
        phase.setSessionPipeline(StubPipelines.create(init -> new AgentSession() {
            @Override
            public Multi<AgentEvent> query(String prompt) {
                callCount.incrementAndGet();
                return Multi.createFrom().item(new AgentEvent.TextDelta(response.get()));
            }
            @Override
            public Uni<Void> interrupt() { return Uni.createFrom().voidItem(); }
            @Override
            public void close(Duration timeout) {}
        }));

        addBeliefWithConfidence("People always abandon me", "agent1", 0.25);
        addEvidence("Peter-Perfect stayed and helped you", "agent1");

        response.set("""
                {"contradictions": [{
                    "beliefNodeId": "any",
                    "beliefText": "People always abandon me",
                    "contradictingEvidence": "Peter-Perfect stayed and helped",
                    "reasoning": "Direct counter-evidence",
                    "contradictionStrength": 0.9,
                    "revisedBelief": "Some people stay when it matters"
                }]}
                """);

        phase.run(TENANT, List.of());

        var revisedNodes = store.nodesIn(cognitiveSubgraphId, TENANT).stream()
                .filter(n -> "belief-revision".equals(n.provenance()))
                .toList();
        assertThat(revisedNodes).hasSize(1);
        assertThat(revisedNodes.get(0).name()).isEqualTo("Some people stay when it matters");

        addEvidence("Peter-Perfect left without saying goodbye", "agent1");

        response.set("""
                {"contradictions": [{
                    "beliefNodeId": "any",
                    "beliefText": "Some people stay when it matters",
                    "contradictingEvidence": "Peter-Perfect left without saying goodbye",
                    "reasoning": "The person who stayed before now abandoned",
                    "contradictionStrength": 0.9,
                    "revisedBelief": "People leave eventually"
                }]}
                """);

        phase.run(TENANT, List.of());

        var secondRevision = store.nodesIn(cognitiveSubgraphId, TENANT).stream()
                .filter(n -> "belief-revision".equals(n.provenance()))
                .filter(n -> n.name().equals("People leave eventually"))
                .toList();
        assertThat(secondRevision)
                .as("Second oscillation should produce another revised belief")
                .hasSize(1);
    }

    @Test
    void fiveConsolidationPasses_onlyOneCallWhenNoNewEvidence() {
        addBelief("The world is dangerous", "agent1");
        addEvidence("Something happened", "agent1");

        phase.run(TENANT, List.of());
        phase.run(TENANT, List.of());
        phase.run(TENANT, List.of());
        phase.run(TENANT, List.of());
        phase.run(TENANT, List.of());

        assertThat(callCount.get())
                .as("Five consolidation passes with same evidence should produce exactly one LLM call")
                .isEqualTo(1);
    }

    private void addBelief(String text, String agentId) {
        addBeliefWithConfidence(text, agentId, 0.8);
    }

    private void addBeliefWithConfidence(String text, String agentId, double confidence) {
        store.addNode(
                NodeInput.of(text, cognitiveSubgraphId)
                        .withTraits(Set.of("Belieflike"))
                        .withConfidence(Confidence.inferred(confidence, Instant.now()))
                        .withProvenance("experience-consolidation")
                        .withPrincipalId(PrincipalId.agent(agentId))
                        .withProperties(Map.of("cognitiveKind", "belief", "agent-id", agentId)),
                TENANT);
    }

    private void addEvidence(String text, String agentId) {
        store.addNode(
                NodeInput.of(text, cognitiveSubgraphId)
                        .withProvenance("experience-consolidation")
                        .withProperties(Map.of("agent-id", agentId, "event-type", "observation")),
                TENANT);
    }

}
