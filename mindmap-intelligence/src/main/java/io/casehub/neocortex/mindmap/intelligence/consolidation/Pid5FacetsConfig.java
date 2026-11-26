package io.casehub.neocortex.mindmap.intelligence.consolidation;

import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithDefault;

import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

@ConfigMapping(prefix = "casehub.caps.classification.cbr")
interface Pid5FacetsConfig {

    @WithDefault("Anxiousness,Emotional Lability,Withdrawal,Impulsivity,Risk Taking,Suspiciousness,Intimacy Avoidance,Callousness,Manipulativeness,Grandiosity,Deceitfulness,Attention Seeking,Hostility,Anhedonia,Depressivity,Perseveration,Rigid Perfectionism,Distractibility,Separation Insecurity,Restricted Affectivity,Irresponsibility,Cognitive Perceptual Dysregulation,Eccentricity,Submissiveness,Unusual Beliefs")
    String pid5Facets();

    default Set<String> pid5FacetSet() {
        return Arrays.stream(pid5Facets().split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .collect(Collectors.toUnmodifiableSet());
    }
}
