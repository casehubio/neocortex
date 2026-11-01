package io.casehub.neocortex.cognition;

import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithDefault;

@ConfigMapping(prefix = "casehub.cognition.retrieval-modulation")
public interface RetrievalModulationProperties {

    @WithDefault("false")
    boolean recencyEnabled();

    @WithDefault("30")
    int recencyHalfLifeDays();

    @WithDefault("false")
    boolean confidenceEnabled();

    @WithDefault("false")
    boolean moodCongruenceEnabled();

    @WithDefault("0.3")
    double moodCongruenceWeight();

    @WithDefault("false")
    boolean goalRelevanceEnabled();

    @WithDefault("false")
    boolean domainEnabled();

    @WithDefault("false")
    boolean entityOverlapEnabled();
}
