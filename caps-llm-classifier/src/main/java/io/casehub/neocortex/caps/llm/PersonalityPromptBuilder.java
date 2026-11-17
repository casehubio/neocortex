package io.casehub.neocortex.caps.llm;

import io.casehub.neocortex.caps.CapsTopology;
import io.casehub.neocortex.caps.NodeType;

import java.util.Map;
import java.util.StringJoiner;

public final class PersonalityPromptBuilder {

    private static final String DISPOSITION_PREFIX = "disposition.";
    private static final String[] AXIS_KEYS = {
        "socialOrient", "ruleFollowing", "riskAppetite", "autonomy", "conflictMode"
    };
    private static final String[] AXIS_LABELS = {
        "", " rule-following", " risk appetite", " autonomy", " conflict style"
    };
    private static final String   PID5_PREFIX = "pid5.";
    private static final String   APPRAISAL_PREFIX = "appraisal.";
    private static final double   NEUTRAL_THRESHOLD = 0.15;

    private static final Map<String, String> APPRAISAL_LABELS = Map.of(
            "urgencyWeight", "urgency sensitivity",
            "relationshipWeight", "relationship sensitivity",
            "fearOnsetThreshold", "fear onset",
            "selfStandardsStrictness", "self-standards",
            "otherStandardsStrictness", "other-standards"
                                                                      );


    private PersonalityPromptBuilder() {}

    public static boolean hasDisposition(Map<String, String> metadata) {
        if (metadata == null) return false;
        for (String key : AXIS_KEYS) {
            if (metadata.containsKey(DISPOSITION_PREFIX + key)) return true;
        }
        return false;
    }

    public static String userPromptSuffix(Map<String, String> metadata) {
        var joiner = new StringJoiner(", ");
        for (int i = 0; i < AXIS_KEYS.length; i++) {
            String val = metadata.get(DISPOSITION_PREFIX + AXIS_KEYS[i]);
            if (val != null) {
                joiner.add(val + AXIS_LABELS[i]);
            }
        }
        var sb = new StringBuilder();
        sb.append("\nCharacter disposition: ").append(joiner).append(".");
        sb.append(pid5Suffix(metadata));
        sb.append(appraisalSuffix(metadata));
        sb.append("\nClassify the situation as this character would perceive it.");
        return sb.toString();
    }

    public static boolean hasPid5(Map<String, String> metadata) {
        if (metadata == null) {return false;}
        return metadata.keySet().stream().anyMatch(k -> k.startsWith(PID5_PREFIX));
    }

    public static String pid5Suffix(Map<String, String> metadata) {
        if (metadata == null) {return "";}
        var facets = new StringJoiner(", ");
        metadata.entrySet().stream()
                .filter(e -> e.getKey().startsWith(PID5_PREFIX))
                .sorted(Map.Entry.comparingByKey())
                .forEach(e -> {
                    String name     = e.getKey().substring(PID5_PREFIX.length());
                    String scoreStr = e.getValue();
                    double score    = Double.parseDouble(scoreStr);
                    String level    = score >= 0.7 ? "high" : score <= 0.3 ? "low" : "moderate";
                    facets.add(level + " " + name + " (" + scoreStr + ")");
                });
        return facets.length() == 0 ? "" :
               "\nPersonality facets: " + facets + ".";
    }

    public static String appraisalSuffix(Map<String, String> metadata) {
        if (metadata == null) {return "";}
        var items = new StringJoiner(", ");
        metadata.entrySet().stream()
                .filter(e -> e.getKey().startsWith(APPRAISAL_PREFIX))
                .sorted(Map.Entry.comparingByKey())
                .forEach(e -> {
                    String field = e.getKey().substring(APPRAISAL_PREFIX.length());
                    double val   = Double.parseDouble(e.getValue());
                    if (Math.abs(val - 1.0) <= NEUTRAL_THRESHOLD) {return;}
                    String label = APPRAISAL_LABELS.getOrDefault(field, field);
                    String direction;
                    if (field.contains("Strictness")) {
                        direction = val > 1.0 ? "strict" : "lenient";
                    } else if (field.equals("fearOnsetThreshold")) {
                        direction = val > 1.0 ? "heightened" : "dampened";
                    } else {
                        direction = val > 1.0 ? "elevated" : "dampened";
                    }
                    items.add(direction + " " + label + " (" + String.format("%.2f", val) + ")");
                });
        return items.length() == 0 ? "" :
               "\nPerception biases: " + items + ".";
    }


    public static String personalitySystemPrompt(Map<String, String> metadata,
                                                 DispositionTension.TensionEntry tension,
                                                 CapsTopology topology) {
        var sb = new StringBuilder();
        sb.append("""
                  You are a psychological situation classifier perceiving the world \
                  through a specific character's lens.
                  
                  This character is """);

        var joiner = new StringJoiner(", ");
        for (int i = 0; i < AXIS_KEYS.length; i++) {
            String val = metadata.get(DISPOSITION_PREFIX + AXIS_KEYS[i]);
            if (val != null) {
                joiner.add(val + AXIS_LABELS[i]);
            }
        }
        sb.append(joiner).append(". ");
        sb.append(tension.narrative()).append("\n\n");

        String pid5 = pid5Suffix(metadata);
        if (!pid5.isEmpty()) {sb.append(pid5.strip()).append("\n");}
        String appraisal = appraisalSuffix(metadata);
        if (!appraisal.isEmpty()) {sb.append(appraisal.strip()).append("\n\n");}

        sb.append("""
                  Classify the following situation into CAPS input node activations.
                  
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

        appendTopologyNodes(sb, topology);

        return sb.toString();
    }

    static void appendTopologyNodes(StringBuilder sb, CapsTopology topology) {
        if (topology == null) return;
        topology.nodes().forEach((id, node) -> {
            if (node.type() == NodeType.INPUT) {
                sb.append("- ").append(id);
                if (!node.description().isEmpty()) {
                    sb.append(" (").append(node.category())
                      .append(": ").append(node.description()).append(")");
                } else {
                    sb.append(" (").append(node.category()).append(")");
                }
                sb.append(node.vocabulary().isEmpty() ? "" :
                          " — vocabulary: " + String.join(", ", node.vocabulary().keySet()));
                sb.append("\n");
            }
        });
    }
}
