package io.casehub.memory.cbr;

import io.casehub.platform.api.memory.EraseRequest;
import io.casehub.platform.api.memory.MemoryDomain;
import java.util.List;

public interface CbrCaseMemoryStore {

    void registerSchema(CbrFeatureSchema schema);

    String store(CbrCase cbrCase, String caseType, String entityId, MemoryDomain domain,
                 String tenantId, String caseId);

    <C extends CbrCase> List<C> retrieveSimilar(CbrQuery query, Class<C> caseType);

    Integer erase(EraseRequest request);

    Integer eraseEntity(String entityId, String tenantId);
}
