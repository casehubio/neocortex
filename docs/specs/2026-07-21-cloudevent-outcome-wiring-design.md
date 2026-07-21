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

Add a `@ObservesAsync @CloudEventType("io.casehub.cbr.outcome")` observer
method to `CbrOutcomeConsumer`. The platform's `CloudEventTypeDispatcher`
(platform#174) re-fires incoming CloudEvents with the `@CloudEventType`
qualifier, so consumers receive only events matching their declared type.

### Flow

```
Stream processor (AMQP/Kafka/Poll)
  → Event<CloudEvent>.fireAsync()
  → CloudEventTypeDispatcher @ObservesAsync CloudEvent
  → re-fires with @CloudEventType("io.casehub.cbr.outcome")
  → CbrOutcomeConsumer.onCloudEvent(@ObservesAsync @CloudEventType(...))
  → deserialize data → onCbrOutcome(CbrOutcomeData)
  → store.recordOutcome()
```

### Changes

**`CbrOutcomeConsumer.java`** — add:
- `ObjectMapper` injection (constructor parameter)
- `void onCloudEvent(@ObservesAsync @CloudEventType("io.casehub.cbr.outcome") CloudEvent event)` — deserializes `event.getData().toBytes()` to `CbrOutcomeData` via ObjectMapper, delegates to `onCbrOutcome()`

**Error handling in `onCloudEvent`:**
- Null `event.getData()` → log warning, return
- `IOException` from deserialization → log error with event ID, return
- No exception propagation — one bad event must not kill the observer

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
| `io.cloudevents:cloudevents-core` | transitive | via `casehub-platform-api` |
| `@CloudEventType` annotation | transitive | via `casehub-platform-api` |
| `com.fasterxml.jackson.core:jackson-databind` | provided | Quarkus runtime |
| `io.cloudevents:cloudevents-core` (test) | test | for `CloudEventBuilder` in tests |

### Pre-requisite

The installed `casehub-platform-api` SNAPSHOT must include the `@CloudEventType`
commit (platform `c9fcc74`). If not, `mvn install` the platform repo first.

### CLAUDE.md

Update `memory/` module description to note: "CbrOutcomeConsumer observes
`@CloudEventType("io.casehub.cbr.outcome")` CloudEvents via platform CDI
dispatch."

## Out of scope

- Reactive parity for the observer — CDI `@ObservesAsync` is inherently async; no Mutiny wrapper needed
- Integration test with real CloudEvent dispatch — unit test with constructed CloudEvent is sufficient
- Changes to `CbrOutcomeData` or the event payload format — owned by `casehub-desiredstate-api`
