package io.casehub.neocortex.memory.cbr.spring.jpa;

import io.casehub.neocortex.memory.cbr.jpa.CbrCaseEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface CbrCaseEntityRepository extends JpaRepository<CbrCaseEntity, String> {

    @Query("SELECT e FROM CbrCaseEntity e WHERE e.tenantId = ?1 AND e.domain = ?2 AND e.caseType = ?3 AND e.supersededAt IS NULL")
    List<CbrCaseEntity> findActiveCases(String tenantId, String domain, String caseType);

    @Query("SELECT e FROM CbrCaseEntity e WHERE e.tenantId = ?1 AND e.domain = ?2 AND e.supersededAt IS NULL")
    List<CbrCaseEntity> findActiveCasesAllTypes(String tenantId, String domain);

    @Query("SELECT e FROM CbrCaseEntity e WHERE e.caseId = ?1 AND e.tenantId = ?2")
    List<CbrCaseEntity> findByCaseIdAndTenantId(String caseId, String tenantId);

    @Modifying
    @Query("DELETE FROM CbrCaseEntity e WHERE e.entityId = ?1 AND e.domain = ?2 AND e.tenantId = ?3")
    int eraseByEntityDomainTenant(String entityId, String domain, String tenantId);

    @Modifying
    @Query("DELETE FROM CbrCaseEntity e WHERE e.entityId = ?1 AND e.domain = ?2 AND e.tenantId = ?3 AND e.caseId = ?4")
    int eraseByEntityDomainTenantCase(String entityId, String domain, String tenantId, String caseId);

    @Modifying
    @Query("DELETE FROM CbrCaseEntity e WHERE e.entityId = ?1 AND e.tenantId = ?2")
    int eraseEntity(String entityId, String tenantId);

    @Query("SELECT DISTINCT e.tenantId FROM CbrCaseEntity e WHERE e.domain = ?1")
    List<String> discoverTenants(String domain);

    @Query("SELECT e FROM CbrCaseEntity e WHERE e.tenantId = ?1 AND e.domain = ?2 AND e.supersededAt IS NOT NULL")
    List<CbrCaseEntity> findSuperseded(String tenantId, String domain);

    @Query("SELECT e FROM CbrCaseEntity e WHERE e.tenantId = ?1 AND e.domain = ?2 AND e.caseType = ?3 AND e.supersededAt IS NOT NULL")
    List<CbrCaseEntity> findSupersededByType(String tenantId, String domain, String caseType);

    @Query("SELECT e FROM CbrCaseEntity e WHERE e.tenantId = ?1 AND e.domain = ?2 AND e.caseType = ?3 ORDER BY e.caseId")
    List<CbrCaseEntity> findForScan(String tenantId, String domain, String caseType);

    @Query("SELECT e FROM CbrCaseEntity e WHERE e.tenantId = ?1 AND e.domain = ?2 AND e.caseType = ?3 AND e.caseId > ?4 ORDER BY e.caseId")
    List<CbrCaseEntity> findForScanAfterCursor(String tenantId, String domain, String caseType, String cursor);
}
