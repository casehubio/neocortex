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
        // Keyword matching misclassifies narrative text: "bullied" maps to dominance
        // (the problem described), not acceptance (the gallant response). Produces
        // wrong DriveProfileInferred inputs that actively break appraisal filtering.
        // Use LlmSituationClassifier until NLP pipeline (#587) provides a viable path.
        throw new UnsupportedOperationException(
            "RuleBasedSituationClassifier disabled — keyword matching produces incorrect " +
            "drive profiles from narrative memory text. Use LlmSituationClassifier.");
    }

    @SuppressWarnings("unused") // preserved for future NLP pipeline (#587)
    public List<SituationActivation> classifyByKeyword(String description,
                                                Map<String, String> metadata) {
        if (description == null || description.isBlank()) {return List.of();}

        String   lower  = description.toLowerCase();
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
            if (node.type() != NodeType.INPUT) {continue;}
            if (node.vocabulary().isEmpty()) {continue;}

            double maxWeight  = 0;
            int    matchCount = 0;
            for (Map.Entry<String, Double> vocab : node.vocabulary().entrySet()) {
                if (containsKeyword(tokens, lower, vocab.getKey().toLowerCase())) {
                    maxWeight = Math.max(maxWeight, vocab.getValue());
                    matchCount++;
                }
            }

            if (maxWeight == 0) {continue;}

            double reinforcement = Math.min(0.15, (matchCount - 1) * 0.05);
            double confidence    = Math.min(1.0, (maxWeight + reinforcement) * salienceMultiplier);

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
