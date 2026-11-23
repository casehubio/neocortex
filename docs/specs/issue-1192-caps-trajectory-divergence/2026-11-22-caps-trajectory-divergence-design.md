# CAPS Topology Trajectory Divergence — Deeper Feedback Loops

**Issue:** casehubio/neocortex#1192
**Date:** 2026-11-22
**Scale:** S | **Complexity:** Med

## Problem

The CAPS topology is currently feedforward — all connections flow INPUT→MEDIATING or MEDIATING→OUTPUT with zero OUTPUT→MEDIATING feedback. The CapsSettler's momentum damping (`newAct = 0.4 * oldAct + 0.6 * rawAct`) dominates convergence rate in this feedforward structure. Result: both open and cautious personality profiles converge in exactly 10 iterations with identical convergence type (CONVERGED) and zero saturation, despite different connection weights and resting activations producing different attractor destinations.

The settling *destinations* diverge (verified by `DispositionAttractorDifferentiationTest`). The settling *trajectories* (iterations, convergence type, saturation) are identical.

## Design

Add 6 output→mediating feedback connections to `caps-topology.yaml`, tagged for disposition modulation. These create recurrent energy paths where personality-dependent weights control how much activation recirculates, producing different convergence dynamics per profile.

### Feedback Connections

| # | From (output) | To (mediating) | Weight | Sign | Tags | Literature |
|---|---------------|----------------|--------|------|------|------------|
| 1 | `approach` | `self_efficacy` | 0.15 | + | `BAS_connections` | Bandura 1997: approach behavior reinforces perceived capability |
| 2 | `withdraw` | `self_worth` | -0.15 | − | `BIS_connections` | Beck 1976: avoidance erodes self-evaluation |
| 3 | `trust` | `other_reliability` | 0.10 | + | `social_input` | Bowlby 1988: trusting action reinforces internal working model |
| 4 | `distrust` | `other_reliability` | -0.10 | − | `social_input` | Bowlby 1988: distrust degrades perceived reliability |
| 5 | `BAS_activation` | `reinforcement_expectation` | 0.15 | + | `BAS_connections` | Gray 1982: BAS amplification loop |
| 6 | `BIS_activation` | `threat_sensitivity` | 0.15 | + | `BIS_connections` | Gray 1982: BIS vigilance amplification |

**Note:** Connections 5 and 6 are MEDIATING→MEDIATING, not OUTPUT→MEDIATING. They create amplification loops within the affect systems. All 6 connections introduce recurrence into what was previously feedforward.

### Why This Produces Divergence

- **Bold/cooperative profiles** (open): `BAS_connections` tag gets multiplied by 1.3 (riskAppetite=bold). The BAS amplification loop and approach→self_efficacy loop recirculate more energy. The network takes longer to settle as activation oscillates through these amplified loops.
- **Conservative/avoidant profiles** (cautious): `BIS_connections` tag gets multiplied by 1.3 (riskAppetite=conservative). The BIS→threat_sensitivity loop and withdraw→self_worth loop recirculate more energy on the avoidance side. Meanwhile, the BAS loops are damped (0.7 multiplier).
- Both profiles have amplified loops, but on *different* pathways. The asymmetry in which loops are strong determines how many iterations each profile needs.

### Weight Rationale

Weights of 0.10–0.15 are deliberately moderate:
- Strong enough to create measurable recurrence (0.15 × 0.6 = ~0.09 effective per iteration after damping)
- Weak enough that convergence is not destabilised — well below the effective weight cap of [-2.0, 2.0]
- The disposition multipliers (0.7–1.3) create a ~2× range between dampened and amplified feedback per profile

### Disposition Modifier Tags

The existing `disposition_modifiers` section already defines multipliers for `BAS_connections`, `BIS_connections`, and `social_input`. No new modifier definitions are needed — the feedback connections use existing tags.

## Test Plan

### 1. Update `ConsolidationDivergenceTest`

Add a new test `capsTrajectoryDiverges` that asserts the open and cautious profiles produce *different* trajectory metadata (iterations, convergence type, or saturation ratio). This replaces the current characterisation that documents identical trajectories.

The existing `capsTrajectoryBothAgentsConverge` test stays — it asserts validity, not equality.

### 2. Unit test in `caps-engine`

Add a test in `DispositionAttractorDifferentiationTest` (or a new `FeedbackLoopDivergenceTest`) that directly settles two profiles and asserts their iteration counts differ.

### 3. Regression guard

Existing tests that assert specific attractor strengths or convergence behavior must still pass. The feedback loops change convergence *rate*, not the fundamental attractor landscape. Run the full `caps-engine` and `caps-testing` test suites.

## Scope

**In scope:**
- 6 new connections in `caps-topology.yaml`
- 1-2 new test methods asserting trajectory divergence
- Update existing test assertions if iteration counts change (the current "exactly 10" characterisation will shift)

**Out of scope:**
- Changing the damping factor or epsilon
- Adding new disposition modifier definitions
- Modifying CapsSettler algorithm

## References

- `caps-engine/src/main/resources/caps-topology.yaml` — topology definition
- `caps-engine/src/main/java/io/casehub/neocortex/caps/engine/CapsSettler.java:99` — damping factor application
- `caps-engine/src/main/java/io/casehub/neocortex/caps/engine/DispositionWeightMapper.java` — personality-to-weight mapping
- `cognition/src/test/java/io/casehub/neocortex/cognition/eval/divergence/ConsolidationDivergenceTest.java` — existing trajectory tests
- `cognition/src/test/java/io/casehub/neocortex/cognition/eval/divergence/DivergenceFixtures.java` — open/cautious profile definitions
- casehubio/neocortex#1171 — CAPS settling trajectory assertions (prior work)
- casehubio/neocortex#1175 — referenced in issue as prior investigation
- Mischel & Shoda 1995 — CAPS theory, recurrent connections as core mechanism
- Gray 1982 — Reinforcement Sensitivity Theory, BAS/BIS amplification
- Bandura 1997 — Self-efficacy theory
- Beck 1976 — Cognitive theory of depression, self-worth erosion
- Bowlby 1988 — Attachment working models
