# io.casehub.neocortex.memory.MemoryPermissions

**Package:** `io.casehub.neocortex.memory`

**Kind:** `class`

## Constructors

### `private MemoryPermissions()`

## Methods

### `public static void assertCrossTenantAdmin(CurrentPrincipal principal)`

Requires cross-tenant admin privilege. No async bypass form — this check must always
enforce. Cross-tenant GDPR erasure is a deliberate administrative operation never
initiated from @ObservesAsync context; unconditional enforcement is correct and required.
Capturing the principal before entering any reactive pipeline is the caller's
responsibility, as with all @RequestScoped beans.

#### Parameters

- `principal` (`CurrentPrincipal`)

### `public static void assertTenant(java.lang.String tenantId, CurrentPrincipal principal)`

#### Parameters

- `tenantId` (`java.lang.String`)
- `principal` (`CurrentPrincipal`)

### `public static void assertTenant(java.lang.String tenantId, CurrentPrincipal principal, boolean requestContextActive)`

Async-safe form. When `requestContextActive=false` (e.g. in an
`@ObservesAsync` handler thread), trusts `tenantId` directly —
the caller is application code running after an authenticated event fire,
not an external actor. When `requestContextActive=true`, delegates
to CurrentPrincipal).

<p>Canonical adapter implementation:
<pre>`private boolean requestContextActive() {
    var c = Arc.container();
    return c == null || c.requestContext().isActive();`
}</pre>
Returns `true` when (a) no CDI container (plain unit test — enforce),
or (b) CDI present and request context active. Returns `false` only
when CDI is present but request context is inactive — the `@ObservesAsync`
condition. All `@QuarkusTest` adapter test classes must be annotated
`@ActivateRequestContext` so this returns `true` during test execution.

#### Parameters

- `tenantId` (`java.lang.String`)
- `principal` (`CurrentPrincipal`)
- `requestContextActive` (`boolean`)
