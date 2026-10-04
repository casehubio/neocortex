package io.casehub.neocortex.caps;

public record NodeState(
    double activationThreshold,
    double restingActivation
) {
    public static final NodeState DEFAULT = new NodeState(0.5, 0.0);
}
