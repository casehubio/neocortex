package io.casehub.neocortex.memory.cbr;

import io.casehub.neocortex.memory.EraseRequest;
import io.casehub.neocortex.memory.MemoryDomain;
import io.casehub.platform.api.path.Path;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;

public abstract class DelegatingCbrRecordStore implements CbrRecordStore {

    protected final CbrRecordStore delegate;

    protected DelegatingCbrRecordStore(CbrRecordStore delegate) {
        this.delegate = delegate;
    }

    @Override public void registerSchema(CbrRecordSchema schema)                                                     { delegate.registerSchema(schema); }
    @Override public String store(CbrRecord c, String ct, String e, MemoryDomain d, String t, String ci, Path scope) { return delegate.store(c, ct, e, d, t, ci, scope); }
    @Override public <C extends CbrRecord> List<CbrMatch<C>> retrieveSimilar(CbrQuery q, Class<C> ct)                { return delegate.retrieveSimilar(q, ct); }
    @Override public int erase(EraseRequest request)                                                             { return delegate.erase(request); }
    @Override public int eraseEntity(String entityId, String tenantId) { return delegate.eraseEntity(entityId, tenantId); }
    @Override public int eraseByScope(Path scope, String tenantId)                          { return delegate.eraseByScope(scope, tenantId); }
    @Override public void recordOutcome(String caseId, CbrOutcome outcome, String tenantId) { delegate.recordOutcome(caseId, outcome, tenantId); }
    @Override public int purge(CbrRetentionPolicy policy)                                   { return delegate.purge(policy); }
    @Override public boolean supersede(String caseId, String supersedingCaseId, String reason, String tenantId) { return delegate.supersede(caseId, supersedingCaseId, reason, tenantId); }
    @Override public boolean reinstate(String caseId, String tenantId)                                          { return delegate.reinstate(caseId, tenantId); }
    @Override public SupersessionStatus getSupersessionStatus(String caseId, String tenantId)           { return delegate.getSupersessionStatus(caseId, tenantId); }
    @Override public List<SupersessionStatus> findSupersededCases(MemoryDomain domain, String tenantId) { return delegate.findSupersededCases(domain, tenantId); }
    @Override public Set<String> discoverTenants(MemoryDomain domain)                                   { return delegate.discoverTenants(domain); }
    @Override public CbrScanResult scan(CbrScanRequest request)                                                                                  { return delegate.scan(request); }
    @Override public List<String> findCaseIds(MemoryDomain domain, String caseType, Map<String, CbrFilter> filters, String tenantId)             { return delegate.findCaseIds(domain, caseType, filters, tenantId); }
    @Override public int supersedeMatching(MemoryDomain domain, String caseType, Map<String, CbrFilter> filters, String reason, String tenantId) { return delegate.supersedeMatching(domain, caseType, filters, reason, tenantId); }
    @Override public int supersedeAll(Collection<String> caseIds, String reason, String tenantId)                                                { return delegate.supersedeAll(caseIds, reason, tenantId); }
    @Override public int reinstateMatching(MemoryDomain domain, String caseType, Map<String, CbrFilter> filters, String tenantId)                { return delegate.reinstateMatching(domain, caseType, filters, tenantId); }
    @Override public int reinstateAll(Collection<String> caseIds, String tenantId)                                                { return delegate.reinstateAll(caseIds, tenantId); }
}
