package io.casehub.neocortex.cognition.narrative;

public sealed interface NarrativeSynthesisTick {
    record Skipped(String reason) implements NarrativeSynthesisTick {}
    record Synthesised(NarrativeState state,
                       int newReflectionsConsumed) implements NarrativeSynthesisTick {}
}
