package io.quarkmind.inference.bayes.llm;

import io.casehub.platform.agent.AgentEvent;
import io.casehub.platform.agent.AgentSession;
import io.casehub.platform.agent.AgentSessionInit;
import io.casehub.platform.agent.SessionRegistry;
import io.quarkmind.inference.bayes.CptSpec;
import io.quarkmind.inference.bayes.ParentInfluence;
import io.quarkmind.inference.bayes.SpecStatus;
import io.quarkmind.inference.bayes.VariableSpec;
import io.smallrye.mutiny.Multi;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class NetworkElicitorEndToEndTest {

    @Test
    void fullPipeline_proposeAcceptBuildInfer() {
        String proposalJson = """
            {
              "variables": [
                {"name": "Smoking", "outcomes": ["yes", "no"], "description": "smoking habit"},
                {"name": "Genetics", "outcomes": ["high-risk", "low-risk"], "description": "genetic predisposition"},
                {"name": "Cancer", "outcomes": ["present", "absent"], "description": "cancer diagnosis"}
              ],
              "edges": [
                {"from": "Smoking", "to": "Cancer", "rationale": "risk factor"},
                {"from": "Genetics", "to": "Cancer", "rationale": "hereditary risk"}
              ],
              "cpts": {
                "Smoking": {"type": "direct", "values": [[0.3, 0.7]]},
                "Genetics": {"type": "direct", "values": [[0.2, 0.8]]},
                "Cancer": {"type": "noisy_or",
                           "parentInfluences": [
                             {"parentName": "Smoking", "strength": 0.7},
                             {"parentName": "Genetics", "strength": 0.4}
                           ], "leak": 0.01}
              }
            }""";

        var elicitor = new NetworkElicitor(stubRegistry(proposalJson), ElicitorConfig.defaults());
        var result = elicitor.propose("Medical diagnosis model");
        assertThat(result.fullyParsed()).isTrue();
        assertThat(result.spec().variables()).hasSize(3);
        assertThat(result.spec().edges()).hasSize(2);

        var spec = result.spec()
            .withEdgeAccepted("Smoking", "Cancer")
            .withEdgeAccepted("Genetics", "Cancer")
            .withVariableAccepted("Smoking");

        var instance = spec.toBuilder().buildInstance();
        assertThat(instance).isNotNull();

        instance.setLikelyhood("Smoking", new double[]{1.0, 0.0});
        instance.globalUpdate();
        var cancerState = instance.marginalize("Cancer");
        double[] dist = cancerState.getDistribution();
        // P(present | Smoking=yes) should be high — Noisy-OR with strength 0.7
        assertThat(dist[0]).isGreaterThan(0.5);
        assertThat(dist[0]).isLessThan(0.95);
    }

    @Test
    void fullPipeline_rejectEdgeExcludesFromNetwork() {
        String proposalJson = """
            {
              "variables": [
                {"name": "A", "outcomes": ["y", "n"], "description": ""},
                {"name": "B", "outcomes": ["y", "n"], "description": ""},
                {"name": "C", "outcomes": ["y", "n"], "description": ""}
              ],
              "edges": [
                {"from": "A", "to": "C", "rationale": ""},
                {"from": "B", "to": "C", "rationale": "spurious"}
              ],
              "cpts": {
                "A": {"type": "direct", "values": [[0.5, 0.5]]},
                "B": {"type": "direct", "values": [[0.5, 0.5]]},
                "C": {"type": "noisy_or",
                      "parentInfluences": [
                        {"parentName": "A", "strength": 0.6},
                        {"parentName": "B", "strength": 0.3}
                      ], "leak": 0.05}
              }
            }""";

        var elicitor = new NetworkElicitor(stubRegistry(proposalJson));
        var result = elicitor.propose("test");
        var spec = result.spec().withEdgeRejected("B", "C");

        var network = spec.toBayesNetwork();
        for (var node : network) {
            var var = node.getContent();
            if (var.getName().equals("C")) {
                assertThat(var.getGiven()).containsExactly("A");
            }
        }
    }

    @Test
    void fullPipeline_uniformFallbackWhenNoCpt() {
        String proposalJson = """
            {
              "variables": [
                {"name": "X", "outcomes": ["a", "b", "c"], "description": ""}
              ],
              "edges": [],
              "cpts": {}
            }""";

        var elicitor = new NetworkElicitor(stubRegistry(proposalJson));
        var result = elicitor.propose("test");
        var network = result.spec().toBayesNetwork();
        for (var node : network) {
            double[][] table = node.getContent().getProbabilityTable();
            assertThat(table[0][0]).isEqualTo(table[0][1]);
            assertThat(table[0][1]).isEqualTo(table[0][2]);
        }
    }

    private static SessionRegistry stubRegistry(String response) {
        return new SessionRegistry() {
            @Override
            public AgentSession openSession(String name, AgentSessionInit init) {
                return new StubSession(response);
            }
            @Override
            public AgentSession openSession(String name, AgentSessionInit init, Duration timeout) {
                return new StubSession(response);
            }
            @Override
            public void releaseSession(String id) {}
            @Override
            public int sessionCount() { return 0; }
        };
    }

    private static class StubSession implements AgentSession {
        private final String response;
        StubSession(String response) { this.response = response; }
        @Override
        public Multi<AgentEvent> query(String message) {
            return Multi.createFrom().item(new AgentEvent.TextDelta(response));
        }
        @Override
        public io.smallrye.mutiny.Uni<Void> interrupt() {
            return io.smallrye.mutiny.Uni.createFrom().voidItem();
        }
        @Override
        public void close(Duration timeout) {}
    }
}
