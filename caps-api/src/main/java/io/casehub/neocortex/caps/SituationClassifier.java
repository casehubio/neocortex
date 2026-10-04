package io.casehub.neocortex.caps;

import java.util.List;
import java.util.Map;

@FunctionalInterface
public interface SituationClassifier {
    List<SituationActivation> classify(String description,
                                        Map<String, String> metadata);
}
