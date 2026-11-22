package io.casehub.neocortex.cognitive.eidos;

import io.casehub.eidos.api.AgentDescriptorUpdated;
import io.casehub.neocortex.cognitive.index.EffectiveCognitiveDefaults;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;

@ApplicationScoped
public class EidosDescriptorUpdateObserver {

    @Inject EffectiveCognitiveDefaults effectiveDefaults;

    void onDescriptorUpdated(@Observes AgentDescriptorUpdated event) {
        var descriptor = event.current();
        effectiveDefaults.invalidateBase(descriptor.tenancyId(), descriptor.agentId());
    }
}
