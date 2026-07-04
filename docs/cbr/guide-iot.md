# IoT — CBR for Situation Handling

## Why CBR

IoT systems generate situation events — temperature anomalies, unexpected motion,
water leaks, smoke detections. Many are false positives (cooking triggers kitchen
temperature alerts). When a new situation occurs, CBR finds similar past situations
and surfaces outcomes: was it a genuine incident or false positive? What was the
resolution? This turns historical IoT data into auto-suppression rules and operator
decision support.

## CBR Paradigm

**Feature-Vector CBR.** Structured features (situation type, device class, room type,
time of day, severity) provide precise filtering. The situation description adds
semantic context for edge cases that categories miss.

## Feature Schema

```java
CbrFeatureSchema SCHEMA = CbrFeatureSchema.of("iot-situation",
    FeatureField.categorical("situation_type"),  // TEMPERATURE_ANOMALY, MOTION_UNEXPECTED, WATER_LEAK, SMOKE_DETECTED, POWER_OUTAGE
    FeatureField.categorical("device_class"),    // THERMOSTAT, CAMERA, SENSOR, ALARM, METER
    FeatureField.categorical("room_type"),       // LIVING, BEDROOM, KITCHEN, BATHROOM, GARAGE, EXTERIOR
    FeatureField.categorical("time_of_day"),     // MORNING, AFTERNOON, EVENING, NIGHT
    FeatureField.categorical("severity"),        // LOW, MEDIUM, HIGH, CRITICAL
    FeatureField.text("situation_description"));
```

### Why these fields

- **situation_type** — different anomaly types have different false positive rates and different resolution patterns
- **device_class** — thermostats and cameras have different failure modes; past resolutions for the same device class are most relevant
- **room_type** — kitchen temperature anomalies are different from bedroom anomalies; cooking is routine in kitchens, unexpected in bedrooms
- **time_of_day** — evening temperature spikes in kitchens are usually cooking; night spikes are suspicious
- **severity** — LOW severities rarely need escalation; CRITICAL always does; historical escalation rates per severity inform triage
- **situation_description** — semantic match catches nuance that categories miss ("oven preheating" vs. "extractor fan failure")

## Retain — Storing Situation Resolutions

When a situation is resolved:

```java
@ApplicationScoped
public class IoTSituationOutcomeObserver {

    @Inject CbrCaseMemoryStore cbrStore;

    void onSituationResolved(@Observes IoTSituationResolvedEvent event) {
        var cbrCase = new FeatureVectorCbrCase(
            event.situationDescription(),                   // problem
            formatSolution(event),                          // solution summary
            event.resolution().name(),                      // RESOLVED_AUTOMATICALLY, OPERATOR_DISMISSED, ESCALATED, WORK_ITEM_CREATED
            event.genuineIncident() ? 1.0 : 0.0,           // confidence: 1.0 = genuine, 0.0 = false positive
            Map.of(
                "situation_type", event.situationType().name(),
                "device_class", event.deviceClass().name(),
                "room_type", event.roomType().name(),
                "time_of_day", event.timeOfDay().name(),
                "severity", event.severity().name(),
                "situation_description", event.situationDescription()));

        cbrStore.store(cbrCase, "iot-situation",
            event.deviceId(), IOT_DOMAIN, event.tenantId(), event.situationId());
    }

    private String formatSolution(IoTSituationResolvedEvent e) {
        return "Resolution: %s. %s. %s"
            .formatted(e.resolution(), e.resolutionDetails(), e.actionTaken());
    }
}
```

## Retrieve — Finding Similar Past Situations

When a new situation is detected:

```java
@ApplicationScoped
public class IoTSituationAdvisor {

    @Inject CbrCaseMemoryStore cbrStore;

    public List<FeatureVectorCbrCase> findSimilarSituations(IoTSituation situation) {
        var query = CbrQuery.of(
            situation.tenantId(),
            IOT_DOMAIN,
            "iot-situation",
            Map.of(
                "situation_type", situation.situationType().name(),
                "room_type", situation.roomType().name()),
            10);

        return cbrStore.retrieveSimilar(query, FeatureVectorCbrCase.class);
    }
}
```

Results tell you: "5 similar past kitchen temperature anomalies — 3 were false
positives (cooking-related, operator dismissed within 2 minutes), 2 were genuine
(boiler/ventilation issues requiring work items). Suggestion: if oven/hob active,
auto-downgrade to LOW severity."

## False Positive Suppression

CBR enables data-driven false positive rules. For kitchen temperature anomalies:
- 60% false positive rate when oven/hob is active
- 10% false positive rate when no cooking appliance is active

The system can auto-downgrade severity or suppress alerts when the historical
false positive rate exceeds a threshold AND contextual signals match (cooking
appliance active, evening time of day).

This is learning from outcomes without requiring manual rule creation. Each
resolved situation contributes evidence. Rules emerge from outcome patterns.

## Mock → Production

| Phase | Backend | What works |
|-------|---------|-----------|
| **Now** | `memory-cbr-inmem` | Schema validation, categorical exact match, store/retrieve round-trip. No persistence. |
| **Production** | `memory-qdrant` | Payload filters on situation_type/device_class/room_type + dense vector on description. Persistent. False positive pattern detection from full situation history. |

## Maven Dependencies

```xml
<dependency>
    <groupId>io.casehub</groupId>
    <artifactId>casehub-neocortex-memory-api</artifactId>
</dependency>
<dependency>
    <groupId>io.casehub</groupId>
    <artifactId>casehub-neocortex-memory</artifactId>
    <scope>runtime</scope>
</dependency>
<dependency>
    <groupId>io.casehub</groupId>
    <artifactId>casehub-neocortex-memory-cbr-inmem</artifactId>
    <scope>test</scope>
</dependency>
```

## Dense Vector Search

To enable semantic similarity on `problem()`, provide an `EmbeddingModel` CDI
bean. See [Dense Vector Search](README.md#dense-vector-search) in the
integration guide.

## Qdrant Configuration

```properties
casehub.memory.cbr.qdrant.host=localhost
casehub.memory.cbr.qdrant.port=6334
casehub.memory.cbr.qdrant.collection-prefix=cbr
```

## Roadmap

Future neocortex phases enhance IoT situation handling:

- **Phase 2 (Weighted Similarity, #82)**: `time_of_day` and `device_class`
  contribute proportionally to similarity scores — evening kitchen anomalies
  weighted differently from night bedroom anomalies.
- **Phase 3 (Semantic Retrieval, #83)**: Situation descriptions enable
  cross-category retrieval — "extractor fan failure" finds similar ventilation
  issues even if they involved different device classes.
- **Phase 6 (Temporal Trajectory, #88)**: Temporal trajectory analysis —
  "temperature anomalies persisting beyond 30 minutes in this room are 90%
  genuine." Duration patterns distinguish false positives from real incidents.
- **Phase 4 (Outcome Learning, #84)**: Auto-suppression rules improve over time
  as more situations resolve — the system learns which patterns are consistently
  false positives.
