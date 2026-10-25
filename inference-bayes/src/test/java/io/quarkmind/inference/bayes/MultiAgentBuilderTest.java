package io.quarkmind.inference.bayes;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class MultiAgentBuilderTest {

    private JunctionTree sharedTree() {
        return BayesNetworkBuilder.create("shared")
            .variable("X", "a", "b").prior(0.5, 0.5)
            .variable("Y", "a", "b").given("X").cpt(0.8, 0.2, 0.3, 0.7)
            .buildJunctionTree();
    }

    private JunctionTree altTree() {
        return BayesNetworkBuilder.create("alt")
            .variable("X", "a", "b").prior(0.6, 0.4)
            .variable("Z", "a", "b").given("X").cpt(0.9, 0.1, 0.2, 0.8)
            .buildJunctionTree();
    }

    @Test
    void sharedNetworkMode_createsComposition() {
        var comp = MultiAgentBuilder.create("test")
            .sharedNetwork(sharedTree())
            .agent("alice")
            .agent("bob")
            .build();

        assertEquals("test", comp.name());
        assertEquals(2, comp.agentNames().size());
        assertTrue(comp.hasAgent("alice"));
        assertTrue(comp.hasAgent("bob"));
        assertEquals(2, comp.agentCount());
    }

    @Test
    void perAgentMode_createsComposition() {
        var comp = MultiAgentBuilder.create("test")
            .agent("alice", sharedTree())
            .agent("bob", altTree())
            .link("alice", "X").to("bob", "X")
            .build();

        assertEquals(2, comp.agentNames().size());
        assertNotNull(comp.composition());
    }

    @Test
    void commonKnowledge_trackedInComposition() {
        var comp = MultiAgentBuilder.create("test")
            .sharedNetwork(sharedTree())
            .agent("alice")
            .agent("bob")
            .commonKnowledge("X")
            .build();

        assertTrue(comp.commonKnowledgeVars().contains("X"));
    }

    @Test
    void duplicateAgentNames_throws() {
        assertThrows(IllegalArgumentException.class, () ->
            MultiAgentBuilder.create("test")
                .sharedNetwork(sharedTree())
                .agent("alice")
                .agent("alice")
                .build());
    }

    @Test
    void fewerThanTwoAgents_throws() {
        assertThrows(IllegalArgumentException.class, () ->
            MultiAgentBuilder.create("test")
                .sharedNetwork(sharedTree())
                .agent("alice")
                .build());
    }

    @Test
    void commonKnowledgeVariableNotInNetwork_throws() {
        assertThrows(IllegalArgumentException.class, () ->
            MultiAgentBuilder.create("test")
                .sharedNetwork(sharedTree())
                .agent("alice")
                .agent("bob")
                .commonKnowledge("nonexistent")
                .build());
    }

    @Test
    void commonKnowledgeOverlapsLinkTarget_throws() {
        assertThrows(IllegalArgumentException.class, () ->
            MultiAgentBuilder.create("test")
                .agent("alice", sharedTree())
                .agent("bob", sharedTree())
                .link("alice", "X").to("bob", "X")
                .commonKnowledge("X")
                .build());
    }

    @Test
    void linkReferencesUnknownAgent_throws() {
        assertThrows(IllegalArgumentException.class, () ->
            MultiAgentBuilder.create("test")
                .sharedNetwork(sharedTree())
                .agent("alice")
                .agent("bob")
                .link("alice", "X").to("charlie", "X")
                .build());
    }

    @Test
    void agentWithVariant_createsSlotWithVariant() {
        var comp = MultiAgentBuilder.create("test")
            .sharedNetwork(sharedTree())
            .agent("alice").variant("aggressive", altTree())
            .agent("bob")
            .build();

        assertNotNull(comp.composition().slotByName("alice").variants().get("aggressive"));
    }

    @Test
    void agentNamesAreImmutable() {
        var comp = MultiAgentBuilder.create("test")
            .sharedNetwork(sharedTree())
            .agent("alice")
            .agent("bob")
            .build();

        assertThrows(UnsupportedOperationException.class, () ->
            comp.agentNames().add("charlie"));
    }
}
