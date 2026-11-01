package io.casehub.neocortex.cognitive.index;

public record RetrievalModulationConfig(
        boolean recencyEnabled,
        int recencyHalfLifeDays,
        boolean confidenceEnabled,
        boolean moodCongruenceEnabled,
        double moodCongruenceWeight,
        boolean goalRelevanceEnabled,
        boolean domainEnabled,
        boolean entityOverlapEnabled) {

    public static RetrievalModulationConfig defaults() {
        return new RetrievalModulationConfig(
                false, 30, false, false, 0.3, false, false, false);
    }
}
