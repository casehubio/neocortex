package io.casehub.neocortex.cognition.prompt.observation;

import io.casehub.neocortex.cognition.drive.DriveAxis;
import io.casehub.neocortex.cognition.drive.DriveProfile;
import io.casehub.neocortex.cognition.narrative.DerivedTheme;
import io.casehub.neocortex.cognition.narrative.NarrativeState;

import java.util.ArrayList;
import java.util.List;

public final class CognitiveObservationSections {

    private CognitiveObservationSections() {}

    public static ObservationSection motivationalStateSection(DriveProfile profile) {
        var items = new ArrayList<String>();
        for (var axis : DriveAxis.values()) {
            var intensity = profile.drives().get(axis);
            if (intensity != null && intensity.intensity() >= 0.05) {
                String name = axis.name().charAt(0) + axis.name().substring(1).toLowerCase();
                items.add(String.format("%s: %.1f — %s", name, intensity.intensity(), intensity.trigger()));
            }
        }
        if (items.isEmpty()) {
            return ObservationSection.items("Motivational State", "No active drives.", List.of());
        }
        return ObservationSection.items("Motivational State", null, items);
    }

    public static ObservationSection narrativeSection(NarrativeState state) {
        var items    = new ArrayList<String>();
        var dominant = state.dominantTheme();
        if (dominant != null) {
            items.add("Core identity: " + dominant.label()
                      + " (salience: " + String.format("%.1f", dominant.salience()) + ")");
        }
        for (var episode : state.episodes()) {
            if (episode.emotionalValence() > 0.3 || episode.emotionalValence() < -0.3) {
                items.add("Memory: " + episode.description());
            }
        }
        for (var theme : state.themes()) {
            if (!theme.equals(dominant) && theme.salience() >= 0.3) {
                items.add("Theme: " + theme.label());
            }
        }
        if (items.isEmpty()) {
            return ObservationSection.items("Self-Narrative", "No established identity yet.", List.of());
        }
        return ObservationSection.items("Self-Narrative", null, items);
    }
}
