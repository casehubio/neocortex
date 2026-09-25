package io.casehub.neocortex.mindmap;

import java.time.Instant;

public record CognitiveAttentionRequired(
    AttentionBriefing briefing,
    Instant firedAt
) {}
