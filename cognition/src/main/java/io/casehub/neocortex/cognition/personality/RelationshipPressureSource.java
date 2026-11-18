package io.casehub.neocortex.cognition.personality;

import io.casehub.eidos.api.AgentDescriptor;
import io.casehub.eidos.api.DispositionValue;
import io.casehub.neocortex.memory.relationship.QualitySignal;
import io.casehub.neocortex.memory.relationship.RelationshipEvent;

import java.util.Comparator;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.List;

@ApplicationScoped
public class RelationshipPressureSource implements TraitPressureSource<RelationshipEvent> {

    @Override
    public Class<RelationshipEvent> eventType() {
        return RelationshipEvent.class;
    }

    @Override
    public boolean relevant(RelationshipEvent event) {
        return event.qualitySignal() != QualitySignal.NEUTRAL;
    }

    @Override
    public List<TraitActivation> translate(final RelationshipEvent event,
                                           final AgentDescriptor descriptor) {
        var profile = descriptor.disposition().dispositionProfile();
        if (profile == null || profile.isEmpty()) {
            return List.of();
        }
        var sorted = profile.stream()
                .sorted(Comparator.comparingDouble(DispositionValue::weight).reversed())
                .toList();

        if (event.qualitySignal() == QualitySignal.POSITIVE) {
            return List.of(new TraitActivation(sorted.getFirst().term()));
        }
        var target = sorted.size() > 1 ? sorted.get(1) : sorted.getFirst();
        return List.of(new TraitActivation(target.term()));
    }
}
