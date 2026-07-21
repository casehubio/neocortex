# CloudEvent Outcome Wiring — Design Spec

**Issue:** casehubio/neocortex#142
**Date:** 2026-07-21

## Problem

`CbrOutcomeConsumer` exists in the `memory/` module and converts
`CbrOutcomeData` → `CbrOutcome` → `store.recordOutcome()`. It has a public
`onCbrOutcome(CbrOutcomeData)` method but no wiring to the platform's
CloudEvent dispatch infrastructure. The feedback loop from desired-state
outcome events to CBR confidence adjustment is not connected.

## Solution

Add a `@ObservesAsync @CloudEventType(CbrEventTypes.CBR_OUTCOME)` observer
method to `CbrOutcomeConsumer`. The platform's `CloudEventTypeDispatcher`
(platform#174) re-fires incoming CloudEvents with the `@CloudEventType`
qualifier, so consumers receive only events matching their declared type.

`CbrEventTypes.CBR_OUTCOME` (`"io.casehub.cbr.outcome"`) is defined in
`casehub-desiredstate-api`, already a compile dependency of `memory/`.

**Erratum:** The approved CBR design spec (`2026-07-13-cbr-revise-spi-design.md`
§CloudEvent Consumer) shows `@Observes` (synchronous). This is incorrect —
`CloudEventTypeDispatcher` re-fires with `fireAsync()`, which delivers only to
`@ObservesAsync` observers. A synchronous observer silently never fires.
This spec's `@ObservesAsync` is correct. Erratum tracked as #171.

### Flow

```
Stream processor (AMQP/Kafka/Poll)
  → Event<CloudEvent>.fireAsync()
  → CloudEventTypeDispatcher @ObservesAsync CloudEvent
  → re-fires with @CloudEventType(CbrEventTypes.CBR_OUTCOME)
  → CbrOutcomeConsumer.onCloudEvent(@ObservesAsync @CloudEventType(...))
  → deserialize data → onCbrOutcome(CbrOutcomeData)
  → store.recordOutcome()
```

### Changes

**`CbrOutcomeConsumer.java`** — add:
- CDI-managed `ObjectMapper` injection (constructor parameter) — the Quarkus-managed instance auto-registers `JavaTimeModule`, required for `Instant` deserialization of `CbrOutcomeData` fields. Tests must use `@Inject ObjectMapper` or manually register `JavaTimeModule`.
- `void onCloudEvent(@ObservesAsync @CloudEventType(CbrEventTypes.CBR_OUTCOME) CloudEvent event)` — deserializes `event.getData().toBytes()` to `CbrOutcomeData` via ObjectMapper, delegates to `onCbrOutcome()`

**Error handling in `onCloudEvent`:**
- Null `event.getData()` → log warning, return
- `IOException` from deserialization → log error with event ID, return
- `store.recordOutcome()` failure → caught by `CloudEventTypeDispatcher.exceptionally()` (platform), logged with event type and ID. Silent loss is acceptable: EMA is self-correcting (the next outcome event adjusts drift), and the idempotency guard makes re-delivery safe if the event infrastructure retries.
- No exception propagation — one bad event must not kill the observer

**`@RequestScoped` constraint:** `@ObservesAsync` observers run on a managed
thread pool where `@RequestScoped` context is not propagated (platform
ARC42STORIES §8). `CbrOutcomeConsumer` and the `CbrCaseMemoryStore` decorator
chain (OutcomeWeighting, TemporalDecay, ErasureNotification, ScopeDecay) must
not inject `@RequestScoped` beans. Currently safe — `tenantId` is passed as a
method parameter, not resolved from `CurrentPrincipal`. Violation would cause
`ContextNotActiveException` at runtime (loud failure, not silent).

**`memory/pom.xml`** — add:
- `com.fasterxml.jackson.core:jackson-databind` (provided scope — available at runtime in all Quarkus applications)

### Testing

**New tests in `CbrOutcomeConsumerTest.java`:**
- `onCloudEvent_deserializesAndDelegates` — build a CloudEvent with JSON-serialized `CbrOutcomeData` as data, call `onCloudEvent`, verify `recordOutcome` was invoked with correct values
- `onCloudEvent_nullData_skips` — CloudEvent with null data, verify no store interaction
- `onCloudEvent_invalidJson_skips` — CloudEvent with garbage data, verify no store interaction and no exception

Existing `onCbrOutcome` tests remain unchanged — they test the domain logic directly.

### Dependencies

| Dependency | Scope | Source |
|-----------|-------|--------|
| `io.cloudevents:cloudevents-core` | transitive | via `casehub-platform-api` (compile scope — includes `CloudEventBuilder` for tests) |
| `@CloudEventType` annotation | transitive | via `casehub-platform-api` |
| `com.fasterxml.jackson.core:jackson-databind` | provided | Quarkus runtime |

### Pre-requisite

The installed platform SNAPSHOT must include commit `c9fcc74` (platform#174).
This commit added `@CloudEventType` to `casehub-platform-api` and
`CloudEventTypeDispatcher` to `casehub-platform` (runtime). Both modules are
required — the annotation for compile-time wiring, the dispatcher for runtime
event routing. Run `mvn install` from the platform repo root to build all modules.

### CLAUDE.md

Update `memory/` module description to note: "CbrOutcomeConsumer observes
`@CloudEventType(CbrEventTypes.CBR_OUTCOME)` CloudEvents via platform CDI
dispatch."

## Out of scope

- Reactive parity for the observer — CDI `@ObservesAsync` is inherently async; no Mutiny wrapper needed
- Integration test with real CloudEvent dispatch — unit test with constructed CloudEvent is sufficient
- Changes to `CbrOutcomeData` or the event payload format — owned by `casehub-desiredstate-api`
