package io.casehub.neocortex.memory;

import java.util.List;
import java.util.Set;

public interface CaseMemoryStore {

    /**
     * Store a memory about an entity. Returns the assigned memoryId.
     *
     * <p>Append-only at the SPI level. The no-op returns {@code ""}.
     * Adapters MUST call {@link MemoryPermissions#assertTenant} before delegating to the backend.
     *
     * <p><b>Emission pattern:</b> inject {@code CaseMemoryStore} directly and call
     * {@code store()} from your domain event handler. This is the canonical pattern —
     * direct injection keeps exception propagation intact ({@link SecurityException} from
     * {@code assertTenant()} reaches the caller), keeps request context active for
     * {@code @RequestScoped} implementations, and is consistent with the read API
     * ({@link #query(MemoryQuery)}).
     *
     * <p><b>{@code @ObservesAsync} callers are supported.</b> Adapters use the
     * async-aware 3-arg {@code MemoryPermissions.assertTenant(tenantId, principal,
     * requestContextActive())} form, which trusts {@code MemoryInput.tenantId()}
     * directly when no CDI request scope is active. The data-scoping by
     * {@code tenantId} is unconditional; only the principal comparison is skipped
     * in async context.
     *
     * <p><b>{@code @Observes} (synchronous) is still valid</b> — it preserves request
     * scope and propagates exceptions normally. A synchronous CDI observer that calls
     * {@code store()} directly keeps the store write atomic with the event-firing
     * transaction — desirable for compliance writes that must not persist if the
     * enclosing operation rolls back, but wrong if fire-and-forget is expected.
     *
     * <p><b>Batch jobs and startup contexts</b> — the 3-arg {@code assertTenant} form
     * handles these too. No request scope active → trust the tenantId from
     * {@code MemoryInput} directly. Explicit {@code @ActivateRequestContext} is not
     * required for memory writes from batch or startup code.
     *
     * <p><b>Fire-and-forget:</b> for CDI observers ({@code @ObservesAsync}) and other
     * contexts where backend failures must not propagate, inject
     * {@link io.casehub.memory.runtime.MemoryEmitter MemoryEmitter} instead — it wraps
     * this store with error isolation and structured logging. {@link SecurityException}
     * from tenant assertion still propagates through {@code MemoryEmitter}.
     *
     * <p><b>Text field guidance:</b> {@link MemoryInput#text()} must be human-readable
     * natural language when using semantic adapters (Mem0, Graphiti) — it is the field
     * embedded for vector search. Use {@link MemoryInput#attributes()} for structured
     * metadata. See {@link MemoryAttributeKeys} for reserved cross-domain attribute keys.
     */
    String store(MemoryInput input);

    /**
     * Recall memories relevant to a query context.
     *
     * <p>Domain isolation is strict equality — only memories tagged with {@code query.domain()}
     * are returned. Non-semantic adapters ignore {@code question} and return
     * entity+domain+tenant+caseId-scoped results ordered by {@code createdAt} descending.
     * Returns an empty list when no adapter is installed.
     */
    List<Memory> query(MemoryQuery query);

    /**
     * Erase memories matching the request. Domain is required — use {@link #eraseEntity}
     * for GDPR Art.17 cross-domain full-entity wipe.
     *
     * <p>Adapters MUST perform hard deletion.
     * Adapters MUST call {@link MemoryPermissions#assertTenant} before delegating to the backend.
     *
     * <p>Adapters that do not declare {@link MemoryCapability#ERASE_DOMAIN_CASE} will throw
     * {@link MemoryCapabilityException}. Check {@link #capabilities()} before calling on
     * adapters that may not support domain+caseId scoped deletion.
     *
     * @return count of memory records erased (for GDPR Art.5(2) audit logging)
     */
    int erase(EraseRequest request);


    default int eraseSubject(Subject subject, String tenantId) {
        return eraseEntity(subject.id(), tenantId);
    }

    @Deprecated(forRemoval = true)
    default int eraseEntity(String entityId, String tenantId) {
        throw new MemoryCapabilityException(MemoryCapability.ERASE_ENTITY, getClass());
    }


    default void eraseById(String memoryId, Subject subject, String tenantId) {
        eraseById(memoryId, subject.id(), tenantId);
    }

    @Deprecated(forRemoval = true)
    default void eraseById(String memoryId, String entityId, String tenantId) {
        throw new MemoryCapabilityException(MemoryCapability.ERASE_BY_ID, getClass());
    }


    default int eraseSubjectAcrossTenants(Subject subject, Set<String> tenantIds) {
        return eraseEntityAcrossTenants(subject.id(), tenantIds);
    }

    @Deprecated(forRemoval = true)
    default int eraseEntityAcrossTenants(String entityId, Set<String> tenantIds) {
        throw new MemoryCapabilityException(MemoryCapability.CROSS_TENANT_ERASE, getClass());
    }

    /**
     * Returns the set of capabilities this adapter declares.
     * Callers should check capabilities before invoking optional operations.
     * The returned set is immutable.
     */
    default Set<MemoryCapability> capabilities() {
        return Set.of();
    }

    /**
     * Asserts this adapter supports the given capability.
     *
     * @throws MemoryCapabilityException if the capability is not in {@link #capabilities()}
     */
    default void requireCapability(final MemoryCapability capability) {
        if (!capabilities().contains(capability))
            throw new MemoryCapabilityException(capability, getClass());
    }

    /**
     * Paginated scan of memories for admin/debug scenarios. Returns up to {@code request.limit()}
     * memories matching the filters in {@code request}. Use {@code request.afterMemoryId()} for
     * pagination.
     *
     * <p>Default throws {@link MemoryCapabilityException} with {@link MemoryCapability#SCAN}.
     *
     * <p>Adapters MUST call {@link MemoryPermissions#assertTenant} before delegating to the backend.
     *
     * @param request scan request with tenant, domain, attribute filter, limit, and cursor
     * @return list of memories matching the request, ordered by memoryId; empty list if none match
     */
    default List<Memory> scan(MemoryScanRequest request) {
        throw new MemoryCapabilityException(MemoryCapability.SCAN, getClass());
    }


    default int purge(MemoryRetentionPolicy policy) {
        throw new MemoryCapabilityException(MemoryCapability.PURGE, getClass());
    }

    /**
     * Returns distinct tenantIds matching the given attribute filter.
     * Both null → all tenants. Both non-null → filtered. Mixed → IllegalArgumentException.
     *
     * <p>Cross-tenant admin operation. Implementations MUST call
     * {@link MemoryPermissions#assertCrossTenantAdmin} before executing.
     *
     * @return a non-null, possibly empty, unmodifiable set of tenant identifiers
     */
    default Set<String> discoverTenants(String attributeKey, String attributeValue) {
        if ((attributeKey == null) != (attributeValue == null)) {
            throw new IllegalArgumentException(
                "attributeKey and attributeValue must both be null or both be non-null");
        }
        throw new MemoryCapabilityException(MemoryCapability.DISCOVER_TENANTS, getClass());
    }

    /**
     * Convenience bulk store. Returns a {@link StoreAllResult} carrying the IDs of
     * successfully stored inputs and any backend failures.
     *
     * <p><strong>Security exceptions always propagate immediately</strong> — a
     * {@link SecurityException} thrown by any tenant-check aborts the call and no
     * {@link StoreAllResult} is returned.
     *
     * <p>Adapters that override this method MUST: (1) call
     * {@link MemoryPermissions#assertTenant} for every input; (2) return stored IDs in
     * input order in {@link StoreAllResult#stored()}; (3) ensure no items are durably
     * written if any tenant check fails — via pre-flight for REST-backed adapters, or
     * single-transaction rollback for JDBC-backed adapters.
     *
     * <p>The default implementation iterates sequentially. It collects backend failures
     * in the result but re-throws {@link SecurityException} immediately. It is not safe
     * for mixed-tenant batches where partial-write prevention is required — override in
     * production adapters.
     */
    default StoreAllResult storeAll(List<MemoryInput> inputs) {
        if (inputs.isEmpty()) return StoreAllResult.empty();
        var stored = new java.util.ArrayList<String>();
        var failures = new java.util.ArrayList<StoreFailure>();
        for (int i = 0; i < inputs.size(); i++) {
            try {
                stored.add(store(inputs.get(i)));
            } catch (SecurityException e) {
                throw e; // always propagate — not a backend failure
            } catch (RuntimeException e) {
                failures.add(new StoreFailure(i, inputs.get(i), e));
            }
        }
        return new StoreAllResult(stored, failures);
    }

}
