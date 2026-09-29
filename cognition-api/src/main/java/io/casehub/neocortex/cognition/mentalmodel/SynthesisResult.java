package io.casehub.neocortex.cognition.mentalmodel;

import org.jspecify.annotations.Nullable;

record SynthesisResult(
        @Nullable String communicationStyle,
        @Nullable String topicsOfInterest,
        @Nullable String preferences,
        @Nullable String synthesisNotes) {}
