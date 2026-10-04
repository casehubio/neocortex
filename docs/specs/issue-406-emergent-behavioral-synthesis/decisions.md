# Decisions — #401 Standardised Experience-to-Behaviour Schema

## D1: Entry granularity — clinical pattern level

**Choice:** One entry per recognisable clinical/behavioural pattern (e.g.,
"anxious attachment", "learned helplessness", "compensatory narcissism").
Each maps a formative experience profile to a behavioural outcome, spanning
multiple CAPS connections. Estimated ~30-60 entries across six models.

**Alternatives:**
- Atomic cause-effect — one entry per CAPS connection (~80-120 entries);
  too granular, duplicates the topology YAML, unusable as design vocabulary
- Composite scenario — one entry per character archetype (~10-15 entries);
  too coarse, mixes independent patterns, not composable

**Rationale:** Clinical patterns are the natural unit of psychological
knowledge — they're how the literature organises cause-effect relationships,
how clinicians formulate cases, and how character designers think about
behavioral profiles. Each pattern spans multiple CAPS connections but
represents a single recognisable phenomenon.

**Trade-offs:** Requires judgment about where to draw pattern boundaries.
Some patterns overlap (anxious attachment and variable-ratio reinforcement
describe the same phenomenon from different theoretical frameworks). Cross-
references between entries handle this.

**Sources:** #407 spec §3-4 (six model compositions), Young's 18 EMS
(schema therapy domain structure), CBT case formulation literature
(Kuyken, Padesky & Dudley)

**Exploration:** quick
**Status:** captured

## D2: Seeding mechanism — experience injection, not direct state overrides

**Choice:** Catalogue entries describe formative ExperienceEvents to inject
into the CAPS network, letting it derive behavioral attractors mechanically.
Entries do NOT prescribe direct CAPS weight values, activation thresholds,
or behavioral attractor strengths.

**Alternatives:**
- Direct state overrides — set connection weights and thresholds directly,
  bypassing CAPS processing. Faster initialization but loses interaction
  effects through shared nodes, produces no experience trace for the
  inhibitory overlay mechanism, and bypasses the model's core mechanism.
- Hybrid — inject experiences for primary patterns, override for fine-tuning.
  Adds complexity without clear benefit; if CAPS can't produce the target
  attractor from experiences alone, the topology or weights need fixing,
  not working around.

**Rationale:** Five converging evidence lines:
1. CAPS theory defines personality as the result of experience processing
   through the network — bypassing this bypasses the model
2. Young's 18 EMS are empirically traced to specific childhood experience
   patterns — the schema is output of experience processing, not a parameter
3. CBT case formulation works through experience identification, not schema
   parameter setting
4. Stanford's interview-based generative agents (2024) achieved 85% accuracy
   with narrative backstory — outperforming parameter-based approaches
5. Sensitive period neuroscience shows amplified learning rates during
   formative periods — maps to elevated salience (α) during seeding

Practical feasibility: CAPS settling is sub-millisecond per agent; 10-20
high-intensity experiences per pattern produce substantial weight shifts at
α×β = 0.1-0.25; single consolidation cycle processes all in batch.

**Trade-offs:** Requires the seeding infrastructure (#398) to translate
catalogue entries into ExperienceEvents and manage amplified salience for
backstory (sensitive period simulation). Slightly more complex than direct
parameter setting, but architecturally correct.

**Sources:**
- Mischel & Shoda (1995) — CAPS personality as network organisation
- Young, Klosko & Weishaar (2003) — 18 EMS developmental origins
- Bach, Lockwood & Young (2018) — EMS definition and elaboration
- Kuyken, Padesky & Dudley — CBT case formulation
- Stanford HAI (2024) — interview-based generative agents, N=1052
- Park et al. (2024, NAACL 2025) — CharacterGPT backstory-driven personas
- Barlas & Ng (2025) — Schema therapy empirical evidence review
- #407 spec §5.1-5.4 — Rescorla-Wagner, weight update, initialization

**Exploration:** deep-analysis
**Status:** captured

## D3: Catalogue format — YAML data file

**Choice:** Machine-readable YAML, consistent with the CAPS topology YAML
from #407. Prose fields (clinical descriptions, source citations) as string
values. Directly consumable by #398's seeding infrastructure.

**Alternatives:**
- Markdown document — better for human review and citation, but requires
  parsing or manual translation for machine consumption
- Both (YAML source + generated markdown) — adds a generation step without
  clear benefit; YAML with prose fields is already human-readable

**Rationale:** The CAPS topology is already YAML. The catalogue is a
companion data file — same format keeps the ecosystem consistent. YAML
handles the structured trigger/outcome/modulation fields naturally, and
string values accommodate the prose. #398's seeding infrastructure can
consume the catalogue directly without parsing.

**Trade-offs:** YAML is less comfortable for long prose passages than
markdown. Mitigated by keeping descriptions concise (1-3 sentences per
entry) and putting extended discussion in the spec document, not the
catalogue.

**Sources:** #407 CAPS topology YAML (docs/specs/2026-10-02-caps-topology.yaml)
**Exploration:** quick
**Status:** captured

## D4: Trigger specification — pre-classified node refs + optional narrative

**Choice:** Entries carry pre-classified CAPS input node IDs as the
authoritative trigger specification. Optional narrative experience templates
provide human-readable context and can be used by #398 to generate richer
ExperienceEvent descriptions for the memory store.

**Alternatives:**
- Pre-classified only — deterministic but loses the human-readable context
  and the narrative material that makes seeded memories feel like lived
  experience
- Narrative only — natural language classified by SituationClassifier at
  seeding time. Non-deterministic, depends on #408 existing, fragile

**Rationale:** The pre-classified node refs ensure deterministic CAPS
activation regardless of SituationClassifier quality. The narrative
templates serve two purposes: (1) human reviewers can understand what
the pattern means without decoding node IDs, (2) #398 can use them to
generate ExperienceEvent descriptions that read as genuine memories
in the memory store — important for the LLM to reason about backstory.

**Trade-offs:** Two representations of the same information. The node refs
are authoritative; the narrative is supplementary. If they diverge, the
node refs win.

**Depends on:** D2 (experience injection — the narrative templates are only
useful because we're injecting experiences, not setting state directly)

**Sources:** #407 spec §3.1 Step 2 (situation classification), #407 spec
§3.1 classification failure handling (justification for pre-classified
bypass)
**Exploration:** quick
**Status:** captured

---

# Decisions — #398 Memory Seeding Infrastructure

## D5: ExperienceEvent type evolution — new sealed permit

**Choice:** Add `FormativeExperience` as a new permit on the
`ExperienceEvent` sealed interface. Type-safe, explicit, forces
consumers to handle it.

**Alternatives:**
- Metadata-only — use existing Observation/Action/Outcome with metadata
  keys like `formative=true`. No API break but consumers can't distinguish
  formative from runtime events without checking metadata strings.
- Wrapper record — a `FormativeExperience` wrapper containing an
  `ExperienceEvent` delegate. Keeps sealed hierarchy unchanged but adds
  indirection and a parallel ingestion path.

**Rationale:** Current permits (Observation, Action, Outcome) model
runtime interaction events. Formative backstory events are categorically
different — they're pre-authored, carry CAPS metadata, have amplified
salience, and should be handled differently by consolidation. A sealed
permit makes this distinction compile-time visible. The breaking change
is small (~3-4 switch expressions in ExperienceEvents, ExperienceRecorderCore,
RelationshipObserver) and the right behavior for most consumers is to
explicitly ignore formative events (e.g., RelationshipObserver should
not create relationship events from backstory).

**Trade-offs:** Every switch on ExperienceEvent must add a
FormativeExperience case. Acceptable cost for type safety.

**Sources:** #401 spec §8 requirement 1, ExperienceEvent.java sealed
interface, ExperienceEvents.java switch expressions

**Exploration:** quick
**Status:** captured

## D6: Provenance — carry catalogueEntryId

**Choice:** `FormativeExperience` includes a `String catalogueEntryId`
field linking back to the source catalogue entry (e.g.,
`attachment-anxious`).

**Alternatives:**
- No provenance field — keep FormativeExperience generic. Catalogue
  provenance goes in metadata if needed. Keeps the type reusable for
  non-catalogue backstory but loses first-class traceability.

**Rationale:** Traceability from behavioral attractors back to source
patterns is essential for debugging, calibration, and
CognitiveEmergenceTest (#402) assertions. A first-class field is
cheaper to query than metadata string parsing.

**Trade-offs:** Couples FormativeExperience to the catalogue vocabulary.
Non-catalogue backstory events would use a synthetic or null entryId.

**Depends on:** D5 (new sealed permit provides the type to add the field to)

**Sources:** #401 spec §8 requirement for cross-cutting traceability,
#402 CognitiveEmergenceTest expected_outcomes assertions

**Exploration:** quick
**Status:** captured

## D7: Graduation interaction — formative-aware GraduationScorer

**Choice:** Extend graduation scoring to detect FormativeExperience
events (via `event-type` metadata) and bypass corroboration gating,
returning the memory's confidence directly. Corroboration still applies
to runtime experiences.

**Alternatives:**
- Pre-graduated injection — seeding infrastructure creates MindMap nodes
  directly from catalogue expected_outcomes alongside memory injection.
  Fast but partially reintroduces the direct state override pattern
  rejected in D2.
- High-volume seeding — seed enough corroborating events per entity to
  satisfy the ≥3 threshold naturally. No code changes but wastes storage
  on redundant events and makes the seeding system fragile (depends on
  repetition counts being high enough).

**Rationale:** Seeded memories come from a curated catalogue — they're
pre-validated by design. Corroboration gating exists to prevent noisy
runtime observations from graduating too early; that concern doesn't
apply to authored backstory. The formative-aware scorer is a targeted
extension (~10 lines) that preserves the existing graduation pipeline
for all other memory types.

**Trade-offs:** Creates a privileged path through graduation. If
catalogue entries are poorly authored, formative memories graduate
without the safety net of corroboration. Mitigated by the #401
validation script that checks catalogue entries against the CAPS
topology.

**Depends on:** D5 (FormativeExperience type provides the event-type
discriminator)

**Sources:** DefaultGraduationScorer (minCorroboration=3),
ExperienceConsolidationPhase graduation pipeline, #401 catalogue
validation script

**Exploration:** quick
**Status:** captured

## D8: Module placement — new memory-seeding module

**Choice:** New `memory-seeding` module. `FormativeExperience` permit
goes in `memory-api` (part of the sealed hierarchy). Catalogue parsing,
event generation, and the `BackstorySeeder` service live in
`memory-seeding`. Uses `jackson-dataformat-yaml` for catalogue parsing,
consistent with `cognitive-index`.

**Alternatives:**
- In cognition module — co-locates with CognitionCore but mixes concerns
  (cognition is prompt rendering and tick lifecycle, not memory ingestion)
- In mindmap-intelligence — thematically related but already large and
  memory-focused work doesn't belong in a graph-focused module

**Rationale:** Clean separation: `memory-api` owns the type hierarchy,
`memory-seeding` owns the catalogue-to-event translation and ingestion
API. The seeding module depends on `memory-api` + Jackson YAML. No
circular dependencies. Consistent with the project pattern of dedicated
modules per concern.

**Trade-offs:** Another module to maintain. Justified by the clean
boundary and the fact that seeding is a distinct concern from memory
storage, retrieval, or consolidation.

**Sources:** cognitive-index/pom.xml (jackson-dataformat-yaml precedent),
module structure in CLAUDE.md

**Exploration:** quick
**Status:** captured

## D9: Emotional conditioning — derive from tick

**Choice:** Seed FormativeExperience events with PAD values on each
memory. MoodOrchestrator's existing appraisal logic aggregates these
during the first CognitionCore tick. No explicit mood seeding.

**Alternatives:**
- Explicit mood seeding — compute aggregate PAD baseline from backstory
  and inject as MoodState. Precise control but bypasses appraisal.
- Both paths — default to tick-derived, optional explicit override.
  More API surface without clear benefit.

**Rationale:** Consistent with D2 (experience injection, not state
override). The mood appraisal mechanism exists precisely to derive
emotional state from experience — let it do its job. Seeded memories
carry PAD values that the appraisal can aggregate.

**Trade-offs:** Initial mood depends on appraisal quality. If appraisal
produces unexpected results, debugging requires tracing through the
full tick rather than checking a directly-set value. Acceptable because
the alternative (bypassing appraisal) undermines the architecture.

**Depends on:** D2 (experience injection principle)

**Sources:** MoodOrchestrator, CognitionCore tick lifecycle,
MoodEvents converter

**Exploration:** quick
**Status:** captured

## D10: Need satisfaction — direct seeding now, experience-derived later

**Choice:** NeedTier satisfaction levels seeded directly as initial state
via MindMap node properties (same mechanism as ManorCognitiveSeeder).
The `BackstoryProfile` API accepts need levels alongside experience
backstory. Future consolidation phase can adjust levels from experience
accumulation — #398 designs for that extension but doesn't build it.

**Alternatives:**
- Build both now — direct seeding plus a consolidation phase mapping
  experiences to need adjustments. Significantly expands scope into
  #397 territory.

**Rationale:** Need satisfaction is present-tense state ("this character
is currently financially desperate"), not a learned pattern. Direct
seeding is appropriate for initialization. Experience-derived adjustment
is a valid future extension but belongs in consolidation (#397 or a
dedicated issue), not the seeding infrastructure.

**Trade-offs:** Initial need levels are author-asserted, not
system-derived. Acceptable for initialization; the system can adjust
them from runtime experience once the consolidation phase exists.

**Sources:** ManorCognitiveSeeder need satisfaction seeding,
NeedTier enum (5 values: SAFETY, TASKS, SOCIAL, SELF_EXPRESSION,
UNDERSTANDING)

**Exploration:** quick
**Status:** captured

## D11: API entry point — BackstoryProfile record

**Choice:** A `BackstoryProfile` record grouping: a list of
`CatalogueSelection` (entryId + optional intensity/repetition
overrides), `Map<NeedTier, Double>` satisfaction levels, and agent/tenant
context. `BackstorySeeder` service accepts this, reads catalogue YAML,
generates `FormativeExperience` events, and injects via
`ExperienceRecorder`.

**Alternatives:**
- Fluent builder API — ergonomic for programmatic use but harder to drive
  from YAML character definitions
- YAML character profile — declarative but adds another YAML format to
  maintain and parse

**Rationale:** Records are the project's value type convention. A
`BackstoryProfile` is data — it can be constructed programmatically
(ManorCognitiveSeeder), deserialized from YAML (future character files),
or built in tests. The record is the common currency; how it's
constructed is the consumer's concern.

**Trade-offs:** Less ergonomic than a fluent builder for inline
construction. Mitigated by record compactness and potential future
builder wrapper if demand appears.

**Sources:** ExperienceRecorder interface, catalogue entry schema
from #401 spec §3

**Exploration:** quick
**Status:** captured

## D12: Timestamp strategy — epoch-relative with period ordering

**Choice:** Generate timestamps in a synthetic past epoch (starting from
`Instant.EPOCH` or a configurable origin). Space them by developmental
period: infancy earliest, childhood next, adolescence, then adult.
Within a period, space events evenly.

**Alternatives:**
- Configurable backstory timeline — realistic timestamps based on
  character age. More realistic but no system consumer reasons over
  absolute ages.
- All at seeding time — current timestamp for all. Simplest but loses
  temporal ordering, breaking temporal decay and retrieval strength.

**Rationale:** Only relative ordering matters. Temporal decay scoring,
retrieval strength calculations, and consolidation sequencing all
operate on relative time differences, not absolute dates. Epoch-relative
timestamps are deterministic, reproducible, and avoid the complexity of
realistic timeline generation.

**Trade-offs:** Timestamps are obviously synthetic (1970s dates for a
character's memories). Not a problem — these are system internals, not
user-facing. If a consumer ever needs to display backstory timeline,
a mapping layer can translate period → display date.

**Sources:** TemporalDecayScorer, RetrievalStrength (Bjork dual-strength
model), ExperienceConsolidationPhase cursor ordering

**Exploration:** quick
**Status:** captured

## D13: Salience amplification — raw multiplier

**Choice:** `FormativeExperience` carries a `double salienceMultiplier`
field (default 1.0). Developmental periods map to default multipliers:
infancy=3.0, childhood=2.0, adolescence=1.5, adult=1.0. Consumer (#408
CAPS engine) applies it directly to weight update α.

**Alternatives:**
- Normalised 0-1 salience — more abstract but requires consumer to map
  back to a concrete multiplier, adding an interpretation layer with no
  benefit.

**Rationale:** The multiplier directly expresses the sensitive-period
neuroscience insight: formative experiences during critical
developmental windows have amplified impact on neural pathway formation.
A raw multiplier is the simplest correct representation — no
interpretation layer needed.

**Trade-offs:** Default multiplier values (3.0/2.0/1.5/1.0) are
estimates. Exact calibration is iterative — #402 CognitiveEmergenceTest
will validate that these produce expected behavioral outcomes.
`CatalogueSelection` can override per-pattern if needed.

**Sources:** #401 spec §3 developmental_period field, #407 spec §5.1
Rescorla-Wagner weight update equation, sensitive period neuroscience
(D2 evidence line 5)

**Exploration:** quick
**Status:** captured

## D14: Pipeline architecture — batch-then-consolidate

**Choice:** `BackstoryProfile` → `BackstorySeeder` reads catalogue,
generates `FormativeExperience` events → batch-injects via
`ExperienceRecorder.recordAll()` → triggers a single consolidation
cycle. Character is "ready" after one consolidation pass.

**Alternatives:**
- Seed-and-settle — generate memories AND directly create behavioral
  graph nodes. Fast but duplicates consolidation logic and contradicts
  D2.
- Streaming injection — inject events one at a time with
  micro-consolidation between each. Faithful to CAPS model but
  dramatically slower with no benefit — temporal ordering is preserved
  in timestamps.

**Rationale:** Respects D2 (experience injection, not state override).
Keeps the seeder simple: generate events, hand off to existing
infrastructure. Consolidation does what it was built for — graduation,
node creation, affect trajectory. The only new consolidation behavior
needed is the formative-aware GraduationScorer (D7).

**Trade-offs:** Character initialization requires a consolidation cycle
to complete. Not a problem — consolidation is fast (sub-second for
typical backstory volumes) and is a one-time initialization cost.

**Depends on:** D2 (experience injection), D7 (formative-aware scorer)

**Sources:** ExperienceRecorder.recordAll(), ConsolidationScheduler,
ExperienceConsolidationPhase

**Exploration:** quick
**Status:** captured

---

# Decisions — #408 Cause-Effect Decision Graph Engine

## D15: Implementation scope — full §5.9 minus migrations

**Choice:** Full requirements contract from §5.9: topology storage,
per-agent weight overlays, dual-weight per connection, per-agent node
state, atomic writes, generation counter, SituationClassifier, module
placement. Topology migrations dropped — pre-release with no installs
means no per-agent data to preserve across topology changes.
**Alternatives:**
- First increment only (topology + weights) — too narrow, defers
  critical mechanics like dual-weight extinction modeling
- Full §5.9 including migrations — unnecessary infrastructure for
  pre-release; just update the topology YAML and recreate
**Rationale:** The engine needs all mechanical components to produce
psychologically valid behavioral attractors. Migrations are the only
requirement that serves deployed installations, which don't exist yet.
**Trade-offs:** Large implementation scope. Mitigated by incremental
delivery in the implementation plan.
**Sources:** #407 spec §5.9
**Exploration:** quick
**Status:** captured

## D16: Module structure — three core + one optional

**Choice:** `caps-api` (SPIs, topology types, value objects), `caps-engine`
(settling, weight storage, topology loading, SQLite backend,
BehavioralSynthesisPhase, rule-based SituationClassifier), `caps-testing`
(contract tests, in-memory backend, CognitiveEmergenceTest framework).
Optional: `caps-llm-classifier` (LLM fallback for situation
classification, classpath-activated, depends on AgentProvider).
**Alternatives:**
- Fine-grained split (API + engine + inmem + sqlite + testing) — more
  modules but the engine is internal to consolidation, no external
  consumers need to swap backends
- Single module — consumers can't depend on just the types without
  pulling in ONNX/SQLite transitive deps
**Rationale:** Follows the mindmap-api / mindmap / mindmap-testing pattern.
Clean separation: consumers depend on caps-api, never on the engine.
LLM classifier in separate module per D19 revision (R1-09).
**Trade-offs:** Four modules vs one. Justified by the clean boundaries
and module parity with mindmap-intelligence.
**Depends on:** D19 (LLM classifier separation)
**Sources:** mindmap module structure, memory module structure
**Exploration:** quick
**Status:** revised (D19 dependency)

## D17: Storage backend — SQLite via sqlite-support

**Choice:** SQLite with HikariCP WAL via the existing sqlite-support
module (SqliteDataSourceFactory). Flyway migrations for schema. Per-agent
weight matrix as relational tables (agent_id, connection_id, excitatory,
inhibitory, decay_resistance, precision).
**Alternatives:**
- In-memory ConcurrentHashMap — simpler but per-agent state lost on
  restart, defeats atomic writes requirement
- YAML files per agent — simple but no transactional guarantees
**Rationale:** Consistent with SqliteMindMapStore and SqliteMemoryStore.
Shared infrastructure via sqlite-support. Atomic writes via SQLite
transactions. Schema evolution via Flyway.
**Trade-offs:** SQLite dependency for the engine. Mitigated by in-memory
backend in caps-testing for tests.
**Sources:** sqlite-support/SqliteDataSourceFactory, mindmap-sqlite,
memory-sqlite
**Exploration:** quick
**Status:** captured

## D18: Topology loading — YAML classpath resource

**Choice:** Load the existing `2026-10-02-caps-topology.yaml` from
classpath at startup. Jackson YAML parsing. Versioned via the `version`
field in the YAML.
**Alternatives:**
- Java DSL — type-safe but psychology researchers can't read Java
- SQLite seed data — mixes species-level (read-only) and agent-level
  (mutable) data
**Rationale:** Consistent with catalogue loading in memory-seeding.
The topology YAML already exists and is human-readable. Psychology
researchers can review and modify it directly.
**Trade-offs:** YAML parsing adds Jackson dependency. Already present
in cognitive-index and memory-seeding.
**Sources:** docs/specs/2026-10-02-caps-topology.yaml, memory-seeding
CatalogueLoader
**Exploration:** quick
**Status:** captured

## D19: SituationClassifier — rule-based in caps-engine, LLM in separate module

**Choice:** Lightweight keyword/rule-based mapper in caps-engine for the
finite situation vocabulary (~30 input nodes). Pre-classified bypass for
catalogue entries (TriggerSpec carries CAPS node IDs directly). LLM
fallback for ambiguous runtime experiences in a separate
classpath-activated module (`caps-llm-classifier`), following the
rag-crossencoder/rag-expansion pattern.
**Alternatives:**
- LLM fallback in caps-engine — introduces AgentProvider dependency,
  breaking module parity with mindmap-intelligence (which has no LLM
  dependency). Revised per review R1-09.
- ONNX multi-label classifier — no training data available
- Pre-classified only — works for backstory but breaks for runtime
**Rationale:** The CAPS network handles all the real inference. The
classifier just bridges text → input node activation. Separating the
LLM fallback into its own module keeps caps-engine at the same
dependency level as mindmap-intelligence, consistent with §5.9.
**Trade-offs:** Additional module for LLM classification. Acceptable —
follows established classpath-activation pattern.
**Sources:** #407 spec §3.1, §5.9 module placement, rag-crossencoder
pattern, CatalogueEntry TriggerSpec. Review R1-09 identified module
parity violation.
**Exploration:** deep-analysis
**Status:** revised (R1-09)

## D20: Disposition modulation — tagged connections

**Choice:** Each connection in the topology YAML carries optional tags
(e.g., `tags: [BIS, threat]`). `DispositionWeightMapper` resolves
pattern references like `BIS_connections` to all connections with the
matching tag.
**Alternatives:**
- Convention-based resolution — match by node name substrings. Fragile,
  a node rename breaks modulation
- Explicit connection lists — verbose, duplicates topology structure
**Rationale:** Explicit, auditable, extensible. Adding a new connection
just requires the right tags. Tag resolution is a simple index lookup.
**Trade-offs:** Topology YAML becomes slightly more verbose. Each
connection gains an optional tags field.
**Sources:** #407 spec §3.3 disposition modulation map, topology YAML
disposition_modifiers section
**Exploration:** quick
**Status:** captured

## D21: Bayesian weight priors — precision-modulated adaptive learning

**Choice:** Each connection carries a `precision` field (double, default
1.0) that modulates the effective Rescorla-Wagner learning rate.
Empirical connections start with high precision (e.g., 10.0 — resist
change); estimated connections start with low precision (e.g., 1.0 —
update readily). Precision increases with consistent evidence (weight
updates that confirm the current value) and decreases with surprising
evidence (large prediction errors). Effective α = base_α / precision.
**Alternatives:**
- Beta distribution (original D21) — domain mismatch: Beta has support
  [0, 1] but CAPS weights span [-2, +2]. Conjugate prior updating
  assumes Bernoulli/binomial observations, which Rescorla-Wagner
  prediction errors are not. Revised per review R1-04.
- Normal(mean, variance) — principled but adds full Bayesian updating
  complexity. The actual need is adaptive learning rates, not posterior
  distributions over weights.
- Point estimates + provenance tags only — simpler but uncertain
  connections learn at the same rate as well-calibrated ones
- Full Bayesian network layer — overkill
**Rationale:** The core insight from the Bayesian analysis holds:
uncertain weights should update faster than well-calibrated ones. But
the implementation should be honest about what it does — it's a
precision-modulated learning rate, not true Bayesian posterior updating.
This avoids the domain mismatch (Beta on wrong support) while
delivering the adaptive learning rate benefit. Precision is a single
additional field per connection with ~20 lines of update logic.
**Trade-offs:** Not formally Bayesian — doesn't give posterior
distributions or uncertainty quantification. Acceptable: the actual
need is adaptive learning rates, not full uncertainty modeling.
**Sources:** Neal (1992), Lake (2025) BIML, ABC framework (2026),
#407 spec §5.1, §6 calibration roadmap. Review R1-04 identified
Beta distribution domain mismatch.
**Exploration:** deep-analysis
**Status:** revised (R1-04)

## D22: BehavioralSynthesisPhase placement — caps-engine

**Choice:** BehavioralSynthesisPhase (@Priority 19) lives in caps-engine,
not in the cognition module. It is tightly coupled to engine internals
(loading weights, running settling, writing attractors).
**Alternatives:**
- cognition module — follows spec literally, keeps all consolidation
  phases together. But adds caps-engine as a dependency of cognition.
**Rationale:** Cleaner dependency graph. caps-engine depends on
mindmap-api + cognitive-api (for ConsolidationPhase + DispositionAxes).
cognition doesn't need a new dependency. BehavioralSynthesisPhase is
the primary consumer of engine internals.
**Trade-offs:** Consolidation phases now live in three locations
(mindmap-intelligence, cognition, caps-engine). Acceptable — each
phase lives where its primary dependencies are.
**Depends on:** D16 (module structure)
**Sources:** #407 spec §2.3 module placement, #407 spec §7.1
consolidation phase placement
**Exploration:** quick
**Status:** captured

## D23: Cognitive distortions — data-driven in topology YAML

**Choice:** Define distortions as data in the CAPS topology YAML
(distortion definitions with type, base threshold, multiplier range,
polarity weighting). The settling loop applies distortions generically
from these definitions — it reads the distortion data, checks activation
thresholds, and applies multipliers. The engine is model-agnostic; the
CBT-specific distortion definitions are content, not code.
**Alternatives:**
- Hardcoded in settling loop (original D23) — couples the engine to
  Beck's 5 CBT distortions specifically. If a seventh model adds its
  own distortion-like mechanics, the engine needs code changes. Revised
  per review R1-10.
- Defer entirely — CBT chains won't work without distortions
**Rationale:** Distortions are weight modifiers applied during settling,
but WHICH distortions exist and their parameters are content-model
knowledge. Defining them as data in the topology YAML keeps the engine
generic while still including distortion mechanics from day one. The
settling loop needs a generic "apply distortion modifiers" step, not
5 hardcoded distortion types.
**Trade-offs:** Slightly more abstract settling implementation.
Distortion definitions need a YAML schema. Worth it for engine
model-agnosticism.
**Sources:** #407 spec §4.3, distortion base thresholds, compound
distortion cap [0.1, 3.0], effective weight cap [-2.0, +2.0]. Review
R1-10 identified content-model coupling.
**Exploration:** quick
**Status:** revised (R1-10)

## D24: Testing — CognitiveEmergenceTest framework in #408

**Choice:** Build the #402 CognitiveEmergenceTest base class in
caps-testing. Seed a character via BackstorySeeder, run consolidation via
BehavioralSynthesisPhase, assert behavioral attractors match catalogue
expected_outcomes. End-to-end validation.
**Alternatives:**
- Deterministic unit tests only — tests settling mechanics in isolation
  but doesn't validate the full pipeline
**Rationale:** BehavioralSynthesisPhase lives in caps-engine (D22),
removing the blocker. BackstorySeeder (#398) is done. The full pipeline
is available: seed → consolidate → assert attractors. Catalogue entries
provide both input fixtures and expected outcome assertions.
**Trade-offs:** Larger testing scope. But the integration tests are
the definitive validation that the engine produces psychologically
valid output.
**Depends on:** D16 (module structure), D22 (phase placement)
**Sources:** #402 CognitiveEmergenceTest issue, catalogue
expected_outcomes field, BackstorySeeder
**Exploration:** quick
**Status:** captured
