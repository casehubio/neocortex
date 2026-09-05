package io.casehub.neocortex.memory.cbr;

import io.casehub.neocortex.memory.EraseRequest;
import io.casehub.neocortex.memory.MemoryDomain;
import java.util.List;

public interface CbrCaseMemoryStore {

    void registerSchema(CbrFeatureSchema schema);

    String store(CbrCase cbrCase, String caseType, String entityId, MemoryDomain domain,
                 String tenantId, String caseId, io.casehub.platform.api.path.Path scope);

    default String store(CbrCase cbrCase, String caseType, io.casehub.neocortex.memory.Subject subject, MemoryDomain domain,
                         String tenantId, String caseId, io.casehub.platform.api.path.Path scope,
                         String principalId, java.util.Set<String> sharedWith) {
        return store(cbrCase, caseType, subject.id(), domain, tenantId, caseId, scope);
    }


    <C extends CbrCase> List<ScoredCbrCase<C>> retrieveSimilar(CbrQuery query, Class<C> caseType);

    Integer erase(EraseRequest request);

    Integer eraseEntity(String entityId, String tenantId);

    default Integer eraseSubject(io.casehub.neocortex.memory.Subject subject, String tenantId) {
        return eraseEntity(subject.id(), tenantId);
    }


    Integer eraseByScope(io.casehub.platform.api.path.Path scope, String tenantId);


    void recordOutcome(String caseId, String tenantId, CbrOutcome outcome);

    Integer purge(CbrRetentionPolicy policy);

    void supersede(String caseId, String tenantId, String supersedingCaseId, String reason);

    void reinstate(String caseId, String tenantId);

    SupersessionStatus getSupersessionStatus(String caseId, String tenantId);

    List<SupersessionStatus> findSupersededCases(String tenantId, MemoryDomain domain);


    default java.util.Set<String> discoverTenants(MemoryDomain domain) {
        throw new UnsupportedOperationException(
                "discoverTenants not supported by " + getClass().getSimpleName());
    }

    default CbrScanResult scan(CbrScanRequest request) {
        throw new UnsupportedOperationException(
                "scan not supported by " + getClass().getSimpleName());
    }
}
