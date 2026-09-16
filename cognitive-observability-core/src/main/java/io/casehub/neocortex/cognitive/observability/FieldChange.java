package io.casehub.neocortex.cognitive.observability;

import io.casehub.neocortex.mindmap.MindMapNode;
import io.casehub.neocortex.mindmap.NodeUpdate;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

public record FieldChange(String field, Object oldValue, Object newValue) {

    public static Map<String, FieldChange> diff(MindMapNode before, NodeUpdate update) {
        Map<String, FieldChange> changes = new HashMap<>();
        if (before == null) return changes;

        if (update.name() != null && !Objects.equals(update.name(), before.name())) {
            changes.put("name", new FieldChange("name", before.name(), update.name()));
        }
        if (update.confidence() != null && !Objects.equals(update.confidence(), before.confidence())) {
            changes.put("confidence", new FieldChange("confidence", before.confidence(), update.confidence()));
        }
        if (update.pleasure() != null && !Objects.equals(update.pleasure(), before.pleasure())) {
            changes.put("pleasure", new FieldChange("pleasure", before.pleasure(), update.pleasure()));
        }
        if (update.arousal() != null && !Objects.equals(update.arousal(), before.arousal())) {
            changes.put("arousal", new FieldChange("arousal", before.arousal(), update.arousal()));
        }
        if (update.dominance() != null && !Objects.equals(update.dominance(), before.dominance())) {
            changes.put("dominance", new FieldChange("dominance", before.dominance(), update.dominance()));
        }
        if (update.validFrom() != null && !Objects.equals(update.validFrom(), before.validFrom())) {
            changes.put("validFrom", new FieldChange("validFrom", before.validFrom(), update.validFrom()));
        }
        if (update.validUntil() != null && !Objects.equals(update.validUntil(), before.validUntil())) {
            changes.put("validUntil", new FieldChange("validUntil", before.validUntil(), update.validUntil()));
        }

        if ((update.traitsToAdd() != null && !update.traitsToAdd().isEmpty())
            || (update.traitsToRemove() != null && !update.traitsToRemove().isEmpty())) {
            Set<String> oldTraits = before.traits() != null ? before.traits() : Set.of();
            Set<String> newTraits = new HashSet<>(oldTraits);
            if (update.traitsToAdd() != null) newTraits.addAll(update.traitsToAdd());
            if (update.traitsToRemove() != null) newTraits.removeAll(update.traitsToRemove());
            if (!Objects.equals(oldTraits, newTraits)) {
                changes.put("traits", new FieldChange("traits", oldTraits, Set.copyOf(newTraits)));
            }
        }

        if ((update.refsToAdd() != null && !update.refsToAdd().isEmpty())
            || (update.refsToRemove() != null && !update.refsToRemove().isEmpty())) {
            Set<?> oldRefs = before.refs() != null ? Set.copyOf(before.refs()) : Set.of();
            var newRefs = new HashSet<>(before.refs() != null ? before.refs() : Set.of());
            if (update.refsToAdd() != null) newRefs.addAll(update.refsToAdd());
            if (update.refsToRemove() != null) newRefs.removeAll(update.refsToRemove());
            Set<?> finalRefs = Set.copyOf(newRefs);
            if (!Objects.equals(oldRefs, finalRefs)) {
                changes.put("refs", new FieldChange("refs", oldRefs, finalRefs));
            }
        }

        if ((update.propertiesToSet() != null && !update.propertiesToSet().isEmpty())
            || (update.propertiesToRemove() != null && !update.propertiesToRemove().isEmpty())) {
            var oldProps = before.properties() != null ? new HashMap<>(before.properties()) : new HashMap<String, String>();
            var newProps = new HashMap<>(oldProps);
            if (update.propertiesToSet() != null) newProps.putAll(update.propertiesToSet());
            if (update.propertiesToRemove() != null) update.propertiesToRemove().forEach(newProps::remove);
            if (!Objects.equals(oldProps, newProps)) {
                changes.put("properties", new FieldChange("properties", Map.copyOf(oldProps), Map.copyOf(newProps)));
            }
        }

        return changes;
    }

    public static Map<String, FieldChange> diffSnapshot(NodeSnapshot before, NodeUpdate update) {
        Map<String, FieldChange> changes = new HashMap<>();
        if (before == null) return changes;

        if (update.name() != null && !Objects.equals(update.name(), before.name())) {
            changes.put("name", new FieldChange("name", before.name(), update.name()));
        }
        if (update.confidence() != null && !Objects.equals(update.confidence(), before.confidence())) {
            changes.put("confidence", new FieldChange("confidence", before.confidence(), update.confidence()));
        }
        if (update.pleasure() != null && !Objects.equals(update.pleasure(), before.pleasure())) {
            changes.put("pleasure", new FieldChange("pleasure", before.pleasure(), update.pleasure()));
        }
        if (update.arousal() != null && !Objects.equals(update.arousal(), before.arousal())) {
            changes.put("arousal", new FieldChange("arousal", before.arousal(), update.arousal()));
        }
        if (update.dominance() != null && !Objects.equals(update.dominance(), before.dominance())) {
            changes.put("dominance", new FieldChange("dominance", before.dominance(), update.dominance()));
        }

        if ((update.traitsToAdd() != null && !update.traitsToAdd().isEmpty())
            || (update.traitsToRemove() != null && !update.traitsToRemove().isEmpty())) {
            Set<String> oldTraits = before.traits() != null ? before.traits() : Set.of();
            Set<String> newTraits = new HashSet<>(oldTraits);
            if (update.traitsToAdd() != null) newTraits.addAll(update.traitsToAdd());
            if (update.traitsToRemove() != null) newTraits.removeAll(update.traitsToRemove());
            if (!Objects.equals(oldTraits, newTraits)) {
                changes.put("traits", new FieldChange("traits", oldTraits, Set.copyOf(newTraits)));
            }
        }

        if ((update.propertiesToSet() != null && !update.propertiesToSet().isEmpty())
            || (update.propertiesToRemove() != null && !update.propertiesToRemove().isEmpty())) {
            var oldProps = before.properties() != null ? new HashMap<>(before.properties()) : new HashMap<String, String>();
            var newProps = new HashMap<>(oldProps);
            if (update.propertiesToSet() != null) newProps.putAll(update.propertiesToSet());
            if (update.propertiesToRemove() != null) update.propertiesToRemove().forEach(newProps::remove);
            if (!Objects.equals(oldProps, newProps)) {
                changes.put("properties", new FieldChange("properties", Map.copyOf(oldProps), Map.copyOf(newProps)));
            }
        }

        return changes;
    }
}
