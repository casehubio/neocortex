package io.casehub.neocortex.mindmap;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

public final class DevelopmentalPhaseResolver {

    public static final String CHILDHOOD = "childhood";
    public static final String ADOLESCENCE = "adolescence";
    public static final String EARLY_ADULTHOOD = "early-adulthood";
    public static final String ADULTHOOD = "adulthood";

    private DevelopmentalPhaseResolver() {}

    public static String resolvePhase(LifecycleDate born, Instant now) {
        if (born == null) return ADULTHOOD;
        long days = ChronoUnit.DAYS.between(born.toStartInstant(), now);
        int years = (int) (days / 365.25);
        if (years <= 12) return CHILDHOOD;
        if (years <= 17) return ADOLESCENCE;
        if (years <= 25) return EARLY_ADULTHOOD;
        return ADULTHOOD;
    }

    public static String resolvePhase(MindMapNode personNode, Instant now) {
        if (personNode == null) return ADULTHOOD;
        String bornValue = personNode.properties().get(LifecycleEvents.key("born"));
        if (bornValue == null || bornValue.isBlank()) return ADULTHOOD;
        try {
            return resolvePhase(LifecycleDate.parse(bornValue), now);
        } catch (IllegalArgumentException e) {
            return ADULTHOOD;
        }
    }
}
