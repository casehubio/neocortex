package io.casehub.neocortex.cognition.personality;

import io.casehub.eidos.api.AgentDescriptor;
import io.casehub.eidos.api.DispositionValue;
import io.casehub.neocortex.memory.relationship.QualitySignal;

import java.util.Comparator;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.List;

@ApplicationScoped
public class InteractionPressureSource implements TraitPressureSource<InteractionAppraised> {

    @Override
    public Class<InteractionAppraised> eventType() {
        return InteractionAppraised.class;
    }

    @Override
    public boolean relevant(InteractionAppraised event) {
        return event.quality() != QualitySignal.NEUTRAL;
    }

    @Override
    public List<TraitActivation> translate(InteractionAppraised event, AgentDescriptor descriptor) {
        var profile = descriptor.disposition().dispositionProfile();
        if (profile == null || profile.isEmpty()) {
            return List.of();
        }
        var sorted = profile.stream()
                .sorted(Comparator.comparingDouble(DispositionValue::weight).reversed())
                .toList();

        if (event.quality() == QualitySignal.POSITIVE) {
            return List.of(new TraitActivation(sorted.getFirst().term()));
        }
        var target = sorted.size() > 1 ? sorted.get(1) : sorted.getFirst();
        return List.of(new TraitActivation(target.term()));
    }
}
