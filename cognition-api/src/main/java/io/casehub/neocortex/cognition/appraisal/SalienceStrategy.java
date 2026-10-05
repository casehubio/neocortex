package io.casehub.neocortex.cognition.appraisal;

@FunctionalInterface
public interface SalienceStrategy {
    PerceivedSituation perceive(SalienceContext context);
}
