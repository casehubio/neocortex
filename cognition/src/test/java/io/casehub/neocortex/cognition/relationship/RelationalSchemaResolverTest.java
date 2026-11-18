package io.casehub.neocortex.cognition.relationship;

import io.casehub.neocortex.cognitive.index.RelationalSchema;
import io.casehub.neocortex.mindmap.MindMapNode;
import io.casehub.neocortex.mindmap.NodeInput;
import io.casehub.neocortex.mindmap.OverlayRef;
import io.casehub.neocortex.mindmap.SubgraphInput;
import io.casehub.neocortex.mindmap.SubgraphTypes;
import io.casehub.neocortex.mindmap.inmem.InMemoryMindMapStore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

class RelationalSchemaResolverTest {

    private static final String TENANT = "test-tenant";

    private InMemoryMindMapStore store;
    private RelationalSchemaResolver resolver;

    @BeforeEach
    void setUp() {
        store = new InMemoryMindMapStore();
        resolver = new RelationalSchemaResolver(store);
    }

    @Test
    void resolvesSchemaFromOverlayNode() {
        var sgId = store.createSubgraph(new SubgraphInput("People", SubgraphTypes.PERSON, null), TENANT);

        var targetNode = store.addNode(
                NodeInput.of("Target Agent", sgId).withProperties(Map.of("agentId", "target-agent")),
                TENANT);

        var overlayProps = Map.of(
                OverlayRef.AGENT_ID, "observer-agent",
                "rel.target-id", "target-agent",
                "rel.role", "ally",
                "rel.trust", "0.75",
                "rel.intimacy", "0.4",
                "rel.utility", "0.6");
        store.addNode(
                NodeInput.of("overlay-obs-target", sgId)
                        .withProperties(overlayProps)
                        .withTraits(Set.of("overlay"))
                        .withRefs(Set.of(OverlayRef.of(targetNode))),
                TENANT);

        var schema = resolver.resolve("observer-agent", "target-agent", TENANT);

        assertThat(schema).isNotNull();
        assertThat(schema.trust()).isCloseTo(0.75, within(1e-9));
        assertThat(schema.role()).isEqualTo("ally");
    }

    @Test
    void returnsNullWhenNoOverlayExists() {
        var sgId = store.createSubgraph(new SubgraphInput("People", SubgraphTypes.PERSON, null), TENANT);
        store.addNode(
                NodeInput.of("Target Agent", sgId).withProperties(Map.of("agentId", "target-agent")),
                TENANT);

        var schema = resolver.resolve("observer-agent", "target-agent", TENANT);
        assertThat(schema).isNull();
    }

    @Test
    void returnsNullWhenNoPeopleSubgraph() {
        var schema = resolver.resolve("observer-agent", "target-agent", TENANT);
        assertThat(schema).isNull();
    }
}
