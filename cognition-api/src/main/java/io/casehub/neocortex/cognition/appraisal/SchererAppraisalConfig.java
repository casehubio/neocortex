package io.casehub.neocortex.cognition.appraisal;

public record SchererAppraisalConfig(
        boolean relevanceEnabled,
        boolean implicationsEnabled,
        boolean copingEnabled,
        boolean normativeEnabled) {
    public static SchererAppraisalConfig allEnabled() {
        return new SchererAppraisalConfig(true, true, true, true);
    }
}
