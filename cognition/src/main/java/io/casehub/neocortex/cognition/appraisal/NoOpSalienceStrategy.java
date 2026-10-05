package io.casehub.neocortex.cognition.appraisal;

public class NoOpSalienceStrategy implements SalienceStrategy {
    @Override
    public PerceivedSituation perceive(SalienceContext context) {
        return PerceivedSituation.passThrough(context.observation());
    }
}
