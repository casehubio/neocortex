package io.casehub.neocortex.memory.importance;

import io.casehub.neocortex.memory.ImportanceScorer;
import io.quarkus.arc.DefaultBean;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.List;
import java.util.Map;

@ApplicationScoped
@DefaultBean
public class NoOpImportanceScorer implements ImportanceScorer {
    @Override
    public Map<String, Integer> score(List<ImportanceScorer.ScoringEntry> batch) {
        return Map.of();
    }
}
