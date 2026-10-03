package io.casehub.neocortex.caps.llm;

import io.casehub.neocortex.caps.*;
import io.casehub.platform.agent.AgentEvent;
import io.casehub.platform.agent.AgentProvider;
import io.casehub.platform.agent.AgentSessionConfig;
import io.smallrye.mutiny.Multi;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class LlmSituationClassifierTest {

    private static final CapsTopology TOPOLOGY = new CapsTopology(1,
        Map.of(
            "reward", new CapsNode("reward", NodeType.INPUT, NodeRange.UNIPOLAR,
                "consequence", List.of("operant"), List.of("rewarded", "praised")),
            "punishment", new CapsNode("punishment", NodeType.INPUT, NodeRange.UNIPOLAR,
                "consequence", List.of("operant"), List.of("punished")),
            "rejection", new CapsNode("rejection", NodeType.INPUT, NodeRange.UNIPOLAR,
                "relationship", List.of("attachment"), List.of("rejected")),
            "approach", new CapsNode("approach", NodeType.OUTPUT, NodeRange.UNIPOLAR,
                "approach_avoidance", List.of(), List.of())
        ),
        List.of(), Map.of(), List.of(),
        new WeightUpdateParameters(0.01, 0.25, 0.1, 0.5, 0.95,
            Map.of(), 0.2, 100, 0.001,
            new double[]{-2.0, 2.0}, new double[]{0.1, 3.0}, 0.6));

    @Test
    void parsesValidResponse() {
        var classifier = new LlmSituationClassifier(stubProvider(
            """
            {"activations": [{"node": "reward", "confidence": 0.8}, {"node": "rejection", "confidence": 0.5}]}
            """), TOPOLOGY);

        List<SituationActivation> result = classifier.classify(
            "Was rewarded and then rejected", Map.of());

        assertThat(result).hasSize(2);
        assertThat(result).anySatisfy(a -> {
            assertThat(a.nodeId()).isEqualTo("reward");
            assertThat(a.confidence()).isEqualTo(0.8);
        });
        assertThat(result).anySatisfy(a -> {
            assertThat(a.nodeId()).isEqualTo("rejection");
            assertThat(a.confidence()).isEqualTo(0.5);
        });
    }

    @Test
    void filtersInvalidNodeIds() {
        var classifier = new LlmSituationClassifier(stubProvider(
            """
            {"activations": [{"node": "reward", "confidence": 0.8}, {"node": "nonexistent", "confidence": 0.9}]}
            """), TOPOLOGY);

        List<SituationActivation> result = classifier.classify("test", Map.of());

        assertThat(result).hasSize(1);
        assertThat(result.getFirst().nodeId()).isEqualTo("reward");
    }

    @Test
    void filtersOutputNodes() {
        var classifier = new LlmSituationClassifier(stubProvider(
            """
            {"activations": [{"node": "approach", "confidence": 0.9}]}
            """), TOPOLOGY);

        List<SituationActivation> result = classifier.classify("test", Map.of());

        assertThat(result).isEmpty();
    }

    @Test
    void filtersBelowThreshold() {
        var classifier = new LlmSituationClassifier(stubProvider(
            """
            {"activations": [{"node": "reward", "confidence": 0.1}]}
            """), TOPOLOGY);

        List<SituationActivation> result = classifier.classify("test", Map.of());

        assertThat(result).isEmpty();
    }

    @Test
    void handlesMarkdownWrappedJson() {
        var classifier = new LlmSituationClassifier(stubProvider(
            """
            ```json
            {"activations": [{"node": "reward", "confidence": 0.7}]}
            ```
            """), TOPOLOGY);

        List<SituationActivation> result = classifier.classify("test", Map.of());

        assertThat(result).hasSize(1);
    }

    @Test
    void handlesGarbledResponse() {
        var classifier = new LlmSituationClassifier(stubProvider(
            "I think the situation involves reward"), TOPOLOGY);

        List<SituationActivation> result = classifier.classify("test", Map.of());

        assertThat(result).isEmpty();
    }

    @Test
    void returnsEmptyForNullDescription() {
        var classifier = new LlmSituationClassifier(stubProvider("{}"), TOPOLOGY);

        assertThat(classifier.classify(null, Map.of())).isEmpty();
        assertThat(classifier.classify("", Map.of())).isEmpty();
    }

    @Test
    void extractJsonHandlesEdgeCases() {
        assertThat(LlmSituationClassifier.extractJson(null)).isNull();
        assertThat(LlmSituationClassifier.extractJson("")).isNull();
        assertThat(LlmSituationClassifier.extractJson("no json here")).isNull();
        assertThat(LlmSituationClassifier.extractJson("prefix {\"a\": 1} suffix"))
            .isEqualTo("{\"a\": 1}");
    }

    private static AgentProvider stubProvider(String response) {
        return new AgentProvider() {
            @Override
            public Multi<AgentEvent> invoke(AgentSessionConfig config) {
                return Multi.createFrom().items(
                    (AgentEvent) new AgentEvent.TextDelta(response),
                    new AgentEvent.InvocationComplete(0, 0, 0, 0, 0, null, 0, 0, null, 1, false));
            }
            @Override
            public io.casehub.platform.agent.AgentSession openSession(
                    io.casehub.platform.agent.AgentSessionInit init) {
                throw new UnsupportedOperationException();
            }
        };
    }
}
