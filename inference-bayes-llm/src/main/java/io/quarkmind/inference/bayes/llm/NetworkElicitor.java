package io.quarkmind.inference.bayes.llm;

import io.casehub.platform.agent.AgentEvent;
import io.casehub.platform.agent.AgentSessionInit;
import io.casehub.platform.agent.SessionRegistry;
import io.quarkmind.inference.bayes.NetworkSpec;
import io.quarkmind.inference.bayes.SpecStatus;

import java.util.List;

public class NetworkElicitor {

    private static final String PROPOSE_SYSTEM_PROMPT = """
        You are a Bayesian network structure elicitation agent. Given a domain description, \
        propose a directed acyclic graph (DAG) of causal relationships as a Bayesian network.

        For each variable, determine:
        - A short name (no spaces, PascalCase)
        - Possible outcomes (at least 2)
        - A brief description

        For each causal edge, determine:
        - Source variable (cause)
        - Target variable (effect)
        - Brief rationale

        For conditional probability tables (CPTs):
        - Root nodes (no parents): estimate direct prior probabilities
        - Nodes with 1 parent: estimate direct conditional probabilities
        - Nodes with 2+ parents and exactly 2 outcomes: use noisy_or parameterization \
          (per-parent influence strength + leak probability)
        - Nodes with 2+ parents and 3+ outcomes: estimate direct conditional probabilities

        Respond with JSON only:
        {
          "variables": [{"name": "...", "outcomes": ["...", "..."], "description": "..."}],
          "edges": [{"from": "...", "to": "...", "rationale": "..."}],
          "cpts": {
            "VarName": {"type": "direct", "values": [[p1, p2, ...], ...]},
            "VarName": {"type": "noisy_or", "parentInfluences": [{"parentName": "...", "strength": 0.0-1.0}], "leak": 0.0-1.0}
          }
        }""";

    private static final String ENRICH_SYSTEM_PROMPT = """
        You are a Bayesian network analysis agent. Given an existing network structure, \
        suggest additional variables or edges that may be missing.

        Consider: confounding factors, mediating variables, indirect effects, \
        common causes, and selection bias.

        Respond with JSON only:
        {
          "suggestions": [
            {"type": "variable", "name": "...", "outcomes": ["...", "..."], \
        "description": "...", "justification": "..."},
            {"type": "edge", "from": "...", "to": "...", "rationale": "...", \
        "justification": "..."}
          ]
        }""";

    private final SessionRegistry registry;
    private final ElicitorConfig config;

    public NetworkElicitor(SessionRegistry registry, ElicitorConfig config) {
        this.registry = registry;
        this.config = config;
    }

    public NetworkElicitor(SessionRegistry registry) {
        this(registry, ElicitorConfig.defaults());
    }

    public ElicitationResult propose(String domainDescription) {
        String response = invokeLlm(PROPOSE_SYSTEM_PROMPT, "Domain: " + domainDescription);
        if (response == null) {
            return new ElicitationResult(
                new NetworkSpec(null, List.of(), List.of(), domainDescription),
                List.of("LLM returned no response"), false);
        }
        return ResponseParser.parseProposalResponse(response, domainDescription);
    }

    public ElicitationResult refine(NetworkSpec spec) {
        String constraintContext = buildConstraintContext(spec);
        String response = invokeLlm(PROPOSE_SYSTEM_PROMPT,
            constraintContext + "\nDomain: " + spec.domainDescription());
        if (response == null) {
            return new ElicitationResult(spec,
                List.of("LLM returned no response during refinement"), false);
        }
        return ResponseParser.parseProposalResponse(response, spec.domainDescription());
    }

    public List<EnrichmentSuggestion> enrich(NetworkSpec spec) {
        String response = invokeLlm(ENRICH_SYSTEM_PROMPT,
            "Current network:\n" + buildNetworkSummary(spec));
        if (response == null) return List.of();
        return ResponseParser.parseEnrichResponse(response, spec);
    }

    private String invokeLlm(String systemPrompt, String userMessage) {
        try (var session = registry.openSession("network-elicitor",
                                                AgentSessionInit.of(systemPrompt))) {
            var responseText = new StringBuilder();
            session.query(userMessage).subscribe().asStream()
                   .filter(e -> e instanceof AgentEvent.TextDelta)
                   .map(e -> ((AgentEvent.TextDelta) e).text())
                   .forEach(responseText::append);
            return responseText.isEmpty() ? null : responseText.toString();
        }
    }

    private static String buildConstraintContext(NetworkSpec spec) {
        var sb = new StringBuilder("Constraints from prior review:\n");
        for (var v : spec.variables()) {
            if (v.status() == SpecStatus.LOCKED) {
                sb.append("- LOCKED variable: ").append(v.name())
                    .append(" (must keep unchanged)\n");
            } else if (v.status() == SpecStatus.ACCEPTED) {
                sb.append("- ACCEPTED variable: ").append(v.name())
                    .append(" (preserve unless structurally incompatible)\n");
            }
        }
        for (var e : spec.edges()) {
            if (e.status() == SpecStatus.LOCKED) {
                sb.append("- LOCKED edge: ").append(e.from()).append(" -> ")
                    .append(e.to()).append(" (must keep)\n");
            } else if (e.status() == SpecStatus.REJECTED) {
                sb.append("- REJECTED edge: ").append(e.from()).append(" -> ")
                    .append(e.to()).append(" (do not re-propose)\n");
            } else if (e.status() == SpecStatus.ACCEPTED) {
                sb.append("- ACCEPTED edge: ").append(e.from()).append(" -> ")
                    .append(e.to()).append(" (preserve)\n");
            }
        }
        return sb.toString();
    }

    private static String buildNetworkSummary(NetworkSpec spec) {
        var sb = new StringBuilder();
        sb.append("Variables:\n");
        for (var v : spec.variables()) {
            sb.append("- ").append(v.name()).append(": ").append(v.outcomes()).append("\n");
        }
        sb.append("Edges:\n");
        for (var e : spec.edges()) {
            if (e.status() != SpecStatus.REJECTED) {
                sb.append("- ").append(e.from()).append(" -> ").append(e.to()).append("\n");
            }
        }
        return sb.toString();
    }
}
