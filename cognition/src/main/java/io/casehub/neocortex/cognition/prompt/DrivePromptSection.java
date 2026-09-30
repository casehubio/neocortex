package io.casehub.neocortex.cognition.prompt;

import io.casehub.neocortex.cognition.drive.DriveOrchestrator;
import io.casehub.neocortex.cognition.prompt.observation.AffordanceRenderer;
import io.casehub.neocortex.cognition.prompt.observation.CognitiveObservationSections;
import org.jspecify.annotations.Nullable;

import java.util.List;

public class DrivePromptSection implements CognitionPromptRenderer {

    private final DriveOrchestrator drives;

    public DrivePromptSection(DriveOrchestrator drives) {
        this.drives = drives;
    }

    @Override
    public @Nullable String render(CognitionRenderContext context) {
        return drives.currentDrives(context.agentId(), context.tenantId())
                .map(profile -> {
                    var section = CognitiveObservationSections.motivationalStateSection(profile);
                    return new AffordanceRenderer().renderObservation(List.of(section));
                })
                .filter(s -> !s.isBlank())
                .orElse(null);
    }
}
