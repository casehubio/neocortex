package io.casehub.neocortex.memory.cbr;

import io.casehub.neocortex.memory.MemoryDomain;
import java.util.Collection;
import java.util.List;
import java.util.Map;

public interface CbrRecordLifecycle {
    boolean supersede(String caseId, String supersedingCaseId, String reason, String tenantId);
    boolean reinstate(String caseId, String tenantId);
    SupersessionStatus getSupersessionStatus(String caseId, String tenantId);
    List<SupersessionStatus> findSupersededCases(MemoryDomain domain, String tenantId);
    void recordOutcome(String caseId, CbrOutcome outcome, String tenantId);

    /**
     * Records an outcome with an optional learning rate override. Per Cattell's (1971)
     * Investment Theory, {@code workingMemorySpan} (Engle &amp; Kane 2004) modulates the EMA
     * learning rate &mdash; higher working memory enables better evaluation of multi-factor
     * outcomes, accelerating confidence convergence.
     *
     * @param learningRateOverride when non-null, replaces the schema's learningRate
     *                             for this outcome's EMA confidence adjustment
     */
    default void recordOutcome(String caseId, CbrOutcome outcome,
                                String tenantId, Double learningRateOverride) {
        recordOutcome(caseId, outcome, tenantId);
    }

    int supersedeMatching(MemoryDomain domain, String caseType, Map<String, CbrFilter> filters, String reason, String tenantId);
    int supersedeAll(Collection<String> caseIds, String reason, String tenantId);
    int reinstateMatching(MemoryDomain domain, String caseType, Map<String, CbrFilter> filters, String tenantId);
    int reinstateAll(Collection<String> caseIds, String tenantId);
}
