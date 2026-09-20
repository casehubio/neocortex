package io.casehub.neocortex.memory.cbr;

import io.casehub.neocortex.memory.EraseRequest;
import io.casehub.neocortex.memory.MemoryDomain;
import io.casehub.neocortex.memory.Subject;
import io.casehub.platform.api.identity.PrincipalId;
import io.casehub.platform.api.path.Path;
import java.util.Set;

public interface CbrRecordOps {
    void registerSchema(CbrRecordSchema schema);

    String store(CbrRecord cbrRecord, String caseType, String entityId,
                 MemoryDomain domain, String tenantId, String caseId, Path scope);

    default String store(CbrRecord cbrRecord, String caseType, Subject subject,
                         MemoryDomain domain, String tenantId, String caseId,
                         Path scope, PrincipalId principalId, Set<String> sharedWith) {
        return store(cbrRecord, caseType, subject.id(), domain, tenantId, caseId, scope);
    }

    Integer erase(EraseRequest request);
    Integer eraseEntity(String entityId, String tenantId);

    default Integer eraseSubject(Subject subject, String tenantId) {
        return eraseEntity(subject.id(), tenantId);
    }

    Integer eraseByScope(Path scope, String tenantId);
}
