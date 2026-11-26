package io.casehub.neocortex.caps.cbr;

import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithDefault;

@ConfigMapping(prefix = "casehub.caps.classification.cbr")
public interface ClassificationCbrConfig {

    @WithDefault("true")
    boolean enabled();

    @WithDefault("0.85")
    double minSimilarity();

    @WithDefault("haiku-pid5")
    String modelVersion();

    @WithDefault("Anxiousness,Emotional Lability,Withdrawal,Impulsivity,Risk Taking,Suspiciousness,Intimacy Avoidance,Callousness,Manipulativeness,Grandiosity,Deceitfulness,Attention Seeking,Hostility,Anhedonia,Depressivity,Perseveration,Rigid Perfectionism,Distractibility,Separation Insecurity,Restricted Affectivity,Irresponsibility,Cognitive Perceptual Dysregulation,Eccentricity,Submissiveness,Unusual Beliefs")
    String pid5Facets();
}
