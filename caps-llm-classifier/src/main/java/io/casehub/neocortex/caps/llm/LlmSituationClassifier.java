package io.casehub.neocortex.caps.llm;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.casehub.neocortex.caps.CapsNode;
import io.casehub.neocortex.caps.CapsTopology;
import io.casehub.neocortex.caps.NodeType;
import io.casehub.neocortex.caps.SituationActivation;
import io.casehub.neocortex.caps.SituationClassifier;
import io.casehub.platform.agent.AgentEvent;
import io.casehub.platform.agent.AgentProvider;
import io.casehub.platform.agent.AgentSessionConfig;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;

@ApplicationScoped
public class LlmSituationClassifier implements SituationClassifier {

    private static final Logger LOG = Logger.getLogger(LlmSituationClassifier.class.getName());
    private static final double CONFIDENCE_THRESHOLD = 0.3;
    private static final Duration TIMEOUT = Duration.ofSeconds(30);

    private final AgentProvider agentProvider;
    private final ObjectMapper mapper = new ObjectMapper();
    private final String systemPrompt;
    private final Set<String> validNodeIds;

    @Inject
    public LlmSituationClassifier(Instance<AgentProvider> agentProviderInstance,
                                   Instance<CapsTopology> topologyInstance) {
        this.agentProvider = agentProviderInstance.isResolvable()
            ? agentProviderInstance.get() : null;

        CapsTopology topology = topologyInstance.isResolvable()
            ? topologyInstance.get() : null;

        this.validNodeIds = topology != null
            ? topology.nodes().entrySet().stream()
                .filter(e -> e.getValue().type() == NodeType.INPUT)
                .map(Map.Entry::getKey)
                .collect(Collectors.toSet())
            : Set.of();

        this.systemPrompt = buildSystemPrompt(topology);
    }

    LlmSituationClassifier(AgentProvider agentProvider, CapsTopology topology) {
        this.agentProvider = agentProvider;
        this.validNodeIds = topology.nodes().entrySet().stream()
            .filter(e -> e.getValue().type() == NodeType.INPUT)
            .map(Map.Entry::getKey)
            .collect(Collectors.toSet());
        this.systemPrompt = buildSystemPrompt(topology);
    }

    @Override
    public List<SituationActivation> classify(String description,
                                               Map<String, String> metadata) {
        if (agentProvider == null || description == null || description.isBlank()) {
            return List.of();
        }

        String userPrompt = buildUserPrompt(description, metadata);

        try {
            var events = agentProvider.invoke(
                    AgentSessionConfig.of(systemPrompt, userPrompt, TIMEOUT))
                .collect().asList()
                .await().atMost(TIMEOUT.plusSeconds(5));

            String text = events.stream()
                .filter(AgentEvent.TextDelta.class::isInstance)
                .map(AgentEvent.TextDelta.class::cast)
                .map(AgentEvent.TextDelta::text)
                .collect(Collectors.joining());

            return parseResponse(text);
        } catch (Exception e) {
            LOG.log(Level.WARNING, "LLM situation classification failed", e);
            return List.of();
        }
    }

    List<SituationActivation> parseResponse(String text) {
        String json = extractJson(text);
        if (json == null) return List.of();

        try {
            JsonNode root = mapper.readTree(json);
            JsonNode activationsNode = root.path("activations");
            if (!activationsNode.isArray()) return List.of();

            List<SituationActivation> result = new ArrayList<>();
            for (JsonNode entry : activationsNode) {
                String nodeId = entry.path("node").asText(null);
                double confidence = entry.path("confidence").asDouble(0.0);

                if (nodeId == null || !validNodeIds.contains(nodeId)) continue;
                if (confidence < CONFIDENCE_THRESHOLD) continue;

                result.add(new SituationActivation(nodeId, Math.min(1.0, confidence)));
            }
            return List.copyOf(result);
        } catch (JsonProcessingException e) {
            LOG.warning("Failed to parse LLM classifier response: " + e.getMessage());
            return List.of();
        }
    }

    private String buildUserPrompt(String description, Map<String, String> metadata) {
        var sb = new StringBuilder("Description: ").append(description);
        if (metadata != null && !metadata.isEmpty()) {
            sb.append("\n\nMetadata:");
            metadata.forEach((k, v) -> sb.append("\n  ").append(k).append(": ").append(v));
        }
        return sb.toString();
    }

    private static String buildSystemPrompt(CapsTopology topology) {
        var sb = new StringBuilder("""
            You are a psychological situation classifier. Given a description of \
            an experience, classify it into one or more CAPS input node activations.

            Respond with a JSON object:
            {"activations": [{"node": "node_id", "confidence": 0.0-1.0}]}

            Rules:
            - Only use node IDs from the list below
            - confidence reflects how strongly the description matches the node
            - Multiple nodes can activate for the same description
            - Minimum confidence threshold is 0.3
            - Respond with valid JSON only

            Available input nodes:
            """);

        if (topology != null) {
            topology.nodes().forEach((id, node) -> {
                if (node.type() == NodeType.INPUT) {
                    sb.append("- ").append(id)
                        .append(" (").append(node.category()).append(")")
                        .append(node.keywords().isEmpty() ? "" :
                            " — keywords: " + String.join(", ", node.keywords()))
                        .append("\n");
                }
            });
        }

        return sb.toString();
    }

    static String extractJson(String text) {
        if (text == null || text.isBlank()) return null;
        int start = text.indexOf('{');
        int end = text.lastIndexOf('}');
        if (start < 0 || end <= start) return null;
        return text.substring(start, end + 1);
    }
}
