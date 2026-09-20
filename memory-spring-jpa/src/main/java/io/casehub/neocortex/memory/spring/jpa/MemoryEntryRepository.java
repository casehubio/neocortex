package io.casehub.neocortex.memory.spring.jpa;

import io.casehub.neocortex.memory.jpa.MemoryEntry;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface MemoryEntryRepository extends JpaRepository<MemoryEntry, String> {

    @Modifying
    @Query("DELETE FROM MemoryEntry e WHERE e.tenantId = ?1 AND e.entityId = ?2 AND e.domain = ?3")
    int eraseByDomainAndEntity(String tenantId, String entityId, String domain);

    @Modifying
    @Query("DELETE FROM MemoryEntry e WHERE e.tenantId = ?1 AND e.entityId = ?2 AND e.domain = ?3 AND e.caseId = ?4")
    int eraseByDomainEntityCase(String tenantId, String entityId, String domain, String caseId);

    @Modifying
    @Query("DELETE FROM MemoryEntry e WHERE e.memoryId = ?1 AND e.entityId = ?2 AND e.tenantId = ?3")
    int eraseById(String memoryId, String entityId, String tenantId);

    @Modifying
    @Query("DELETE FROM MemoryEntry e WHERE e.tenantId = ?1 AND e.entityId = ?2")
    int eraseSubject(String tenantId, String entityId);

    @Modifying
    @Query("DELETE FROM MemoryEntry e WHERE e.entityId = ?1 AND e.tenantId IN ?2")
    int eraseSubjectAcrossTenants(String entityId, List<String> tenantIds);
}
