package io.casehub.neocortex.mindmap.intelligence.consolidation;

import io.casehub.neocortex.cognitive.Confidence;
import io.casehub.neocortex.memory.MemoryInput;
import io.casehub.neocortex.memory.Subject;
import io.casehub.neocortex.memory.experience.ExperienceEvents;
import io.casehub.neocortex.memory.inmem.InMemoryMemoryStore;
import io.casehub.neocortex.mindmap.NodeInput;
import io.casehub.neocortex.mindmap.SignalCategory;
import io.casehub.neocortex.mindmap.SubgraphInput;
import io.casehub.neocortex.mindmap.SubgraphTypes;
import io.casehub.neocortex.mindmap.inmem.InMemoryMindMapStore;
import io.casehub.platform.api.identity.CurrentPrincipal;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class RecurringThoughtPhaseTest {

    private InMemoryMindMapStore mindMapStore;
    private InMemoryMemoryStore memoryStore;
    private RecurringThoughtPhase phase;
    private static final String TENANT = "t1";

    private final CurrentPrincipal principal = new CurrentPrincipal() {
        @Override public String actorId() { return "actor"; }
        @Override public Set<String> groups() { return Set.of(); }
        @Override public String tenancyId() { return TENANT; }
        @Override public boolean isCrossTenantAdmin() { return true; }
    };

    @BeforeEach
    void setUp() {
        mindMapStore = new InMemoryMindMapStore();
        memoryStore = new InMemoryMemoryStore(principal);
        phase = new RecurringThoughtPhase(memoryStore, mindMapStore, null);
    }

    @Test
    void noExperienceMemoriesProducesNoSignals() {
        phase.beginTick();
        phase.run(TENANT, List.of());
        assertThat(phase.signals()).isEmpty();
        assertThat(phase.artifacts()).isEmpty();
    }

    @Test
    void fixationTopicEmitsSignalAndArtifact() {
        String sgId = mindMapStore.createSubgraph(new SubgraphInput("cognitive", SubgraphTypes.CONCEPT, null), TENANT);
        mindMapStore.addNode(NodeInput.of("dark-thought", sgId).withConfidence(Confidence.stated(0.3, Instant.now())).withPleasure(-0.7).withArousal(0.8).withProperty("agent-id", "agent-1"), TENANT);
        addExperienceMemories("dark-thought", 8);

        phase.beginTick();
        phase.run(TENANT, List.of());

        var signals = phase.signals();
        assertThat(signals).isNotEmpty();
        assertThat(signals.getFirst().category()).isEqualTo(SignalCategory.THOUGHT_RECURRENCE);
        assertThat(phase.artifacts()).isNotEmpty();
    }

    @Test
    void cachedAssessmentsAccessible() {
        String sgId = mindMapStore.createSubgraph(new SubgraphInput("cognitive", SubgraphTypes.CONCEPT, null), TENANT);
        mindMapStore.addNode(NodeInput.of("recurring-thought", sgId).withConfidence(Confidence.stated(0.3, Instant.now())).withPleasure(-0.7).withArousal(0.8).withProperty("agent-id", "agent-1"), TENANT);
        addExperienceMemories("recurring-thought", 8);

        phase.beginTick();
        phase.run(TENANT, List.of());

        var cached = phase.getAssessments("agent-1", TENANT);
        assertThat(cached).isNotEmpty();
    }

    @Test
    void nullMemoryStoreIsNoOp() {
        phase = new RecurringThoughtPhase(null, mindMapStore, null);
        phase.beginTick();
        phase.run(TENANT, List.of());
        assertThat(phase.signals()).isEmpty();
    }

    private void addExperienceMemories(String topicName, int count) {
        for (int i = 0; i < count; i++) {
            memoryStore.store(MemoryInput.of(Subject.of("agent", "agent-1"), ExperienceEvents.DOMAIN, TENANT, "Observation about " + topicName + " iteration " + i).withAttributes(Map.of("event-type", "observation", "subject", topicName)));
        }
    }
}
