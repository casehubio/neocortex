package io.casehub.neocortex.rag.scoring;

import io.casehub.neocortex.rag.PostRetrievalScorer;
import io.casehub.neocortex.rag.RetrievalQuery;
import io.casehub.neocortex.rag.RetrievedChunk;
import io.casehub.neocortex.rag.ScoringContext;

public class VersionScorer implements PostRetrievalScorer {

    public record Config(double decayFactor, double floor, double defaultTopicWeight) {}

    private final String versionKey;
    private final Config config;

    public VersionScorer(String versionKey, Config config) {
        this.versionKey = versionKey;
        this.config = config;
    }

    @Override
    public double adjust(RetrievedChunk chunk, RetrievalQuery query, ScoringContext context) {
        String verifiedOn = chunk.metadata().get(versionKey);
        if (verifiedOn == null || verifiedOn.isBlank()) return 1.0;
        if (context.versionProfile().isEmpty()) return 1.0;

        String[] parts = verifiedOn.split(":", 2);
        if (parts.length < 2) return 1.0;

        String stack = parts[0];
        String entryVersion = parts[1];
        String bomVersion = context.versionProfile().get(stack);
        if (bomVersion == null) return 1.0;

        int[] entryParts = parseVersion(entryVersion);
        int[] bomParts = parseVersion(bomVersion);

        if (entryParts[0] != bomParts[0]) return config.floor;

        int minorDistance = Math.abs(bomParts[1] - entryParts[1]);
        if (minorDistance == 0) return 1.0;

        double topicWeight = queryContainsStack(query.text(), stack) ? 1.0 : config.defaultTopicWeight;
        return Math.max(config.floor, 1.0 - minorDistance * config.decayFactor * topicWeight);
    }

    static boolean queryContainsStack(String queryText, String stack) {
        if (queryText == null || queryText.isBlank()) return false;
        String lowerStack = stack.toLowerCase();
        for (String token : queryText.toLowerCase().split("[\\s\\-]+")) {
            if (token.length() >= 3 && lowerStack.contains(token)) return true;
        }
        return false;
    }

    static int[] parseVersion(String version) {
        String[] parts = version.split("\\.");
        int major = 0, minor = 0;
        try { major = Integer.parseInt(parts[0]); } catch (NumberFormatException ignored) {}
        if (parts.length > 1) {
            try { minor = Integer.parseInt(parts[1]); } catch (NumberFormatException ignored) {}
        }
        return new int[]{major, minor};
    }
}
