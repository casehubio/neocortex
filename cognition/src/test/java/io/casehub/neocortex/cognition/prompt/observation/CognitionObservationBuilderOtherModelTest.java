package io.casehub.neocortex.cognition.prompt.observation;

import io.casehub.neocortex.cognition.core.CognitionConfig;
import io.casehub.neocortex.cognition.core.CognitionCore;
import io.casehub.neocortex.cognition.mood.MoodConfig;
import io.casehub.neocortex.cognition.mood.MoodOrchestrator;
import io.casehub.neocortex.mindmap.NodeInput;
import io.casehub.neocortex.mindmap.SubgraphInput;
import io.casehub.neocortex.mindmap.SubgraphTypes;
import io.casehub.neocortex.mindmap.inmem.InMemoryMindMapStore;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class CognitionObservationBuilderOtherModelTest {

    @Test
    void renderTaggedSectionsThreadsTargetAgentIdAsSubjectId() {
        var store = new InMemoryMindMapStore();
        var sgId = store.createSubgraph(
            new SubgraphInput("Cognitive", SubgraphTypes.COGNITIVE, null), "t1");
        store.addNode(NodeInput.of("other-model-agent1-target1", sgId)
            .withTraits(Set.of("OtherModelDerived"))
            .withProperties(Map.of(
                "agent-id", "agent1",
                "target-id", "target1",
                "empathic-sensing", "You sense their guardedness.")),
            "t1");

        var mood = new MoodOrchestrator(MoodConfig.defaults());
        var config = CognitionConfig.all().with("otherModel", true);
        var core = new CognitionCore(mood, null, null, null, null, null, null, null,
            null, config, store, null, null, null, null, null, null, null);

        core.tick("agent1", "t1", null, (a, t) -> Set.of(), "obs", "target1");

        var builder = new CognitionObservationBuilder(
            "agent1", "t1", store, core, null, List.of());

        var tagged = builder.renderTaggedSections();
        assertThat(tagged).anyMatch(s -> s.contains("guardedness"));
    }

    @Test
    void renderTaggedSectionsReturnsNothingWhenNoTargetAgentId() {
        var store = new InMemoryMindMapStore();
        var sgId = store.createSubgraph(
            new SubgraphInput("Cognitive", SubgraphTypes.COGNITIVE, null), "t1");
        store.addNode(NodeInput.of("other-model-agent1-target1", sgId)
            .withTraits(Set.of("OtherModelDerived"))
            .withProperties(Map.of(
                "agent-id", "agent1",
                "target-id", "target1",
                "empathic-sensing", "You sense their guardedness.")),
            "t1");

        var mood = new MoodOrchestrator(MoodConfig.defaults());
        var config = CognitionConfig.all().with("otherModel", true);
        var core = new CognitionCore(mood, null, null, null, null, null, null, null,
            null, config, store, null, null, null, null, null, null, null);

        core.tick("agent1", "t1", null, (a, t) -> Set.of());

        var builder = new CognitionObservationBuilder(
            "agent1", "t1", store, core, null, List.of());

        var tagged = builder.renderTaggedSections();
        assertThat(tagged).noneMatch(s -> s.contains("guardedness"));
    }
}
