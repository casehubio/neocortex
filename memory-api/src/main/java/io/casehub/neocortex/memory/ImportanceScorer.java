package io.casehub.neocortex.memory;

import java.util.List;
import java.util.Map;

@FunctionalInterface
public interface ImportanceScorer {
    Map<String, Integer> score(List<ScoringEntry> batch);

    record ScoringEntry(String memoryId, String text, String domain, String tenantId) {}
}
