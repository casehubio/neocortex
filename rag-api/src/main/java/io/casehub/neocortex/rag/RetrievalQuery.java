package io.casehub.neocortex.rag;

public record RetrievalQuery(String text, String expandedText, java.util.Map<String, Double> weightMultipliers) {

    public RetrievalQuery {
        if (text == null || text.isBlank()) {throw new IllegalArgumentException("text must not be null or blank");}
        if (expandedText != null && expandedText.isBlank()) {
            throw new IllegalArgumentException("expandedText must not be blank when provided");
        }
        if (weightMultipliers == null) {
            weightMultipliers = java.util.Map.of();
        } else {
            for (var entry : weightMultipliers.entrySet()) {
                if (entry.getValue() <= 0) {
                    throw new IllegalArgumentException("Multiplier must be positive, got "
                                                       + entry.getValue() + " for " + entry.getKey());
                }
            }
            weightMultipliers = java.util.Map.copyOf(weightMultipliers);
        }
    }

    public static RetrievalQuery of(String text) {
        return new RetrievalQuery(text, null, java.util.Map.of());
    }

    public String searchText() {
        return expandedText != null ? expandedText : text;
    }

    public RetrievalQuery withExpansion(String expandedText) {
        return new RetrievalQuery(text, expandedText, weightMultipliers);
    }

    public RetrievalQuery withBm25Boost(double multiplier) {
        var m = new java.util.HashMap<>(weightMultipliers);
        m.put("bm25", multiplier);
        return new RetrievalQuery(text, expandedText, m);
    }

    public RetrievalQuery withWeightMultiplier(String leg, double multiplier) {
        var m = new java.util.HashMap<>(weightMultipliers);
        m.put(leg, multiplier);
        return new RetrievalQuery(text, expandedText, m);
    }
}
