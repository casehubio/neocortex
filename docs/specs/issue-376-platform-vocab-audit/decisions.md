# Decisions — #376 Platform vocabulary audit

## D1: Two primary nouns — Case and Cbr

**Choice:** The platform has two first-class nouns: `Case` (the thing being worked on) and `Cbr` (the subsystem that learns from past cases). Both are nouns, not prefixes. Types follow `Noun[Role]` pattern.
**Alternatives:**
- Unified "Case" vocabulary — everything uses Case + qualifier (CaseRecord, CaseRecordStore). Rejected after two adversarial reviews: "Case" namespace collision makes CaseRecordStore vs CaseMemoryStore confusable. Cbr prefix does real disambiguation work.
- Engine renames away from Case — CaseInstance → PlanInstance. Rejected: engine is frozen, unnecessary once compound naming clarifies.
- CBR uses Resolution vocabulary — ResolvedCase, ResolutionGuide. Rejected: not CBR terminology, incomplete rename (#304), broke downstream.
- CBR uses Precedent/Exemplar vocabulary. Rejected: jargon, no connection to platform.
**Rationale:** Cbr is not an ugly prefix — it IS the noun of the reasoning subsystem, the same way Rule is the noun in Drools' RuleUnit. CaseInstance and CbrRecord are peers, not parent-child. The CBR subsystem isn't a footnote on the Case subsystem — it's a peer. Validated through extensive exploration: 4 directions proposed, 2 adversarial reviews, side-by-side code comparison.
**Trade-offs:** Developers must know "Cbr = Case-Based Reasoning." Mitigated: the acronym is the standard term in the AI/ML field, and once learned it's self-consistent.
**Sources:** Issue #376 body, #304 decisions.md (D10), engine#90, Google Scholar survey, Drools v2 RuleUnit/DataStore naming philosophy, 2 adversarial codebase reviews.
**Exploration:** multi-agent-debate (2 adversarial rounds)
**Status:** captured

## D2: CBR types rename to CbrRecord family

**Choice:** `CbrCase` → `CbrRecord`. Subtypes follow `Cbr[Qualifier]Record`: CbrPlanRecord, CbrTextRecord, CbrFeatureRecord.
**Alternatives:**
- CaseRecord family (Case[Qualifier]Record) — rejected: "Case" collision with CaseMemoryStore, misleading for non-case usage in blocks (personality, mental models).
- Revert to PlanCbrCase/TextualCbrCase — rejected: "Case" in CbrCase collides with engine's Case concept. "Record" is more accurate than "Case" for structured data stored for similarity retrieval.
- Keep current (ResolvedCase/ResolutionGuide) — rejected: inconsistent with FeatureVectorCbrCase, CBR_TYPE discriminators still say "plan"/"textual".
**Rationale:** "Record" describes what the data IS — structured, schema-defined, similarity-searchable data. Not a live instance (CaseInstance), not episodic memory (CaseMemoryStore). A record in the CBR subsystem.
**Trade-offs:** All downstream construction sites must change. Accepted: pre-release, one chance to get it right.
**Sources:** CbrCase.java, ResolvedCase.java, FeatureVectorCbrCase.java (memory-api), adversarial review finding on blocks usage patterns.
**Exploration:** multi-agent-debate
**Depends on:** D1 (Cbr as noun)
**Status:** captured

## D3: Engine execution vocabulary is frozen

**Choice:** Plan and PlanItem in the engine are settled. CaseInstance, CaseMetaModel, SubCaseGroup also stay as-is.
**Alternatives:** None considered — user confirmed these were the result of a large prior refactor.
**Rationale:** A big refactor already standardised the engine on Plan/PlanItem for execution. That work is done and stable.
**Trade-offs:** None.
**Sources:** User confirmation, engine CLAUDE.md.
**Exploration:** quick
**Status:** captured

## D4: Store naming — CbrRecordStore

**Choice:** `CbrCaseMemoryStore` → `CbrRecordStore`. One composite SPI. ISP sub-interfaces provide T-Box/A-Box separation internally.
**Alternatives:**
- CaseRecordStore — rejected: confusable with CaseMemoryStore (adversarial review BLOCKER 1).
- CaseBase — rejected: same T-Box/A-Box ambiguity as Drools' KnowledgeBase. Abstract CBR terminology, not concrete.
- Split into CaseTypeRegistry (T-Box) + CaseBase (A-Box) — rejected: ISP sub-interfaces already separate concerns, nobody injects them individually (adversarial review RISK 2), adds ceremony.
**Rationale:** `CbrRecordStore` keeps the Cbr noun for disambiguation, "Record" signals structured data (vs episodic "Memory"), "Store" is concrete. No confusion with CaseMemoryStore.
**Trade-offs:** Name doesn't explicitly surface T-Box/A-Box distinction. Accepted: ISP sub-interfaces handle this; the split is theoretical — no consumer uses it today.
**Sources:** CbrCaseMemoryStore.java, CaseMemoryStore.java (both SPIs), adversarial review of ISP injection patterns, Drools KnowledgeBase/RuleUnit evolution.
**Exploration:** deep-analysis
**Depends on:** D1 (Cbr as noun), D2 (Record family)
**Status:** captured

## D5: Scored results — CbrMatch

**Choice:** `ScoredCbrCase<T>` → `CbrMatch<T>`.
**Alternatives:**
- ScoredCbrRecord — "CbrRecord" appears twice in `ScoredCbrRecord<CbrPlanRecord>`. Rejected: stuttering.
- CaseMatch — rejected: uses "Case" noun in CBR context.
- CaseBaseResult — rejected: uses abstract "CaseBase" concept.
**Rationale:** `CbrMatch<CbrPlanRecord>` reads naturally — "a CBR match containing a plan record." "Match" signals similarity retrieval result without repeating "Record."
**Trade-offs:** "Match" is less descriptive than "ScoredCbrCase" about the presence of a similarity score. Mitigated: the type carries a score() accessor.
**Sources:** ScoredCbrCase.java, adversarial review RISK 3.
**Exploration:** quick
**Depends on:** D1, D2
**Status:** captured

## D6: Config properties stay as casehub.cbr.*

**Choice:** No config property rename. `casehub.cbr.tracking.enabled`, `casehub.cbr.diversity.*`, etc. all stay.
**Alternatives:**
- Rename to casehub.cbr-record.* — unnecessary churn, `cbr` prefix is consonant with Cbr noun.
**Rationale:** Config prefix `casehub.cbr` aligns with Cbr as the subsystem noun. No inconsistency.
**Sources:** 42 config properties across modules (adversarial review RISK 1).
**Exploration:** quick
**Status:** captured

## D7: Package stays io.casehub.neocortex.memory.cbr

**Choice:** No package rename. `cbr` package consonant with `Cbr` noun prefix on types.
**Alternatives:**
- Rename to io.casehub.neocortex.memory.cbrrecord — unnecessary, `cbr` is already correct.
**Rationale:** Package `cbr` + types `CbrRecord`, `CbrRecordStore` — fully consonant.
**Sources:** Adversarial review finding on package/type consonance.
**Exploration:** quick
**Status:** captured

## D8: Migration strategy — @Deprecated bridges via interface inheritance

**Choice:** `@Deprecated interface CbrCase extends CbrRecord` + `@Deprecated interface CbrCaseMemoryStore extends CbrRecordStore`. Records implement the bridge interfaces. Gradual migration for interface references. Construction sites change atomically (pre-release, accepted).
**Alternatives:**
- Big-bang only (no bridges) — rejected: bridges are free and ease migration.
- Static factory methods — adds complexity for marginal benefit.
**Rationale:** Interfaces can extend other interfaces. Records implementing CbrCase (deprecated) automatically satisfy CbrRecord (new). Downstream code using the interface migrates gradually. Construction of concrete records (`new CbrPlanRecord(...)`) requires same-day change — accepted as pre-release cost.
**Trade-offs:** Bridge interfaces add temporary type system complexity. Removed after migration window.
**Sources:** Java interface inheritance mechanics, adversarial review of bridge feasibility.
**Exploration:** deep-analysis
**Depends on:** D2
**Status:** captured

## D9: Decorators and internals rename consistently

**Choice:** All types rename, including decorators. Pre-release — no half measures.
**Alternatives:**
- Rename only developer-facing types (12 of ~50) — rejected: user wants semantic consistency throughout, not compromise driven by blast radius.
**Rationale:** Pre-release. One chance to get all names right. Inconsistency between public API (CbrRecord) and internals (CbrCaseMemoryStore decorators) creates cognitive dissonance for contributors.
**Trade-offs:** ~50 type renames vs ~12. Accepted.
**Sources:** User directive: "I don't want half measures or compromises, due to concerns on blast radius."
**Exploration:** quick
**Status:** captured

## D10: Adaptation types use Cbr noun

**Choice:** `PlanAdapter` → `CbrPlanAdapter`, `PlanEnsembleAnalyzer` → `CbrPlanEnsembleAnalyzer`. Engine-output types (`AdaptedPlan`, `AdaptedStep`) stay unchanged.
**Alternatives:**
- Keep PlanAdapter — rejected: "Plan" alone doesn't signal which subsystem this belongs to.
- CasePlanAdapter — rejected: uses "Case" noun in CBR context.
- CbrEnsembleAnalyzer (drop "Plan") — rejected: the SPI is plan-specific (input: CbrPlanRecord, output: EnsemblePlan). Name should be honest about scope.
**Rationale:** CbrPlanAdapter bridges from CbrRecord back to engine Plan. CbrPlanEnsembleAnalyzer preserves plan specificity. The Cbr noun signals which side of the bridge owns the SPI.
**Trade-offs:** None significant.
**Exploration:** quick
**Depends on:** D1
**Status:** captured

## D11: Textual CBR record — CbrGuidanceRecord

**Choice:** `ResolutionGuide` / `TextualCbrCase` → `CbrGuidanceRecord`. `GuidanceStep` → `CbrGuidanceStep`.
**Alternatives:**
- CbrTextRecord — rejected: loses the "guidance" semantic weight. Only describes format (text), not purpose (guidance).
- CbrProseRecord — rejected: describes format (prose), not purpose.
**Rationale:** The textual CBR paradigm stores prose advice — problem→solution pairs retrieved to guide future decisions. "Guidance" captures what the record DOES (guides), not just what it IS (text). Consistent with CbrPlanRecord (captures what a plan DID) and CbrFeatureRecord (captures feature vectors).
**Trade-offs:** CBR_TYPE discriminator stays "textual" — discriminators describe storage format, names describe purpose. Acceptable asymmetry.
**Sources:** ResolutionGuide.java, GuidanceStep.java.
**Exploration:** quick
**Depends on:** D2
**Status:** captured

## D12: Custom CbrCase implementations — coordinated rename

**Choice:** Rename all external `implements CbrCase` classes in the same pass: IoT's `PlanCbrCase` → `PlanCbrRecord`, quarkmind's `SC2GameCbrCase` → `SC2GameCbrRecord`, `SC2AdvisoryCbrCase` → `SC2AdvisoryCbrRecord`.
**Alternatives:**
- Bridge-only (defer rename to each team) — rejected: pre-release, one pass, all internal repos. The cbrType() → recordType() method rename requires changes anyway.
**Rationale:** Pre-release. All repos are internal. Coordinated rename is trivial incremental cost on top of the method signature change.
**Trade-offs:** 4 extra files across 2 repos.
**Sources:** Codebase audit: IoT PlanCbrCase, quarkmind SC2GameCbrCase + SC2AdvisoryCbrCase.
**Exploration:** quick
**Depends on:** D2, D8
**Status:** captured

## D13: CDI events — singular sealed interface

**Choice:** CDI event sealed interfaces use singular form: `CbrRecordErased`, `CbrRecordSuperseded`, `CbrRecordReinstated`. Inner records stay as-is (`ByRequest`, `ByEntity`, `ByScope`).
**Alternatives:**
- Plural form (CbrRecordsErased) — rejected: grammatically clunky.
- Event suffix pattern (CbrErasureEvent) — rejected: changes naming convention for all CDI events, more churn for less gain.
**Rationale:** Singular reads naturally: "a CBR record was erased, by entity." Minimal change from current pattern.
**Trade-offs:** Minor convention shift from plural to singular for sealed event interfaces only.
**Sources:** CbrCasesErased.java, CbrCasesSuperseded.java, CbrCasesReinstated.java.
**Exploration:** quick
**Depends on:** D2
**Status:** captured
