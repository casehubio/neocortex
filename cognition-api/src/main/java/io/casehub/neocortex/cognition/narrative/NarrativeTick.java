package io.casehub.neocortex.cognition.narrative;

import org.jspecify.annotations.Nullable;

import java.util.List;

public sealed interface NarrativeTick {
    record NoChange(@Nullable String reason) implements NarrativeTick {}

    record Updated(@Nullable NarrativeState previous, NarrativeState current,
                   List<String> newEpisodeIds,
                   List<String> newThemeLabels) implements NarrativeTick {}
}
