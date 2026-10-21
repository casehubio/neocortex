package io.casehub.neocortex.caps.engine;

import io.casehub.neocortex.caps.CapsTopology;
import io.casehub.neocortex.caps.NodeType;
import io.casehub.neocortex.caps.SituationActivation;
import io.casehub.neocortex.caps.SituationContext;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Shared validation logic for situation classification results.
 * Filters activations by valid INPUT node IDs and confidence threshold.
 * Extracted from LlmSituationClassifier and PipelineSituationClassifier
 * to eliminate duplication.
 */
public class ClassificationValidator {

    public static final double CONFIDENCE_THRESHOLD = 0.3;

    private final CapsTopology topology;
    private final Set<String> validNodeIds;

    public ClassificationValidator(CapsTopology topology) {
        this.topology = topology;
        this.validNodeIds = topology.nodes().entrySet().stream()
            .filter(e -> e.getValue().type() == NodeType.INPUT)
            .map(Map.Entry::getKey)
            .collect(Collectors.toSet());
    }

    public SituationContext validate(List<SituationActivation> activations) {
        if (activations == null || activations.isEmpty()) {
            return SituationContext.EMPTY;
        }
        var filtered = activations.stream()
            .filter(a -> a.nodeId() != null && validNodeIds.contains(a.nodeId()))
            .filter(a -> a.confidence() >= CONFIDENCE_THRESHOLD)
            .map(a -> new SituationActivation(a.nodeId(), Math.min(1.0, a.confidence())))
            .toList();
        return SituationContext.from(filtered);
    }

    public Set<String> validInputNodeIds() {
        return Set.copyOf(validNodeIds);
    }

    public String buildNodeVocabulary() {
        var sb = new StringBuilder();
        topology.nodes().forEach((id, node) -> {
            if (node.type() == NodeType.INPUT) {
                sb.append("- ").append(id);
                if (!node.description().isEmpty()) {
                    sb.append(" (").append(node.category())
                      .append(": ").append(node.description()).append(")");
                } else {
                    sb.append(" (").append(node.category()).append(")");
                }
                if (!node.vocabulary().isEmpty()) {
                    sb.append(" — vocabulary: ").append(String.join(", ", node.vocabulary().keySet()));
                }
                sb.append("\n");
            }
        });
        return sb.toString();
    }
}
