package io.casehub.neocortex.memory.cbr;

import io.casehub.neocortex.memory.MemoryDomain;
import java.util.List;
import java.util.Map;

public interface CbrRecordRetrieval {
    <C extends CbrRecord> List<CbrMatch<C>> retrieveSimilar(CbrQuery query, Class<C> caseType);

    default CbrScanResult scan(CbrScanRequest request) {
        throw new UnsupportedOperationException(
                "scan not supported by " + getClass().getSimpleName());
    }

    List<String> findCaseIds(String tenantId, MemoryDomain domain,
                             String caseType, Map<String, CbrFilter> filters);
}
