# Psychology Cause-Effect Models for Mechanical Encoding

**Issue:** casehubio/neocortex#407
**Parent epic:** casehubio/neocortex#406 — Emergent Behavioral Synthesis
**Date:** 2026-10-02

## 1. Overview

This document defines the psychological foundation for a mechanical behavioral
inference system. Characters accumulate experiences over time; during
consolidation ("sleep"), those experiences are processed through a hybrid
network that produces stable behavioral attractors — crystallized behavioral
tendencies that are pre-built and ready to fire when situations arise.

The system is **hybrid deterministic + LLM**: established psychological
cause-effect patterns are encoded mechanically for determinism and scalability;
LLMs validate, enrich, and discover new patterns over time.

## 2. Network Architecture

### 2.1 Two-Layer Architecture

The encoding uses two layers:

**Layer 1 — CAPS Spreading Activation Network (computation)**
A Cognitive-Affective Processing System (Mischel & Shoda, 1995) network
encodes all psychological cause-effect chains directly. CAPS is formally a
parallel constraint satisfaction network (Shoda, LeeTiernan & Mischel, 2002)
where stable attractor states emerge from dynamic interactions among
cognitive-affective units. Key properties:
- Handles cyclic interactions (beliefs affect behavior; behavioral outcomes
  revise beliefs)
- Produces behavioral attractors — stable states the system settles into
- Compositional by nature — multiple cause-effect patterns interact through
  shared mediating nodes
- Disposition-parameterised — same topology, different weights per character
- Principled weight updates via Rescorla-Wagner prediction error rule (§5.1),
  which provides Bayesian-like learning without requiring a separate Bayesian
  network layer — the math is in the weight update function

**Convergence:** CAPS settling uses synchronous parallel update with a
maximum of 100 iterations. All activations are clamped to [-1, +1] after
each propagation step, preventing unbounded growth through positive
feedback cycles. Convergence is reached when the maximum activation
change across all nodes falls below ε = 0.001. If the network oscillates
(period-2 detected: activation vector at iteration N matches N-2 within ε),
both attractor states are recorded with their relative strengths — this
is psychologically valid (e.g., fearful-avoidant oscillation). If neither
convergence nor period-2 oscillation is detected by the iteration limit,
the system takes the most settled state from the last 10 iterations:
`argmin_{t ∈ last_10} max_i |a_i(t) − a_i(t-1)|` — the iteration with
the smallest maximum activation change across all nodes.
Update schedule is synchronous (all nodes update simultaneously per step)
following Shoda et al. (2002). Settling runs during consolidation only —
never at runtime. The PCSinR R package (referenced in §8) validates this
approach for parallel constraint satisfaction.

**Distortion interaction with convergence:** When cognitive distortions
(§4.3) amplify connection weights (e.g., catastrophizing ×2.0 on a 0.8
base weight → effective 1.6), multiple amplified inputs can push nodes
to the [-1, +1] activation ceiling. This is psychologically valid — severe
depression or anxiety produces exactly this kind of saturated cognitive
state where multiple schemas are simultaneously at maximum activation.
The convergence criterion (max change < ε) remains meaningful: it detects
when the system has stopped changing, and the PATTERN of saturated vs
non-saturated nodes across the network defines the attractor state. The
combination of compound distortion cap [0.1, 3.0] (§4.3) + effective
weight cap [-2.0, +2.0] + activation clamping [-1, +1] bounds the
interaction. **Saturation monitoring:** during implementation, each
settling run must track the fraction of nodes at ceiling (|activation| >
0.99). If >60% of mediating nodes are saturated, the settling result is
flagged with `high_saturation=true` on the behavioral attractor — this
signals the calibration pipeline (§6) that weight initialization or
distortion thresholds may need rebalancing for that character.

**Layer 2 — Behavioral Attractors (output)**
Crystallized behavioral tendencies stored as MindMap nodes in a BEHAVIORAL
subgraph. These are pre-built during consolidation and rendered to the LLM
as a finished behavioral profile. The LLM receives the profile, not raw
memories to reason over.

**BEHAVIORAL subgraph schema:**

Node types:
- `attractor` — a stable behavioral tendency (e.g., "avoidant attachment",
  "approach under reward cues"). Properties: `strength` (double, 0–1),
  `category` (one of the output node categories from §3.2), `decay_rate`
  (double), `last_activated` (timestamp), `composition_trace` (JSON —
  which CAPS nodes contributed to this attractor), `source_generation`
  (long — graph engine generation that produced this attractor).
  Traits: `CapsGenerated` — identifies this node as CAPS output.
  MergeDetectionPhase (@Priority 20) runs immediately after
  BehavioralSynthesisPhase (@Priority 19) and uses name similarity +
  neighbor overlap to detect duplicates. The `CapsGenerated` trait
  ensures MergeDetectionPhase skips BEHAVIORAL attractor nodes — CAPS
  manages its own attractor identity through `composition_trace`, not
  through string-similarity merge detection.
- `situation_trigger` — a situation pattern that activates this attractor.
  Properties: `situation_type` (from §3.2 input vocabulary), `match_threshold`
  (double). Traits: `CapsGenerated`.

Edge types:
- `TRIGGERS` (situation_trigger → attractor): `activation_strength` (double)
- `INHIBITS` (attractor → attractor): `inhibition_strength` (double)
- `COMPETES` (attractor ↔ attractor): bidirectional competition

**Storage separation:** The CAPS network's internal state (connection
weights, activation levels, thresholds) is managed by the #408 graph
engine — a separate computational structure from MindMap. MindMapEdge is
NOT used for CAPS connections. The graph engine stores per-agent CAPS
weights alongside the species-level topology. During consolidation, the
graph engine processes experiences → runs CAPS settling → outputs behavioral
attractors to the MindMap BEHAVIORAL subgraph. The LLM reads from MindMap,
never from the CAPS engine directly.

### 2.2 Why CAPS Over Alternatives

| Property | Bayesian Network | Decision Trees | CAPS |
|----------|-----------------|----------------|------|
| Cyclic interactions | No (DAG constraint) | No | Yes |
| Behavioral attractors | No (gives probabilities) | No | Yes |
| Conditional independence | Required | N/A | Not assumed |
| Composability | Limited (shared nodes) | Manual composition | Natural (shared activation) |
| Interpretable weights | Yes | Yes | Yes (every connection has psychological meaning) |
| Incremental updating | Yes | Limited | Yes (Hebbian + reinforcement) |
| Empirical grounding | Strong | Moderate | Moderate |

CAPS wins because the epic requires behavioral ATTRACTORS — stable states,
not probability distributions. The principled weight updating that Bayesian
networks provide is achieved through the Rescorla-Wagner prediction error
rule (§5.1), which is mathematically equivalent to Bayesian updating for
the single-cue case and runs natively within the CAPS network — no separate
Bayesian layer needed.

**Architectural pivot from epic #406:** The epic describes the mechanical
inference layer as "composable probability decision trees — like ONNX-style
weighted graphs." Issue #408 asks for "composable weighted trees." This
spec delivers CAPS (spreading activation networks) instead — a cyclic
graph with parallel constraint satisfaction, not a tree. This is the right
outcome: the research (#407) determined that decision trees cannot model
cyclic interactions (beliefs→behavior→beliefs), behavioral attractors, or
cross-model composition through shared nodes. Issue #408's body explicitly
anticipated this: "Research (#407) will determine which structure fits
best." The epic description (#406) and issue #408's title should be updated
to reflect the CAPS architecture. Downstream issues (#401, #397) are
unaffected — they consume behavioral attractors, not the computation graph.

### 2.3 Integration with Existing Architecture

- **Storage:** MindMap graph (nodes, typed edges, PAD affect, temporal validity)
  for behavioral attractor output (BEHAVIORAL subgraph). CAPS network state
  (connection weights, activation levels) stored separately in the #408 graph engine.
- **Processing:** ConsolidationPhase (idle-triggered, tenant-scoped, priority-ordered).
  CAPS settling runs as a new ConsolidationPhase implementation.
- **Input:** ExperienceEvent hierarchy (Observation, Action, Outcome) → graduated
  experience memories
- **Disposition:** DispositionAxes (5 dimensions) parameterise connection weights
- **Affect:** PAD model on nodes provides emotional valence natively
- **Updating:** Rescorla-Wagner prediction error rule (§5.1) within the CAPS network

**Tenant scoping:** ConsolidationPhase.run() takes `tenantId` — all CAPS
computation is tenant-scoped. The CAPS network topology (the "species-level"
graph from #408) is stored globally — it defines the universal node/connection
structure derived from the six psychological models. Per-agent weights are
tenant-scoped within the #408 graph engine, keyed by (tenantId, agentId).
During consolidation, the phase loads the global topology, applies per-agent
weights, processes new experiences, and writes updated weights back to the
tenant-scoped store.

**Relationship with CognitiveDerivationEngine:** CDE and CAPS disposition
modulation serve different purposes at different lifecycle stages:

- **CDE** runs once at agent creation. It derives 9 static cognitive defaults
  from DispositionAxes: personality weights, mood baseline (PAD), curiosity
  config, temporal focus, CBR strategy, social cognition (trust rates,
  conflict interpretation), graph structure, extraction bias, and appraisal
  weights. These are the agent's initial cognitive configuration.
- **CAPS disposition modulation** (§3.3) runs during every consolidation cycle.
  It modulates CAPS connection weights based on DispositionAxes — the same
  disposition values influence HOW the agent learns from experience, not just
  what defaults it starts with.

The two systems are complementary, not competing:
- CDE's `deriveMoodBaseline()` maps riskAppetite→pleasure, socialOrient→arousal,
  autonomy→dominance. This sets the PAD baseline that calibrates OCC emotion
  thresholds (see §2.4). CAPS's riskAppetite→BIS/BAS balance modulates how
  strongly approach vs avoidance LEARNING occurs — a different operation.
- CDE's `deriveSocialCognition()` maps socialOrient→`trustFormationRate`.
  This governs **relationship-specific** trust velocity — how quickly trust
  accumulates between two specific agents in the RelationshipStagePhase.
  CAPS's socialOrient trust pathway modulation governs **dispositional**
  trust learning — how strongly social experiences update the agent's
  general trust/distrust behavioral tendencies across all relationships.
  These operate at different abstraction levels: CDE/trustFormationRate
  governs dyadic trust (Agent A trusts Agent B); CAPS trust pathways
  govern trait trust (Agent A tends toward trusting vs distrusting behavior
  in general). When CAPS eventually subsumes DriveAdaptationPhase (see
  transition protocol below), CDE's trustFormationRate continues to serve
  its dyadic function — the two are not competing rates on the same system.
- CDE's `deriveAppraisalWeights()` maps ruleFollowing→selfStandardsStrictness.
  This calibrates OCC emotion intensity for Pride/Shame. CAPS's ruleFollowing
  modulation affects schema rigidity and guilt pathway learning rates.

No values conflict because CDE produces initial state and CAPS modulates
learning dynamics.

**Relationship with existing ConsolidationPhase implementations:**

The `cognition` module has three ConsolidationPhase implementations that
overlap in domain with the CAPS network. These are complementary, not
competing — they operate at different abstraction levels and use different
mechanisms:

| Phase | @Priority | Mechanism | CAPS Relationship |
|-------|-----------|-----------|-------------------|
| `BeliefRevisionPhase` | 16 | LLM-mediated belief-evidence contradiction detection | Complementary — BeliefRevisionPhase uses LLM to detect nuanced contradictions that mechanical pattern-matching cannot. CAPS provides mechanical schema activation via weight propagation. In the long term, CAPS schema activation can flag beliefs ripe for LLM revision, making BeliefRevisionPhase more targeted. |
| `DriveAdaptationPhase` | 17 | Simple reinforcement-based drive intensity modification (PAD → scalar drive modifier) | Subsumable — DriveAdaptationPhase's reward-based drive adaptation is a subset of what the CAPS operant conditioning model does. Once CAPS is operational with BIS/BAS, DriveAdaptationPhase's function is covered by the CAPS approach/avoidance system with richer interaction effects. Migration path: keep DriveAdaptationPhase as-is during CAPS development; retire it when the CAPS BIS/BAS model is validated. |
| `RelationshipStagePhase` | 18 | Familiarity scoring from interaction counts and sentiment | Input provider — RelationshipStagePhase tracks interaction history and familiarity between specific agents. This data feeds into the CAPS attachment model as relationship-specific input (§4.1's context-dependent update rules). The phase's output (familiarity score, interaction count, sentiment balance) becomes input events for the attachment theory chains. |

The `BehavioralSynthesisPhase` (new, @Priority 19) runs AFTER all three
existing cognitive phases, consuming their outputs as input:
- Graduated experience nodes from ExperienceConsolidationPhase (15)
- Revised beliefs from BeliefRevisionPhase (16) — contradiction events feed
  into CAPS schema revision
- Updated drive intensities from DriveAdaptationPhase (17) — until CAPS
  subsumes this function
- Relationship familiarity from RelationshipStagePhase (18) — relationship
  data feeds attachment model

**DriveAdaptationPhase → BehavioralSynthesisPhase transition protocol:**

During the overlap period where both phases coexist, the following rules
prevent conflicting behavioral signals:

1. **BehavioralSynthesisPhase reads DriveAdaptationPhase output as input.**
   Drive intensity values (from priority 17) feed into the CAPS BIS/BAS
   system as contextual modifiers — they set the initial BAS_activation
   resting value for the current cycle based on the drive-intensity node's
   value. This means DriveAdaptationPhase's output is consumed, not
   competing.
2. **LLM behavioral profile precedence:** For approach/avoidance domains
   where BOTH drive-intensity nodes AND CAPS behavioral attractors exist,
   the rendered behavioral profile includes ONLY the CAPS attractor. The
   drive-intensity node is marked with `caps_superseded=true` during
   BehavioralSynthesisPhase and excluded from the LLM behavioral profile.
   For domains where CAPS does not yet produce attractors (e.g., before
   CBT model implementation), drive-intensity nodes remain active in the
   profile.
3. **Retirement gate:** DriveAdaptationPhase is retired when the CAPS
   BIS/BAS model produces validated approach/avoidance attractors for ALL
   drive types currently managed by DriveAdaptationPhase. Validation
   requires: (a) CAPS attractors correlate with DriveAdaptationPhase
   output for a test character set, (b) behavioral plausibility review
   (§6.3) confirms CAPS output is at least as psychologically valid.

**Module placement:** The CAPS engine — network topology, weight storage,
settling algorithm, weight update rules — lives in the #408 graph engine
module. This is a new module at the same dependency level as
`mindmap-intelligence`: it depends on `mindmap-api` and `cognitive-api`
(for DispositionAxes) but NOT on `cognition`. The `BehavioralSynthesisPhase`
itself lives in `cognition` and depends on the #408 graph engine. This avoids
circular dependencies:
- `#408 graph engine` → `mindmap-api`, `cognitive-api`
- `cognition` → `#408 graph engine`, `mindmap-api`, `cognitive-api`

### 2.4 Integration with OCC Emotion System

The neocortex codebase has a fully implemented OCC (Ortony, Clore & Collins)
emotion appraisal architecture. The CAPS network and OCC system operate at
different psychological levels and are complementary:

**OCC operates at the situational level** — it appraises specific events
against goals (Hope, Fear, Satisfaction), actions against standards (Pride,
Shame), and objects against attitudes (Love, Hate). Each appraisal produces
a `CognitiveEmotion` with type, intensity, and PAD projection. OCC emotions
are transient — they fire during GoalAffectPhase (consolidation, priority 37)
and ActionAppraisalObserver (real-time) and decay within a tick cycle.

**CAPS operates at the dispositional level** — it models stable behavioral
tendencies that have been shaped by accumulated experience patterns. CAPS
produces behavioral attractors (enduring traits), not momentary emotions.

The relationship between overlapping constructs:

| CAPS construct | OCC construct | Relationship |
|---------------|---------------|-------------|
| `BIS_activation` | Fear (prospect emotion) | BIS_activation is a **trait sensitivity parameter** — how reactive the BIS system is. OCC Fear is a **state emotion** triggered by a specific threatening event. High BIS_activation means lower threshold for OCC Fear onset (mediated through `AppraisalWeights.fearOnsetThreshold` from CDE). |
| `FFFS_activation` | Fear (high intensity) | FFFS_activation is the **acute response system** engagement level. OCC Fear at high intensity activates FFFS. The distinction: OCC classifies the emotion; FFFS determines the behavioral response (fight/flight/freeze). |
| `BAS_activation` | Joy, Hope (prospect emotions) | BAS_activation is the **approach motivation trait**. OCC Joy/Hope are state emotions. High BAS_activation → lower threshold for approach-related emotions and behaviors. |
| `arousal_level` | PAD arousal on MindMapNode | Different constructs. CAPS `arousal_level` tracks the global activation state for window-of-tolerance computation (§4.4). PAD arousal on MindMapNode tracks the emotional tone of a specific knowledge node. They share a name but serve different purposes. |
| `threat_sensitivity` | `AppraisalWeights.fearOnsetThreshold` | `threat_sensitivity` in CAPS tracks sensitization from accumulated trauma experiences (kindling model). `fearOnsetThreshold` from CDE is the personality-derived initial threshold. Trauma history lowers `threat_sensitivity` below the CDE-derived baseline. |

**Integration mechanism:** OCC emotions feed into CAPS as reinforcement
signals during consolidation — they modulate connection weight updates,
not input node activations. The situation vocabulary (§3.2) classifies
the INPUT EVENT via the SituationClassifier. OCC emotions determine the
REINFORCEMENT DIRECTION and INTENSITY for weight updates on connections
that were active during that event's processing.

**EmotionType → reinforcement signal mapping:**

| EmotionType | λ sign | CAPS pathway affected | Rationale |
|-------------|--------|----------------------|-----------|
| FEAR, FEARS_CONFIRMED | λ < 0 | BIS strengthening, FFFS threshold lowering | Negative reinforcement of threat-related connections |
| HOPE, RELIEF | λ > 0 | BAS approach pathway | Positive reinforcement of goal-approach connections |
| JOY, SATISFACTION, GRATIFICATION | λ > 0 | BAS reward pathway, self_worth positive | Positive outcome confirms active behavioral strategy |
| DISTRESS, DISAPPOINTMENT | λ < 0 | BIS strengthening, future_outlook negative | Negative outcome weakens active approach connections |
| PRIDE, ADMIRATION | λ > 0 | self_efficacy, self_worth positive | Self/other standard-exceeding strengthens competence pathways |
| SHAME, REPROACH | λ < 0 | self_worth negative, guilt pathway | Standard-violating weakens self-model, strengthens compliance |
| ANGER, RESENTMENT | λ < 0 | FFFS fight pathway, other_reliability negative | Attribution of blame strengthens fight/assert connections |
| LOVE, HAPPY_FOR, GRATITUDE | λ > 0 | other_reliability positive, trust pathway | Positive other-appraisal strengthens social approach |
| HATE, GLOATING | λ < 0 | other_safety negative, distrust pathway | Negative other-appraisal strengthens social avoidance |
| PITY | λ > 0 (mild) | other_reliability, social_comparison | Mixed: prosocial but with downward comparison |
| REMORSE | λ < 0 | self_worth negative, guilt → compliance | Compound: self-blame + other-harm awareness |

The `intensity` field on `CognitiveEmotion` maps to `β_outcome` in the
Rescorla-Wagner equation (§5.1). The PAD projection on the emotion record
provides additional modulation: high arousal amplifies the learning rate
(modifier = 1 + β × |arousal|, per §5.3).

**Timing:** BehavioralSynthesisPhase runs at @Priority(19);
GoalAffectPhase runs at @Priority(37). Within a single consolidation
cycle, CAPS settling completes before GoalAffectPhase produces its OCC
emotions. Consequently:
- **Goal-based OCC emotions** (from GoalAffectPhase) produced in cycle N
  are stored by `AffectTrajectoryDecorator`, graduated by
  ExperienceConsolidationPhase (15) in cycle N+1, and processed by CAPS
  (19) in cycle N+1. This is a **one-cycle delay**.
- **Action-based OCC emotions** (from ActionAppraisalObserver, which fires
  between cycles during real-time interaction) are stored before the next
  consolidation cycle begins and are available to CAPS immediately in the
  next cycle.

The one-cycle delay for goal-based emotions is psychologically appropriate:
behavioral tendencies are slow-changing dispositions, not reactive to
single emotion events. A one-cycle lag between emotional appraisal and
behavioral tendency update is consistent with the trait/state distinction
this architecture models.

## 3. Meta-Model: Translating Psychology to Network Structure

### 3.1 The Seven-Step Translation Framework

Any psychological model can be translated into CAPS network components
through these steps:

**Step 1 — Identify the cause-effect chain.** What does the model claim?
What input (experience/situation) leads to what output (behavioral tendency)
through what mediating process?

**Step 2 — Map inputs to situation vocabulary nodes.** Abstract the model's
input conditions into reusable situation-type categories. Use existing
vocabulary where possible; extend only when a genuinely new input type is
needed.

**Runtime classification (Step 2 at runtime):** During consolidation, each
ExperienceEvent's `description()` and `metadata()` are classified into
situation vocabulary categories (§3.2) by a `SituationClassifier`
component in the #408 graph engine module. This is a **multi-label
classifier** using per-label sigmoid activation with threshold-based label
assignment — NOT the existing single-label `TextClassifier` from
inference-tasks, which uses softmax+argmax and returns exactly one label.
Multi-label classification is required because a single event can map to
multiple situation categories (e.g., a betrayal event activates both
`betrayal` and `social_threat`). The `SituationClassifier` wraps an ONNX
model trained with binary cross-entropy loss per label, producing
independent [0, 1] confidence scores for each situation vocabulary node.
Classifications are stored as properties on the experience record in the
#408 graph engine (key: `situation_types`, value: JSON array of
`{type, confidence}` pairs). Classification runs once per experience during
the first consolidation cycle that processes it; results are cached for
subsequent cycles.

**Classification failure handling:**
- **Per-experience isolation:** Classification failures are per-experience,
  not per-phase. A failed classification does not abort the
  BehavioralSynthesisPhase or block other experiences.
- **Retry next cycle:** Failed classifications do NOT advance the experience
  cursor past the failed event. The experience remains unclassified and is
  retried in the next consolidation cycle. Successfully classified
  experiences before and after the failure are processed normally.
- **Retry limit:** After 3 consecutive failed classification attempts across
  cycles, the experience is marked `classification_failed` and the cursor
  advances past it. The failure is logged with the experience ID and error
  details.
- **Minimum confidence threshold:** Classifications with confidence below
  0.3 are discarded — they do not activate CAPS input nodes. An experience
  where ALL classifications fall below 0.3 is treated as unclassified and
  retried next cycle (it counts toward the retry limit).
- **Partial classification:** If an experience produces some classifications
  above 0.3 and some below, only the above-threshold classifications are
  used. The experience is considered successfully classified.

**Step 3 — Map mediating variables to cognitive-affective processing units.**
These are the model's internal constructs: beliefs, affects, goals,
expectancies, self-regulatory plans, competencies (Mischel's six unit types).

**Step 4 — Map outputs to behavioral tendency nodes.** What does the model
predict the person will DO? Abstract to reusable behavioral categories.

**Step 5 — Derive connection weights.** For each connection (input→mediator,
mediator→mediator, mediator→output), derive an initial weight from:
- Published effect sizes (empirical — strongest)
- Meta-analysis consensus (consensus — moderate)
- Clinical/theoretical derivation (estimated — weakest)
Classify each weight's provenance.

**Step 6 — Identify disposition modulation points.** For each connection,
determine which of the 5 DispositionAxes dimensions affect its weight and
in which direction. Not all connections are modulated — some are universal.

**Step 7 — Identify composition interfaces.** Which mediating nodes are
shared with other models? These shared nodes are how models compose naturally
in the CAPS network.

### 3.2 Node Type Taxonomy

**Input nodes (situation vocabulary):**

| Category | Nodes | Source models |
|----------|-------|---------------|
| Relationship | secure_attachment, inconsistent_care, rejection, betrayal, abandonment, neglect | Attachment, CBT |
| Threat | physical_threat, social_threat, psychological_threat, unpredictable_danger | Trauma, BIS/BAS |
| Achievement | mastery, failure, performance_judged, competence_recognition | Operant, CBT |
| Social | acceptance, exclusion, dominance, submission, modeling_observed | Bandura, Attachment |
| Resource | scarcity, abundance, competition, sharing | Operant, BIS/BAS |
| Autonomy | agency_granted, controlled, choice_available, powerlessness | Trauma, CBT |
| Consequence | reward, punishment, reward_removal, aversive_removal | Operant |

**Mediating nodes (cognitive-affective processing units):**

| Category | Nodes | Source models |
|----------|-------|---------------|
| Self-model | `self_worth`, `self_efficacy` | Attachment (IWM), CBT (core beliefs), Bandura |
| Other-model | `other_reliability`, `other_safety` | Attachment (IWM), CBT |
| World-model | `world_predictability`, `future_outlook` | CBT (cognitive triad) |
| Affect systems | `BAS_activation`, `BIS_activation`, `FFFS_activation`, `arousal_level` | BIS/BAS, Trauma |
| Threat processing | `threat_sensitivity`, `autonomic_activation`, `escape_assessment` | Trauma, BIS/BAS |
| Response-outcome | `reinforcement_expectation`, `punishment_expectation` | Operant |
| Social cognition | `model_attention`, `behavior_schema`, `behavior_attempt`, `behavior_persistence`, `vicarious_learning`, `social_comparison` | Bandura |

The first three categories (Self-model, Other-model, World-model) contain
**6 bipolar nodes**, each with a continuous [-1, +1] activation range:

| Bipolar Node | Negative pole (-1) | Positive pole (+1) |
|-------------|-------------------|-------------------|
| `self_worth` | worthless / helpless / incompetent | worthy / competent / capable |
| `self_efficacy` | incompetent / incapable | capable / competent |
| `other_reliability` | hostile / unreliable | supportive / reliable |
| `other_safety` | dangerous / judgmental | safe / accepting |
| `world_predictability` | dangerous / unfair | safe / fair |
| `future_outlook` | hopeless | hopeful |

The pole labels in this table (e.g., "worthless", "helpless", "hostile",
"safe", "fair", "hopeful") are NOT separate nodes — they are semantic
labels for regions of the continuous [-1, +1] dimension. The mediating
nodes table above lists exactly the 6 bipolar nodes; the bipolar table
here expands their semantics. Names like `self_helpless`, `self_competence`,
`others_hostile`, `world_fairness`, `world_safety`, `future_hopeless` that
appear in the psychological literature map to activation regions of these
6 nodes, not to separate network entries. The network has 6
self/other/world-model nodes, not 12.

When §4.3 (CBT) references "6 bipolar nodes with activation range
[-1, +1]," it is referring to these 6 nodes. The 12 named endpoints
(6 nodes × 2 poles) are semantic descriptors, not separate nodes.

**Output nodes (behavioral tendencies):**

| Category | Nodes |
|----------|-------|
| Approach/Avoidance | approach, withdraw, cautious_approach |
| Social | trust, distrust, proximity_seek, distance_maintain |
| Conflict | fight, flight, freeze, fawn, assert, submit |
| Achievement | persist, abandon, perfectionism, risk_take |
| Autonomy | comply, rebel, self_direct, depend |
| Learning | explore, consolidate, generalize, discriminate |

**Taxonomy management:**

- **Shared topology, per-agent weights.** The node taxonomy defines the
  species-level graph — all agents share the same network structure. What
  differs per agent is the connection weights and activation thresholds,
  stored in the #408 graph engine keyed by (tenantId, agentId).
- **Registration:** Node types are registered in the #408 graph engine's
  topology definition, not via `MindMapVocabulary` (which handles edge types
  only). The topology is defined declaratively — a static graph description
  loaded at startup that specifies all nodes, their types, and the
  connections between them with default weights.
- **Versioning:** Topology changes (adding nodes, renaming, splitting) are
  handled via graph engine migrations analogous to Flyway database
  migrations. When a node is renamed, the migration updates the topology
  and remaps per-agent weights to the new node ID. When a node is split,
  the migration creates the new nodes and distributes the original weights
  proportionally. Existing per-agent state is preserved through migration.
- **Vocabulary growth:** New psychological models add nodes through the
  seven-step translation framework (§3.1). Step 2 ("use existing vocabulary
  where possible") constrains growth — new input nodes are added only when
  no existing node captures the concept. The composition analysis (§4.7)
  provides the governance mechanism: shared nodes are preferred over
  model-specific ones.

### 3.3 Disposition Modulation Map

Each DispositionAxes dimension modulates specific connection weights:

**socialOrient:**
- independent → social_input→self_worth ×0.5, mastery→self_worth ×1.3
- cooperative → acceptance→self_worth ×1.3, other_reliability→trust ×1.2
- competitive → performance_judged→self_worth ×1.3, BAS_activation→approach ×1.2

**ruleFollowing:**
- flexible → punishment→behavioral_suppression ×0.7, schema_rigidity ×0.8
- moderate → all connections ×1.0 (no modulation)
- strict → norm_violation→guilt ×1.4, rule_formation ×1.3, schema_rigidity ×1.3

**riskAppetite:**
- conservative → BIS connections ×1.3, threat_sensitivity threshold ×1.2, BAS connections ×0.7
- calculated → BIS ×1.05, BAS ×0.95 (near-neutral)
- bold → BAS connections ×1.3, threat_sensitivity threshold ×0.7, BIS connections ×0.7

**autonomy:**
- low → compliance connections ×1.3, agency→satisfaction ×0.6
- moderate → all connections ×1.0 (no modulation)
- high → self_direction connections ×1.3, external_control→compliance ×0.6

**conflictMode:**
- cooperative → fawn/accommodate pathways ×1.3
- competitive → fight/assert pathways ×1.3
- analytical → cautious_approach ×1.3, delay_response ×1.2
- avoidant → flight/freeze/withdraw pathways ×1.3

These modifiers follow the same pattern as CognitiveDerivationEngine's
lookup tables (e.g., `SOCIAL_TRUST_RATE`, `AUTONOMY_STRUCTURAL`). A
`DispositionWeightMapper` will convert categorical axis values to these
numerical modifiers. The ×1.0 default for unknown values is the
multiplicative identity — it means "no modulation," analogous to CDE's
context-specific additive defaults (0.0 for PAD dimensions in [-1,1],
0.5 for trust formation rate in [0,1]). Both systems use the neutral
element for their respective mathematical operation.

**Unmapped value handling:** Unknown or new axis values default to ×1.0
(no modulation) to prevent agent creation failures when DispositionAxes
gains new enum variants. However, silent ×1.0 fallback could produce
psychologically invalid behavior if a new variant should have produced
meaningful modulation. The mapper must:
- Log a warning including the axis name, unmapped value, and agent ID
- Record the unmapped encounter in a `disposition_coverage` health metric
  on the BehavioralSynthesisPhase, surfaced in consolidation health
  reporting (not just application logs)
- In test environments (detected via configuration flag), throw
  `IllegalStateException` for unmapped values to catch coverage gaps
  during development

## 4. Composed Topology — Six Models Unified

### 4.1 Attachment Theory (Bowlby, Ainsworth)

**Core insight:** Internal working models (IWMs) are probabilistic computations
about caregiver responsiveness that crystallize into stable self/other models.

#### Cause-Effect Chains

1. Consistent responsiveness → secure base script → positive self + positive other →
   comfort with intimacy, balanced autonomy/closeness
2. Inconsistent responsiveness → uncertain other-model → hypervigilance to rejection →
   proximity-seeking + reassurance-demanding (variable-ratio reinforcement — see §4.5)
3. Rejecting/unavailable caregiver → negative other-model → defensive positive self →
   suppression of attachment needs → emotional distancing
4. Frightening caregiver (source of both safety and threat) → contradictory models →
   no coherent strategy → oscillation between approach and avoidance

#### Four Styles as Attractor States

| Style | Self-Model | Other-Model | Behavioral Output |
|-------|-----------|-------------|-------------------|
| Secure | Positive (worthy) | Positive (reliable) | Flexible intimacy, secure base use |
| Anxious-Preoccupied | Negative (doubts worth) | Positive but uncertain | Clings, monitors for rejection, recall bias |
| Dismissive-Avoidant | Positive (defensive) | Negative (unreliable) | Self-reliance, withdrawal in conflict |
| Fearful-Avoidant | Negative (unworthy) | Negative (dangerous) | Oscillation, dissociation under stress |

#### Quantitative Data

| Link | Effect Size | Provenance |
|------|-------------|------------|
| Caregiver sensitivity → security | r = .24–.32 | Empirical (van IJzendoorn 1995; Madigan et al. 2024) |
| Attachment anxiety → negative mental health | r = .42 | Empirical (Zhang et al. 2022, N=79,722) |
| Attachment avoidance → negative mental health | r = .28 | Empirical (Zhang et al. 2022) |
| Insecure → externalizing behavior | d = .37 | Empirical (Deneault et al. 2021) |
| Big Five → self-model variance | 48% | Empirical (Griffin & Bartholomew 1994) |
| Big Five → other-model variance | 27% | Empirical (Griffin & Bartholomew 1994) |
| No genetic component to secure/insecure | — | Empirical (behaviour-genetic studies) |

#### Update Rules (Earned Security)

Attachment patterns are among the most change-resistant. Update characteristics:
- **Slow:** Low learning rate relative to other models
- **Asymmetric:** Negative experiences update faster than positive (single betrayal
  undoes years of trust-building)
- **Context-dependent:** New models may be relationship-specific before generalizing
- **Not complete erasure:** Old attractor weakens but persists — earned-secure adults
  still show slightly elevated depression risk vs continuously-secure

Mechanisms: repeated corrective experience (gradual Hebbian), coherent narrative
formation (new interpretive pathways), co-regulation (external activation dampening)

### 4.2 Reinforcement Sensitivity Theory (Gray's BIS/BAS/FFFS)

**Core insight:** Three interacting systems (BAS, BIS, FFFS) jointly determine
behavioral output. The Joint Subsystems Hypothesis confirms all three must be
modeled together — isolated chains miss the interaction effects.

#### Three Systems

**BAS (Behavioral Activation System):**
- Inputs: reward signals, non-punishment cues, goal-relevant opportunities
- Mediating: dopaminergic reward prediction error. Sub-dimensions: Drive
  (persistence), Fun Seeking (novelty/impulsiveness), Reward Responsiveness
- Output: approach behavior, positive affect, goal-directed action

**BIS (Behavioral Inhibition System) — revised RST:**
- Inputs: goal conflict (BAS and FFFS simultaneously active, or competing goals)
- Mediating: inhibits ongoing behavior, increases risk assessment, promotes
  cautious approach ("defensive approach")
- Output: anxiety (not fear), behavioral inhibition, passive avoidance

**FFFS (Fight-Flight-Freeze System):**
- Inputs: all aversive/threatening stimuli
- Mediating: fear activation, response selection by **defensive distance**
  (close → fight, intermediate → flight, distant/uncertain → freeze)
- Output: fight, flight, or freeze; subjective fear and panic

#### Quantitative Data

| Link | Effect Size | Provenance |
|------|-------------|------------|
| BIS → anxiety | g = 1.21 | Empirical (meta-analysis, 204 studies) |
| BIS → depression | g = 0.99 | Empirical (meta-analysis) |
| BAS → depression | g = −0.21 | Empirical (meta-analysis) |
| Childhood neglect → BIS shift | ρ = 0.24 | Empirical (Tamada et al. 2025 SEM) |
| Indirect: childhood abuse → BIS → depression | β = 0.092 | Empirical (SEM path) |
| Severe trauma (ACE ≥ 6/7) → BAS decrease | threshold effect | Empirical (Miu et al.) |

#### Experience-Based Modification

- Aversive experiences → BIS weight increase (proportional to intensity)
- Threshold effect: severe trauma reduces BAS (anhedonia), lower severity does not
- Repeated uncontrollable threat → FFFS freeze weights increase (learned helplessness)
- Conflict resolution success → BIS weight decrease
- Stress sensitization creates neuronal ensembles preferentially reactivated by
  later stress (Hebbian mechanism — literally "neurons that fire together wire together")

#### Computational Reference

Pickering's MATLAB simulation provides actual equations for the three-system
interaction: excitation, cross-system inhibition, and decay terms. Brown (2017)
adapted these for CTA integration. Available at
`homepages.gold.ac.uk/aphome/newrst4.m`

### 4.3 Cognitive Model (Beck's CBT)

**Core insight:** Schemas operate on a quantitative continuum (Beck & Haigh 2014).
The difference between normal and pathological is the *degree* of bias, not its
kind. Schema activation has three CAPS-mappable parameters: threshold, permeability,
and valence.

#### The Cognitive Chain

Activating situation → Schema activation → Core beliefs → Intermediate beliefs
(rules/attitudes) → Automatic thoughts → Emotional response → Behavioral response.

Self-reinforcing loop: activated schemas bias downstream processing through
cognitive distortions, which selectively attend to confirming evidence —
strengthening their own input pathways.

#### Core Belief Nodes (Cognitive Triad)

6 bipolar nodes with activation range [-1, +1] (see §3.2 for the
definitive node list). The negative and positive poles below are
endpoints of continuous dimensions, not separate nodes:

| Domain | Negative pole (-1) | Positive pole (+1) |
|--------|-------------------|-------------------|
| Self | worthless, incompetent, helpless | worthy, competent, capable |
| Others | hostile, unreliable, judgmental | supportive, reliable, safe |
| World/Future | dangerous, unfair, hopeless | safe, fair, hopeful |

#### Schema Activation Parameters → CAPS Mapping

| Parameter | Meaning | CAPS equivalent |
|-----------|---------|-----------------|
| Activation threshold | Trigger strength needed | Node bias / resting activation |
| Permeability | How many situation types activate it | Number of input connections |
| Charge/valence | Emotional intensity | Weight of schema→emotion connections |

Beck's **modes** (clusters of co-activating schemas) ARE CAPS attractor states.

#### Cognitive Distortions as Weight Multipliers

Distortions are NOT separate nodes — they are multipliers on existing connections:

| Distortion | Effect | Multiplier |
|------------|--------|-----------|
| All-or-nothing | Amplifies extreme poles | ×1.5–2.0 |
| Overgeneralization | Increases permeability | +0.2–0.4 to unrelated inputs |
| Catastrophizing | Amplifies negative predictions | ×1.5–2.0 |
| Mental filtering | Suppresses positive inputs | ×0.3–0.5 |
| Emotional reasoning | Reverse connection emotion→belief | +0.3 new connection |

**Activation mechanism:** Distortions are activated by their parent schema's
activation level. Each distortion has an activation threshold on the schema
it modifies — when the schema's activation exceeds this threshold, the
distortion's weight multiplier applies to all connections passing through
that schema. Thresholds use a **polarity-weighted** formula that activates
distortions more readily for negative schemas than positive ones:

`distortion_threshold = base_threshold × (1 − negative_weight × max(0, −schema_strength) − positive_weight × max(0, schema_strength))`

Where `negative_weight = 1.0` and `positive_weight = 0.3`.

This reflects Beck's CBT model: the five listed distortions
(all-or-nothing, overgeneralization, catastrophizing, mental filtering,
emotional reasoning) are negative-bias mechanisms that primarily maintain
*negative* schemas. A strong negative schema (self_worth = -0.8 →
threshold = base × 0.2) activates distortions easily, creating the
positive feedback loop central to CBT theory. A strong positive schema
(self_worth = +0.8 → threshold = base × 0.76) provides only mild
distortion activation — positive schemas can still produce some rigidity
(e.g., all-or-nothing thinking about one's own competence) but not the
cascading bias that characterizes clinical depression or anxiety. A
neutral schema (0.0 → threshold = base × 1.0) produces no distortion
activation.

The asymmetry is the key insight: Beck's cognitive triad specifically
describes negative schemas of self, world, and future. Symmetric treatment
(using `|schema_strength|`) would incorrectly predict that a person with
healthy self-worth (+0.8) has the same distortion susceptibility as
someone with negative self-worth (-0.8). Base thresholds per distortion type:

| Distortion | Base threshold |
|------------|---------------|
| All-or-nothing | 0.6 |
| Overgeneralization | 0.5 |
| Catastrophizing | 0.7 |
| Mental filtering | 0.4 |
| Emotional reasoning | 0.8 |

When multiple distortions are active simultaneously, they compose
multiplicatively (catastrophizing ×1.5 + mental filtering ×0.4 on positive
inputs = compounded negative bias). Additive effects (overgeneralization
+0.2 to unrelated inputs) add new connections rather than modifying existing
weights, so they don't multiply with other distortions.

**Numerical bounds (mandatory):**
- **Compound distortion cap:** The cumulative multiplicative distortion
  factor is clamped to [0.1, 3.0]. Even with all-or-nothing (×2.0) +
  catastrophizing (×2.0) both active, the effective multiplier is capped
  at 3.0, not 4.0. This prevents numerical explosion while preserving
  clinically meaningful amplification.
- **Effective weight cap:** After distortion application, the effective
  connection weight (excitatory × distortion_factor − inhibitory) is
  clamped to [-2.0, +2.0]. Combined with activation clamping at [-1, +1]
  (§2.1), this bounds the maximum input to any node.
- **Threshold floor:** Activation thresholds cannot fall below 0.05,
  regardless of trauma sensitization (§5.6). This prevents division-by-
  near-zero in threshold-based computations and ensures even maximally
  sensitized nodes require non-trivial input to activate.

Disposition-dependent: ruleFollowing=strict amplifies "should" distortions.

#### Quantitative Data

| Link | Effect Size | Provenance |
|------|-------------|------------|
| CBT vs controls (anxiety) | g = 0.51 | Empirical (meta-analysis) |
| Sudden gains predict outcome | g = 0.61–0.68 | Empirical |
| Sudden gains frequency (depression) | 40–50% of patients | Empirical |
| Schema change timeline | 12–20 sessions (3–6 months) | Consensus |
| Smith et al. Active Inference harm aversion | −12 in preference matrix | Empirical (computational model) |

#### Schema Change Mechanisms

**Gradual restructuring (primary):** ~5–10% weight reduction per disconfirming
episode. Requires consistent evidence — sporadic disconfirmation is filtered.

**Sudden gains (secondary):** Phase transition — accumulated evidence reaches
tipping point where schema "flips" to competing attractor state. 40–50% of
depression patients. Validates CAPS attractor architecture.

**Smith et al. insight:** Most effective change induces **uncertainty** (P=0.5)
first, not direct belief reversal. Weaken existing schema precision before
strengthening the competitor

### 4.4 Trauma Response Models

**Core insight:** Trauma sensitization follows a kindling model — repeated
sub-threshold stimulation progressively lowers activation thresholds. The
dose-response is multiplicative: each additional ACE multiplies risk, not
adds to it.

#### 4F Model (Walker) — Chronic Patterns

| Response | Trigger Profile | Chronic Pattern | Disposition Bias |
|----------|----------------|-----------------|------------------|
| Fight | Proximate threat, capacity to overpower | Controlling, narcissistic defenses | bold, competitive |
| Flight | Escapable, moderate intensity | Workaholism, compulsive busyness | conservative, strict |
| Freeze | Inescapable, overwhelming, no social recourse | Dissociation, learned helplessness | low autonomy, avoidant |
| Fawn | Threat from attachment figure | People-pleasing, codependency | cooperative (both axes) |

Most survivors are hybrid types (primary + secondary). The network models this
as competing attractor strengths, not single classification.

#### Polyvagal Hierarchy — Activation Cascade

Three-tiered dissolution sequence under escalating threat:
1. **Ventral vagal** (social engagement) → prosocial, calm, connection
2. **Sympathetic** (mobilization) → fight/flight
3. **Dorsal vagal** (immobilization) → freeze, shutdown, dissociation

Newer circuits disinhibit in reverse evolutionary order. Maps to cascading
activation in the CAPS network: each tier has an activation threshold, and
exceeding it disengages the current tier and engages the next.

**Neuroception** (subconscious threat evaluation) maps to the input classifier —
operates before conscious processing, biased by trauma history.

#### Window of Tolerance (Siegel) → CAPS Activation Range

- Within window: normal processing, flexible response selection
- Hyperarousal (above): fight/flight dominate
- Hypoarousal (below): freeze/collapse/dissociation

Trauma narrows the window (sensitization). Recovery widens it. The window IS
the activation threshold range on the global arousal node.

#### Sensitization Mechanism (Kindling)

- Repeated stress → elevated CRF in amygdala
- Loss of inhibitory interneurons → reduced dendritic inhibition
- Prefrontal inhibitory control weakens simultaneously
- Result: less intense triggers produce greater responses over time
- Dose-response: episode 1 → 50% recurrence; episode 2 → 70%; episode 3 → 90%

**CAPS encoding:** Traumatic experiences don't just increase connection weight —
they also LOWER the activation threshold. Decay resistance proportional to
intensity × repetition count.

#### Quantitative Data (ACE Dose-Response)

| Outcome | OR (4+ ACEs vs 0) | Provenance |
|---------|-------------------|------------|
| Suicide attempt | 12.2× | Empirical (Felitti et al. 1998) |
| Injected drugs | 10.0× | Empirical |
| Alcoholism | 7.4× | Empirical |
| Depressed mood | 4.6× | Empirical |
| Depressed mood prevalence shift | 14% → 51% | Empirical |
| Suicide attempt prevalence (7+ ACEs) | OR = 31.1× | Empirical (Dube et al. 2001) |
| Resilience reduction (4+ ACEs with resources) | 29% → 14% illness | Empirical (Hughes 2017) |

#### Recovery Mechanism

Positive experiences build competing connections via the same Hebbian mechanism
that built trauma connections. Key asymmetry: trauma connections have HIGH decay
resistance (kindling), so recovery requires ACTIVE competing connections, not
passive decay. Weight update for recovery:
`safety_weight += learning_rate × intensity` while
`trauma_weight -= (1/decay_resistance) × safety_activation`

### 4.5 Operant Conditioning

**Core insight:** Operant conditioning is not just one model among six — it is
the **universal weight update engine** for the entire CAPS network. The
Rescorla-Wagner prediction error rule provides the mathematical foundation.

#### The Four Contingencies as Weight Update Rules

| Contingency | λ value | Weight change |
|-------------|---------|---------------|
| Positive reinforcement (→ reward) | λ > 0 | Strengthen behavior→outcome |
| Negative reinforcement (→ aversive removed) | λ = 0 (expected absent) | Strengthen behavior→escape |
| Positive punishment (→ aversive) | λ < 0 | Weaken behavior, strengthen avoidance |
| Negative punishment (→ reward removed) | λ = 0 (expected absent) | Weaken behavior→reward |

#### Rescorla-Wagner Prediction Error Rule

`ΔW = α × β × (λ − ΣW)`

Where α = stimulus salience (0–1), β = outcome intensity (0–1),
λ = actual outcome, ΣW = predicted outcome. Learning occurs only when
outcomes differ from expectations.

**Pearce-Hall extension:** Dynamic learning rate where α ≈ |λ − V| from
previous trial — attention increases when outcomes are surprising, decreases
when predictable. Novel experiences produce larger weight updates.

#### Reinforcement Schedules and Extinction Resistance

| Schedule | Extinction Resistance | Attractor Formation Speed |
|----------|----------------------|--------------------------|
| Continuous (CRF) | Lowest | Fastest |
| Fixed ratio (FR) | Moderate | Fast |
| **Variable ratio (VR)** | **Highest (2–4× CRF)** | Fast, steady |
| Fixed interval (FI) | Low | Slow |
| Variable interval (VI) | Moderate-high | Moderate |

VR-reinforced connections should have the highest weight stability (lowest
effective decay rate). This explains why anxious attachment (variable-ratio
reinforcement from inconsistent caregiver) is so resistant to change.

#### Extinction as Inhibitory Overlay (NOT Unlearning)

Extinction creates a competing inhibitory connection rather than zeroing the
original weight. Evidence: spontaneous recovery, renewal, reinstatement.

**Dual-weight architecture per connection:**
- Original weight: decays slowly (or not at all)
- Inhibitory overlay: decays faster
- Spontaneous recovery = decay of inhibition while original persists

This means every connection in the CAPS network needs TWO weight values:
the excitatory weight and the inhibitory overlay.

#### Universal Weight Update for CAPS

See §5.1 for the canonical weight update equation (Rescorla-Wagner +
Pearce-Hall with disposition_modifier). The equation is defined once
in §5.1 to avoid divergence.

#### Quantitative Parameters

| Parameter | Value | Provenance |
|-----------|-------|------------|
| Learning rate (α × β product) | 0.01–0.25 per trial | Empirical |
| β range | 0.1–0.5 | Empirical |
| VR extinction resistance | 2–4× CRF | Empirical (behavioral momentum) |
| Temporal discount factor γ | 0.9–0.99 | Empirical (TD learning) |

### 4.6 Social Learning Theory (Bandura)

**Core insight:** Social learning adds a fundamentally different input channel —
learning from others' experiences, not just your own. Self-efficacy is the
critical gate between knowledge and action (14% variance explained, the largest
single predictor).

#### Observational Learning Chain

| Stage | Network Node | Activation Depends On |
|-------|--------------|-----------------------|
| Attention | model_attention | Model salience (status, similarity, warmth) |
| Retention | behavior_schema | Cognitive rehearsal, complexity |
| Reproduction | behavior_attempt | Self-efficacy (critical gate), capability |
| Motivation | behavior_persistence | Vicarious/direct/self reinforcement |

Feedback loop: successful reproduction → strengthened self-efficacy →
increased future reproduction probability. Inherently recurrent — CAPS
handles natively.

#### Vicarious vs Direct Reinforcement

Vicarious reinforcement is **15–25% the strength** of direct experience.
Effects are temporary without subsequent direct reinforcement.

Weight update rules:
- Direct: `Δw = α × outcome × activation`
- Vicarious: `Δw = α × β_vicarious × outcome × model_similarity × activation`
  where β_vicarious ≈ 0.3–0.5

#### Self-Efficacy — Four Sources (Empirically Ranked)

| Source | Relative Weight | Duration |
|--------|----------------|----------|
| Mastery experience | 1.0 (reference) | Lasting |
| Vicarious experience | ~0.6 | Moderate |
| Verbal persuasion | ~0.4 | Short-lived without mastery |
| Physiological states | ~0.3 | Transient |

Self-efficacy is domain-specific (effect sizes 2× larger for domain-specific
vs general measures). In the network, separate self_efficacy nodes per
behavioral domain. Domains map to the output node categories defined in
§3.2: approach/avoidance, social, conflict, achievement, autonomy, learning.
Each domain gets one self_efficacy node (6 total), activated by mastery
experiences within that domain. The generic `self_efficacy` node in §3.2's
mediating nodes table is the aggregate — computed as the weighted average
of domain-specific efficacy nodes, weighted by recent activation frequency.

#### Reciprocal Determinism

Person ↔ Behavior ↔ Environment — all bidirectional. Inherently cyclic.
Validates CAPS architecture (impossible in Bayesian DAG).

#### Quantitative Data

| Link | Effect Size | Provenance |
|------|-------------|------------|
| Self-efficacy → performance variance | ~14% | Empirical (Multon et al. 1991) |
| SE intervention effect (controlled) | g = 0.27–0.47 | Empirical |
| Domain-specific vs general SE | 2× effect | Empirical (Sitzmann & Ely 2011) |
| Vicarious vs direct discount | 0.15–0.25× | Empirical |

#### Unique Contribution

Without Bandura, characters can only learn from direct experience. Social
learning adds: observational acquisition (schemas without experience),
vicarious emotional learning (fears learned by watching), self-efficacy as
universal behavioral gate, model-based social influence

### 4.7 Composition Analysis — Shared Nodes

Models compose through shared mediating nodes. These shared nodes are where
the CAPS network's compositional power emerges — activation from one model
propagates through shared nodes to influence another model's processing.

| Shared Node | Models | Interaction |
|-------------|--------|-------------|
| `self_worth` | Attachment (IWM of self), CBT (core belief `self_worthless`↔`self_worthy`), Bandura (self-efficacy) | Attachment sets developmental baseline; CBT maintains/modifies through schema activation; Bandura's self-efficacy gates behavioral reproduction. All three write to the same node — their combined activation determines the stable self-model. |
| `other_reliability` | Attachment (IWM of others), CBT (core belief `others_unreliable`↔`others_supportive`) | Attachment sets baseline from caregiver experience; CBT schema activation reinforces or challenges. Collins' hierarchical IWM model maps to Beck's schema hierarchy. |
| `threat_sensitivity` | BIS (sensitivity parameter), Trauma (kindling/sensitization) | BIS provides trait baseline; trauma history raises sensitivity via amygdala kindling. Childhood neglect shifts BIS at trait level (ρ=0.24). Additive: high BIS + trauma history produces maximum sensitivity. |
| `FFFS_activation` | BIS/BAS (FFFS system), Trauma (4F response) | Same neurobiological system. BIS/BAS sets baseline activation parameters; trauma creates specific trigger patterns and lowers thresholds. "Fawn" is BIS-mediated conflict resolution co-opting BAS social-approach — unique to attachment-based trauma, not pure FFFS. |
| `reinforcement_expectation` | Operant (response-outcome), CBT (automatic thoughts), Bandura (vicarious learning) | Operant builds from direct experience; CBT mediates through belief-filtered predictions; Bandura adds observational data at 15-25% strength. Three independent evidence streams converge. |
| `arousal_level` | BIS/BAS (activation levels), Trauma (window of tolerance), Attachment (attachment system activation) | Global state variable. Window of tolerance (Siegel) defines the range; polyvagal hierarchy determines which response tier is accessible. Attachment anxiety produces chronic BIS activation (perpetual approach-avoidance conflict). |
| `escape_assessment` | Trauma (perceived escape options), BIS/BAS (FFFS defensive distance) | Determines fight vs flight vs freeze selection. Autonomy disposition modulates perceived escape — high autonomy → flight viable; low → freeze/fawn. |

#### Cross-Model Bridges (Non-Obvious Interactions)

1. **Inconsistent caregiving IS variable-ratio reinforcement.** Attachment's
   "inconsistent responsiveness" is operant conditioning's most extinction-
   resistant schedule. This is why anxious attachment is so change-resistant —
   the reinforcement schedule, not just the content, creates the persistence.

2. **Cognitive distortions amplify sensitization.** CBT's catastrophizing
   multiplier (×1.5–2.0) acts on trauma's threat-sensitivity node. A
   traumatized character with catastrophizing distortion has compounded
   hypervigilance — the distortion amplifies the already-lowered threshold.

3. **Self-efficacy gates recovery.** Bandura's self-efficacy mediates whether
   corrective experiences (positive relationships, successful coping) actually
   update the network. Low self-efficacy → positive experiences don't
   strengthen safety connections because the character doesn't attempt the
   behaviors that would generate positive outcomes.

4. **BAS reward prediction error IS operant learning.** The dopaminergic
   reward prediction error in BAS is the neurobiological implementation of
   Rescorla-Wagner. BAS sensitivity (disposition: riskAppetite) directly
   modulates the learning rate for positive reinforcement.

## 5. Weight Update Rules

### 5.1 Unified Weight Update Rule (Rescorla-Wagner + Pearce-Hall)

The Rescorla-Wagner prediction error rule provides the mathematical
foundation for ALL weight updates across the network:

```
ΔW_ij = α_i × β_outcome × (λ − Σ_k W_kj × a_k) × schedule_modifier × disposition_modifier
```

Where:
- `α_i` = dynamic salience of source node (Pearce-Hall: high when
  outcomes are surprising, decreases when predictable)
- `β_outcome` = outcome intensity parameter (0.1–0.5)
- `λ` = actual outcome value
- `Σ_k W_kj × a_k` = predicted outcome from all active nodes
- `schedule_modifier` = VR history → higher base + higher extinction resistance
- `disposition_modifier` = per-axis scaling (see §3.3)

Learning occurs only when outcomes differ from expectations (prediction error).

### 5.2 Dual-Weight Architecture

Every connection carries TWO weight values:
- **Excitatory weight:** the original learned association. Decays slowly.
- **Inhibitory overlay:** created during extinction. Decays faster.

Effective weight = excitatory − inhibitory. Spontaneous recovery occurs
when the inhibitory overlay decays while the excitatory weight persists.

Each excitatory weight also carries a `decay_resistance` attribute (default 1.0).
Higher values mean slower decay — trauma sensitization sets this proportional
to intensity × repetition count. VR reinforcement schedules also increase
decay resistance (2–4× CRF baseline).

**Storage:** All three values (excitatory weight, inhibitory overlay,
decay_resistance) per connection are stored in the #408 graph engine's
per-agent weight store, NOT in MindMapEdge. The CAPS network is a
computational structure separate from MindMap — see §2.1 for the
storage separation rationale. The graph engine maintains a sparse weight
matrix per agent: only connections with non-default weights are stored
(default: excitatory=0.5, inhibitory=0.0, decay_resistance=1.0).

**Atomic writes:** The #408 graph engine writes the entire per-agent
network state (all updated weights, thresholds, and inhibitory overlays)
in a single atomic operation at the end of each consolidation pass. If
the write fails (OOM, timeout), the entire update is discarded and the
previous state persists — no partial writes. The experience cursor is
advanced only after BOTH the engine write AND the MindMap attractor
write succeed.

**Two-store consistency:** The BehavioralSynthesisPhase writes to two
stores: (1) updated CAPS weights → #408 graph engine, (2) behavioral
attractor nodes → MindMap BEHAVIORAL subgraph. To prevent stale
attractors when the MindMap write fails after a successful engine write:
- Each per-agent CAPS state carries a monotonic `generation` counter,
  incremented on every successful engine write.
- Each behavioral attractor node in MindMap stores the `source_generation`
  it was derived from.
- At the start of each consolidation cycle, BehavioralSynthesisPhase
  compares engine generation vs attractor source_generation. If the
  attractor is stale (source_generation < engine generation), the phase
  regenerates attractors from current CAPS state before processing new
  experiences. This regeneration is idempotent — CAPS state is the
  source of truth; attractors are a derived projection.
- The experience cursor is advanced only after BOTH writes succeed. If the
  MindMap write fails, the cursor stays put. Next cycle: the engine write
  is replayed (same experiences, same result — deterministic), and the
  MindMap write is retried.

**Inhibitory decay specification:** Inhibitory overlays decay during each
consolidation cycle via exponential decay:
`inhibitory_new = inhibitory_old × (1 − base_decay_rate / effective_resistance)`

Base decay rate: 0.05 per consolidation cycle. The decay_resistance
attribute reduces the rate — high-resistance connections (VR-reinforced,
trauma-sensitized) decay more slowly. Decay is applied during every
consolidation cycle regardless of whether the relevant situation was
re-experienced — this is time-based, not activation-triggered.
effective_resistance for inhibitory overlays is half that of excitatory:
`effective_resistance = 1.0 + (decay_resistance − 1.0) × 0.5`. This
ensures inhibitory overlays always decay faster than their excitatory
counterparts, producing spontaneous recovery.

Spontaneous recovery timeline example: an extinguished fear connection
with decay_resistance=2.0, initial inhibitory=0.8. Effective resistance
for inhibitory = 1.5. After 10 cycles: 0.8 × (1 − 0.05/1.5)^10 ≈ 0.57.
After 30 cycles: ≈ 0.29. The effective weight (excitatory − inhibitory)
gradually increases as the overlay decays.

This architecture is essential for modeling:
- Spontaneous recovery of extinguished behaviors
- Context-dependent renewal (behavior returns in original context)
- Reinstatement after unsignaled stimulus presentation

### 5.3 Consolidation-Based Updating

During consolidation ("sleep"), recent experiences replay through the network
with four modulation factors:

- **Frequency:** Repeated activation strengthens connections (Hebbian)
- **Intensity:** PAD arousal amplifies updates: modifier = 1 + β × |arousal|
- **Recency:** Temporal decay via existing half-life mechanism
- **Consistency:** Confirming experiences strengthen; contradicting experiences
  trigger active revision through inhibitory overlay creation (§5.2)

**Per-character optimization:** CAPS convergence only runs for characters
(agentId) that have unprocessed experience events since the last
consolidation cycle. Characters with no new experiences skip convergence
entirely — their behavioral attractors are unchanged. Within a tenant,
characters are processed in order of unprocessed experience count
(most new experiences first).

**Computational cost bounds:** With the 100-iteration convergence limit
(§2.1) and a network of ~80 nodes × ~200 connections, each convergence
pass completes in O(100 × 200) = O(20,000) multiply-accumulate
operations — sub-millisecond on modern hardware. The dominant cost is
I/O: loading per-agent weights from the #408 engine and writing back
updated state. For a tenant with 50 characters, even if ALL have new
experiences, the total CAPS computation is under 1 second. The per-tenant
time budget is 30 seconds; if exceeded, remaining characters are deferred
to the next cycle (logged, not silently dropped).

### 5.4 Network Initialization for New Characters

A new character's CAPS network is initialized from the species-level
topology (the universal graph structure derived from the six psychological
models) with weights parameterized by the character's DispositionAxes.

**Initialization procedure:**
1. **Load species-level topology** — all nodes and connections defined in
   §3.2 and §4.1–§4.6 with their default weights (from published effect
   sizes where available, estimated otherwise — see §6.1 provenance).
2. **Apply disposition modulation** — for each connection, multiply the
   default weight by the relevant disposition modifier(s) from §3.3.
   Multiple axes may modulate the same connection: modifiers compose
   multiplicatively.
3. **Set initial activation thresholds** — all nodes start at their
   species-level default threshold. No sensitization or resilience
   adjustments apply to new characters (no experience history).
4. **Set resting activations** — mediating nodes start at 0.0 (neutral).
   Self-model and other-model bipolar nodes start at a disposition-derived
   value: socialOrient=cooperative → other_reliability resting = +0.2;
   riskAppetite=bold → BAS_activation resting = +0.1. These initial
   offsets ensure the character's personality is expressed from the first
   interaction, before any experiences are processed.
5. **Initialize all inhibitory overlays to 0.0** — no extinction has
   occurred. All decay_resistance values start at 1.0 (default).

**Default weight sources:**
- Connections with empirical effect sizes: weight = normalized effect size
  (e.g., r=0.32 for caregiver sensitivity → security, BIS→anxiety g=1.21
  normalized to the [0, 1] range)
- Connections without empirical data: weight = 0.5 (neutral prior)
- Inhibitory connections: weight = negative of the excitatory default

This initialization ensures a character with riskAppetite=bold and
conflictMode=competitive immediately exhibits approach behavior and
assertive tendencies, even without any experience history. The
CognitiveDerivationEngine provides the static cognitive defaults (mood
baseline, trust rates, etc.); CAPS initialization provides the dynamic
behavioral network that will evolve with experience.

### 5.5 Activation Level and Threshold Storage

Each CAPS node in the #408 graph engine stores two per-agent values:

- **`activation_level`** (double, [-1, +1]): the node's current activation.
  Recomputed each consolidation cycle by running CAPS settling (§2.1 Layer 1).
  NOT persisted between cycles — it is a transient computation result.
  Only the post-settling attractor state is persisted (as behavioral attractor
  nodes in the BEHAVIORAL subgraph). **Per-cycle starting point:** every
  cycle, CAPS settling begins from the same disposition-derived resting
  activations defined in §5.4 step 4 (not from the previous cycle's
  settled state). The connection weights — which ARE persisted — carry the
  learning between cycles; resting activations provide a stable
  disposition-derived starting bias. New experience inputs from the current
  cycle then perturb these resting values during settling.
- **`activation_threshold`** (double, default from species-level topology):
  the minimum input required to activate this node. Persisted per-agent
  because trauma sensitization permanently lowers it. This is the mechanism
  behind kindling (§4.4) and the window of tolerance.

Both values are stored in the #408 graph engine's per-agent node state,
NOT as MindMapNode properties. MindMapNode properties are for the agent's
semantic knowledge graph; CAPS node state is computational.

### 5.6 Sensitization and Resilience (Asymmetric)

**Trauma sensitization:** High-intensity threat experiences create connections
with elevated weights AND lowered activation thresholds. Update:
`threshold_new = max(0.05, threshold_old × (1 − γ × intensity))`
The 0.05 threshold floor (see §4.3 numerical bounds) prevents thresholds
from approaching zero regardless of trauma intensity. Decay resistance
proportional to intensity × repetition (kindling model: episode 1 → 50%
recurrence, episode 2 → 70%, episode 3 → 90%).

**Recovery:** Requires ACTIVE building of competing safety connections, not
passive decay. Recovery rate < sensitization rate (asymmetric):
`safety_weight += learning_rate × intensity`
`trauma_weight -= (1 / decay_resistance) × safety_activation`

**Earned security:** Attachment update rate is lowest of all models. Change
is asymmetric (negative events update faster) and context-dependent (may be
relationship-specific before generalizing).

### 5.7 Vicarious Learning Discount

Observational learning updates weights at 15–25% of direct experience
strength: `Δw_vicarious = Δw_direct × β_vicarious × model_similarity`
where β_vicarious ≈ 0.3–0.5 and model_similarity ∈ [0, 1].

Effects are temporary without subsequent direct reinforcement — vicarious
weights have higher decay rates than direct-experience weights.

### 5.8 Schema Change Dynamics

Two patterns from CBT research:

**Gradual:** ~5–10% weight reduction per disconfirming episode. Requires
consistent evidence (sporadic disconfirmation is filtered by the schema).

**Sudden gains:** Phase transition when accumulated evidence reaches tipping
point. The competing schema's activation exceeds the incumbent's, and the
system transitions to a new attractor state. Occurs in 40–50% of depression
cases. Smith et al. finding: most effective change induces uncertainty
(weakens existing schema precision) before strengthening the competitor.

### 5.9 #408 Graph Engine Requirements Contract

This spec defines the CAPS requirements for issue #408 (Cause-effect
decision graph engine). The requirements below are consolidated from
multiple sections; each links to its authoritative definition. #408's
scope should be validated against this list.

| Requirement | Description | Spec reference |
|-------------|-------------|----------------|
| Species-level topology storage | Global read-only graph defining all nodes, connections, and default weights | §2.1, §3.2, §5.4 |
| Per-agent weight overlays | Sparse weight matrix per (tenantId, agentId) storing only non-default values | §5.2 |
| Dual-weight per connection | Excitatory weight + inhibitory overlay + decay_resistance per connection | §5.2 |
| Per-agent node state | activation_threshold per node per agent (persisted); activation_level is transient | §5.5 |
| Atomic writes | Entire per-agent network state written atomically; no partial writes on failure | §5.2 |
| Generation counter | Monotonic per-agent generation for two-store consistency with MindMap | §5.2 |
| Topology migrations | Flyway-style migrations for node rename/split/add with per-agent weight remapping | §3.2 |
| SituationClassifier | Multi-label classifier component (sigmoid per label, threshold-based) | §3.1 |
| Module placement | Same dependency level as mindmap-intelligence; depends on mindmap-api, cognitive-api; no cognition dependency | §2.3 |

These requirements are individually well-understood patterns (sparse
overlay storage, atomic writes, schema migrations). The sophistication
is in their combination, not in any single requirement. Implementation
should proceed incrementally: topology storage + per-agent weights first
(enables BIS/BAS model), then dual-weight architecture (enables extinction
modeling), then migrations (enables topology evolution).

## 6. Calibration Roadmap

### 6.1 Weight Provenance Classification

Every connection weight is classified:

| Provenance | Meaning | Calibration approach |
|------------|---------|---------------------|
| **empirical** | Published effect size from meta-analysis or large-N study | Verify against most recent meta-analysis |
| **consensus** | Derived from clinical literature, therapeutic manuals, or expert consensus | Seek quantitative validation studies |
| **estimated** | Derived from related data or theoretical reasoning | Priority for empirical calibration |

### 6.2 Calibration Priority Order

1. **Shared nodes** — these affect multiple models, so miscalibration cascades
2. **High-traffic connections** — frequently activated in typical experience patterns
3. **Disposition modulation weights** — wrong modulation produces wrong personality expression
4. **Model-specific weights** — lower priority as they affect narrower behavioral domains

### 6.3 Calibration Approaches

- **Meta-analysis search:** For estimated weights, search for published effect sizes
- **Expert validation:** Present network outputs to clinical psychologists for face validity
- **Behavioral plausibility testing:** Generate characters with known disposition + experience profiles, evaluate whether emergent behavior matches clinical expectations
- **Shadow network training:** Train a parallel neural network on rated behavioral outputs to suggest weight adjustments for the CAPS network

## 7. Recommended First Implementation

### 7.1 Consolidation Phase Placement

The new `BehavioralSynthesisPhase` runs at @Priority(19), fitting into
the existing consolidation sequence:

| Priority | Phase | Role |
|----------|-------|------|
| 10 | AccessFrequencyPhase | Flush access counters |
| 12 | SurfacingAggregationPhase | Aggregate surfacing signals |
| 15 | ExperienceConsolidationPhase | Graduate experiences to COGNITIVE nodes |
| 16 | BeliefRevisionPhase | LLM-mediated belief revision |
| 17 | DriveAdaptationPhase | Reinforcement-based drive adaptation |
| 18 | RelationshipStagePhase | Familiarity scoring |
| **19** | **BehavioralSynthesisPhase** | **CAPS settling → behavioral attractors** |
| 20 | MergeDetectionPhase | Detect duplicate nodes |
| 25 | SchemaDiscoveryPhase | Discover property patterns |
| 37 | GoalAffectPhase | OCC emotion appraisal (§2.4 — one-cycle delay to CAPS) |

CAPS settling happens entirely within the single BehavioralSynthesisPhase
invocation — no multi-phase state passing required. The phase loads
per-agent weights from the #408 graph engine, processes new experience
events through the network, runs settling (max 100 iterations of vector
math — sub-millisecond per agent), and writes updated weights back plus
any new behavioral attractor nodes to the BEHAVIORAL subgraph.

### 7.2 Implementation Order

Based on tree encodability (D4) and infrastructure readiness:

**First model: Operant Conditioning + BIS/BAS**

Rationale:
1. Operant conditioning provides the universal weight update engine — it must
   be built first regardless of which content model comes next
2. BIS/BAS provides the simplest content model with the strongest quantitative
   data (g = 1.21 for BIS→anxiety, Pickering's computational equations exist)
3. Together they produce a working approach/avoidance system that other models
   layer on top of
4. BIS/BAS maps most directly to the existing DispositionAxes (riskAppetite →
   BAS/BIS balance, conflictMode → FFFS response selection)

**Second model: Attachment Theory**

Rationale: introduces the self/other model dimensions that CBT, Bandura, and
Trauma all build on. Must come before CBT (shared nodes: IWMs = core beliefs).

**Third: CBT** (adds belief processing and cognitive distortions on top of
attachment's IWMs)

**Fourth: Trauma** (adds sensitization and 4F chronic patterns — requires
BIS/BAS FFFS integration)

**Fifth: Bandura** (adds observational learning channel — can be added
independently at any point after operant conditioning)

## 8. References

### Attachment Theory
- van IJzendoorn, M.H. (1995). Adult attachment representations, parental
  responsiveness, and infant attachment: A meta-analysis. Psychological Bulletin
- Madigan, S. et al. (2024). Caregiver sensitivity meta-analysis update
- Zhang, F. et al. (2022). Attachment dimensions and mental health. 245 samples,
  N=79,722
- Deneault, A.A. et al. (2021). Insecure attachment and externalizing behavior
- Griffin, D.W. & Bartholomew, K. (1994). Big Five and attachment dimensions
- Collins, N.L. — Hierarchical internal working model theory

### BIS/BAS (Gray's RST)
- Gray, J.A. & McNaughton, N. (2000). The Neuropsychology of Anxiety (2nd ed.)
- Corr, P.J. (2008). Reinforcement Sensitivity Theory of Personality
- Pickering, A.D. — Computational RST model (MATLAB simulation)
- Brown, L.H. (2017). CTARST model adaptation
- Tamada, Y. et al. (2025). Childhood abuse, BIS/BAS, and depression (SEM)
- Miu, A.C. et al. — Severe trauma and BAS decrease
- Carver, C.S. & White, T.L. — BIS/BAS scales

### CBT (Beck's Cognitive Model)
- Beck, A.T. & Haigh, E.A.P. (2014). Advances in cognitive theory and therapy:
  The generic cognitive model. Annual Review of Clinical Psychology
- Smith, R. et al. (2021). Simulating CBT with Active Inference. Scientific Reports
- Shickel, B. et al. (2019). Automatic Detection of Cognitive Distortions.
  arXiv:1909.07502
- Padesky, C.A. — Schema change processes in cognitive therapy
- Wenzel, A. — Modification of core beliefs in cognitive therapy
- Collins, N.L. — Hierarchical IWM = schema hierarchy mapping

### Trauma Response Models
- Walker, P. — Complex PTSD: From Surviving to Thriving (4F model)
- Felitti, V.J. et al. (1998). Adverse Childhood Experiences Study. AJPM
- Hughes, K. et al. (2017). ACE meta-analysis. Lancet Public Health
- Dube, S.R. et al. (2001). ACE and suicide attempts. JAMA
- Porges, S.W. (2022). Polyvagal Theory: A Science of Safety. Frontiers in
  Integrative Neuroscience
- Siegel, D.J. — Window of tolerance model
- Neuhuber, W.L. & Berthoud, H.R. (2022). Polyvagal theory critique

### Operant Conditioning
- Rescorla, R.A. & Wagner, A.R. (1972). A theory of Pavlovian conditioning
- Pearce, J.M. & Hall, G. (1980). Attention-associability model
- Nevin, J.A. & Grace, R.C. — Behavioral Momentum Theory
- Sutton, R.S. & Barto, A.G. — Temporal Difference learning

### Social Learning Theory (Bandura)
- Bandura, A. (1977). Social Learning Theory
- Bandura, A. (1997). Self-Efficacy: The Exercise of Control
- Multon, K.D., Brown, S.D. & Lent, R.W. (1991). Self-efficacy and academic
  performance meta-analysis
- Sitzmann, T. & Ely, K. (2011). Domain-specific self-efficacy meta-analysis
- Ollendick, T.H. et al. (1983). Vicarious reinforcement durability studies
- Mozahem, N.A. (2022). Agent-based reciprocal determinism simulation

### CAPS Architecture
- Mischel, W. & Shoda, Y. (1995). A cognitive-affective system theory of
  personality. Psychological Review
- Shoda, Y., LeeTiernan, S. & Mischel, W. (2002). Personality as a dynamical
  system: Emergence of stability and distinctiveness from intra- and
  interpersonal interactions. Personality and Social Psychology Review
- PCSinR — R package for parallel constraint satisfaction networks
  (github.com/FelixHenninger/PCSinR)

### casehub Architecture
- casehubio/neocortex#406 — Emergent behavioral synthesis epic
- casehubio/neocortex#401 — Standardised experience-to-behaviour schema
- casehubio/neocortex#408 — Cause-effect decision graph engine
- casehubio/neocortex#397 — Behavioral attractor synthesis
- CognitiveDerivationEngine — existing 9-pathway disposition→personality derivation
- ConsolidationScheduler + ConsolidationPhase SPI — existing consolidation infrastructure
- MindMap graph — nodes, typed edges, PAD affect, temporal validity
