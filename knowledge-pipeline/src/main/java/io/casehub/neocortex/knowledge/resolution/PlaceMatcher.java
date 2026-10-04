package io.casehub.neocortex.knowledge.resolution;

import io.casehub.neocortex.knowledge.CachedEntity;
import io.casehub.neocortex.knowledge.EntityMatcher;
import io.casehub.neocortex.knowledge.MatchResult;
import io.casehub.neocortex.knowledge.MatchTier;
import io.casehub.neocortex.mindmap.intelligence.consolidation.JaroWinkler;

import java.util.ArrayList;
import java.util.List;

public class PlaceMatcher implements EntityMatcher<CachedEntity> {

    @Override
    public MatchResult match(CachedEntity candidate, CachedEntity existing) {
        if (candidate.source().equals(existing.source())
                && candidate.externalId().equals(existing.externalId())) {
            return new MatchResult(1.0, List.of("same external ID"), MatchTier.DEFINITIVE);
        }

        List<String> signals = new ArrayList<>();
        double best = 0.0;

        if (candidate.coordinates() != null && existing.coordinates() != null) {
            double dist = Haversine.distanceMeters(
                candidate.coordinates(), existing.coordinates());
            double nameSim = JaroWinkler.similarity(
                candidate.name().toLowerCase(), existing.name().toLowerCase());

            if (dist < 50 && nameSim > 0.85) {
                signals.add("proximity <50m + name similarity "
                    + String.format("%.2f", nameSim));
                best = Math.max(best, 0.9);
            } else if (dist < 200 && nameSim > 0.9) {
                signals.add("proximity <200m + name similarity "
                    + String.format("%.2f", nameSim));
                best = Math.max(best, 0.65);
            }
        }

        String phoneA = PhoneNormalizer.normalize(
            candidate.properties().get("phone"));
        String phoneB = PhoneNormalizer.normalize(
            existing.properties().get("phone"));
        if (!phoneA.isEmpty() && phoneA.equals(phoneB)) {
            signals.add("phone match");
            best = Math.max(best, 0.85);
        }

        if (signals.isEmpty()) {
            return MatchResult.noMatch();
        }
        return new MatchResult(best, signals, MatchTier.fromConfidence(best));
    }
}
