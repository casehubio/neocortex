package io.casehub.neocortex.caps.llm;

import io.casehub.neocortex.caps.CapsTopology;
import io.casehub.neocortex.caps.NodeType;
import io.casehub.neocortex.caps.TrajectoryEntry;

import java.util.List;

/**
 * Builds prompt fragments for inline situation classification.
 * Two modes: degree-only (most ticks) and degree+assessment (when
 * the accumulator fires). Uses the same CAPS node vocabulary as
 * LlmSituationClassifier.
 */
public class InlineClassificationPromptBuilder {

    private final String nodeVocabulary;

    public InlineClassificationPromptBuilder(CapsTopology topology) {
        this.nodeVocabulary = buildNodeVocabulary(topology);
    }

    public String buildDegreeOnlyInstruction() {
        return """
            "situationChangeDegree": 0.0-1.0 (how much the situation changed this tick: 0.0 = nothing, 1.0 = dramatic shift)""";
    }

    public String buildAssessmentInstruction(List<TrajectoryEntry> trajectory) {
        String trajectoryText = renderTrajectoryText(trajectory);
        return """
            "situationChangeDegree": 0.0-1.0 (how much the situation changed this tick),
            "situationAssessment": {
              "reasoning": "explain what happened and why it matters",
              "activations": [{"node": "node_id", "confidence": 0.0-1.0, "source": [start_char, end_char]}]
            }

            [SITUATION ASSESSMENT — classify objectively, not in character]
            Classify the following situation trajectory into CAPS input node activations.
            Source ranges reference character offsets in the trajectory text below.
            Only use node IDs from the available list. Minimum confidence 0.3.

            Trajectory:
            """ + trajectoryText + "\n\nAvailable input nodes:\n" + nodeVocabulary;
    }

    public String renderTrajectoryText(List<TrajectoryEntry> trajectory) {
        var sb = new StringBuilder();
        for (var entry : trajectory) {
            sb.append("Tick ").append(entry.tick()).append(": ").append(entry.actionResult()).append("\n");
        }
        return sb.toString();
    }

    private static String buildNodeVocabulary(CapsTopology topology) {
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
