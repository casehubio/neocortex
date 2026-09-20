package io.casehub.neocortex.memory.cbr.spring.jpa;

import io.casehub.neocortex.memory.cbr.jpa.CbrRecordEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface CbrRecordEntityRepository extends JpaRepository<CbrRecordEntity, String> {

    @Query("SELECT e FROM CbrRecordEntity e WHERE e.tenantId = ?1 AND e.domain = ?2 AND e.caseType = ?3 AND e.supersededAt IS NULL")
    List<CbrRecordEntity> findActiveCases(String tenantId, String domain, String caseType);

    @Query("SELECT e FROM CbrRecordEntity e WHERE e.tenantId = ?1 AND e.domain = ?2 AND e.supersededAt IS NULL")
    List<CbrRecordEntity> findActiveCasesAllTypes(String tenantId, String domain);

    @Query("SELECT e FROM CbrRecordEntity e WHERE e.caseId = ?1 AND e.tenantId = ?2")
    List<CbrRecordEntity> findByCaseIdAndTenantId(String caseId, String tenantId);

    @Modifying
    @Query("DELETE FROM CbrRecordEntity e WHERE e.entityId = ?1 AND e.domain = ?2 AND e.tenantId = ?3")
    int eraseByEntityDomainTenant(String entityId, String domain, String tenantId);

    @Modifying
    @Query("DELETE FROM CbrRecordEntity e WHERE e.entityId = ?1 AND e.domain = ?2 AND e.tenantId = ?3 AND e.caseId = ?4")
    int eraseByEntityDomainTenantCase(String entityId, String domain, String tenantId, String caseId);

    @Modifying
    @Query("DELETE FROM CbrRecordEntity e WHERE e.entityId = ?1 AND e.tenantId = ?2")
    int eraseEntity(String entityId, String tenantId);

    @Query("SELECT DISTINCT e.tenantId FROM CbrRecordEntity e WHERE e.domain = ?1")
    List<String> discoverTenants(String domain);

    @Query("SELECT e FROM CbrRecordEntity e WHERE e.tenantId = ?1 AND e.domain = ?2 AND e.supersededAt IS NOT NULL")
    List<CbrRecordEntity> findSuperseded(String tenantId, String domain);

    @Query("SELECT e FROM CbrRecordEntity e WHERE e.tenantId = ?1 AND e.domain = ?2 AND e.caseType = ?3 AND e.supersededAt IS NOT NULL")
    List<CbrRecordEntity> findSupersededByType(String tenantId, String domain, String caseType);

    @Query("SELECT e FROM CbrRecordEntity e WHERE e.tenantId = ?1 AND e.domain = ?2 AND e.caseType = ?3 ORDER BY e.caseId")
    List<CbrRecordEntity> findForScan(String tenantId, String domain, String caseType);

    @Query("SELECT e FROM CbrRecordEntity e WHERE e.tenantId = ?1 AND e.domain = ?2 AND e.caseType = ?3 AND e.caseId > ?4 ORDER BY e.caseId")
    List<CbrRecordEntity> findForScanAfterCursor(String tenantId, String domain, String caseType, String cursor);
}
