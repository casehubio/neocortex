package io.casehub.neocortex.cognition.prompt;

import io.casehub.neocortex.mindmap.NodeInput;
import io.casehub.neocortex.mindmap.SubgraphInput;
import io.casehub.neocortex.mindmap.SubgraphTypes;
import io.casehub.neocortex.mindmap.inmem.InMemoryMindMapStore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class BehavioralPromptSectionTest {

    private InMemoryMindMapStore store;
    private BehavioralPromptSection section;
    private CognitionRenderContext context;

    @BeforeEach
    void setUp() {
        store = new InMemoryMindMapStore();
        section = new BehavioralPromptSection(store);
        context = new CognitionRenderContext("agent1", "t1", null);
    }

    @Test
    void returnsNullWhenNoAttractors() {
        assertThat(section.render(context)).isNull();
    }

    @Test
    void rendersSingleStrongAttractor() {
        var sgId = store.createSubgraph(
            new SubgraphInput("Behavioral", SubgraphTypes.BEHAVIORAL, null), "t1");
        store.addNode(NodeInput.of("completionism", sgId)
            .withTraits(Set.of("CapsGenerated"))
            .withProperties(Map.of(
                "caps-node-id", "completionism",
                "category", "achievement",
                "strength", "0.82",
                "agent-id", "agent1",
                "source-count", "14",
                "source-names", "task abandonment, deadline pressure")),
            "t1");

        var rendered = section.render(context);
        assertThat(rendered).isNotNull();
        assertThat(rendered).contains("deeply ingrained");
        assertThat(rendered).contains("completionism");
        assertThat(rendered).contains("task abandonment");
    }

    @Test
    void rendersStrengtheningTrend() {
        var sgId = store.createSubgraph(
            new SubgraphInput("Behavioral", SubgraphTypes.BEHAVIORAL, null), "t1");
        store.addNode(NodeInput.of("authority-distrust", sgId)
            .withTraits(Set.of("CapsGenerated"))
            .withProperties(Map.of(
                "caps-node-id", "authority-distrust",
                "category", "social",
                "strength", "0.55",
                "previous-strength", "0.40",
                "agent-id", "agent1",
                "source-count", "6",
                "source-names", "criticism encounters, hierarchical interactions")),
            "t1");

        var rendered = section.render(context);
        assertThat(rendered).isNotNull();
        assertThat(rendered).contains("noticeable");
        assertThat(rendered).contains("strengthening");
    }

    @Test
    void rendersFadingTrend() {
        var sgId = store.createSubgraph(
            new SubgraphInput("Behavioral", SubgraphTypes.BEHAVIORAL, null), "t1");
        store.addNode(NodeInput.of("resource-anxiety", sgId)
            .withTraits(Set.of("CapsGenerated"))
            .withProperties(Map.of(
                "caps-node-id", "resource-anxiety",
                "category", "survival",
                "strength", "0.28",
                "previous-strength", "0.45",
                "agent-id", "agent1",
                "source-count", "3",
                "source-names", "early scarcity experiences")),
            "t1");

        var rendered = section.render(context);
        assertThat(rendered).isNotNull();
        assertThat(rendered).contains("emerging");
        assertThat(rendered).contains("fading");
    }

    @Test
    void omitsTrendWhenStable() {
        var sgId = store.createSubgraph(
            new SubgraphInput("Behavioral", SubgraphTypes.BEHAVIORAL, null), "t1");
        store.addNode(NodeInput.of("stable-pattern", sgId)
            .withTraits(Set.of("CapsGenerated"))
            .withProperties(Map.of(
                "caps-node-id", "stable-pattern",
                "category", "social",
                "strength", "0.50",
                "previous-strength", "0.48",
                "agent-id", "agent1",
                "source-count", "5",
                "source-names", "repeated interactions")),
            "t1");

        var rendered = section.render(context);
        assertThat(rendered).isNotNull();
        assertThat(rendered).doesNotContain("strengthening");
        assertThat(rendered).doesNotContain("fading");
    }

    @Test
    void filtersAttractorsBelowThreshold() {
        var sgId = store.createSubgraph(
            new SubgraphInput("Behavioral", SubgraphTypes.BEHAVIORAL, null), "t1");
        store.addNode(NodeInput.of("weak-pattern", sgId)
            .withTraits(Set.of("CapsGenerated"))
            .withProperties(Map.of(
                "caps-node-id", "weak-pattern",
                "category", "misc",
                "strength", "0.08",
                "agent-id", "agent1",
                "source-count", "1")),
            "t1");

        assertThat(section.render(context)).isNull();
    }

    @Test
    void limitsToTopFiveByStrength() {
        var sgId = store.createSubgraph(
            new SubgraphInput("Behavioral", SubgraphTypes.BEHAVIORAL, null), "t1");
        for (int i = 1; i <= 7; i++) {
            store.addNode(NodeInput.of("pattern-" + i, sgId)
                .withTraits(Set.of("CapsGenerated"))
                .withProperties(Map.of(
                    "caps-node-id", "pattern-" + i,
                    "category", "cat",
                    "strength", String.valueOf(0.2 + i * 0.1),
                    "agent-id", "agent1",
                    "source-count", "3")),
                "t1");
        }

        var rendered = section.render(context);
        assertThat(rendered).isNotNull();
        assertThat(rendered).contains("pattern-7");
        assertThat(rendered).contains("pattern-3");
        assertThat(rendered).doesNotContain("pattern-1");
        assertThat(rendered).doesNotContain("pattern-2");
    }

    @Test
    void onlyRendersForRequestingAgent() {
        var sgId = store.createSubgraph(
            new SubgraphInput("Behavioral", SubgraphTypes.BEHAVIORAL, null), "t1");
        store.addNode(NodeInput.of("other-agent-pattern", sgId)
            .withTraits(Set.of("CapsGenerated"))
            .withProperties(Map.of(
                "caps-node-id", "other-pattern",
                "category", "social",
                "strength", "0.80",
                "agent-id", "agent2",
                "source-count", "10")),
            "t1");

        assertThat(section.render(context)).isNull();
    }

    @Test
    void rendersWithoutSourceNames() {
        var sgId = store.createSubgraph(
            new SubgraphInput("Behavioral", SubgraphTypes.BEHAVIORAL, null), "t1");
        store.addNode(NodeInput.of("unnamed-pattern", sgId)
            .withTraits(Set.of("CapsGenerated"))
            .withProperties(Map.of(
                "caps-node-id", "unnamed-pattern",
                "category", "misc",
                "strength", "0.60",
                "agent-id", "agent1",
                "source-count", "4")),
            "t1");

        var rendered = section.render(context);
        assertThat(rendered).isNotNull();
        assertThat(rendered).contains("unnamed-pattern");
        assertThat(rendered).doesNotContain("rooted in");
    }
}
