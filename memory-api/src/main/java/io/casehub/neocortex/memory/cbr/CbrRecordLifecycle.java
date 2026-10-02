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

    int supersedeMatching(MemoryDomain domain, String caseType, Map<String, CbrFilter> filters, String reason, String tenantId);
    int supersedeAll(Collection<String> caseIds, String reason, String tenantId);
    int reinstateMatching(MemoryDomain domain, String caseType, Map<String, CbrFilter> filters, String tenantId);
    int reinstateAll(Collection<String> caseIds, String tenantId);
}
