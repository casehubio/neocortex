package io.casehub.neocortex.cognition.appraisal;

public class NoOpAppraisalStrategy implements AppraisalStrategy {
    @Override
    public AppraisalResult appraise(AppraisalContext context) {
        return AppraisalResult.empty();
    }
}
