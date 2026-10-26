package io.quarkmind.inference.bayes.llm;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.quarkmind.inference.bayes.CptSpec;
import io.quarkmind.inference.bayes.EdgeSpec;
import io.quarkmind.inference.bayes.NetworkSpec;
import io.quarkmind.inference.bayes.NoisyMaxParent;
import io.quarkmind.inference.bayes.ParentInfluence;
import io.quarkmind.inference.bayes.SpecStatus;
import io.quarkmind.inference.bayes.VariableSpec;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

final class ResponseParser {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private ResponseParser() {}

    static ElicitationResult parseProposalResponse(String json, String domainDescription) {
        List<String> warnings = new ArrayList<>();
        try {
            var root = MAPPER.readTree(json);
            var variables = parseVariables(root, warnings);
            var edges = parseEdges(root);
            var cpts = parseCpts(root, variables, warnings);
            var spec = new NetworkSpec(null, cpts, edges, domainDescription);
            return new ElicitationResult(spec, warnings, warnings.isEmpty());
        } catch (Exception e) {
            return new ElicitationResult(
                new NetworkSpec(null, List.of(), List.of(), domainDescription),
                List.of("Failed to parse LLM response: " + e.getMessage()),
                false);
        }
    }

    static List<EnrichmentSuggestion> parseEnrichResponse(String json, NetworkSpec existing) {
        Set<String> existingNames = existing.variables().stream()
            .map(VariableSpec::name)
            .collect(Collectors.toSet());
        List<EnrichmentSuggestion> suggestions = new ArrayList<>();
        try {
            var root = MAPPER.readTree(json);
            var suggestionsNode = root.path("suggestions");
            if (!suggestionsNode.isArray()) return List.of();

            for (var node : suggestionsNode) {
                String type = node.path("type").asText("");
                String justification = node.path("justification").asText("");
                if ("variable".equals(type)) {
                    String name = node.path("name").asText();
                    if (existingNames.contains(name)) continue;
                    var outcomes = parseStringArray(node.path("outcomes"));
                    String desc = node.path("description").asText("");
                    var varSpec = new VariableSpec(name, outcomes, desc, SpecStatus.PROPOSED, null);
                    suggestions.add(new EnrichmentSuggestion.SuggestedVariable(varSpec, justification));
                } else if ("edge".equals(type)) {
                    String from = node.path("from").asText();
                    String to = node.path("to").asText();
                    String rationale = node.path("rationale").asText("");
                    var edgeSpec = new EdgeSpec(from, to, rationale, SpecStatus.PROPOSED);
                    suggestions.add(new EnrichmentSuggestion.SuggestedEdge(edgeSpec, justification));
                }
            }
        } catch (Exception e) {
            // Graceful degradation — return whatever we parsed
        }
        return suggestions;
    }

    private static List<VariableSpec> parseVariables(JsonNode root, List<String> warnings) {
        List<VariableSpec> variables = new ArrayList<>();
        var varsNode = root.path("variables");
        if (!varsNode.isArray()) return variables;

        for (var node : varsNode) {
            String name = node.path("name").asText();
            var outcomes = parseStringArray(node.path("outcomes"));
            String description = node.path("description").asText("");
            if (name.isEmpty() || outcomes.size() < 2) {
                warnings.add("Skipped variable with invalid name or < 2 outcomes: " + name);
                continue;
            }
            variables.add(new VariableSpec(name, outcomes, description, SpecStatus.PROPOSED, null));
        }
        return variables;
    }

    private static List<EdgeSpec> parseEdges(JsonNode root) {
        List<EdgeSpec> edges = new ArrayList<>();
        var edgesNode = root.path("edges");
        if (!edgesNode.isArray()) return edges;

        for (var node : edgesNode) {
            String from = node.path("from").asText();
            String to = node.path("to").asText();
            String rationale = node.path("rationale").asText("");
            if (!from.isEmpty() && !to.isEmpty()) {
                edges.add(new EdgeSpec(from, to, rationale, SpecStatus.PROPOSED));
            }
        }
        return edges;
    }

    private static List<VariableSpec> parseCpts(JsonNode root, List<VariableSpec> variables,
                                                 List<String> warnings) {
        var cptsNode = root.path("cpts");
        if (!cptsNode.isObject()) return variables;

        List<VariableSpec> result = new ArrayList<>();
        for (var v : variables) {
            var cptNode = cptsNode.path(v.name());
            if (cptNode.isMissingNode()) {
                result.add(v);
                continue;
            }
            try {
                CptSpec cptSpec = parseSingleCpt(cptNode);
                result.add(new VariableSpec(v.name(), v.outcomes(), v.description(),
                    v.status(), cptSpec));
            } catch (Exception e) {
                warnings.add("Failed to parse CPT for '" + v.name() + "': " + e.getMessage());
                result.add(v);
            }
        }
        return result;
    }

    private static CptSpec parseSingleCpt(JsonNode cptNode) {
        String type = cptNode.path("type").asText("");
        return switch (type) {
            case "direct" -> parseDirectCpt(cptNode);
            case "noisy_or" -> parseNoisyOr(cptNode);
            case "uniform" -> new CptSpec.Uniform();
            case "noisy_max" -> parseNoisyMax(cptNode);
            default -> throw new IllegalArgumentException("Unknown CPT type: " + type);
        };
    }

    private static CptSpec.DirectCpt parseDirectCpt(JsonNode node) {
        var valuesNode = node.path("values");
        int rows = valuesNode.size();
        double[][] values = new double[rows][];
        for (int r = 0; r < rows; r++) {
            var rowNode = valuesNode.get(r);
            double[] row = new double[rowNode.size()];
            for (int c = 0; c < rowNode.size(); c++) {
                row[c] = rowNode.get(c).asDouble();
            }
            values[r] = row;
        }
        return new CptSpec.DirectCpt(values);
    }

    private static CptSpec.NoisyOr parseNoisyOr(JsonNode node) {
        double leak = node.path("leak").asDouble(0.01);
        var influencesNode = node.path("parentInfluences");
        List<ParentInfluence> influences = new ArrayList<>();
        if (influencesNode.isArray()) {
            for (var inf : influencesNode) {
                String parentName = inf.path("parentName").asText();
                double strength = inf.path("strength").asDouble();
                influences.add(new ParentInfluence(parentName, strength));
            }
        }
        return new CptSpec.NoisyOr(influences, leak);
    }

    private static CptSpec.NoisyMax parseNoisyMax(JsonNode node) {
        var leakNode = node.path("leak");
        if (!leakNode.isArray()) {
            throw new IllegalArgumentException("NoisyMax requires 'leak' array");
        }
        double[] leak = new double[leakNode.size()];
        for (int i = 0; i < leakNode.size(); i++) {
            leak[i] = leakNode.get(i).asDouble();
        }

        var                  parentsNode = node.path("parents");
        List<NoisyMaxParent> parents     = new ArrayList<>();
        if (parentsNode.isArray()) {
            for (var pNode : parentsNode) {
                String   parentName = pNode.path("parentName").asText();
                var      distNode   = pNode.path("distribution");
                double[] dist       = new double[distNode.size()];
                for (int i = 0; i < distNode.size(); i++) {
                    dist[i] = distNode.get(i).asDouble();
                }
                parents.add(new NoisyMaxParent(parentName, dist));
            }
        }
        return new CptSpec.NoisyMax(parents, leak);
    }


    private static List<String> parseStringArray(JsonNode node) {
        List<String> result = new ArrayList<>();
        if (node.isArray()) {
            for (var item : node) {
                result.add(item.asText());
            }
        }
        return result;
    }
}
