# Life — CBR for Contractor Coordination

## Why CBR

Life manages household maintenance jobs — boiler repairs, electrical work, roofing,
appliance installs. When a new job is needed, CBR finds similar past jobs and surfaces
outcomes: which contractors completed on time, which overcharged, which delivered
excellent service. This turns historical job data into contractor recommendations
grounded in actual performance, not just ratings.

## CBR Paradigm

**Feature-Vector CBR.** Structured features (job type, urgency, property area, season)
provide precise filtering. The job description adds semantic context for cases that
share the same structural profile but differ in specifics.

## Feature Schema

```java
CbrFeatureSchema SCHEMA = CbrFeatureSchema.of("life-contractor",
    FeatureField.categorical("job_type"),       // PLUMBING, ELECTRICAL, ROOFING, APPLIANCE, GENERAL
    FeatureField.categorical("urgency"),        // EMERGENCY, ROUTINE, PLANNED
    FeatureField.categorical("property_area"),  // KITCHEN, BATHROOM, EXTERIOR, HVAC, GENERAL
    FeatureField.categorical("cost_band"),      // UNDER_100, 100_250, 250_500, 500_1000, OVER_1000
    FeatureField.categorical("season"),         // WINTER, SPRING, SUMMER, AUTUMN
    FeatureField.text("job_description"));
```

### Why these fields

- **job_type** — different trades have different contractor ecosystems; a good plumber may not be a good electrician
- **urgency** — emergency response contractors have different SLAs and pricing than planned work contractors
- **property_area** — contractors specialise by area; an HVAC specialist is different from a general kitchen contractor
- **cost_band** — bucketed rather than continuous because contractor reliability patterns differ by job scale; £150 jobs and £1500 jobs have different risk profiles
- **season** — availability patterns differ by season; winter boiler contractors are in high demand, spring roofing contractors have more capacity
- **job_description** — semantic match catches domain-specific patterns that categories miss ("boiler losing pressure" vs. "intermittent ignition failure")

## Retain — Storing Job Outcomes

When a contractor job completes:

```java
@ApplicationScoped
public class ContractorJobOutcomeObserver {

    @Inject CbrCaseMemoryStore cbrStore;

    void onJobClosed(@Observes ContractorJobClosedEvent event) {
        var cbrCase = new FeatureVectorCbrCase(
            event.jobDescription(),                         // problem
            formatSolution(event),                          // solution summary
            event.disposition().name(),                     // COMPLETED_ON_TIME, DELAYED, OVERCHARGED, EXCELLENT
            event.satisfactionScore(),                      // nullable 0.0-1.0
            Map.of(
                "job_type", event.jobType().name(),
                "urgency", event.urgency().name(),
                "property_area", event.propertyArea().name(),
                "cost_band", event.costBand(),
                "season", event.season().name(),
                "job_description", event.jobDescription()));

        cbrStore.store(cbrCase, "life-contractor",
            event.propertyId(), LIFE_DOMAIN, event.tenantId(), event.jobId());
    }

    private String formatSolution(ContractorJobClosedEvent e) {
        return "Contractor: %s. Cost: £%d (quoted £%d). SLA: %dh, completed in %dh. %s"
            .formatted(e.contractorName(), e.actualCost(), e.quotedCost(),
                      e.slaHours(), e.actualHours(), e.notes());
    }
}
```

## Retrieve — Finding Similar Past Jobs

When a new job request arrives:

```java
@ApplicationScoped
public class ContractorRecommender {

    @Inject CbrCaseMemoryStore cbrStore;

    public List<FeatureVectorCbrCase> findSimilarJobs(JobRequest request) {
        var query = CbrQuery.of(
            request.tenantId(),
            LIFE_DOMAIN,
            "life-contractor",
            Map.of(
                "job_type", request.jobType().name(),
                "property_area", request.propertyArea().name()),
            10);

        return cbrStore.retrieveSimilar(query, FeatureVectorCbrCase.class);
    }
}
```

Results tell you: "3 similar past HVAC jobs in winter — ABC Heating completed
all 3 on time with average cost £190. QuickFix Ltd had 1 job, delayed 3 days,
cost 60% over quote. Suggested contractor: ABC Heating."

## Trust Score Derivation

Contractor recommendations are grounded in outcome data, not ratings. Each past
job contributes evidence:
- On-time delivery vs. delays (SLA adherence)
- Quoted cost vs. actual cost (pricing accuracy)
- Satisfaction score (when available)

CBR finds all past jobs for candidate contractors in the same job_type and
property_area. Trust scores are derived from aggregate outcome patterns — a
contractor with 5 on-time jobs and 0 delays has higher trust than one with
3 on-time and 2 delayed.

## Mock → Production

| Phase | Backend | What works |
|-------|---------|-----------|
| **Now** | `memory-cbr-inmem` | Schema validation, categorical exact match, store/retrieve round-trip. No persistence. |
| **Production** | `memory-qdrant` | Payload filters on job_type/urgency/area + dense vector on description. Persistent. Trust scores from full job history. |

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

Future neocortex phases enhance Life's contractor coordination:

- **Phase 2 (Weighted Similarity, #82)**: `cost_band` and `season` contribute
  proportionally to similarity scores — winter vs. spring jobs weighted by
  seasonal availability patterns, cost bands weighted by scale.
- **Phase 3 (Semantic Retrieval, #83)**: Job descriptions enable cross-category
  retrieval — "boiler losing pressure" finds similar pressure-related jobs even
  if they involved different property areas.
- **Phase 4 (Outcome Learning, #84)**: Contractor trust scores improve over time
  as more jobs complete — the system learns which contractors deliver reliably
  for which job types.
- **Phase 5 (Plan Adaptation, #85)**: Plan-Based CBR suggests specific case plan
  steps (e.g., "call contractor → confirm parts availability → schedule") based
  on successful sequences from past jobs.
