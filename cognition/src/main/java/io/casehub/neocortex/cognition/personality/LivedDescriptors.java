package io.casehub.neocortex.cognition.personality;

import io.casehub.eidos.api.AgentDescriptor;
import io.casehub.neocortex.cognitive.eidos.EidosCognitiveProfileBridge;
import io.casehub.neocortex.cognition.situational.PersonalityState;

public final class LivedDescriptors {
    private LivedDescriptors() {}

    public static AgentDescriptor compose(AgentDescriptor authored, PersonalityState state) {
        if (state == null || state.livedProfile() == null) return authored;
        return EidosCognitiveProfileBridge.livedDescriptor(authored, state.livedProfile());
    }
}
