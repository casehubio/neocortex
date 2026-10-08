package io.casehub.neocortex.cognition.subthought;

import io.casehub.neocortex.memory.experience.SubThoughtClassifier;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@ApplicationScoped
public class RuleBasedSubThoughtExtractor {

    private final ConcurrentHashMap<String, Set<String>> entityCache = new ConcurrentHashMap<>();

    public List<SubThought> extract(String observation, String agentId, String tenantId) {
        if (observation == null || observation.isBlank()) return List.of();

        var matches = SubThoughtClassifier.classify(observation);
        Set<String> entities = entityCache.get(tenantId);

        return matches.stream()
                .map(m -> new SubThought(m.type(), m.text(), findEntity(m.text(), entities), 0.5, SubThought.Source.SYNC))
                .toList();
    }

    public void refreshEntityCache(String tenantId, Set<String> names) {
        entityCache.put(tenantId, Set.copyOf(names));
    }

    private String findEntity(String sentence, Set<String> entities) {
        if (entities == null || entities.isEmpty()) return null;
        String lower = sentence.toLowerCase(Locale.ROOT);
        for (String entity : entities) {
            if (SubThoughtClassifier.containsWord(lower, entity.toLowerCase(Locale.ROOT))) {
                return entity;
            }
        }
        return null;
    }
}
