package io.casehub.neocortex.caps.llm;

import io.casehub.neocortex.caps.CapsNode;
import io.casehub.neocortex.caps.CapsTopology;
import io.casehub.neocortex.caps.NodeRange;
import io.casehub.neocortex.caps.NodeType;
import io.casehub.neocortex.caps.SituationActivation;
import io.casehub.neocortex.caps.WeightUpdateParameters;
import io.casehub.platform.agent.AgentBackend;
import io.casehub.platform.agent.AgentEvent;
import io.casehub.platform.agent.AgentSession;
import io.casehub.platform.agent.AgentSessionConfig;
import io.casehub.platform.agent.AgentSessionInit;
import io.casehub.platform.agent.session.SessionPool;
import io.casehub.platform.agent.session.SessionPoolConfig;
import io.smallrye.mutiny.Multi;
import io.smallrye.mutiny.Uni;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class LlmSituationClassifierTest {

    private static final CapsTopology TOPOLOGY = new CapsTopology(1,
        Map.of(
            "reward", new CapsNode("reward", NodeType.INPUT, NodeRange.UNIPOLAR,
                "consequence", List.of("operant"), Map.of("rewarded", 0.8, "praised", 0.5)),
            "punishment", new CapsNode("punishment", NodeType.INPUT, NodeRange.UNIPOLAR,
                "consequence", List.of("operant"), Map.of("punished", 0.9)),
            "rejection", new CapsNode("rejection", NodeType.INPUT, NodeRange.UNIPOLAR,
                "relationship", List.of("attachment"), Map.of("rejected", 0.9)),
            "approach", new CapsNode("approach", NodeType.OUTPUT, NodeRange.UNIPOLAR,
                "approach_avoidance", List.of(), Map.of())
        ),
        List.of(), Map.of(), List.of(),
        new WeightUpdateParameters(0.01, 0.25, 0.1, 0.5, 0.95,
            Map.of(), 0.2, 100, 0.001,
            new double[]{-2.0, 2.0}, new double[]{0.1, 3.0}, 0.6));

    @Test
    void classify_usesSessionPoolCheckoutAndRelease() {
        var pool = stubPool("""
                            {"activations": [{"node": "reward", "confidence": 0.8}]}
                            """);
        var classifier = new LlmSituationClassifier(pool, TOPOLOGY);

        var result = classifier.classify("Got rewarded", Map.of());

        assertThat(result).hasSize(1);
        assertThat(result.getFirst().nodeId()).isEqualTo("reward");

        // session returned to pool — second call succeeds without exhaustion
        var result2 = classifier.classify("Got rewarded again", Map.of());
        assertThat(result2).hasSize(1);
    }

    @Test
    void classify_releasesSessionOnParseFailure() {
        var pool       = stubPool("garbled response no json");
        var classifier = new LlmSituationClassifier(pool, TOPOLOGY);

        // garbled response → empty list (no valid JSON activations)
        var result = classifier.classify("test", Map.of());
        assertThat(result).isEmpty();

        // session returned to pool — second call succeeds
        var result2 = classifier.classify("test again", Map.of());
        assertThat(result2).isEmpty();
    }

    @Test
    void parsesValidResponse() {
        var pool = stubPool("""
            {"activations": [{"node": "reward", "confidence": 0.8}, {"node": "rejection", "confidence": 0.5}]}
            """);
        var classifier = new LlmSituationClassifier(pool, TOPOLOGY);

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
        var pool = stubPool("""
            {"activations": [{"node": "reward", "confidence": 0.8}, {"node": "nonexistent", "confidence": 0.9}]}
            """);
        var classifier = new LlmSituationClassifier(pool, TOPOLOGY);

        List<SituationActivation> result = classifier.classify("test", Map.of());

        assertThat(result).hasSize(1);
        assertThat(result.getFirst().nodeId()).isEqualTo("reward");
    }

    @Test
    void filtersOutputNodes() {
        var pool = stubPool("""
            {"activations": [{"node": "approach", "confidence": 0.9}]}
            """);
        var classifier = new LlmSituationClassifier(pool, TOPOLOGY);

        List<SituationActivation> result = classifier.classify("test", Map.of());

        assertThat(result).isEmpty();
    }

    @Test
    void filtersBelowThreshold() {
        var pool = stubPool("""
            {"activations": [{"node": "reward", "confidence": 0.1}]}
            """);
        var classifier = new LlmSituationClassifier(pool, TOPOLOGY);

        List<SituationActivation> result = classifier.classify("test", Map.of());

        assertThat(result).isEmpty();
    }

    @Test
    void handlesMarkdownWrappedJson() {
        var pool = stubPool("""
            ```json
            {"activations": [{"node": "reward", "confidence": 0.7}]}
            ```
            """);
        var classifier = new LlmSituationClassifier(pool, TOPOLOGY);

        List<SituationActivation> result = classifier.classify("test", Map.of());

        assertThat(result).hasSize(1);
    }

    @Test
    void handlesGarbledResponse() {
        var pool = stubPool("I think the situation involves reward");
        var classifier = new LlmSituationClassifier(pool, TOPOLOGY);

        List<SituationActivation> result = classifier.classify("test", Map.of());

        assertThat(result).isEmpty();
    }

    @Test
    void returnsEmptyForNullDescription() {
        var pool = stubPool("{}");
        var classifier = new LlmSituationClassifier(pool, TOPOLOGY);

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

    private static SessionPool stubPool(String response) {
        AgentBackend backend = new AgentBackend() {
            @Override public String key() { return "stub"; }
            @Override public AgentSession openSession(AgentSessionInit init) {
                return new AgentSession() {
                    @Override public Multi<AgentEvent> query(String prompt) {
                        return Multi.createFrom().items(
                            (AgentEvent) new AgentEvent.TextDelta(response),
                            new AgentEvent.InvocationComplete(0, 0, 0, 0, 0, null, 0, 0, null, 1, false, null, null));
                    }
                    @Override public Uni<Void> interrupt() { return Uni.createFrom().voidItem(); }
                    @Override public void close(Duration timeout) {}
                    @Override public void clear() {}
                };
            }
            @Override public Multi<AgentEvent> invoke(AgentSessionConfig config) {
                throw new UnsupportedOperationException();
            }
        };
        return new SessionPool(backend,
            new SessionPoolConfig("test-classifier", "stub", 0, 6,
                Duration.ofSeconds(60), Duration.ofSeconds(5)));
    }
}
