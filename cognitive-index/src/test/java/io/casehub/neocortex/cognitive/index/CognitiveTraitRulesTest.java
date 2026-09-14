package io.casehub.neocortex.cognitive.index;

import io.casehub.neocortex.mindmap.MindMapNode;
import io.casehub.neocortex.mindmap.NodeInput;
import io.casehub.neocortex.mindmap.SubgraphInput;
import io.casehub.neocortex.mindmap.SubgraphTypes;
import io.casehub.neocortex.mindmap.TraitRule;
import io.casehub.neocortex.mindmap.inmem.InMemoryMindMapStore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class CognitiveTraitRulesTest {

    private InMemoryMindMapStore store;
    private String subgraphId;
    private List<TraitRule> rules;

    @BeforeEach
    void setUp() throws IOException {
        store = new InMemoryMindMapStore();
        subgraphId = store.createSubgraph(
            new SubgraphInput("Cognitive", SubgraphTypes.COGNITIVE, null), "t1");
        var registry = DeclarativeRuleRegistry.loadFromClasspath(
            "rules/", null, Thread.currentThread().getContextClassLoader());
        rules = registry.allTraitRules();
    }

    private MindMapNode nodeWith(String name, Map<String, String> props) {
        String id = store.addNode(new NodeInput(name, subgraphId,
            null, "test", null, null,
            null, null, null, null, null, props), "t1");
        return store.getNode(id, "t1");
    }

    private boolean matches(String traitName, MindMapNode node) {
        return rules.stream()
            .filter(r -> r.traitName().equals(traitName))
            .anyMatch(r -> r.matches(node, List.of()));
    }

    @Test
    void belieflike_matchesCognitiveKindBelief() {
        var node = nodeWith("Project is on track", Map.of("cognitiveKind", "belief"));
        assertThat(matches("Belieflike", node)).isTrue();
    }

    @Test
    void intentionlike_matchesCognitiveKindIntention() {
        var node = nodeWith("Ship by Friday", Map.of("cognitiveKind", "intention"));
        assertThat(matches("Intentionlike", node)).isTrue();
    }

    @Test
    void predictive_matchesCognitiveKindPrediction() {
        var node = nodeWith("Market will crash", Map.of("cognitiveKind", "prediction"));
        assertThat(matches("Predictive", node)).isTrue();
    }

    @Test
    void evaluative_matchesCognitiveKindJudgment() {
        var node = nodeWith("Alice is trustworthy", Map.of("cognitiveKind", "judgment"));
        assertThat(matches("Evaluative", node)).isTrue();
    }

    @Test
    void fearlike_matchesCognitiveKindFear() {
        var node = nodeWith("Server will crash", Map.of("cognitiveKind", "fear"));
        assertThat(matches("Fearlike", node)).isTrue();
    }

    @Test
    void desirelike_matchesCognitiveKindDesire() {
        var node = nodeWith("Get promoted", Map.of("cognitiveKind", "desire"));
        assertThat(matches("Desirelike", node)).isTrue();
    }

    @Test
    void noMatch_withoutCognitiveKind() {
        var node = nodeWith("Abstract concept", Map.of());
        assertThat(matches("Belieflike", node)).isFalse();
        assertThat(matches("Intentionlike", node)).isFalse();
        assertThat(matches("Predictive", node)).isFalse();
        assertThat(matches("Evaluative", node)).isFalse();
        assertThat(matches("Fearlike", node)).isFalse();
        assertThat(matches("Desirelike", node)).isFalse();
    }

    @Test
    void compositionality_beliefWithTimeframeAlsoPredictive() {
        var node = nodeWith("Economy will recover",
            Map.of("cognitiveKind", "belief", "timeframe", "Q4 2026"));
        assertThat(matches("Belieflike", node)).isTrue();
        assertThat(matches("Predictive", node)).isTrue();
    }

    @Test
    void compositionality_beliefWithTargetAndStanceAlsoEvaluative() {
        var node = nodeWith("Alice is reliable",
            Map.of("cognitiveKind", "belief", "target", "Alice", "stance", "positive"));
        assertThat(matches("Belieflike", node)).isTrue();
        assertThat(matches("Evaluative", node)).isTrue();
    }
}
