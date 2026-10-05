package io.casehub.neocortex.cognition.appraisal;

@FunctionalInterface
public interface AppraisalStrategy {
    AppraisalResult appraise(AppraisalContext context);
}
