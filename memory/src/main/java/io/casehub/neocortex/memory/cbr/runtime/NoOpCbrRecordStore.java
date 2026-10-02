package io.casehub.neocortex.memory.cbr.runtime;

import io.casehub.neocortex.memory.EraseRequest;
import io.casehub.neocortex.memory.MemoryDomain;
import io.casehub.neocortex.memory.cbr.CbrMatch;
import io.casehub.neocortex.memory.cbr.CbrRecord;
import io.casehub.neocortex.memory.cbr.CbrRecordStore;
import io.casehub.neocortex.memory.cbr.SupersessionStatus;
import io.casehub.neocortex.memory.cbr.CbrRecordSchema;
import io.casehub.neocortex.memory.cbr.CbrOutcome;
import io.casehub.neocortex.memory.cbr.CbrQuery;
import io.casehub.neocortex.memory.cbr.CbrRetentionPolicy;
import io.quarkus.arc.DefaultBean;
import jakarta.enterprise.context.ApplicationScoped;

import io.casehub.neocortex.memory.cbr.CbrFilter;

import java.util.Collection;
import java.util.List;
import java.util.Map;

@DefaultBean
@ApplicationScoped
public class NoOpCbrRecordStore implements CbrRecordStore {

    @Override
    public void registerSchema(CbrRecordSchema schema) {}

    @Override
    public String store(CbrRecord cbrRecord, String caseType, String entityId, MemoryDomain domain,
                        String tenantId, String caseId, io.casehub.platform.api.path.Path scope) {
        return "";
    }

    @Override
    public <C extends CbrRecord> List<CbrMatch<C>> retrieveSimilar(CbrQuery query, Class<C> caseClass) {
        return List.of();
    }

    @Override
    public int erase(EraseRequest request) {
        return 0;
    }

    @Override
    public int eraseEntity(String entityId, String tenantId) {
        return 0;
    }

    @Override
    public int eraseByScope(io.casehub.platform.api.path.Path scope, String tenantId) {
        return 0;
    }


    @Override
    public void recordOutcome(String caseId, CbrOutcome outcome, String tenantId) {}

    @Override
    public int purge(CbrRetentionPolicy policy) {
        return 0;
    }

    @Override
    public boolean supersede(String caseId, String supersedingCaseId, String reason, String tenantId) {
        return false;
    }

    @Override
    public boolean reinstate(String caseId, String tenantId) {
        return false;
    }

    @Override
    public SupersessionStatus getSupersessionStatus(String caseId, String tenantId) {
        return SupersessionStatus.NOT_SUPERSEDED;
    }

    @Override
    public java.util.List<SupersessionStatus> findSupersededCases(MemoryDomain domain, String tenantId) {
        return java.util.List.of();
    }

    @Override
    public List<String> findCaseIds(MemoryDomain domain, String caseType, Map<String, CbrFilter> filters, String tenantId) {
        return List.of();
    }

    @Override
    public int supersedeMatching(MemoryDomain domain, String caseType, Map<String, CbrFilter> filters, String reason, String tenantId) {
        return 0;
    }

    @Override
    public int supersedeAll(Collection<String> caseIds, String reason, String tenantId) {
        return 0;
    }

    @Override
    public int reinstateMatching(MemoryDomain domain, String caseType, Map<String, CbrFilter> filters, String tenantId) {
        return 0;
    }

    @Override
    public int reinstateAll(Collection<String> caseIds, String tenantId) {
        return 0;
    }

}
