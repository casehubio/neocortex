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

class OtherModelPromptSectionTest {

    private InMemoryMindMapStore store;
    private OtherModelPromptSection section;

    @BeforeEach
    void setUp() {
        store = new InMemoryMindMapStore();
        section = new OtherModelPromptSection(store);
    }

    @Test
    void returnsNullWhenSubjectIdIsNull() {
        var context = new CognitionRenderContext("agent1", "t1", null);
        assertThat(section.render(context)).isNull();
    }

    @Test
    void returnsNullWhenNoOtherModelNode() {
        var context = new CognitionRenderContext("agent1", "t1", "target1");
        assertThat(section.render(context)).isNull();
    }

    @Test
    void rendersEmpathicSensingAndRelationalUnderstanding() {
        var sgId = store.createSubgraph(
                new SubgraphInput("Cognitive", SubgraphTypes.COGNITIVE, null), "t1");
        store.addNode(NodeInput.of("other-model-agent1-target1", sgId)
                               .withTraits(Set.of("OtherModelDerived"))
                               .withProperties(Map.of(
                                       "agent-id", "agent1",
                                       "target-id", "target1",
                                       "empathic-sensing", "You sense their guardedness — a tightness behind the words.",
                                       "relational-understanding", "The bond is built on shared adversity.",
                                       "inferred-values", "They value autonomy above all.",
                                       "appraisal-sensitivities", "Criticism hits them hardest.")),
                      "t1");

        var context  = new CognitionRenderContext("agent1", "t1", "target1");
        var rendered = section.render(context);
        assertThat(rendered).isNotNull();
        assertThat(rendered).contains("guardedness");
        assertThat(rendered).contains("shared adversity");
        assertThat(rendered).doesNotContain("autonomy above all");
        assertThat(rendered).doesNotContain("Criticism hits");
    }

    @Test
    void rendersPartialProperties() {
        var sgId = store.createSubgraph(
                new SubgraphInput("Cognitive", SubgraphTypes.COGNITIVE, null), "t1");
        store.addNode(NodeInput.of("other-model-agent1-target1", sgId)
                               .withTraits(Set.of("OtherModelDerived"))
                               .withProperties(Map.of(
                                       "agent-id", "agent1",
                                       "target-id", "target1",
                                       "empathic-sensing", "A warmth radiates from them.")),
                      "t1");

        var context  = new CognitionRenderContext("agent1", "t1", "target1");
        var rendered = section.render(context);
        assertThat(rendered).isNotNull();
        assertThat(rendered).contains("warmth radiates");
        assertThat(rendered).doesNotContain("relational");
    }

    @Test
    void returnsNullWhenNodeBelongsToDifferentAgent() {
        var sgId = store.createSubgraph(
                new SubgraphInput("Cognitive", SubgraphTypes.COGNITIVE, null), "t1");
        store.addNode(NodeInput.of("other-model-agent2-target1", sgId)
                               .withTraits(Set.of("OtherModelDerived"))
                               .withProperties(Map.of(
                                       "agent-id", "agent2",
                                       "target-id", "target1",
                                       "empathic-sensing", "Invisible to agent1.")),
                      "t1");

        var context = new CognitionRenderContext("agent1", "t1", "target1");
        assertThat(section.render(context)).isNull();
    }

    @Test
    void returnsNullWhenBothPropertiesEmpty() {
        var sgId = store.createSubgraph(
                new SubgraphInput("Cognitive", SubgraphTypes.COGNITIVE, null), "t1");
        store.addNode(NodeInput.of("other-model-agent1-target1", sgId)
                               .withTraits(Set.of("OtherModelDerived"))
                               .withProperties(Map.of(
                                       "agent-id", "agent1",
                                       "target-id", "target1",
                                       "inferred-values", "They value order.")),
                      "t1");

        var context = new CognitionRenderContext("agent1", "t1", "target1");
        assertThat(section.render(context)).isNull();
    }
@Test
void blockTagIsOtherModel() {
    assertThat(section.blockTag()).isEqualTo(BlockTag.OTHER_MODEL);
}



}
