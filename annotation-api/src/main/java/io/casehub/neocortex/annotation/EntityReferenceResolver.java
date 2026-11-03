package io.casehub.neocortex.annotation;

import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

public final class EntityReferenceResolver {

    private EntityReferenceResolver() {}

    public static Map<String, String> resolve(
            EntityAnnotationIndex index,
            String sourceType,
            Map<String, String> sourceTexts,
            String tenantId,
            Function<String, String> nodeIdToName) {

        if (sourceTexts.isEmpty()) return Map.of();

        List<EntityAnnotation> annotations = index.findBySource(
            sourceType, sourceTexts.keySet(), tenantId);

        Map<String, List<EntityAnnotation>> bySource = annotations.stream()
            .collect(Collectors.groupingBy(EntityAnnotation::sourceId));

        Map<String, String> resolved = new HashMap<>();
        for (var entry : sourceTexts.entrySet()) {
            String sourceId = entry.getKey();
            String text = entry.getValue();
            List<EntityAnnotation> anns = bySource.getOrDefault(sourceId, List.of());

            if (anns.isEmpty()) {
                resolved.put(sourceId, text);
                continue;
            }

            resolved.put(sourceId, applySubstitutions(text, anns, nodeIdToName));
        }
        return Map.copyOf(resolved);
    }

    private static String applySubstitutions(String text,
            List<EntityAnnotation> annotations,
            Function<String, String> nodeIdToName) {

        List<EntityAnnotation> sorted = annotations.stream()
            .sorted(Comparator.comparingInt(EntityAnnotation::startOffset).reversed())
            .toList();

        StringBuilder sb = new StringBuilder(text);
        for (EntityAnnotation ann : sorted) {
            String currentName = nodeIdToName.apply(ann.entityNodeId());
            if (currentName == null) continue;
            if (currentName.equals(ann.mentionText())) continue;
            if (ann.startOffset() >= 0 && ann.endOffset() <= sb.length()) {
                sb.replace(ann.startOffset(), ann.endOffset(), currentName);
            }
        }
        return sb.toString();
    }
}
