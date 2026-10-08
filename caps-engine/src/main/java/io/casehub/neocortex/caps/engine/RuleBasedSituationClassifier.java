package io.casehub.neocortex.caps.engine;

import io.casehub.neocortex.caps.CapsNode;
import io.casehub.neocortex.caps.CapsTopology;
import io.casehub.neocortex.caps.NodeType;
import io.casehub.neocortex.caps.SituationActivation;
import io.casehub.neocortex.caps.SituationClassifier;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class RuleBasedSituationClassifier implements SituationClassifier {

    private static final double CONFIDENCE_THRESHOLD = 0.3;

    private final CapsTopology topology;

    public RuleBasedSituationClassifier(CapsTopology topology) {
        this.topology = topology;
    }

    @Override
    public List<SituationActivation> classify(String description,
                                               Map<String, String> metadata) {
        if (description == null || description.isBlank()) return List.of();

        String lower = description.toLowerCase();
        String[] tokens = lower.split("\\W+");

        double salienceMultiplier = 1.0;
        if (metadata != null) {
            String salience = metadata.get("salience-multiplier");
            if (salience != null) {
                salienceMultiplier = Double.parseDouble(salience);
            }
        }

        List<SituationActivation> activations = new ArrayList<>();

        for (Map.Entry<String, CapsNode> entry : topology.nodes().entrySet()) {
            CapsNode node = entry.getValue();
            if (node.type() != NodeType.INPUT) continue;
            if (node.keywords().isEmpty()) continue;

            int matchCount = 0;
            for (String keyword : node.keywords()) {
                if (containsKeyword(tokens, lower, keyword.toLowerCase())) {
                    matchCount++;
                }
            }

            if (matchCount == 0) continue;

            double confidence = Math.min(1.0, matchCount * 0.3 * salienceMultiplier);

            if (confidence >= CONFIDENCE_THRESHOLD) {
                activations.add(new SituationActivation(node.id(), confidence));
            }
        }

        return List.copyOf(activations);
    }

    private boolean containsKeyword(String[] tokens, String fullText, String keyword) {
        if (keyword.contains(" ")) {
            return fullText.contains(keyword);
        }
        for (String token : tokens) {
            if (token.equals(keyword)) return true;
        }
        return false;
    }
}
