package io.quarkmind.inference.bayes.llm;

import io.casehub.platform.agent.AgentEvent;
import io.casehub.platform.agent.AgentSession;
import io.casehub.platform.agent.SessionRegistry;
import io.quarkmind.inference.bayes.CptSpec;
import io.quarkmind.inference.bayes.NetworkSpec;
import io.quarkmind.inference.bayes.SpecStatus;
import io.quarkmind.inference.bayes.VariableSpec;
import io.smallrye.mutiny.Multi;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class NetworkElicitorTest {

    @Test
    void proposeWellFormedJson() {
        String json = """
            {
              "variables": [
                {"name": "Rain", "outcomes": ["yes", "no"], "description": "Will it rain"}
              ],
              "edges": [],
              "cpts": {
                "Rain": {"type": "direct", "values": [[0.3, 0.7]]}
              }
            }""";
        var elicitor = new NetworkElicitor(stubRegistry(json), ElicitorConfig.defaults());
        var result = elicitor.propose("weather prediction");
        assertThat(result.fullyParsed()).isTrue();
        assertThat(result.warnings()).isEmpty();
        assertThat(result.spec().variables()).hasSize(1);
        assertThat(result.spec().variables().get(0).name()).isEqualTo("Rain");
        assertThat(result.spec().variables().get(0).cptSpec())
            .isInstanceOf(CptSpec.DirectCpt.class);
    }

    @Test
    void proposeMalformedJsonReturnsEmptySpec() {
        var elicitor = new NetworkElicitor(stubRegistry("not json at all"), ElicitorConfig.defaults());
        var result = elicitor.propose("anything");
        assertThat(result.fullyParsed()).isFalse();
        assertThat(result.warnings()).isNotEmpty();
        assertThat(result.spec().variables()).isEmpty();
    }

    @Test
    void proposeNoisyOrParsing() {
        String json = """
            {
              "variables": [
                {"name": "A", "outcomes": ["y", "n"], "description": ""},
                {"name": "B", "outcomes": ["y", "n"], "description": ""},
                {"name": "C", "outcomes": ["y", "n"], "description": ""}
              ],
              "edges": [
                {"from": "A", "to": "C", "rationale": ""},
                {"from": "B", "to": "C", "rationale": ""}
              ],
              "cpts": {
                "A": {"type": "direct", "values": [[0.5, 0.5]]},
                "B": {"type": "direct", "values": [[0.4, 0.6]]},
                "C": {"type": "noisy_or",
                      "parentInfluences": [
                        {"parentName": "A", "strength": 0.7},
                        {"parentName": "B", "strength": 0.3}
                      ],
                      "leak": 0.05}
              }
            }""";
        var elicitor = new NetworkElicitor(stubRegistry(json), ElicitorConfig.defaults());
        var result = elicitor.propose("test domain");
        assertThat(result.fullyParsed()).isTrue();
        var cVar = result.spec().variables().stream()
            .filter(v -> v.name().equals("C")).findFirst().orElseThrow();
        assertThat(cVar.cptSpec()).isInstanceOf(CptSpec.NoisyOr.class);
        var noisy = (CptSpec.NoisyOr) cVar.cptSpec();
        assertThat(noisy.parentInfluences()).hasSize(2);
    }

    @Test
    void proposePreservesDomainDescription() {
        String json = """
            {"variables": [], "edges": [], "cpts": {}}""";
        var elicitor = new NetworkElicitor(stubRegistry(json), ElicitorConfig.defaults());
        var result = elicitor.propose("my domain");
        assertThat(result.spec().domainDescription()).isEqualTo("my domain");
    }

    @Test
    void enrichFiltersDuplicateVariables() {
        String json = """
            {
              "suggestions": [
                {"type": "variable", "name": "A", "outcomes": ["y", "n"],
                 "description": "", "justification": "already exists"},
                {"type": "variable", "name": "New", "outcomes": ["y", "n"],
                 "description": "", "justification": "missing factor"}
              ]
            }""";
        var a = new VariableSpec("A", List.of("y", "n"), "", SpecStatus.PROPOSED, null);
        var spec = new NetworkSpec("test", List.of(a), List.of(), "desc");

        var elicitor = new NetworkElicitor(stubRegistry(json), ElicitorConfig.defaults());
        var suggestions = elicitor.enrich(spec);
        assertThat(suggestions).hasSize(1);
        assertThat(((EnrichmentSuggestion.SuggestedVariable) suggestions.get(0))
            .variable().name()).isEqualTo("New");
    }

    private static SessionRegistry stubRegistry(String response) {
        return new SessionRegistry() {
            @Override
            public AgentSession openSession(String name, io.casehub.platform.agent.AgentSessionInit init) {
                return new StubSession(response);
            }
            @Override
            public AgentSession openSession(String name, io.casehub.platform.agent.AgentSessionInit init, java.time.Duration timeout) {
                return new StubSession(response);
            }
            @Override
            public void releaseSession(String conversationId) {}
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
        public io.smallrye.mutiny.Uni<Void> interrupt() { return io.smallrye.mutiny.Uni.createFrom().voidItem(); }
        @Override
        public void close(java.time.Duration timeout) {}
    }
}
