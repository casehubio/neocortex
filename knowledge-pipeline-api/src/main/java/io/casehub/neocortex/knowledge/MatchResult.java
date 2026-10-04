package io.casehub.neocortex.knowledge;

import java.util.List;

public record MatchResult(double confidence, List<String> matchedSignals, MatchTier tier) {

    public MatchResult {
        if (confidence < 0.0 || confidence > 1.0)
            throw new IllegalArgumentException("confidence must be in [0,1]");
        matchedSignals = List.copyOf(matchedSignals);
    }

    public static MatchResult noMatch() {
        return new MatchResult(0.0, List.of(), MatchTier.LOW);
    }
}
