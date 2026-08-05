# io.casehub.neocortex.memory.CaseMemoryStore

**Package:** `io.casehub.neocortex.memory`

**Kind:** `interface`

## Methods

### `public default java.util.Set<io.casehub.neocortex.memory.MemoryCapability> capabilities()`

Returns the set of capabilities this adapter declares.
Callers should check capabilities before invoking optional operations.
The returned set is immutable.

### `public default java.util.Set<java.lang.String> discoverTenants(java.lang.String attributeKey, java.lang.String attributeValue)`

Returns distinct tenantIds matching the given attribute filter.
Both null → all tenants. Both non-null → filtered. Mixed → IllegalArgumentException.

<p>Cross-tenant admin operation. Implementations MUST call
`MemoryPermissions.assertCrossTenantAdmin` before executing.

#### Parameters

- `attributeKey` (`java.lang.String`)
- `attributeValue` (`java.lang.String`)

#### Returns

a non-null, possibly empty, unmodifiable set of tenant identifiers

### `public abstract int erase(io.casehub.neocortex.memory.EraseRequest request)`

Erase memories matching the request. Domain is required — use `.eraseEntity`
for GDPR Art.17 cross-domain full-entity wipe.

<p>Adapters MUST perform hard deletion.
Adapters MUST call `MemoryPermissions.assertTenant` before delegating to the backend.

<p>Adapters that do not declare `MemoryCapability.ERASE_DOMAIN_CASE` will throw
`MemoryCapabilityException`. Check `.capabilities()` before calling on
adapters that may not support domain+caseId scoped deletion.

#### Parameters

- `request` (`io.casehub.neocortex.memory.EraseRequest`)

#### Returns

count of memory records erased (for GDPR Art.5(2) audit logging)

### `public default void eraseById(java.lang.String memoryId, java.lang.String entityId, java.lang.String tenantId)`

Erase a specific memory by its assigned memoryId.

<p>The memory must belong to `entityId` within `tenantId`. If the memory
does not exist, or belongs to a different entity within the same tenant, the method
returns silently — no information is revealed about whether the memory exists
under a different entity (silent no-op, GDPR satisfied).

<p>Default throws `MemoryCapabilityException` with `MemoryCapability.ERASE_BY_ID`.
`NoOpCaseMemoryStore` overrides with a true no-op (nothing stored). Real adapters
override with actual deletion.
Adapters MUST call `MemoryPermissions.assertTenant` before delegating to the backend.

#### Parameters

- `memoryId` (`java.lang.String`) — the ID assigned by the store at write time
- `entityId` (`java.lang.String`) — the entity the memory must belong to; mismatch = silent no-op
- `tenantId` (`java.lang.String`) — the tenant the caller is authenticated for

### `public default int eraseEntity(java.lang.String entityId, java.lang.String tenantId)`

GDPR Art.17 full-entity wipe across ALL domains for this entity within the tenant.

<p>Adapters MUST perform hard deletion across every domain.
Adapters MUST call `MemoryPermissions.assertTenant` before delegating to the backend.

<p>Default throws `MemoryCapabilityException` with `MemoryCapability.ERASE_ENTITY`.
`NoOpCaseMemoryStore` overrides with `return 0` (nothing stored → erasure
trivially satisfied). Real adapters must override with actual cross-domain deletion
and return the count of records deleted.

<p>For REST-backed adapters (Mem0, Graphiti) where a precise count requires a pre-fetch,
the count is a best-effort estimate — document the race or cap in the adapter's Javadoc.

#### Parameters

- `entityId` (`java.lang.String`)
- `tenantId` (`java.lang.String`)

#### Returns

count of memory records erased (for GDPR Art.5(2) audit logging)

### `public default int eraseEntityAcrossTenants(java.lang.String entityId, java.util.Set<java.lang.String> tenantIds)`

GDPR Art.17 full-entity wipe across all supplied tenantIds.
Caller must be a cross-tenant admin. Supply the complete set of tenantIds
for the data subject from the tenant management system.

<p>Adapters MUST call `MemoryPermissions.assertCrossTenantAdmin` before
delegating to the backend. Do NOT call eraseEntity() internally — assertTenant()
rejects cross-tenant access. Implement deletion directly against the backend.

<p>Default throws `MemoryCapabilityException`. `NoOpCaseMemoryStore`
overrides with `return 0` but does NOT declare
`MemoryCapability.CROSS_TENANT_ERASE` in capabilities().

#### Parameters

- `entityId` (`java.lang.String`)
- `tenantIds` (`java.util.Set<java.lang.String>`) — the set of tenantIds to erase from; caller supplies from tenant management.
                 Set semantics enforced at the type level — duplicates are impossible.

#### Returns

total count of records erased across all tenantIds (best-effort for REST adapters)

### `public default int purge(io.casehub.neocortex.memory.MemoryRetentionPolicy policy)`

#### Parameters

- `policy` (`io.casehub.neocortex.memory.MemoryRetentionPolicy`)

### `public abstract java.util.List<io.casehub.neocortex.memory.Memory> query(io.casehub.neocortex.memory.MemoryQuery query)`

Recall memories relevant to a query context.

<p>Domain isolation is strict equality — only memories tagged with `query.domain()`
are returned. Non-semantic adapters ignore `question` and return
entity+domain+tenant+caseId-scoped results ordered by `createdAt` descending.
Returns an empty list when no adapter is installed.

#### Parameters

- `query` (`io.casehub.neocortex.memory.MemoryQuery`)

### `public default void requireCapability(io.casehub.neocortex.memory.MemoryCapability capability)`

Asserts this adapter supports the given capability.

#### Parameters

- `capability` (`io.casehub.neocortex.memory.MemoryCapability`)

#### Throws

- `MemoryCapabilityException` — if the capability is not in `.capabilities()`

### `public default java.util.List<io.casehub.neocortex.memory.Memory> scan(io.casehub.neocortex.memory.MemoryScanRequest request)`

Paginated scan of memories for admin/debug scenarios. Returns up to `request.limit()`
memories matching the filters in `request`. Use `request.afterMemoryId()` for
pagination.

<p>Default throws `MemoryCapabilityException` with `MemoryCapability.SCAN`.

<p>Adapters MUST call `MemoryPermissions.assertTenant` before delegating to the backend.

#### Parameters

- `request` (`io.casehub.neocortex.memory.MemoryScanRequest`) — scan request with tenant, domain, attribute filter, limit, and cursor

#### Returns

list of memories matching the request, ordered by memoryId; empty list if none match

### `public abstract java.lang.String store(io.casehub.neocortex.memory.MemoryInput input)`

Store a memory about an entity. Returns the assigned memoryId.

<p>Append-only at the SPI level. The no-op returns `""`.
Adapters MUST call `MemoryPermissions.assertTenant` before delegating to the backend.

<p><b>Emission pattern:</b> inject `CaseMemoryStore` directly and call
`store()` from your domain event handler. This is the canonical pattern —
direct injection keeps exception propagation intact (`SecurityException` from
`assertTenant()` reaches the caller), keeps request context active for
`@RequestScoped` implementations, and is consistent with the read API
(`.query(MemoryQuery)`).

<p><b>`@ObservesAsync` callers are supported.</b> Adapters use the
async-aware 3-arg `MemoryPermissions.assertTenant(tenantId, principal,
requestContextActive())` form, which trusts `MemoryInput.tenantId()`
directly when no CDI request scope is active. The data-scoping by
`tenantId` is unconditional; only the principal comparison is skipped
in async context.

<p><b>`@Observes` (synchronous) is still valid</b> — it preserves request
scope and propagates exceptions normally. A synchronous CDI observer that calls
`store()` directly keeps the store write atomic with the event-firing
transaction — desirable for compliance writes that must not persist if the
enclosing operation rolls back, but wrong if fire-and-forget is expected.

<p><b>Batch jobs and startup contexts</b> — the 3-arg `assertTenant` form
handles these too. No request scope active → trust the tenantId from
`MemoryInput` directly. Explicit `@ActivateRequestContext` is not
required for memory writes from batch or startup code.

<p><b>Fire-and-forget:</b> for CDI observers (`@ObservesAsync`) and other
contexts where backend failures must not propagate, inject
MemoryEmitter instead — it wraps
this store with error isolation and structured logging. `SecurityException`
from tenant assertion still propagates through `MemoryEmitter`.

<p><b>Text field guidance:</b> `MemoryInput.text()` must be human-readable
natural language when using semantic adapters (Mem0, Graphiti) — it is the field
embedded for vector search. Use `MemoryInput.attributes()` for structured
metadata. See `MemoryAttributeKeys` for reserved cross-domain attribute keys.

#### Parameters

- `input` (`io.casehub.neocortex.memory.MemoryInput`)

### `public default io.casehub.neocortex.memory.StoreAllResult storeAll(java.util.List<io.casehub.neocortex.memory.MemoryInput> inputs)`

Convenience bulk store. Returns a `StoreAllResult` carrying the IDs of
successfully stored inputs and any backend failures.

<p><strong>Security exceptions always propagate immediately</strong> — a
`SecurityException` thrown by any tenant-check aborts the call and no
`StoreAllResult` is returned.

<p>Adapters that override this method MUST: (1) call
`MemoryPermissions.assertTenant` for every input; (2) return stored IDs in
input order in `StoreAllResult.stored()`; (3) ensure no items are durably
written if any tenant check fails — via pre-flight for REST-backed adapters, or
single-transaction rollback for JDBC-backed adapters.

<p>The default implementation iterates sequentially. It collects backend failures
in the result but re-throws `SecurityException` immediately. It is not safe
for mixed-tenant batches where partial-write prevention is required — override in
production adapters.

#### Parameters

- `inputs` (`java.util.List<io.casehub.neocortex.memory.MemoryInput>`)
