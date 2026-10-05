package io.casehub.neocortex.cognition.appraisal;

@FunctionalInterface
public interface SecCheck {
    SecResult evaluate(AppraisalContext context);
}
