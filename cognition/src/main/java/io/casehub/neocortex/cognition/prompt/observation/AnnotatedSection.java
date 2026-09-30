package io.casehub.neocortex.cognition.prompt.observation;

import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.Map;

public record AnnotatedSection(
        ObservationSection section,
        List<String> requiredTags,
        Map<ResolutionTier, ObservationSection> resolutions,
        @Nullable String interpretiveFrame
) implements ObservationSection {
    public AnnotatedSection {
        if (section == null) throw new IllegalArgumentException("section required");
        requiredTags = List.copyOf(requiredTags);
        resolutions = Map.copyOf(resolutions);
    }

    @Override
    public String header() {
        return section.header();
    }
}
