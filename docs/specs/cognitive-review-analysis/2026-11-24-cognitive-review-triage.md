# Cognitive Review Triage — Verification at HEAD

**Date:** 2026-11-24
**Review commit:** c3230fd | **Verified at:** 107c043b (HEAD of cognitive-review-analysis)
**Review source:** `claude/magical-sagan-5e2uum/docs/research/2026-11-22-cognitive-review/REVIEW.md`

---

## Fix Status

**Updated:** 2026-11-24 — after SE1 (Pipeline Spine) landed + prior mechanical fixes.

23 of 107 findings now fixed. 8 partially addressed. 68 still valid. 2 not verifiable (agency-side).

**Mechanical fixes (pre-SE1):** bff7afbe (C15), 37daaed8 (D2, A10), fccd7ae3 (B11, B13, B14), 1e320b85 (A9, F2, F9, F10, F15)
**SE1 (#1221):** 91aa9b6c (A1, A13), 98c4577e (A3, C19, D13), ccf5915d (C1, C8 partial), bdb8b7c8 (A6, A7, A8, D7, E3, E11)
**Deferred:** A2 partial → #1231, E2 → #1232

---

## Overview

The review contains 120+ findings across 9 sections. All were verified against current HEAD by reading the actual code paths.

| Section | Total | Still Valid | Partially Addressed | Fixed | Not Verifiable |
|---------|-------|------------|--------------------:|------:|---------------:|
| A — Wiring | 13 | 3 | 0 | 8 | 2 (agency-side) |
| B — CAPS | 17 | 9 | 2 | 3 | 0 |
| C — Affect | 21 | 11 | 4 | 3 | 0 |
| D — Personality | 19 | 15 | 1 | 3 | 0 |
| E — Higher cognition | 17 | 15 | 0 | 2 | 0 |
| F — Consolidation | 20 | 15 | 1 | 4 | 0 |
| **Total** | **107** | **68** | **8** | **23** | **2** |

Sections G (Evidence base), H (Direction), I (Recorded run data) are analysis/recommendations, not code findings.

---

## Categorisation

### By type

| Category | Count | Description |
|----------|------:|-------------|
| **genuine-bug** | 55 | Code does something wrong or harmful |
| **integration-gap** | 28 | Built but not wired into the production pipeline |
| **design-disagreement** | 14 | Reviewer questions a design choice; not necessarily wrong |
| **not-verifiable** | 2 | Agency-side code not in this repo |

### Corrections to the review

- **C16:** Threshold is `> 0` not `> 0.1` — normalization problem less severe than stated
- **C7:** Reclassified from genuine-bug to design-disagreement — polarity depends on eidos encoding
- **B8:** 7 of 9 output nodes are documented as intentionally predictor-only (coping outputs)
- **B12:** Disposition resolution now checks effectiveDefaults first, falls back to default only when unavailable
- **C21:** Layer 1 (experience search) is now agent-scoped; only Layer 2 (affect fallback) scans tenant-wide

---

## Priority groupings for issue creation

### P1: Unified pipeline composition

The meta-issue. ~~Production (CognitionConfig.all()), tests (withAllSubsystems()), and wacky-manor each assemble a different pipeline.~~ SE1 unified all paths via EnumSet-backed CognitionConfig. Builder wiring partially complete (search budget/curiosity deferred to #1231).

| Finding | Summary | Status |
|---------|---------|--------|
| A1 | CognitionConfig.all() leaves 11 subsystems off | ✅ Fixed (91aa9b6c) |
| A2 | Pipeline builder doesn't wire configureSearchBudget, configureCuriosity, etc. | ⚠️ Partial — ToM wired; search budget/curiosity → #1231 |
| A9 | RecurringThoughtPhase missing CDI scope — never discovered | ✅ Fixed (1e320b85) |
| A10 | Classpath profile loading fails inside jars | ✅ Fixed (37daaed8) |
| A11 | Missing orchestrator implementations in this repo | — Not verifiable (agency-side) |
| A13 | CognitionConfig is 27 positional booleans | ✅ Fixed (91aa9b6c) |

### P2: Per-agent state isolation

~~One CognitionCore shared by all agents with agent-specific state in instance fields.~~ SE1 added ConcurrentHashMap<AgentKey, PerTickState> for core state isolation. Drive sources, affect trajectories, memory retriever, and narrative still need agent-scoping (SE5, SE6, SE7).

| Finding | Summary | Status |
|---------|---------|--------|
| A3 | Shared CognitionCore leaks lastAgentId, lastDescriptor, lastBriefing, etc. | ✅ Fixed (98c4577e) |
| C9 | CuriosityDrive and other drive sources share state across agents | Open (SE6) |
| D11 | Affect trajectories not agent-scoped | Open (SE5) |
| D19 | CognitiveMemoryRetriever has one shared mutable tick context | Open (SE5) |
| C19 | LLM session ids not tenant/subject scoped | ✅ Fixed (98c4577e) |
| E7 | Narrative partitions by tenant not agent | Open (SE7) |
| F3 | Cross-tenant leakage in attention path | Open (SE4) |

### P3: State corruption — bugs that would corrupt any run

These produce incorrect state that compounds over time. Must fix before any empirical measurement is valid. 7 of 14 now fixed.

| Finding | Summary | Status |
|---------|---------|--------|
| F1 | UUID cursor ordering skips memories on SQLite/JPA — **critical** | Open (SE3) |
| C1 | Stale appraisal re-bridged to mood/drives every tick | ✅ Fixed (ccf5915d) |
| C2 | Mood additive deltas with no rate limit → pins at clamp | Open (SE6) |
| C3 | Decay timing: fresh signal immediately decayed after idle gap | Open (SE6) |
| C4 | Standing goal emotions added as delta every tick | Open (SE6) |
| C5 | CognitiveResourcePool hardcoded null — fatigue gate never fires | Open (SE6) |
| C6 | Coping drive modulations overwritten by put() | Open (SE6) |
| C8 | drives.tick runs twice per core tick — double smoothing | ⚠️ Partial (ccf5915d) — gated on observation; full fix in SE6 |
| E4 | Mental-model confidence decay compounds (doesn't advance reference) | Open (SE7) |
| D7 | Recency decay captures Instant.now() once at startup — frozen | ✅ Fixed (bdb8b7c8) |
| D13 | updateBase called every tick — invalidates cache, runs two full derivations | ✅ Fixed (98c4577e) |
| F2 | GoalAffect writes synthetic experience every pass, every goal | ✅ Fixed (1e320b85) |
| F12 | Uniform 30-day confidence decay, per-type half-life never read | Open (SE3) |
| D2 | Fear threshold inverted — anxious agents harder to frighten | ✅ Fixed (37daaed8) |

### P4: Affect pipeline correctness

Bugs in emotion mapping, regulation, and drive computation that produce wrong values but don't corrupt state cumulatively. 1 of 7 fixed.

| Finding | Summary | Status |
|---------|---------|--------|
| C13 | Regulation triggers on JOY/PRIDE (no valence check) | Open (SE6) |
| C14 | EmotionMapper missing 6 emotions; INTERRUPTION intensity inverted | Open (SE6) |
| C15 | AlmaPadTable RELIEF dominance sign wrong (Gebhard 2005) | ✅ Fixed (bff7afbe) |
| C17 | CognitiveLoadEstimator: drives().size() always 4, LOW unreachable | Open (SE6) |
| C11 | Appraisal weights and habituation config derived but never read | Open (SE6) |
| C12 | Regulation never changes what reaches mood or drives | Open (SE6) |
| C16 | Concern salience normalisation penalises short goals | Open (SE6) |

### P5: Higher cognition and consolidation wiring

Features built but not integrated or producing meaningful output. 6 of 13 fixed.

| Finding | Summary | Status |
|---------|---------|--------|
| E1 | Mental-model/user-model/strategy LLM synthesis never fires (gate too strict) | Open (SE7) |
| E2 | Interaction quality uses agent's pre-interaction mood | Deferred → #1232 |
| E3 | configureTheoryOfMind never called in production | ✅ Fixed (bdb8b7c8) |
| E8 | Memory hygiene: nothing evicted (composite always above threshold) | Open (SE7) |
| E10 | Goal tick results discarded; queryGoals returns all tenant goals | Open (SE7) |
| E11 | Regex JSON parsing breaks on ] in text, rejects +0.3 | ✅ Fixed (bdb8b7c8) |
| F4 | SQLite LIMIT before trait filter; subgraph type/id confusion | Open (SE3) |
| F7 | Graduation cursor skip; actions/outcomes always pass | Open (SE4) |
| F9 | CuriosityRefreshPhase computes signals and discards them | ✅ Fixed (1e320b85) |
| F10 | Attention signals re-fire every pass (level-triggered) | ✅ Fixed (1e320b85) |
| F11 | Auto-merge at JW ≥ 0.9 merges similar but distinct entities | Open (SE4) |
| F15 | Active goals default to HOPE → never go dormant | ✅ Fixed (1e320b85) |
| F19 | Every paragraph becomes STATED 1.0 node; fuzzy name resolution errors | Open (SE4) |

### P6: CAPS effectiveness (pending measurement)

CAPS produces near-identical output across characters. The reviewer recommends "rebuild or retire" but this should wait until P1-P3 are resolved and the unified pipeline can be measured properly. 3 of 11 fixed.

| Finding | Summary | Status |
|---------|---------|--------|
| B1 | Unipolar nodes at 0.5 background swamp situation input | Open (SE2) |
| B2 | Weight update is not multi-cue Rescorla-Wagner | Open (SE2) |
| B3 | Regulation discards updated weights — learning is a no-op | Open (SE2) |
| B5 | Distortions add positive constant regardless of sign | Open (SE2) |
| B6 | Convergence threshold too generous (variance 0.05) | Open (SE2) |
| B9 | ClassificationOutcomeEvaluator is roughly per-agent constant | Open (SE2) |
| B10 | CBR cache: case id from query not hit; fewer filter fields | Open (SE2) |
| B11 | RuleBasedSituationClassifier.classify() returns empty | ✅ Fixed (fccd7ae3) |
| B13 | Threshold multipliers × 0 = 0 | ✅ Fixed (fccd7ae3) |
| B14 | LlmSituationClassifier session reuse accumulates history | ✅ Fixed (fccd7ae3) |
| B17 | Tests don't constrain behaviour | Open (SE2) |

### Design disagreements (not issues — park for later)

| Finding | Summary |
|---------|---------|
| A12 | safeRun generic warning (observability preference) |
| B2 | Weight update variant (not textbook R-W) |
| B4 | Resting activations as post-hoc strength (design intent unclear) |
| B7 | "Attractor" terminology for feed-forward network |
| B15 | Precision tracking variant (not Pearce-Hall) |
| C7 | Drive polarity depends on eidos encoding |
| D3 | Fear from Sensing/Intuition (Jungian architecture constraint) |
| D5 | PersonalityWeights ineffective in single-domain queries |
| D8 | RetrievalModulator discards store relevance |
| D9 | DTW correlation normalisation |
| D12 | Null caller = admin access (intentional) |
| D17 | Effective personality dimensionality |
| E13 | Inner-life silence from last tick vs last user activity |
| F8 | Curiosity channels near-constant by construction |
| F13 | Information-gap score measures provenance/age |
| F18 | Per-pass consolidation cost |

---

## Issue structure

Epics created as #1220 (parent) with 7 sub-epics (#1221–#1227) + cross-repo #1228. Follow-up issues: #1231, #1232.

| Epic | Status | Remaining findings |
|------|--------|-------------------|
| SE1: Pipeline Spine (#1221) | **Closed** — landed as bdb8b7c8 | 0 (2 deferred to #1231, #1232) |
| SE2: CAPS Rework (#1222) | Open | 8 |
| SE3: Storage & Data Integrity (#1223) | Open | 4 |
| SE4: Consolidation Phases (#1224) | Open | 9 (5 from P5 + 4 original) |
| SE5: cognitive-index (#1225) | Open | 6 (2 from P2) |
| SE6: Affect & Emotion (#1226) | Open — unblocked by SE1 | 14 (from P3 + P4) |
| SE7: Orchestrators (#1227) | Open — unblocked by SE1 | 10 (from P2 + P5) |

Design disagreements don't need issues — they're documented here for reference and can be revisited after measurement.
