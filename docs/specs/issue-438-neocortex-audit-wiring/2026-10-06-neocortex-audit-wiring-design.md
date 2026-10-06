# Neocortex Full Audit — Wiring Gaps, Dead Pipelines, Activation Boundary Fixes

**Epic:** casehubio/neocortex#438
**Branch:** issue-438-neocortex-audit-wiring
**Date:** 2026-10-06

## Summary

A root-to-tip audit revealed that newer cognitive subsystems (CARMA appraisal, gut feeling, behavioral synthesis, domain activation, social comparison, mood persistence, attention signaling) are fully implemented and individually tested but not wired into the production runtime. The established infrastructure (memory, CBR, MindMap, RAG, inference) is production-grade and correctly wired.

The gap is at the activation boundary — missing CDI annotations, production config defaulting newer subsystems off, and blocks not calling registration methods.

## Execution Strategy

- **Single branch** with all 20 issues
- **One commit per issue**, landed in tier order
- **Tier ordering:** Tier 1 (CDI annotations) → Tier 2 (pipeline wiring) → Tiers 3+4 (independent)
- **Cross-repo:** neocortex-side changes for #444 land here; a separate blocks issue handles the `configureAppraisal()`/`configureGutFeeling()` call site

## Tier 1 — CDI Bean Annotations (prerequisites)

All XS/Low. Pattern: add missing CDI annotations so beans are discovered.

| Issue | Class | Fix |
|-------|-------|-----|
| #439 | `CognitiveAttentionAccumulator` | Add `@ApplicationScoped`, add `@Observes` to `onAffectRecorded()` and `onExperienceRecorded()`. Constructor needs CDI-compatible refactor (currently takes `Consumer<CognitiveAttentionRequired>` — replace with `Event<CognitiveAttentionRequired>`). |
| #440 | `BeliefRevisionPhase` | Add `@ApplicationScoped`. Has `@Priority(16)` but no scope annotation. |
| #441 | `RelationshipStagePhase` | Add `@ApplicationScoped`. Has `@Priority(18)` but no scope annotation. |
| #442 | `HeuristicGoalAppraisal` | Add `@DefaultBean @ApplicationScoped` producer. Without it, `Instance<GoalAppraisal>` is unresolvable → OCC appraisal unreachable. |
| #443 | `SnapshotCaptureService` | Add `@Observes` to `onConsolidationCompleted()` parameter. |

## Tier 2 — Pipeline Wiring (depends on Tier 1)

S-M / Med-High. Pattern: connect implemented subsystems to the runtime.

| Issue | Component | Fix |
|-------|-----------|-----|
| #444 | CARMA + gut feeling | Neocortex side: ensure tick context carries observation text. Blocks side: call `configureAppraisal()` and `configureGutFeeling()` (separate blocks issue). Blocked by #442. |
| #445 | `CognitiveAttentionMediator` | Fix double-drain: `CognitionCore.tick()` drains queue, then `CognitiveProfileParticipant` gets nothing. Solution: snapshot-and-share via tick context. Blocked by #439. |
| #446 | `ConsolidationMediator` | `ConsolidationScheduler` passes `List.of()` artifacts. Add `artifacts()` drain to `ConsolidationPhase` SPI, wire phases to produce artifacts. |
| #447 | `SchemaDiscoveryPhase` | `subgraphPriority()` returns UUIDs but phase expects type strings. Change priority list to return type strings. |
| #448 | Mood persistence | After `mood.tick()`, persist new mood via `MoodEvents.toMemoryInput()` → `CaseMemoryStore.store()`. |

## Tier 3 — Functional Gaps (independent)

| Issue | Component | Fix |
|-------|-----------|-----|
| #449 | Retention scheduling | Three retention tick methods in `MemoryBeans` are dead code. Add `@Scheduled` annotations. |
| #450 | `GoalRecognitionPhase` | No cursor — re-reads from beginning every tick. Add cursor persistence (sentinel node pattern). |
| #451 | `DelegatingCbrRecordStore` | Missing `eraseSubject()` override — Subject type info lost in decorator chain. |
| #452 | `GutFeelingParticipant` | Runs unconditionally when registered. Check `behavioralEnabled` config flag at `tick()` start. |

## Tier 4 — Cleanup (independent)

| Issue | Component | Fix |
|-------|-----------|-----|
| #453 | `CognitionConfig.all()` | Named "all" but disables 6 subsystems. Add `withAllSubsystems()`, keep `all()` for compat. |
| #454 | `InnerLifeOrchestrator` | Injected but `tick()` never calls it. Clarify: add tick call, remove injection, or document as adapter-driven. |
| #455 | `habituationEnabled` | Never checked at runtime. Remove field (integral to appraisal). |
| #456 | `ConfidenceDecayDecorator` | 3-arg PrincipalId variants bypass decay. Override them and delegate. |
| #457 | `BehavioralSynthesisPhase` | Dead cursor code (`loadCursor`/`saveCursor`/`findSentinelNode`). Remove. |
| #458 | `SurfacingAggregationPhase` | Not in CLAUDE.md phase listing. Documentation-only fix. |

## Testing Approach

- Tier 1 fixes are structural (CDI annotations) — verified by existing tests passing + new CDI discovery tests where warranted
- Tier 2 fixes need targeted unit tests for the wiring changes (snapshot-and-share for #445, artifact drain for #446, mood persistence for #448)
- Tier 3/4 fixes include tests per issue scope
- Full `mvn clean install` after each tier completes

## References

- casehubio/neocortex#438 — epic issue with full audit findings
- `CognitionCore.java:549-568` — configureAppraisal/configureGutFeeling methods
- `CognitiveAttentionAccumulator.java:25` — missing @ApplicationScoped
- `BeliefRevisionPhase.java:26` — missing @ApplicationScoped
- `RelationshipStagePhase.java:21` — missing @ApplicationScoped
