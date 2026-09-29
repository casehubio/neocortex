package io.casehub.neocortex.cognition.innerlife;

@FunctionalInterface
public interface CivilityConstraint {
    CivilityCheck permitInitiation(InitiationContext context);
}
