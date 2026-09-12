package io.casehub.neocortex.cognitive.index;

import io.casehub.platform.api.identity.PrincipalId;

import java.util.Objects;

public record AffectSnapshot(
        PrincipalId agent,
        Double pleasure, Double arousal, Double dominance,
        AffectTrajectory trajectory
) {
    public AffectSnapshot {Objects.requireNonNull(agent);}
}