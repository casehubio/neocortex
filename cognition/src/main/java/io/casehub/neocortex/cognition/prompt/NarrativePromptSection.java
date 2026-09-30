package io.casehub.neocortex.cognition.prompt;

import io.casehub.neocortex.cognition.narrative.NarrativeOrchestrator;
import io.casehub.neocortex.cognition.prompt.observation.AffordanceRenderer;
import io.casehub.neocortex.cognition.prompt.observation.CognitiveObservationSections;
import org.jspecify.annotations.Nullable;

import java.util.List;

public class NarrativePromptSection implements CognitionPromptRenderer {

    private final NarrativeOrchestrator narrative;

    public NarrativePromptSection(NarrativeOrchestrator narrative) {
        this.narrative = narrative;
    }

    @Override
    public @Nullable String render(CognitionRenderContext context) {
        return narrative.currentNarrative(context.agentId(), context.tenantId())
                .map(state -> {
                    var section = CognitiveObservationSections.narrativeSection(state);
                    return new AffordanceRenderer().renderObservation(List.of(section));
                })
                .filter(s -> !s.isBlank())
                .orElse(null);
    }
}
