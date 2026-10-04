package io.casehub.neocortex.knowledge;

import java.util.List;
import java.util.Optional;

@FunctionalInterface
public interface SubsumptionRule {

    Optional<List<CachedEntity>> subsume(NormalizedQuery query,
                                          List<CachedEntity> broaderResults,
                                          NormalizedQuery broaderQuery);
}
