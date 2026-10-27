# Cognitive Extraction Methodology

A prescriptive, checklist-driven methodology for extracting a complete cognitive model from biographical source material. This document defines the process — what to do, in what order, with what checklist. It is designed to be executable by a subagent with no prior context.

**Companion document:** `cognitive-model-reference.md` — defines the model types and values referenced here.

---

## Foundational Principles

These principles apply to every step of the pipeline. They are non-negotiable.

### Single-Pass Entity Detection

Entity mentions in memory text must be detected **ONCE** at import time and stored as structured metadata (`mentioned-entities` attribute). Cross-referencing uses this index — not runtime text search. Auto-linking in the UI reads from the same index.

**One detection pass, one source of truth, no runtime matching bugs.**

Runtime text search is fragile: it produces word-boundary bugs (e.g., "fine" matching inside "confined"), behaves differently across Java and JavaScript implementations, and requires every query path to re-implement the same matching logic. By detecting entity mentions once at import time and storing the result, all downstream consumers — cross-referencing, auto-linking, UI rendering — share the same pre-computed index.

### Word-Boundary Matching

All entity name matching must use word-boundary checks. Plain substring `.contains()` / `.includes()` / `.indexOf()` is **never acceptable** for entity matching.

- "fine" must NOT match inside "confined"
- "Art" must NOT match inside "Arthur"
- "Leon" must NOT match inside "Napoleon"

The `containsWord()` utility checks characters before and after the match to ensure the match is at a word boundary (non-letter/digit or string boundary).

For multi-word entity names (e.g., "Guillermo Kahlo"), first-name matching is allowed only when:
1. The first name is ≥5 characters
2. It uniquely identifies one entity in the graph (no ambiguity)
3. It passes the same word-boundary check

### Cognitive Model vs Reference Overlay Separation

Information about entities is stored in two distinct layers:

| Layer | What it contains | Stored in | Modifiable by |
|---|---|---|---|
| **Cognitive model** | Memories, PAD values, OCC emotions, SEC appraisal, edges, confidence — the psychological model | MindMap + CaseMemoryStore | Extraction pipeline |
| **Reference overlay** | General knowledge descriptions, biography context, external links (Wikipedia, Wikidata) — navigational aid | Separate `reference-overlay.json` | `generate_overlay.py` |

The overlay is NOT part of the cognitive model. It is regenerable, versionable, and clearly labelled in the UI as "REFERENCE — not stored in cognitive model." It exists to help researchers navigate unfamiliar entities without bloating the MindMap with encyclopaedic knowledge.

---

## 1. Source Material Management

### 1.1 Corpus Preparation

Biographical source material must be chunked into **addressable passages** before extraction begins. A passage is the atomic unit of provenance — every derived artifact traces back to one or more passages.

**Chunking rules:**

| Rule | Rationale |
|------|-----------|
| One passage = one coherent episode or topic | Keeps provenance meaningful |
| Maximum ~500 words per passage | Fits in an LLM context window alongside the checklist |
| Preserve paragraph boundaries | Natural semantic breaks |
| Assign a stable passage ID: `{document}:{index}` | Enables re-processing |

**Passage record:**

```
passage-id:   frida-biography:037
source-uri:   /corpus/frida-kahlo-biography.txt
char-start:   14280
char-end:     15102
text:         "In 1934, Frida discovered that Diego was having an affair..."
```

### 1.2 Provenance Chain

Every derived artifact — every memory, node, edge, property, PAD value, emotion annotation — MUST carry provenance attributes linking it to the source passage.

**On memory attributes:**

| Attribute Key | Value | Required |
|---------------|-------|----------|
| `source-uri` | Path or URI to the source document | Yes |
| `source-start` | Character offset or paragraph index (start) | Yes |
| `source-end` | Character offset or paragraph index (end) | Yes |
| `extraction-method` | `manual`, `llm-extract`, `rule-based`, `llm-enrich` | Yes |
| `extraction-pass` | `phase-1-structural`, `phase-2a-passage`, or `phase-2b-entity` | Yes |
| `passage-id` | Stable passage identifier | Yes |
| `enrichment-version` | Methodology version that produced this enrichment (e.g., `v1.2`) | Yes (Phase 2 only) |
| `enrichment-timestamp` | ISO-8601 timestamp of when the enrichment was produced | Yes (Phase 2 only) |

**Re-processing with versioning:** When re-running Phase 2 with an improved methodology, the orchestrator replaces all enrichments matching the prior `enrichment-version`. Enrichments from different versions can be compared side-by-side. Every enrichment is traceable to the exact methodology version that produced it.

**On MindMap nodes:**

Use `NodeRef(scheme="biography", id=documentName, qualifier=passageRange)` to link nodes to source passages.

Example: `NodeRef(scheme="biography", id="frida-kahlo-biography", qualifier="037")`

### 1.3 Re-processing Principle

**Phase 2 enrichment MUST read from the original passage, not from the stored summary.**

When a subagent enriches a memory or node with emotions, appraisal dimensions, or drive impacts, it receives the original source passage text (resolved via provenance attributes) alongside the current stored artifact. This prevents summary-of-summary fidelity loss.

The workflow:
1. Look up the artifact's `source-uri` + `source-start` / `source-end`
2. Read the original passage text
3. Enrich using the original text as primary input, the stored summary as context
4. Record `extraction-method=llm-enrich` and `extraction-pass=phase-2-enrichment`

---

## 2. Mechanical NLP Baseline (Phase 0)

Before any LLM pass, run a deterministic NLP extraction over the entire corpus. This produces the **ground truth entity index** — the exhaustive baseline that all subsequent LLM passes are audited against. Zero LLM cost, fully reproducible, runs in seconds.

### 2.1 What NLP Extracts Mechanically

**NLP owns completeness, LLM owns accuracy.** Every NLP extraction goes into the candidate pool with a confidence indicator — even lower-accuracy extractions are valuable because a misclassification is cheap (LLM corrects it) while a gap is permanent (nobody ever asks about it). The NLP step should maximise recall, not precision.

| Extraction | Tool | Accuracy | Output |
|---|---|---|---|
| Person names | spaCy NER `PERSON` | ~95% | Entity index entries typed `PERSON` |
| Place names | spaCy NER `GPE` + `LOC` | ~93% | Entity index entries typed `PLACE` |
| Organizations | spaCy NER `ORG` | ~90% | Entity index entries typed `ORG` |
| Dates and years | spaCy NER `DATE` + HeidelTime | ~95% | Temporal expression index |
| Works of art | spaCy NER `WORK_OF_ART` | ~85% | Entity index entries typed `WORK` |
| Sentence boundaries | spaCy sentencizer | ~99% | Passage segmentation input |
| Noun chunks | spaCy dependency parser | ~95% | Full noun concordance index |
| Term frequency | Counting | 100% | Mention counts per entity |

### 2.2 Lower-Confidence NLP Extractions

These extractions have lower accuracy but still contribute to recall. They go into the candidate pool with a `confidence` flag so the LLM knows to verify rather than trust.

| Extraction | Accuracy | Candidate pool role |
|---|---|---|
| Coreference resolution ("she" → Frida) | ~75% | Provides pronoun→entity candidates. LLM confirms or corrects. Catches 75% of implicit references that would otherwise be gaps. |
| Coarse sentiment polarity | ~80% | Tags passages as positive/negative/neutral. Useful hint for the OCC emotion pass. |
| Dependency parsing (subject-verb-object) | ~85% | Candidate relationship triples. LLM refines "married" vs "affair" vs "treated". |

### 2.3 What NLP Cannot Extract

These require semantic understanding and go entirely to LLM passes. NLP does not attempt them.

| Extraction | Why |
|---|---|
| OCC-level emotion granularity (22 types) | Requires understanding narrative context, not keyword matching |
| Goal extraction | "she wanted independence" requires narrative comprehension |
| Causal chains | "because of the accident" requires discourse-level reasoning |
| Metaphor and symbolism | "the broken column" as artistic vs literal — impossible mechanically |
| Relationship type semantics | "married" vs "affair" requires contextual judgment |

**The principle:** if NLP can find it — even with errors — include it in the candidate pool. A false positive is cheap (LLM rejects it). A false negative is permanent (nobody asks about it).

### 2.4 Entity Concordance Index

The NLP pass produces a concordance index — the complete inventory of every entity mention in the corpus.

```yaml
frida-kahlo:
  nlpType: PERSON
  mentions: 47
  passages: [1, 3, 5, 7, 8, 12, 14, 18, 22, ...]
  aliases: ["Frida", "Kahlo", "Frida Kahlo"]
  firstMention: 1

diego-rivera:
  nlpType: PERSON
  mentions: 31
  passages: [2, 5, 8, 11, 15, 19, ...]
  aliases: ["Diego", "Rivera", "Diego Rivera"]
  firstMention: 2

casa-azul:
  nlpType: GPE
  mentions: 8
  passages: [4, 22, 35, 41, ...]
  aliases: ["Casa Azul", "the Blue House"]
  firstMention: 4

the-two-fridas:
  nlpType: WORK_OF_ART
  mentions: 3
  passages: [37, 52, 78]
  aliases: ["The Two Fridas", "Las dos Fridas"]
  firstMention: 37
```

Each entry records: NLP entity type, total mention count, every passage where it appears, all spelling variants, and the first mention passage.

### 2.5 Temporal Expression Index

Separately from entities, extract all temporal expressions with their resolved values:

```yaml
temporal-expressions:
  - text: "1925"
    resolved: "1925-01-01/1925-12-31"
    passages: [5, 12]
  - text: "July 1932"
    resolved: "1932-07-01/1932-07-31"
    passages: [28]
  - text: "after the divorce"
    resolved: null  # relative — requires LLM resolution
    passages: [37]
```

Expressions with `resolved: null` are flagged for LLM resolution in Phase 1.

### 2.6 NLP Gap Sweep (LLM-assisted)

After Phase 0 NLP extraction and before Phase 1 structural extraction, run a focused LLM pass over each passage to catch what NLP missed. This is NOT open-ended extraction — it's a targeted completeness check.

**Prompt pattern (one call per passage):**

```
Here are the entities NLP identified in this passage:
- Frida Kahlo (PERSON)
- Diego Rivera (PERSON)
- 1934 (DATE)

Passage: "<original text>"

Are there any entities mentioned in this passage that are NOT in the list above?
Look for: metaphorical references, implicit subjects, non-standard names,
abstract concepts, places referred to by description rather than name.
```

**This catches:**
- Metaphorical references: "the Blue House" → Casa Azul (PLACE)
- Implicit subjects: pronouns NLP couldn't resolve
- Non-standard names: nicknames, titles, role-based references ("my doctor")
- Abstract concepts NLP doesn't recognise as entities

**Output merges into the candidate pool** — new entities get added to the concordance index with `source: "gap-sweep"` and the NLP confidence flag set to `llm-discovered`.

**Cost:** One LLM call per passage with a focused prompt (not open-ended extraction). The NLP candidate list in the prompt keeps the LLM focused on what's missing rather than re-discovering what NLP already found.

### 2.7 Gap Reporting

After each LLM pass (Phase 1 and Phase 2), the orchestrator diffs the LLM output against the NLP baseline to catch omissions.

**Gap report format:**

| Entity | NLP Type | Mentions | LLM Status | Notes |
|---|---|---|---|---|
| Frida Kahlo | PERSON | 47 | **Covered** — node exists, 12 memories | |
| Diego Rivera | PERSON | 31 | **Covered** — node exists, 8 memories | |
| Dr Eloesser | PERSON | 6 | **Covered** — node exists, 1 memory | |
| Alejandro Arias | PERSON | 3 | **Gap** — no node created | First boyfriend — review passages 8, 12, 14 |
| Casa Azul | GPE | 8 | **Covered** — PLACE node exists | |
| Hotel Reforma | GPE | 2 | **Excluded** — incidental location | Reason: mentioned as exhibition venue only, no emotional significance |
| 1925 | DATE | 4 | **Covered** — Bus Accident node anchored | |
| 1946 | DATE | 2 | **Gap** — no event anchored to this year | Review passages 55, 62 |

**Gap categories:**
- **Covered** — NLP entity has a corresponding MindMap node and/or memory
- **Gap** — NLP entity has no corresponding artifact. Requires review: was it genuinely irrelevant, or did the LLM skip it?
- **Excluded** — deliberately not extracted, with documented reason

**Coverage metrics per entity type:**

| NLP Type | Total Found | Covered | Gap | Excluded | Coverage % |
|---|---|---|---|---|---|
| PERSON | 15 | 12 | 2 | 1 | 80% |
| GPE/LOC | 8 | 5 | 1 | 2 | 63% |
| ORG | 3 | 2 | 0 | 1 | 67% |
| DATE | 12 | 10 | 2 | 0 | 83% |
| WORK_OF_ART | 7 | 5 | 1 | 1 | 71% |

Gaps are not automatically wrong — some entities are genuinely irrelevant. But every gap MUST have a documented reason. An undocumented gap is a defect.

### 2.8 Resolution Dictionary (LLM→NLP Feedback Loop)

When Phase 1 LLM extraction or the NLP gap sweep discovers entity resolutions that NLP missed, write them to a **resolution dictionary**. This dictionary feeds future NLP runs, improving the mechanical baseline over time.

```json
{
  "the Blue House": { "canonical": "Casa Azul", "type": "place" },
  "my husband": { "canonical": "Diego Rivera", "type": "person", "context": "Frida Kahlo narration" },
  "Mama": { "canonical": "Matilde Calderón", "type": "person", "context": "Frida Kahlo family" },
  "the accident": { "canonical": "Bus Accident (1925)", "type": "activity" },
  "Coyoacán": { "canonical": "Casa Azul", "type": "place", "context": "metonymic — Frida's Coyoacán home" }
}
```

**Usage:** Future NLP runs load this dictionary as a pre-processing step. Before spaCy runs, a simple string-match pass resolves known aliases to their canonical entities. This means:

- The NLP concordance index improves with every extraction run
- Metaphorical and context-dependent references are caught mechanically after the first LLM discovery
- Re-processing a corpus with an updated dictionary produces better NLP baselines without additional LLM cost

The dictionary is versioned alongside the corpus and methodology.

---

## 3. Phase 1 — Structural Extraction

Phase 1 extracts the knowledge graph skeleton: entities, relationships, and experience memories. Each passage is processed once, left to right through the source corpus. The NLP concordance index (Phase 0) provides the entity inventory — Phase 1 classifies, connects, and contextualises those entities using LLM passes.

### 3.1 Extraction Steps (in order)

Process each passage through these steps. A single passage may produce artifacts in multiple steps.

#### Step 1: Person Identification

| Aspect | Value |
|--------|-------|
| **Look for** | Named individuals — family members, friends, colleagues, lovers, doctors, political figures, artists, mentors |
| **MindMap type** | `SubgraphTypes.PERSON` |
| **Traits** | `Personable` (assign role, occupation, age if stated) |
| **Properties** | `role`, `occupation`, `relationship-to-subject` |
| **Confidence** | `STATED` if named explicitly, `INFERRED` if referenced indirectly |
| **Provenance** | `NodeRef(scheme="biography", id=doc, qualifier=passageId)` |
| **Deduplication** | Resolve by name before creating. Use `MindMapStore.resolveNode()` |

#### Step 2: Place Identification

| Aspect | Value |
|--------|-------|
| **Look for** | Named locations — cities, buildings, hospitals, countries, homes, galleries |
| **MindMap type** | `SubgraphTypes.PLACE` |
| **Traits** | `Locatable` (assign latitude, longitude, address, city, country if known) |
| **Properties** | `significance` (why this place matters to the subject) |
| **Edges** | `located-in` to parent place (e.g., Casa Azul → Mexico City) |

#### Step 3: Activity/Event Identification

| Aspect | Value |
|--------|-------|
| **Look for** | Discrete events — accidents, marriages, exhibitions, births, deaths, surgeries, travels, political events |
| **MindMap type** | `SubgraphTypes.ACTIVITY` |
| **Traits** | `Eventlike` (assign date, location, duration), `Temporal` (start-date, end-date) |
| **Properties** | `period` (year or date range), `significance` |
| **Temporal anchoring** | Set `validFrom`/`validUntil` on the node. Set `period` attribute on related memories. |
| **Edges** | `at` → PLACE, `participated` → PERSON |

#### Step 4: Goal Identification

| Aspect | Value |
|--------|-------|
| **Look for** | Aspirations, desires, ambitions, projects with an objective — explicit or implied |
| **MindMap type** | `SubgraphTypes.GOAL` |
| **Traits** | `Goallike` (assign horizon, priority, status, urgency, need-tier) |
| **Goal tier** | Thematic (life-level), Strategic (multi-year), Tactical (actionable) |
| **Properties** | `horizon` (immediate/short/medium/long/aspirational), `status` (active/blocked/completed/dormant/abandoned), `need-tier` (SAFETY/TASKS/SOCIAL/SELF_EXPRESSION/UNDERSTANDING) |
| **Edges** | `enables`, `blocks`, `requires`, `contributes-to`, `decomposes-into` between goals |

#### Step 5: Project Identification

| Aspect | Value |
|--------|-------|
| **Look for** | Creative works, exhibitions, publications, initiatives with defined output |
| **MindMap type** | `SubgraphTypes.PROJECT` |
| **Traits** | `Projectlike` (status, deadline, description) |
| **Edges** | `inspired-by` → ACTIVITY or CONCEPT, `created-by` → PERSON |

#### Step 6: Concept Identification

| Aspect | Value |
|--------|-------|
| **Look for** | Abstract themes, philosophies, movements, psychological patterns, recurring metaphors |
| **MindMap type** | `SubgraphTypes.CONCEPT` |
| **Properties** | `definition`, `significance` |
| **Edges** | `associated-with` from PERSON, `influenced-by` from other CONCEPT |

#### Step 7: Relationship Edges

For every pair of entities mentioned together in a passage, evaluate whether an edge should exist.

**Edge type vocabulary:**

| Category | Edge Types |
|----------|-----------|
| Kinship | `child-of`, `sibling-of`, `parent-of` |
| Romantic | `married-to`, `affair-with`, `divorced-from` |
| Professional | `treated-by`, `taught-by`, `championed`, `collaborated-with` |
| Goal | `pursues`, `enables`, `blocks`, `requires`, `contributes-to`, `decomposes-into` |
| Spatial | `lives-at`, `located-in`, `at` |
| Creative | `inspired-by`, `created-by`, `depicted-in` |
| Conceptual | `associated-with`, `influenced-by` |

**Confidence on edges:** `STATED` if the relationship is explicitly described. `INFERRED` if implied by context.

**Validation tier:** `REGISTERED` if the edge type is from the vocabulary above. `UNVALIDATED` for ad-hoc types.

**Contradiction handling:** When two passages contain contradictory information about the same entity or relationship (e.g., passage 12 says "Frida forgave Diego" and passage 37 says "Frida never forgave Diego"):
- Create **both** memories with full provenance
- Add a `contradicts` attribute to each, linking to the other memory's ID
- Set confidence based on source quality (primary source > secondary biographer > speculation)
- **Emotional contradictions across time are natural** — different feelings about the same entity at different life stages represent evolution, not error. Both memories are valid.
- The consolidation pipeline's `BeliefRevisionPhase` handles contradiction resolution at runtime

**Metaphorical and symbolic content:** For metaphorical or symbolic content ("the broken column", "wings to fly"), evaluate the emotional INTENT, not the literal description. The PAD/OCC assessment should reflect what the subject meant to express, not what the words literally say. Note `metaphorical: true` in memory attributes when the text is non-literal.

**Unreliable narrator:** Biographical subjects narrate with bias. Self-report gets `STATED` origin but the confidence VALUE should reflect source reliability. A diary entry expressing feelings = high confidence on the emotion, variable confidence on factual claims. A court transcript = high confidence on facts.

#### Step 8: Experience Memory Creation

For each passage that describes something the subject experienced, observed, did, or the outcome of an action, create an experience memory.

| Field | How to set |
|-------|-----------|
| `Subject` | `Subject("person", "<subject-id>")` |
| `MemoryDomain` | `"experience"` |
| `text` | Summary of the experience (1-3 sentences). See §1.3 — also record provenance to the original passage. |
| `event-type` | `observation` (saw/noticed/learned), `action` (did/chose/created), `outcome` (resulted-in/achieved/failed) |
| `subject` | Entity ID the experience is primarily about |
| `period` | Year or date if known |
| `source-uri`, `source-start`, `source-end` | Provenance to original passage |
| `extraction-method` | `llm-extract` or `manual` |
| `extraction-pass` | `phase-1-structural` |

**FormativeExperience decision rule:** If the experience occurred during a formative developmental period (childhood, adolescence, early adulthood) and describes a character-shaping event (trauma, first love, parental relationship, identity formation, early achievement, loss), use the `FormativeExperience` event type instead of plain `observation`/`action`/`outcome`. Set these additional attributes:

| Attribute | How to set |
|-----------|-----------|
| `developmental-period` | `childhood`, `adolescence`, `early-adulthood`, `adulthood` |
| `situation-types` | CAPS input node IDs that this formative event would activate (comma-separated, from the table in §4.6) |
| `salience-multiplier` | 0.5–2.0 based on life impact. Traumatic events = 1.5–2.0, formative relationships = 1.0–1.5, routine developmental milestones = 0.5–1.0 |
| `reinforcement-schedule` | `single` (one-off event), `intermittent` (recurring pattern), `continuous` (ongoing situation) |
| `catalogue-entry-id` | Optional reference to a backstory catalogue entry if applicable |

FormativeExperience memories are critical for CAPS personality shaping — the `situation-types` attribute maps directly to CAPS input nodes, and the `salience-multiplier` controls how strongly the event shapes personality weights through the backstory seeder pipeline.

#### Step 9: Temporal Ordering

After all passages are processed:
1. Sort all ACTIVITY nodes by `validFrom`
2. Verify temporal consistency (no child event before parent event)
3. Set `period` attributes on all experience memories that reference dated events
4. Create a temporal index: ordered list of (date, nodeId, eventName) for the timeline view

### 3.2 Phase 1 Quality Gate

Before proceeding to Phase 2, verify:

- [ ] **NLP coverage met** — gap report generated, all entity types meet thresholds (§6.3), every gap has a documented reason
- [ ] Every PERSON node has at least one edge
- [ ] Every ACTIVITY node has a `validFrom` or `period` property
- [ ] Every GOAL has a `horizon` and `status` property
- [ ] Every experience memory has `event-type` and `source-uri` attributes
- [ ] No orphan nodes (nodes with zero edges) unless intentional (standalone concepts)
- [ ] Deduplication complete — no two nodes with the same name in the same subgraph
- [ ] Every unresolved temporal expression (from the NLP temporal index) has been either anchored to an ACTIVITY node or documented as irrelevant

---

## 4. Phase 2 — Cognitive Enrichment Checklist

Phase 2 is the core of this methodology. It runs in **two sub-phases** and applies a **type-discriminated checklist** at a **depth tier** determined by the entity's prominence.

**Runtime-only domains (intentionally excluded):** The `engagement` and `affect` memory domains are NOT extracted from biographical material. `engagement` tracks per-interaction social signals (responded, response-time-ms, reaction-count) generated by live conversation. `affect` stores PAD values recorded by `AffectTrajectoryDecorator` when MindMap node PAD changes at runtime. Both are populated by the live cognitive pipeline, not by biographical extraction. Their absence from the extracted corpus is intentional and correct.

The domains extracted by this methodology are: `experience`, `relationship`, `reflection`, and `mood`. Subgraph types `type-system`, `cognitive`, and `behavioral` are similarly runtime-only — generated by consolidation phases, not extracted from source material.

### 4.0a Depth Tiers

Not all entities warrant the same enrichment depth. Tier assignment is automatic, driven by NLP concordance mention counts and Phase 1 edge counts.

| Tier | Criteria | Treatment |
|---|---|---|
| **Central** | Top 15% by composite prominence score | Full type-discriminated checklist (Phase 2a + 2b) |
| **Supporting** | Next 35% by composite prominence score | Phase 2a only (passage-level enrichment). Skip Phase 2b. |
| **Peripheral** | Bottom 50% by composite prominence score | Basic node only — PAD, confidence, temporal. No enrichment pass. |

**Composite prominence score** = `(NLP mention count × 1.0) + (Phase 1 edge count × 2.0) + (memory count × 1.5)`. This prevents central-but-shallow over-promotion — an entity mentioned 20 times but with zero edges and zero memories gets a lower score than an entity mentioned 8 times with 5 edges and 3 memories.

Percentile thresholds adapt automatically to corpus size. A short 30-passage biography and a 500-passage biography both produce meaningful tier distributions.

The tier determines the maximum enrichment investment. Central entities get the full cognitive treatment. Peripheral entities get a node in the graph with basic metadata — enough to be referenced by other entities but not independently enriched.

### 4.0b Type-Discriminated Checklist

The full 16-item checklist (§4.1–4.16) is the reference. But not every item applies to every entity type. Subagents evaluate ONLY the applicable items — skipped items are not evaluated at all, not marked N/A.

| Entity/Memory type | Applicable items (§ reference) |
|---|---|
| **Person node** | All 16 |
| **Experience memory** | PAD (4.1), OCC (4.2), SEC (4.3), Action tendencies (4.4), Drive impact (4.5), Goal impact (4.7), Relationship (4.8), Confidence (4.9), Sub-thoughts (4.10), Temporal (4.13) |
| **Relationship memory** | PAD (4.1), OCC (4.2), Relationship (4.8), Confidence (4.9), Drive impact — affiliation axis only (4.5) |
| **Reflection memory** | Confidence (4.9), Drive impact (4.5), Goal impact (4.7), Temporal (4.13) |
| **Mood memory** | PAD only (4.1) — it already IS the PAD values |
| **Activity node** | PAD (4.1), Confidence (4.9), Temporal (4.13), Traits (4.12), Goal impact (4.7) |
| **Place node** | Confidence (4.9), Traits (4.12), Temporal (4.13) |
| **Goal node** | PAD — goal affect (4.1), Goal system (4.7), Confidence (4.9), Temporal (4.13), Drive impact (4.5) |
| **Project node** | Confidence (4.9), Traits (4.12), Temporal (4.13), Goal impact (4.7) |
| **Concept node** | Confidence (4.9), Traits (4.12) |
| **Organisation node** | Confidence (4.9), Traits (4.12) |

### 4.0c Two-Pass Enrichment

Phase 2 splits into two sub-phases because some checklist items can be assessed from a single passage while others require the full entity context.

**Phase 2a — Passage-level enrichment:** Each passage is processed independently. The subagent sees ONE passage and its derived artifacts.

Applicable items: PAD (4.1), OCC emotions (4.2), SEC appraisal (4.3), Action tendencies (4.4), Confidence (4.9), Sub-thoughts (4.10), Temporal anchoring (4.13), Habituation (4.11), Gut feeling (4.16).

**Phase 2b — Entity-level enrichment:** Each *central-tier entity* is processed with its full context. The subagent sees the entity's complete node, ALL edges, ALL memories already created, and the graph neighbourhood.

Applicable items: Drive impact (4.5) — needs baseline to assess delta. CAPS input activation (4.6) — needs the person's full psychological profile. Goal system (4.7) — needs the full goal graph. Reflection generation (4.14) — needs multiple experiences to synthesise from. Mood snapshot (4.15) — needs temporal context of surrounding experiences.

**Disposition profile extraction (one-time, primary subject only):** After all Phase 2b entity enrichments complete, run a final pass for the primary biographical subject to infer their disposition profile from the cumulative evidence. Assess position on each of the 5 disposition axes:

| Axis | Range | Infer from |
|------|-------|-----------|
| `socialOrient` | introvert ↔ extrovert | Relationship patterns, social preference, interaction frequency |
| `ruleFollowing` | flexible ↔ strict | Adherence to norms, reaction to rule violations, moral judgments |
| `riskAppetite` | cautious ↔ bold | Risk-taking behaviour, tolerance for uncertainty, adventurousness |
| `autonomy` | dependent ↔ independent | Need for independence, resistance to control, self-reliance |
| `conflictMode` | avoidant ↔ confrontational | Response to disagreement, willingness to fight, conflict patterns |

This produces the `CognitiveDefaults` seed — the foundation for the `CognitiveDerivationEngine` to derive PersonalityWeights, MoodBaseline, AppraisalWeights, CbrStrategy, SocialCognitionDefaults, GraphStructureDefaults, ExtractionBiasDefaults, AppraisalWeights, and HabituationConfig (all 9 derivation pathways).

**Supporting-tier entities skip Phase 2b entirely.** Peripheral-tier entities skip both phases.

The subagent receives:
1. The original source passage (resolved via provenance — not the stored summary)
2. The current artifact (memory or node with its properties)
3. The current graph context (Phase 2a: immediate neighbours; Phase 2b: full entity graph)
4. The type-discriminated checklist for this artifact type
5. The depth tier assignment

### 4.1 Affect Assessment (PAD)

**Applies to:** All memories and all nodes.

| # | Checklist Item | Output | Range |
|---|---------------|--------|-------|
| A1 | **Pleasure (P):** How positive/negative is this experience or entity for the subject? | `pleasure` field on memory or node | [-1, +1] |
| A2 | **Arousal (A):** How activating/calming is this? | `arousal` field | [-1, +1] |
| A3 | **Dominance (D):** How much control/agency does the subject feel? | `dominance` field | [-1, +1] |
| A4 | **PAD justification:** Why these values? One sentence per dimension. | Stored as `pad-justification` attribute | Free text |

**PAD aggregation rule:** Assess each memory's PAD relative to its **specific content**, not the passage's overall mood. A single passage can contain contradictory emotions — joy at recognition and anger at being labelled. Each memory captures its own affective state independently. When a passage produces multiple memories, each gets its own PAD assessment based on the cognitive fragment it represents, not a blended average of the passage.

**Guidance:**

| P range | Interpretation |
|---------|---------------|
| [-1, -0.5] | Severe distress, pain, anguish |
| [-0.5, -0.1] | Mild discomfort, sadness, frustration |
| [-0.1, +0.1] | Neutral |
| [+0.1, +0.5] | Mild pleasure, satisfaction |
| [+0.5, +1] | Joy, ecstasy, deep fulfilment |

| A range | Interpretation |
|---------|---------------|
| [-1, -0.5] | Deep calm, lethargy, sleepy |
| [-0.5, -0.1] | Relaxed, at ease |
| [-0.1, +0.1] | Neutral activation |
| [+0.1, +0.5] | Alert, engaged, tense |
| [+0.5, +1] | Highly aroused, agitated, frantic |

| D range | Interpretation |
|---------|---------------|
| [-1, -0.5] | Helpless, controlled, trapped |
| [-0.5, -0.1] | Submissive, constrained |
| [-0.1, +0.1] | Balanced agency |
| [+0.1, +0.5] | In control, influential |
| [+0.5, +1] | Dominant, powerful, fully autonomous |

### 4.2 OCC Emotion Assessment

**Applies to:** All experience memories. Evaluate for nodes only when the node represents an event or relationship.

For each memory, identify ALL applicable emotions from the 22 OCC types. Most experiences carry 1-3 emotions; complex passages may carry more.

| # | Checklist Item | Output |
|---|---------------|--------|
| E1 | **Primary emotion:** Which OCC emotion dominates? | `primary-emotion` attribute |
| E2 | **Primary intensity:** How strong? | `primary-intensity` attribute [0, 1] |
| E3 | **Secondary emotions:** Any additional emotions present? List all. | `secondary-emotions` attribute (comma-separated: `type:intensity`) |
| E4 | **Emotion source:** Is each emotion INTRINSIC (directly felt), EMPATHIC (felt for another), or ATTRIBUTED (inferred)? | `emotion-source` attribute |

**The 22 OCC emotions — evaluation guide:**

| Branch | Emotion | Ask this question |
|--------|---------|------------------|
| Prospect | `HOPE` | Does the subject anticipate a positive outcome? |
| Prospect | `FEAR` | Does the subject anticipate a negative outcome? |
| Prospect | `SATISFACTION` | Was a hoped-for outcome confirmed? |
| Prospect | `DISAPPOINTMENT` | Was a hoped-for outcome disconfirmed? |
| Prospect | `RELIEF` | Was a feared outcome averted? |
| Prospect | `FEARS_CONFIRMED` | Was a feared outcome realised? |
| Well-being | `JOY` | Is the event desirable for the subject? |
| Well-being | `DISTRESS` | Is the event undesirable for the subject? |
| Fortunes (liked) | `HAPPY_FOR` | Is a liked person experiencing something good? |
| Fortunes (liked) | `PITY` | Is a liked person experiencing something bad? |
| Fortunes (disliked) | `RESENTMENT` | Is a disliked person experiencing something good? |
| Fortunes (disliked) | `GLOATING` | Is a disliked person experiencing something bad? |
| Attribution | `PRIDE` | Is the subject's own action praiseworthy? |
| Attribution | `SHAME` | Is the subject's own action blameworthy? |
| Attribution | `REPROACH` | Is another's action blameworthy? |
| Attribution | `ADMIRATION` | Is another's action praiseworthy? |
| Compound | `GRATITUDE` | Admiration for another + joy at the outcome? |
| Compound | `ANGER` | Reproach for another + distress at the outcome? |
| Compound | `REMORSE` | Shame at own action + distress at the outcome? |
| Compound | `GRATIFICATION` | Pride in own action + joy at the outcome? |
| Object | `LOVE` | Does the subject feel deep positive regard for an entity? |
| Object | `HATE` | Does the subject feel deep negative regard for an entity? |

### 4.3 Appraisal (SEC Pipeline)

**Applies to:** All experience memories.

Evaluate the passage through all four stages of the Scherer SEC pipeline. Record all 8 dimensions.

| # | Checklist Item | Dimension | Range | Question |
|---|---------------|-----------|-------|----------|
| S1 | **Relevance** | `relevance` | [0, 1] | How relevant is this event to the subject's concerns? |
| S2 | **Novelty** | `novelty` | [0, 1] | How unexpected or new is this? |
| S3 | **Urgency** | `urgency` | [0, 1] | How time-pressured is the response? |
| S4 | **Conduciveness** | `conduciveness` | [-1, 1] | Does this help (+) or hinder (-) the subject's goals? |
| S5 | **Controllability** | `controllability` | [0, 1] | How much can the subject influence the outcome? |
| S6 | **Adjustability** | `adjustability` | [0, 1] | How well can the subject adapt to this? |
| S7 | **Internal standards** | `internal-standards` | [0, 1] | Does this conform to the subject's personal values? (Low = violation) |
| S8 | **External standards** | `external-standards` | [0, 1] | Does this conform to social norms? (Low = violation) |

All 8 values are stored as memory attributes with the `sec-` prefix: `sec-relevance`, `sec-novelty`, etc.

### 4.4 Action Tendency Assessment

**Applies to:** All experience memories.

| # | Checklist Item | Output |
|---|---------------|--------|
| T1 | **Dominant tendency:** Which of the 9 Frijda tendencies is the strongest response? | `action-tendency` attribute |
| T2 | **Intensity:** How strong is the impulse? | `tendency-intensity` attribute [0, 1] |
| T3 | **Secondary tendencies:** Any additional tendencies activated? | `secondary-tendencies` attribute |

**The 9 tendencies — decision guide:**

| Tendency | Triggered when | Example |
|----------|---------------|---------|
| `APPROACH` | Positive, attractive, desired | Seeking an exhibition, pursuing a relationship |
| `AVOIDANCE` | Threatening, uncontrollable | Withdrawing from Diego's infidelity |
| `ATTENDING` | Novel, unexpected, curious | Discovering Breton's interest in her work |
| `REJECTION` | Repulsive, violating values | Refusing to be labelled "surrealist" |
| `ANTAGONISM` | Threatening + high control | Confronting Diego, painting defiant self-portraits |
| `INTERRUPTION` | Boring, habituated | Leaving Detroit, breaking routine |
| `SUBMISSION` | Overwhelming, low control | Accepting medical procedures |
| `DOMINANCE` | Controllable, agency available | Setting conditions for remarriage |
| `INDIFFERENCE` | Irrelevant | N/A for most biographical material |

### 4.5 Drive Impact Assessment

**Applies to:** All experience memories and PERSON, GOAL, ACTIVITY nodes.

For each of the four drive axes, assess the impact of this artifact:

| # | Checklist Item | Axis | Output | Range |
|---|---------------|------|--------|-------|
| D1 | Does this increase or decrease curiosity? | `CURIOSITY` | `drive-curiosity-delta` | [-1, +1] |
| D2 | Does this affect sense of competence/mastery? | `COMPETENCE` | `drive-competence-delta` | [-1, +1] |
| D3 | Does this affect social bonding/belonging? | `AFFILIATION` | `drive-affiliation-delta` | [-1, +1] |
| D4 | Does this affect sense of independence/agency? | `AUTONOMY` | `drive-autonomy-delta` | [-1, +1] |
| D5 | **Justification:** Why each delta? One sentence per axis. | — | `drive-justification` | Free text |

**Interpretation of deltas:**

| Delta | Meaning |
|-------|---------|
| +0.5 to +1.0 | Strong activation (this event significantly fuels this drive) |
| +0.1 to +0.5 | Mild activation |
| -0.1 to +0.1 | No significant impact |
| -0.5 to -0.1 | Mild suppression (this event dampens this drive) |
| -1.0 to -0.5 | Strong suppression |

### 4.6 CAPS Input Activation

**Applies to:** All experience memories (these become input to the CAPS personality network).

Identify which CAPS input nodes this experience activates. Each input node represents a perceived situational encoding.

| # | Checklist Item | Output |
|---|---------------|--------|
| C1 | **Active input nodes:** Which CAPS inputs does this passage activate? | `caps-inputs` attribute (comma-separated: `nodeId:activation`) |
| C2 | **Expected output pattern:** What behavioural tendencies would CAPS settling produce? | `caps-expected-outputs` attribute |

**CAPS input node reference (select all that apply):**

| Category | Input Nodes |
|----------|-------------|
| Relationship | `trust_offered`, `trust_betrayed`, `intimacy_available`, `rejection`, `relationship_loss`, `social_support` |
| Threat | `criticism_received`, `social_threat`, `physical_threat`, `psychological_threat`, `abandonment_threat`, `status_threat` |
| Achievement | `achievement_opportunity`, `failure_experienced`, `competition_present`, `skill_challenge`, `recognition_received`, `competence_questioned` |
| Social | `group_inclusion`, `group_exclusion`, `social_comparison`, `authority_present`, `norm_violation_observed` |
| Resource | `resource_scarcity`, `resource_abundance`, `loss_experienced`, `gain_experienced` |
| Autonomy | `choice_available`, `choice_restricted`, `agency_granted`, `agency_denied`, `novelty_encountered`, `routine_established` |

Activation levels: 0.0 (not activated) to 1.0 (fully activated).

#### C3: Cognitive Distortion Identification

Biographical text often reveals cognitive distortions that warp CAPS settling behaviour. Identify any distortions present in the passage.

| # | Checklist Item | Output |
|---|---------------|--------|
| C3 | **Cognitive distortions:** Does this passage contain evidence of cognitive distortions? | `cognitive-distortions` attribute (comma-separated: `type:confidence`) |

**Distortion types (evaluate each):**

| Distortion | Pattern in text | Example |
|-----------|----------------|---------|
| `all_or_nothing` | Absolute language: "never", "always", "everything", "nothing" | "I NEVER painted dreams" |
| `catastrophizing` | Worst-case projection, exaggerated negative outcome | "This will destroy everything" |
| `overgeneralization` | Single event → universal rule | "cut deeper than ANY physical pain" |
| `mental_filter` | Focus exclusively on negative, ignoring positive | "Paris meant nothing" (despite Louvre acquisition) |
| `disqualifying_positive` | Dismissing positive evidence | "The recognition doesn't count" |
| `mind_reading` | Assuming others' thoughts without evidence | "He wanted to humiliate me" |
| `fortune_telling` | Predicting negative future with certainty | "I will never walk again" |
| `magnification` | Exaggerating the importance of negative events | "The worst thing that ever happened" |
| `emotional_reasoning` | Feelings treated as facts | "I feel worthless, therefore I am" |
| `should_statements` | Rigid rules about how things must be | "He should have been faithful" |
| `labelling` | Global label from specific behaviour | "He is a monster" |
| `personalization` | Taking responsibility for external events | "The miscarriage was my fault" |

Store as `cognitive-distortions` attribute on the experience memory, with confidence: e.g., `overgeneralization:0.7,all_or_nothing:0.4`.

### 4.7 Goal System Assessment

**Applies to:** All experience memories and all GOAL nodes.

| # | Checklist Item | Output |
|---|---------------|--------|
| G1 | **Related goals:** Which goals does this relate to? | List of goal node IDs or names |
| G2 | **Relationship type:** For each goal, what is the relationship? | `blocks`, `enables`, `contributes-to`, `requires`, `decomposes-into` |
| G3 | **Goal state impact:** Does this change the goal's status? | New status: `active`, `blocked`, `completed`, `dormant`, `abandoned` |
| G4 | **Urgency change:** Does this affect the goal's urgency? | Delta to urgency value |
| G5 | **Goal affect:** What PAD values should be set on the goal node as a result? | P, A, D values on the goal node |
| G6 | **Need tier mapping:** Which need tier does the goal serve? | `SAFETY`, `TASKS`, `SOCIAL`, `SELF_EXPRESSION`, `UNDERSTANDING` |

### 4.8 Relationship Assessment

**Applies to:** All memories involving two or more people.

| # | Checklist Item | Output |
|---|---------------|--------|
| R1 | **Quality signal:** Is this interaction POSITIVE, NEGATIVE, or NEUTRAL for the relationship? | `quality-signal` attribute |
| R2 | **Other agent:** Who is the other person? | `other-agent` attribute |
| R3 | **Trust trajectory:** Is trust increasing, stable, or decreasing? | `trust-trajectory` attribute |
| R4 | **Relationship stage transition:** Does this change the relationship stage? | From → To (e.g., "trusted → estranged") |
| R5 | **Engagement signals:** Did the other person respond? How quickly? How much? | `responded`, `response-length`, `continued` attributes |

If a single passage describes a relationship change, also create a `relationship` domain memory with these attributes.

### 4.9 Confidence Assessment

**Applies to:** All memories and all nodes.

| # | Checklist Item | Output |
|---|---------------|--------|
| F1 | **Origin:** Is this STATED, INFERRED, SPECULATED, or UNKNOWN? | `Confidence.origin` |
| F2 | **Value:** How certain is this information? | `Confidence.value` [0, 1] |
| F3 | **Decay reference:** Is this time-sensitive? If so, what is the decay reference date? | `Confidence.decayReference` (Instant, nullable) |
| F4 | **Justification:** Why this confidence level? | `confidence-justification` attribute |

**Decision table:**

| Source quality | Origin | Value range |
|---------------|--------|-------------|
| Direct quote, primary source | STATED | 0.85-1.0 |
| Biographical consensus (multiple biographers agree) | STATED | 0.75-0.90 |
| Single biographer's interpretation | INFERRED | 0.55-0.75 |
| Speculation about private feelings, undocumented events | SPECULATED | 0.30-0.55 |
| Unknown provenance | UNKNOWN | 0.20-0.40 |

### 4.10 Sub-thought Decomposition

**Applies to:** All experience memories with text longer than one sentence.

Decompose the memory text into typed sub-thoughts. Each sub-thought is a sentence-level cognitive fragment.

| # | Checklist Item | Output |
|---|---------------|--------|
| ST1 | **Identify sub-thoughts:** Break the text into distinct cognitive fragments. | List of (text, type) pairs |
| ST2 | **Classify each:** Assign one of the 7 types. | Type per sub-thought |
| ST3 | **Entity linkage:** Which MindMap entity does each sub-thought relate to? | Entity name per sub-thought |

**The 7 sub-thought types:**

| Type | Description | Look for |
|------|-------------|----------|
| `affect-observation` | Emotional reaction | "I felt...", "it hurt...", emotional language |
| `causal-inference` | Reasoning about why | "because...", "this was due to...", explanatory logic |
| `evaluative` | Value judgment | "it was wrong...", "beautiful...", "terrible..." |
| `intention` | Future plan or desire | "I will...", "I want to...", "I decided..." |
| `self-reflection` | Metacognitive insight | "I realise...", "looking back...", "I understand now..." |
| `association` | Connection to other knowledge | "like when...", "reminds me of...", "similar to..." |
| `concern` | Worry or anxiety | "I worry...", "what if...", "I'm afraid that..." |

### 4.11 Habituation Assessment

**Applies to:** All experience memories.

| # | Checklist Item | Output |
|---|---------------|--------|
| H1 | **Novelty:** Is this a novel observation or a repeated pattern? | `habituation-novelty` attribute: `novel`, `familiar`, `habituated` |
| H2 | **Pattern context:** If familiar, what prior experiences does it echo? | `habituation-echoes` attribute (comma-separated memory IDs or descriptions) |
| H3 | **Threshold assessment:** Would this pass the novelty threshold (default 0.3)? | `habituation-score` [0, 1] (1.0 = completely novel) |

**Decision guide:**

| Scenario | Score | Classification |
|----------|-------|---------------|
| First occurrence of this type of event | 0.8-1.0 | Novel |
| Second or third occurrence, still surprising | 0.4-0.7 | Familiar |
| Recurring pattern, expected | 0.1-0.3 | Habituated |
| Completely routine, no informational value | 0.0-0.1 | Fully habituated |

### 4.12 Trait Assignment

**Applies to:** All MindMap nodes.

| # | Checklist Item | Output |
|---|---------------|--------|
| TR1 | **Applicable traits:** Which trait interfaces apply to this node? | List of trait names |
| TR2 | **Trait properties:** For each applicable trait, fill in all defined properties. | Property key-value pairs |

**Trait checklist per subgraph type:**

| SubgraphType | Required Traits | Evaluate |
|-------------|----------------|----------|
| `person` | `Personable` | role, occupation, age, email, phone |
| `place` | `Locatable` | latitude, longitude, address, city, country |
| `activity` | `Eventlike`, `Temporal` | date, location, duration, start-date, end-date |
| `goal` | `Goallike` | horizon, priority, status, urgency, feasibility, target-date, need-tier |
| `project` | `Projectlike` | status, deadline, description |
| `concept` | (none required) | Consider: definition, significance, origin |
| `organisation` | `Organisational` | industry, size, founded |

### 4.13 Temporal Anchoring

**Applies to:** All memories and ACTIVITY, GOAL, PROJECT nodes.

| # | Checklist Item | Output |
|---|---------------|--------|
| TA1 | **validFrom:** When did this entity or state begin? | `validFrom` on node |
| TA2 | **validUntil:** When did it end (if applicable)? | `validUntil` on node |
| TA3 | **Period attribute:** What year/date range does this memory belong to? | `period` attribute on memory |
| TA4 | **Ordering verification:** Is this temporally consistent with related events? | Pass/fail |

### 4.14 Reflection Generation

**Applies to:** After all memories in a passage are enriched.

| # | Checklist Item | Output |
|---|---------------|--------|
| RF1 | **Does this passage warrant a reflection?** A reflection is a synthesised insight that goes beyond the factual content. | Yes/No |
| RF2 | **If yes:** Write the reflection text. | `reflection` domain memory |
| RF3 | **Reflection level:** Is this a direct insight (1) or a meta-insight (2+)? | `reflection-level` attribute |
| RF4 | **Source memories:** Which experience memories does this reflection synthesise? | `source-memory-ids` attribute |

### 4.15 Mood Snapshot

**Applies to:** At the end of each passage or temporal period.

| # | Checklist Item | Output |
|---|---------------|--------|
| M1 | **Should a mood snapshot be recorded?** If the passage describes a sustained emotional state. | Yes/No |
| M2 | **If yes:** Set PAD values for the mood at this point in time. | `mood` domain memory with P, A, D |
| M3 | **Active contexts:** What domains are influencing the mood? | `active-context-ids` attribute |

### 4.16 Gut Feeling Candidates

**Applies to:** Passages describing instinctive reactions, snap judgments, or bodily sensations.

| # | Checklist Item | Output |
|---|---------------|--------|
| GF1 | **Does this passage describe a gut feeling?** A pre-cognitive, somatic signal. | Yes/No |
| GF2 | **If yes:** Valence — APPROACH, AVOID, or CAUTIOUS? | Noted in experience memory attributes |
| GF3 | **Intensity:** How strong is the somatic signal? | [0, 1] |
| GF4 | **Resonance description:** What does the feeling remind the subject of? | Free text |

---

## 5. Subagent Pipeline Design

### 5.1 Architecture

```
Orchestrator
  ├── Phase 0: Mechanical NLP extraction (deterministic, no LLM)
  │   ├── spaCy/Stanza: NER, noun chunks, coreference, temporal expressions,
  │   │   sentence boundaries, sentiment, dependency triples
  │   ├── Output: entity concordance index + temporal index (candidate pool)
  │   └── All extractions carry confidence — NLP owns completeness
  │
  ├── Phase 0.5: NLP gap sweep (focused LLM, §2.6)
  │   ├── One LLM call per passage: "what entities are NOT in this list?"
  │   ├── Catches: metaphors, implicit subjects, non-standard names
  │   └── Output merges into candidate pool
  │
  ├── Phase 1: Structural extraction (LLM)
  │   ├── For each passage → classify entities, create edges, create memories
  │   ├── Uses NLP candidate pool — LLM confirms/reclassifies, doesn't discover
  │   ├── Writes resolution dictionary (§2.8) for LLM→NLP feedback
  │   ├── Assigns depth tiers: Central / Supporting / Peripheral (§4.0a)
  │   └── Gap report: diff Phase 1 output against NLP baseline
  │
  ├── Phase 2a: Passage-level enrichment (LLM, parallel)
  │   ├── Type-discriminated checklist per artifact (§4.0b)
  │   ├── Items: PAD, OCC, SEC, action tendencies, confidence,
  │   │   sub-thoughts, temporal, habituation, gut feeling
  │   ├── Central + Supporting tiers only (Peripheral skipped)
  │   └── Each enrichment tagged with enrichment-version
  │
  ├── Phase 2b: Entity-level enrichment (LLM, per central entity)
  │   ├── Full entity context: all edges + all memories + graph neighbourhood
  │   ├── Items: drives, CAPS activation, goal system, reflection, mood
  │   ├── Central tier only (Supporting + Peripheral skipped)
  │   └── Each enrichment tagged with enrichment-version
  │
  ├── Step 5b: Reference overlay generation (LLM)
  │   ├── generate_overlay.py: one-line description + biography context per entity
  │   ├── External links (Wikipedia, Wikidata) where available
  │   ├── Provenance: source model, generation date, version
  │   └── Output: reference-overlay.json (separate from cognitive model)
  │
  ├── Corpus Assembly
  │   ├── assemble_corpus.py: combine nodes, edges, enriched memories
  │   ├── Entity Mention Indexing (mandatory, see Foundational Principles):
  │   │   ├── Scan every memory text + attributes for entity name mentions
  │   │   ├── Word-boundary matching via containsWord() — no substring
  │   │   ├── First-name matching for multi-word names (≥5 chars, unique)
  │   │   └── Store as `mentioned-entities` attribute on each memory
  │   └── Output: corpus.json + coverage-report.json + model-coverage.json
  │
  └── Audit: Gap reporting + coverage metrics
      ├── Diff NLP index against MindMap nodes — flag uncovered entities
      ├── Diff temporal index against ACTIVITY nodes — flag unanchored dates
      ├── Entity mention coverage — bidirectional integrity check
      ├── Checklist completion rate per tier
      └── Generate coverage report per entity type (see §2.7)
```

### 5.1b Chunked Extraction Architecture

When an extraction prompt asks for multiple independent output types (entities, edges, memories), the combined JSON response can exceed the LLM's output token limit and be truncated mid-output, producing invalid JSON.

**The pattern:** decompose each LLM call into independent focused calls, one per output type. Each call receives the same input context but produces a smaller, well-bounded JSON response. Results are merged in post-processing.

**When it applies:** any extraction or enrichment step where the prompt requests multiple independent JSON arrays or objects.

**Sizing rule:** each call should target <2KB of JSON output (~1000 tokens). If a single output type is itself too large (e.g., 8 enrichment sections for a primary subject with 50+ edges), decompose further by grouping related sections.

#### Phase 1 decomposition (extract_structure.py)

| Call | System prompt | Output |
|------|--------------|--------|
| Entities | Entity types, traits, goals, confidence | `{"entities": [...]}` |
| Edges | Relationship vocabulary, validation tiers | `{"edges": [...]}` |
| Memories | Domain classification, formative fields | `{"memories": [...]}` |

Merge: per-passage, combine the three arrays into one result.

#### Phase 2b decomposition (enrich_entity.py)

| Call | System prompt | Output |
|------|--------------|--------|
| Drives + CAPS | Drive baselines, activation patterns | `{"drive_baselines": {...}, "caps_patterns": [...]}` |
| Goals + Relationships | Goal coherence, stage transitions | `{"goal_coherence": {...}, "relationship_stages": [...]}` |
| Reflections + Mood | Reflections, mood snapshots, attractors, disposition | `{"reflections": [...], "mood_snapshots": [...], ...}` |

Merge: per-entity, combine the three dicts into one result.

#### Anti-patterns

- **Raising max_tokens** shifts the truncation threshold but doesn't eliminate it. Prompts grow over time as new fields are added. Chunking is structurally robust.
- **Sequential chaining** (edges depend on entity list) adds latency and coupling. Independent calls with the same context are faster and more fault-tolerant — if one chunk fails, the others still produce usable output.

### 5.1c Convergence Loop Architecture

The pipeline enforces a fixed-point invariant: every text — source or generated — must pass through structural extraction, cognitive enrichment, and entity-level synthesis. This is achieved via a convergence loop.

#### Loop structure

```
Pass 1 (initial):
  chunk → nlp_extract → gap_sweep → extract_structure → enrich_cognitive
  → enrich_entity → backfill

Pass 2+ (convergence):
  convergence_extract → backfill_enrich → incremental_entity → backfill_new
  → check: new_memories == 0 → converged

Post-convergence:
  generate_overlay → assemble_corpus
```

Typically converges in 2-3 passes. Safety bound: `--max-passes` (default 5).

#### Convergence extraction

Generated memories (reflections, mood snapshots) are grouped by subject entity. One batched LLM call per entity with the entity's generated memories + known graph context. The prompt asks only for NEW content not already in the graph. This is prompt-level dedup — the LLM sees what exists and identifies only genuinely new entities, edges, and memories.

#### Confidence decay

Each pass caps maximum confidence to prevent circular amplification:

| Pass | Ceiling | Typical content |
|------|---------|-----------------|
| 1 | 1.0 | Source passage extraction |
| 2 | 0.8 | Reflections/moods from pass 1 synthesis |
| 3 | 0.6 | Insights from pass 2 generated content |
| 4+ | 0.4 | Diminishing returns |

LLM-assigned values are clamped: `min(llm_confidence, ceiling)`. All convergence-generated content gets `confidence.origin = INFERRED`. Synthesis skips entities where all new data is below confidence 0.4.

#### Convergence modes

| Mode | Behaviour |
|------|-----------|
| `fixed-point` (default) | All generated content triggers re-synthesis |
| `selective` | Only reflections trigger re-synthesis; moods get extract+enrich but don't feed back into Phase 2b |

#### Idempotency

Two-level tracking for crash-safe restarts:

1. **Item markers** on data: `structurally_extracted`, `enriched` (cognitive key), `synthesis_pass` — scripts skip items with existing markers
2. **Pass-level manifest** (`manifest.json`): tracks pass number, current step, completion status — scripts skip completed steps on restart

All LLM-based scripts write incrementally via atomic rename (`os.replace`) after each processed item. On restart: manifest determines pass/step, markers determine item-level progress. Maximum lost work on crash: one item.

#### Provenance

Convergence-generated items carry full derivation context:

- `extraction_pass`: `convergence-pass-N`
- `extraction_method`: `llm-convergence-extract`
- `convergence_pass`: integer pass number

This enables auditing: any item can be traced to its origin pass and the inference chain that produced it.

### 5.2 Subagent Dispatch Pattern

**Phase 2a: one subagent per passage** (not per artifact). A single passage typically produces 2-5 related artifacts. Processing them together preserves context and prevents conflicting enrichments. Only Central and Supporting tier artifacts are dispatched.

**Phase 2b: one subagent per central entity.** The subagent receives the entity's full node, all edges, all memories created in Phase 1 and enriched in Phase 2a, and the graph neighbourhood. This is a holistic assessment — drives, CAPS, goals, reflection — that requires the complete picture.

**Context window management:** For entities with >30 memories, the Phase 2b subagent receives a summarised context to fit within LLM token limits:
- All edges (these are compact — typically <100 even for central entities)
- The 20 most significant memories (highest confidence, highest absolute PAD magnitude)
- Statistical summary of remaining memories: count per domain, average PAD per domain, dominant emotions (top 5 by frequency), dominant action tendencies
- The subagent may request specific additional memories by ID if the summary is insufficient for a particular checklist item

For entities with ≤30 memories, the full context is provided.

Each subagent receives:

```json
{
  "passage": {
    "id": "frida-biography:037",
    "text": "<original passage text — NOT the stored summary>",
    "sourceUri": "/corpus/frida-kahlo-biography.txt",
    "charStart": 14280,
    "charEnd": 15102
  },
  "artifacts": [
    {
      "type": "memory",
      "id": "mem-001",
      "domain": "experience",
      "text": "<stored summary>",
      "currentAttributes": { "event-type": "observation", ... }
    },
    {
      "type": "node",
      "id": "node-042",
      "name": "Diego Rivera",
      "subgraphType": "person",
      "currentPad": { "p": 0.3, "a": 0.4, "d": 0.8 }
    }
  ],
  "graphContext": {
    "neighboringNodes": [ ... ],
    "existingEdges": [ ... ],
    "relatedGoals": [ ... ]
  },
  "checklist": "experience-memory",
  "tier": "central",
  "phase": "2a",
  "enrichmentVersion": "v1.0"
}
```

Each subagent outputs:

```json
{
  "passageId": "frida-biography:037",
  "enrichments": [
    {
      "artifactId": "mem-001",
      "artifactType": "memory",
      "pad": { "p": -0.7, "a": 0.6, "d": -0.4 },
      "padJustification": "Betrayal causes deep pain (P-), high activation (A+), loss of control (D-)",
      "primaryEmotion": { "type": "ANGER", "intensity": 0.8, "source": "INTRINSIC" },
      "secondaryEmotions": [
        { "type": "DISTRESS", "intensity": 0.7, "source": "INTRINSIC" },
        { "type": "REPROACH", "intensity": 0.6, "source": "INTRINSIC" }
      ],
      "sec": {
        "relevance": 1.0, "novelty": 0.9, "urgency": 0.6,
        "conduciveness": -0.8, "controllability": 0.2, "adjustability": 0.3,
        "internalStandards": 0.3, "externalStandards": 0.4
      },
      "actionTendency": { "dominant": "ANTAGONISM", "intensity": 0.7, "secondary": ["AVOIDANCE"] },
      "driveDeltas": {
        "curiosity": 0.0, "competence": -0.2, "affiliation": -0.8, "autonomy": 0.5
      },
      "capsInputs": { "trust_betrayed": 0.9, "relationship_loss": 0.7, "social_threat": 0.5 },
      "goalImpacts": [
        { "goal": "Motherhood", "relationship": "blocks", "urgencyDelta": 0.0 },
        { "goal": "Financial Independence", "relationship": "enables", "urgencyDelta": 0.3 }
      ],
      "relationship": { "otherAgent": "diego-rivera", "qualitySignal": "NEGATIVE", "trustTrajectory": "decreasing" },
      "confidence": { "origin": "STATED", "value": 0.85 },
      "subThoughts": [
        { "text": "Diego and Cristina's betrayal cut deeper than any physical pain", "type": "affect-observation", "entity": "diego-rivera" },
        { "text": "I cut my hair", "type": "intention", "entity": "frida-kahlo" },
        { "text": "stopped wearing the Tehuana dresses he loved", "type": "intention", "entity": "frida-kahlo" }
      ],
      "habituation": { "novelty": "novel", "score": 0.95 },
      "temporalPeriod": "1934",
      "newAttributes": {
        "extraction-method": "llm-enrich",
        "extraction-pass": "phase-2a-passage",
        "enrichment-version": "v1.0"
      }
    }
  ],
  "newRelationshipMemory": {
    "text": "Diego's affair with Cristina destroyed Frida's trust",
    "otherAgent": "diego-rivera",
    "qualitySignal": "NEGATIVE"
  },
  "reflectionCandidate": null,
  "moodSnapshot": { "p": -0.7, "a": 0.6, "d": -0.4 },
  "checklistCompletion": {
    "affect": true, "occ": true, "sec": true, "tendencies": true,
    "drives": true, "caps": true, "goals": true, "relationship": true,
    "confidence": true, "subThoughts": true, "habituation": true,
    "traits": false, "temporal": true, "reflection": true, "mood": true, "gut": true
  }
}
```

### 5.3 Orchestrator Responsibilities

1. **Tier assignment** (after Phase 1): compute depth tiers from NLP concordance mention counts + Phase 1 edge counts
2. **Phase 2a dispatch:** for each passage with Central or Supporting artifacts, dispatch a subagent with the type-discriminated checklist
3. **Phase 2b dispatch** (after all 2a completes): for each Central-tier entity, dispatch a subagent with the full entity context
4. **Apply enrichments:** after each subagent completes:
   - Update memory attributes via `CaseMemoryStore.enrichAttributes()`
   - Update node PAD via `MindMapStore.updateNode()`
   - Create new relationship/reflection/mood memories
   - Add sub-thought attributes
   - Tag all enrichments with `enrichment-version` and `enrichment-timestamp`
5. **Resolution dictionary:** after Phase 1, write discovered aliases to the resolution dictionary (§2.8) for future NLP runs
6. **Validation:** check `checklistCompletion` — flag any `false` values that should have been `true` per the type-discriminated table
7. **Progress tracking:** log enrichment progress per passage and per entity, including tier breakdown
8. **Output validation:** validate all subagent output before applying enrichments:
   - PAD values must be in [-1, +1]
   - SEC values must be in [0, 1] (except `conduciveness` which is [-1, +1])
   - Intensity values must be in [0, 1]
   - Emotion types must be from the 22 OCC enum
   - Action tendencies must be from the 9 Frijda enum
   - ConfidenceOrigin must be one of: `STATED`, `INFERRED`, `SPECULATED`, `UNKNOWN`
   - Confidence value must be in [0, 1]
   - `checklistCompletion` must match the type-discriminated table — items expected for this type must be `true`
9. **Error handling:** invalid subagent output is quarantined and retried once with the same input. Persistent failures (two consecutive invalid outputs for the same artifact) are logged in the coverage report as `enrichment-failed` with the error reason and the invalid output preserved for debugging. The pipeline continues — a single failed enrichment does not block other artifacts.

---

## 6. Quality Gates

### 6.1 Completeness Checks

After all Phase 2 enrichment is complete, verify:

**Nodes:**
- [ ] Every node has PAD values (pleasure, arousal, dominance all non-null)
- [ ] Every node has a confidence value with an origin
- [ ] Every PERSON node has at least one trait (`Personable`)
- [ ] Every GOAL node has `horizon`, `status`, `urgency`, `need-tier` properties
- [ ] Every ACTIVITY node has `validFrom` or `period`
- [ ] Every PLACE node has at least `city` or `country`

**Memories:**
- [ ] Every experience memory has all 8 SEC dimensions (`sec-*` attributes)
- [ ] Every experience memory has `primary-emotion` and `primary-intensity`
- [ ] Every experience memory has `action-tendency` and `tendency-intensity`
- [ ] Every experience memory has `drive-*-delta` for all 4 axes
- [ ] Every Central-tier experience memory has `caps-inputs` (at least one input node) — Phase 2b
- [ ] Every experience memory has `source-uri`, `source-start`, `source-end` (provenance)
- [ ] Every experience memory has `habituation-novelty` and `habituation-score`
- [ ] Every enrichment has `enrichment-version` and `enrichment-timestamp` attributes

**Relationships:**
- [ ] Every PERSON node has relationship memories for all directly connected people
- [ ] Every relationship memory has `quality-signal` and `other-agent`

**Goals:**
- [ ] Every GOAL node has PAD affect values
- [ ] Every GOAL has at least one edge (enables/blocks/contributes-to) to another entity

**Provenance:**
- [ ] No memory has `extraction-pass=phase-2-enrichment` without `source-uri` (all enrichments trace to source)
- [ ] No summary-of-summary: every Phase 2 enrichment has an `extraction-method` that is NOT applied to a Phase 1 summary

### 6.1b Entity Mention Coverage

The `mentioned-entities` attribute (see Foundational Principles: Single-Pass Entity Detection) enables bidirectional integrity checking:

- [ ] **Node→Memory coverage:** Every entity with ≥3 edges should appear in at least one memory's `mentioned-entities`. An entity with rich graph connections but zero memory mentions is a cross-referencing gap — the entity exists in the graph but has no narrative evidence.
- [ ] **Memory→Node coverage:** Every entity name in a memory's `mentioned-entities` must correspond to a node in the MindMap. An entity referenced in memories but absent from the node list is a potential extraction gap.
- [ ] **Bidirectional integrity:** If the UI auto-links entity X in memory M's text, then entity X's cross-reference query must return memory M. The `mentioned-entities` index is the single source of truth for both directions.
- [ ] **No orphan mentions:** No `mentioned-entities` entry should contain an entity name that doesn't match any node (accounting for first-name matching rules).

### 6.1c Reference Overlay Completeness

- [ ] Every entity in the corpus has an entry in `reference-overlay.json`
- [ ] Every entry has a non-empty `description` (general knowledge)
- [ ] Every entry has a non-empty `context` (role in the biography)
- [ ] Entity names in the overlay match entity names in the corpus (no type suffixes like "(person)")
- [ ] Overlay provenance fields are set: `source`, `generated`, `version`

### 6.2 Consistency Checks

- [ ] PAD values on nodes are consistent with the emotions on related memories (e.g., a node with P=+0.8 should not have only DISTRESS/ANGER memories)
- [ ] Goal status matches event history (a "completed" goal should have a contributing outcome memory)
- [ ] Temporal ordering is consistent (no cause-effect reversal)
- [ ] CAPS input activations are consistent with the emotions produced (e.g., `trust_betrayed=0.9` should correlate with ANGER or DISTRESS, not JOY)

### 6.3 NLP Baseline Coverage Thresholds

The NLP concordance index (Phase 0) is the completeness audit tool. After Phase 1, every NLP-identified entity must be accounted for — either covered by a MindMap node or explicitly excluded with a documented reason.

**Minimum coverage thresholds per entity type:**

| NLP Type | Threshold | Rationale |
|---|---|---|
| `PERSON` | **95%** | People are rarely irrelevant in biographical text — almost every named person matters |
| `GPE` / `LOC` | **80%** | Some locations are incidental (mentioned in passing, no emotional significance) |
| `ORG` | **75%** | Organizations may be background context (newspaper names, generic institutions) |
| `DATE` | **90%** | Temporal anchoring is critical for timeline construction — most dates matter |
| `WORK_OF_ART` | **85%** | Creative works are usually significant in an artist's biography |

If coverage falls below these thresholds, the gap report must be reviewed before proceeding to Phase 2. Gaps are not automatically wrong — but every gap must have a reason.

### 6.4 Coverage Report

Generate a coverage report showing:

| Metric | Value |
|--------|-------|
| **NLP baseline** | |
| NLP entities identified | N (by type) |
| NLP coverage after Phase 1 | % per type (see §2.5) |
| NLP gaps without documented reason | Count (must be 0) |
| Temporal expressions unanchored | Count |
| **Phase 1** | |
| Total passages processed | N |
| Total nodes created | N |
| Total memories created | N |
| **Phase 2** | |
| Nodes with complete PAD | N / total (%) |
| Memories with complete SEC | N / total (%) |
| Memories with OCC emotions | N / total (%) |
| Memories with sub-thoughts | N / total (%) |
| Memories with CAPS inputs | N / total (%) |
| Checklist completion rate | Average % across all subagent runs |
| **Entity Mention Index** | |
| Memories with mentioned-entities | N / total (%) |
| Entities with ≥3 edges but no memory mentions | Count (must be 0) |
| Orphan mentions (entity name not in node list) | Count (must be 0) |
| **Reference Overlay** | |
| Overlay entries | N / total entities (%) |
| Entries with external links (Wikipedia/Wikidata) | N / total (%) |
| **Tier breakdown** | |
| Central entities | Count, Phase 2a + 2b completion % |
| Supporting entities | Count, Phase 2a completion % |
| Peripheral entities | Count (basic nodes only, no enrichment) |

---

## 7. Worked Example

**Passage:** "Diego and Cristina's betrayal cut deeper than any physical pain. I cut my hair, stopped wearing the Tehuana dresses he loved."

**Source:** `frida-biography:037` (char 14280-14402)

### 7.1 Phase 1 Output

**Entities identified:**
- Diego Rivera (existing PERSON node)
- Cristina Kahlo (existing PERSON node)
- Frida Kahlo (existing PERSON, subject)

**Edges identified:**
- `affair-with`: Diego Rivera → Cristina Kahlo (if not already present)

**Experience memory created:**

```
domain:     experience
text:       Diego and Cristina's betrayal cut deeper than any physical pain.
            I cut my hair, stopped wearing the Tehuana dresses he loved.
event-type: observation
subject:    frida-kahlo
period:     1934
source-uri: /corpus/frida-kahlo-biography.txt
source-start: 14280
source-end:   14402
passage-id: frida-biography:037
extraction-method: llm-extract
extraction-pass: phase-1-structural
```

### 7.2 Phase 2 Enrichment — Full Checklist

#### 3.1 Affect (PAD)

| Dimension | Value | Justification |
|-----------|-------|---------------|
| P (Pleasure) | **-0.75** | Betrayal by husband and sister — deep emotional pain |
| A (Arousal) | **+0.60** | Highly activated — compelled to take visible action (cutting hair, changing dress) |
| D (Dominance) | **-0.40** | Loss of relational power, but reclaiming agency through defiance |

#### 3.2 OCC Emotions

| # | Emotion | Intensity | Source | Justification |
|---|---------|-----------|--------|---------------|
| Primary | **ANGER** | 0.80 | INTRINSIC | Reproach at Diego's action + distress at the outcome = compound ANGER |
| Secondary | **DISTRESS** | 0.70 | INTRINSIC | Undesirable event — loss of trust |
| Secondary | **REPROACH** | 0.65 | INTRINSIC | Diego's action judged against moral standards |
| Secondary | **SHAME** | 0.30 | INTRINSIC | Self-blame component — "what did I fail to provide?" |
| Secondary | **HATE** | 0.40 | INTRINSIC | Negative object-based emotion toward the situation |

#### 3.3 Appraisal (SEC)

| Dimension | Value | Justification |
|-----------|-------|---------------|
| `relevance` | **1.0** | Core relationship — maximally relevant to well-being |
| `novelty` | **0.9** | First betrayal by sister — unprecedented |
| `urgency` | **0.6** | Not physically dangerous, but emotionally acute |
| `conduciveness` | **-0.85** | Severely hinders affiliation, trust, and family goals |
| `controllability` | **0.2** | Cannot undo the affair; can only control her own response |
| `adjustability` | **0.4** | She adapts by changing her appearance and asserting independence |
| `internal-standards` | **0.2** | Deep violation of personal values (loyalty, family) |
| `external-standards` | **0.3** | Affair with a sibling violates social norms |

#### 3.4 Action Tendencies

| Tendency | Intensity | Justification |
|----------|-----------|---------------|
| **ANTAGONISM** (dominant) | 0.70 | Active defiance: cutting hair and rejecting the Tehuana dress are confrontational acts aimed at Diego |
| **AVOIDANCE** (secondary) | 0.50 | Withdrawal from the intimacy symbols that defined the marriage |
| **DOMINANCE** (secondary) | 0.45 | Reclaiming personal agency through self-directed transformation |

#### 3.5 Drive Impact

| Axis | Delta | Justification |
|------|-------|---------------|
| Curiosity | **0.0** | No curiosity impact |
| Competence | **-0.15** | Mild blow to self-concept as a partner, but not to artistic competence |
| Affiliation | **-0.85** | Devastating blow to social bonding — trust in husband and sister both destroyed |
| Autonomy | **+0.60** | Strong autonomy activation — hair cutting and dress change are declarations of independence |

#### 4.6 CAPS Input Activation

| Input Node | Activation | Justification |
|-----------|-----------|---------------|
| `trust_betrayed` | **0.95** | Core betrayal by spouse and sibling |
| `relationship_loss` | **0.70** | Marriage effectively broken |
| `social_threat` | **0.50** | Public knowledge of the affair threatens social standing |
| `psychological_threat` | **0.65** | Identity threat — the Tehuana dress was part of how she defined herself for Diego |
| `agency_granted` | **0.40** | Self-directed response demonstrates agency in crisis |
| `norm_violation_observed` | **0.60** | Sister-husband affair violates kinship norms |

**Expected CAPS outputs:** High `withdraw` + high `assert` + moderate `fight` — approach-avoidance conflict typical of betrayal trauma.

#### 3.7 Goal Impact

| Goal | Relationship | State Impact | Urgency Delta |
|------|-------------|-------------|---------------|
| Artistic Recognition | `enables` (indirectly — pain fuels art) | No change | 0.0 |
| Physical Recovery | No impact | No change | 0.0 |
| Motherhood | `blocks` (marital breakdown reduces motherhood prospects) | Active → Blocked | 0.0 |
| Financial Independence | `enables` (independence drive strengthened) | Active → More urgent | +0.3 |
| Political Impact | No direct impact | No change | 0.0 |

#### 4.8 Relationship Assessment

| Field | Value |
|-------|-------|
| Quality signal | **NEGATIVE** |
| Other agent | `diego-rivera` |
| Trust trajectory | **Decreasing** (sharply — from trusted to betrayed) |
| Stage transition | Trusted → Estranged |

Additional relationship memory for Cristina:

| Field | Value |
|-------|-------|
| Quality signal | **NEGATIVE** |
| Other agent | `cristina-kahlo` |
| Trust trajectory | **Decreasing** |
| Stage transition | Family → Betrayed |

#### 3.9 Confidence

| Field | Value | Justification |
|-------|-------|---------------|
| Origin | **STATED** | Documented in multiple biographies and Frida's own diary entries |
| Value | **0.90** | Well-established historical fact |
| Decay reference | None | Historical fact does not decay |

#### 4.10 Sub-thought Decomposition

| # | Text | Type | Entity |
|---|------|------|--------|
| 1 | "Diego and Cristina's betrayal cut deeper than any physical pain" | `affect-observation` | diego-rivera, cristina-kahlo |
| 2 | "cut deeper than any physical pain" | `evaluative` | frida-kahlo |
| 3 | "I cut my hair" | `intention` (executed) | frida-kahlo |
| 4 | "stopped wearing the Tehuana dresses he loved" | `intention` (executed) | frida-kahlo |

#### 3.11 Habituation

| Field | Value | Justification |
|-------|-------|---------------|
| Novelty | **Novel** | First betrayal by sister; while Diego's infidelity was known, Cristina's involvement is new |
| Score | **0.92** | Highly novel — changes the nature of both relationships simultaneously |
| Echoes | Diego's previous affairs (partial habituation to infidelity, but not this specific betrayal) |

#### 4.12 Trait Assignment

No new traits required — Diego, Cristina, and Frida already have `Personable` trait from Phase 1.

#### 4.13 Temporal Anchoring

| Field | Value |
|-------|-------|
| Period | 1934 |
| validFrom | (approximate — set on related activity node if one exists) |
| Ordering | After "Marriage to Diego (1929)", before "Paris Exhibition (1939)" |

#### 3.14 Reflection Candidate

**Yes** — this passage warrants a reflection.

```
domain:     reflection
text:       Betrayal by someone you trust completely doesn't just break the
            relationship — it breaks the framework through which you
            understand relationships. Diego's affairs were painful but
            expected. Cristina's involvement rewrote the rules.
level:      1
source-memory-ids: <id of the experience memory above>
```

#### 4.15 Mood Snapshot

```
domain:     mood
pleasure:   -0.70
arousal:    +0.60
dominance:  -0.30
period:     1934
```

#### 3.16 Gut Feeling

**Yes** — the passage describes an instinctive physical response (cutting hair is a somatic act).

| Field | Value |
|-------|-------|
| Valence | **AVOID** (pulling away from the relationship's symbols) |
| Intensity | 0.85 |
| Resonance | "The body knew before the mind decided — the scissors were the body's answer" |

### 7.3 Checklist Completion

| Dimension | Complete | Notes |
|-----------|----------|-------|
| Affect (PAD) | Yes | P=-0.75, A=+0.60, D=-0.40 |
| OCC Emotions | Yes | 5 emotions identified |
| SEC Appraisal | Yes | All 8 dimensions evaluated |
| Action Tendencies | Yes | 3 tendencies identified |
| Drive Impact | Yes | All 4 axes evaluated |
| CAPS Inputs | Yes | 6 input nodes activated |
| Goal Impact | Yes | 5 goals evaluated |
| Relationship | Yes | 2 relationships assessed |
| Confidence | Yes | STATED, 0.90 |
| Sub-thoughts | Yes | 4 sub-thoughts decomposed |
| Habituation | Yes | Novel, score 0.92 |
| Traits | Yes (no change needed) | Existing traits sufficient |
| Temporal | Yes | Period: 1934 |
| Reflection | Yes | Level 1 reflection generated |
| Mood | Yes | Snapshot recorded |
| Gut Feeling | Yes | AVOID, intensity 0.85 |

**Completion: 16/16 (100%)**

---

## Appendix A — LLM Prompt Templates

Concrete prompt templates for each pipeline phase. These are starting points — refine based on output quality.

### A.1 Phase 0.5: NLP Gap Sweep

**System prompt:**

```
You are an entity extraction auditor. Given a passage and a list of entities already
identified by NLP tools, your job is to find entities the NLP MISSED. Do not re-identify
entities already in the list. Focus on:
- Metaphorical or indirect references (e.g., "the Blue House" = a place)
- Implicit subjects (pronouns, role references like "my doctor", "the children")
- Abstract concepts treated as entities (e.g., "surrealism", "pain", "independence")
- Works of art, creative works, or events referenced by description not title

Return ONLY newly discovered entities not already in the provided list.
```

**User prompt:**

```
Entities already identified by NLP:
{{nlpEntityList}}

Known aliases from resolution dictionary:
{{resolutionDictionary}}

Passage ({{passageId}}):
"{{passageText}}"

List any entities mentioned in this passage that are NOT in the list above.
For each, provide:
- name: canonical name
- type: PERSON | PLACE | ORG | ACTIVITY | GOAL | PROJECT | CONCEPT
- confidence: 0.0-1.0
- reason: why NLP missed it
```

**Expected output:**

```json
{
  "newEntities": [
    { "name": "Casa Azul", "type": "PLACE", "confidence": 0.9, "reason": "referred to as 'the Blue House'" }
  ]
}
```

### A.2 Phase 1: Structural Extraction

**System prompt:**

```
You are a biographical knowledge graph builder. Given a passage from a biography and a
list of candidate entities (from NLP), extract the structural knowledge: classify entities,
identify relationships, and create experience memories.

Rules:
- Use the NLP candidate list — confirm, reclassify, or exclude each candidate
- Every extracted artifact MUST reference the passage ID for provenance
- Experience memories should be 1-3 sentence summaries of what happened
- Classify event-type as: observation (saw/noticed/learned), action (did/chose/created),
  outcome (resulted-in/achieved/failed)
- For formative events (childhood, adolescence, early adulthood character-shaping events),
  use FormativeExperience type with developmental-period and situation-types
- When contradictory to earlier passages, create the memory anyway with a contradicts attribute
```

**User prompt:**

```
Passage ({{passageId}}):
"{{passageText}}"

NLP candidates for this passage:
{{nlpCandidates}}

Existing graph context (nodes already created):
{{existingNodes}}

Extract:
1. Entity classifications (confirm/reclassify/exclude each NLP candidate)
2. New edges between entities mentioned in this passage
3. Experience memories with event-type and provenance
```

**Expected output:**

```json
{
  "entityClassifications": [
    { "nlpName": "Diego Rivera", "confirmed": true, "subgraphType": "person" },
    { "nlpName": "the Blue House", "confirmed": true, "subgraphType": "place", "canonicalName": "Casa Azul" }
  ],
  "edges": [
    { "source": "Frida Kahlo", "target": "Diego Rivera", "type": "married-to", "confidence": "STATED" }
  ],
  "memories": [
    {
      "text": "Diego and Cristina's betrayal cut deeper than any physical pain.",
      "eventType": "observation",
      "subject": "frida-kahlo",
      "period": "1934",
      "isFormative": false
    }
  ],
  "excludedEntities": [
    { "nlpName": "Hotel Reforma", "reason": "incidental venue mention, no emotional significance" }
  ]
}
```

### A.3 Phase 2a: Passage-Level Enrichment

**System prompt:**

```
You are a cognitive psychologist analysing biographical text. Given an original passage
and the artifacts already extracted from it, apply the cognitive enrichment checklist.

For each artifact, evaluate ONLY the checklist items applicable to its type.
Use the ORIGINAL passage text for your assessment, not the stored summary.

Value ranges:
- PAD dimensions: [-1, +1]
- SEC dimensions: [0, 1] (except conduciveness: [-1, +1])
- Intensities: [0, 1]
- OCC emotions: one of the 22 types (JOY, DISTRESS, HOPE, FEAR, SATISFACTION,
  DISAPPOINTMENT, RELIEF, FEARS_CONFIRMED, PRIDE, SHAME, REPROACH, ADMIRATION,
  GRATITUDE, ANGER, REMORSE, GRATIFICATION, HAPPY_FOR, PITY, RESENTMENT,
  GLOATING, LOVE, HATE)
- Action tendencies: one of the 9 types (APPROACH, AVOIDANCE, ATTENDING, REJECTION,
  ANTAGONISM, INTERRUPTION, SUBMISSION, DOMINANCE, INDIFFERENCE)
- Cognitive distortions: any of (all_or_nothing, catastrophizing, overgeneralization,
  mental_filter, disqualifying_positive, mind_reading, fortune_telling, magnification,
  emotional_reasoning, should_statements, labelling, personalization)

Every value must have a justification. Low/neutral values are valid — they indicate
emotional neutrality, not a gap.
```

**User prompt:**

```
Original passage ({{passageId}}):
"{{passageText}}"

Artifacts to enrich:
{{artifactList}}

Graph context (neighbouring nodes and edges):
{{graphContext}}

Checklist type: {{checklistType}} (items: {{applicableItems}})
Depth tier: {{tier}}

Apply the enrichment checklist. Return the enrichment JSON.
```

**Expected output:** (see §5.2 for the full JSON structure)

### A.4 Phase 2b: Entity-Level Enrichment

**System prompt:**

```
You are a cognitive psychologist building a holistic psychological profile of a
biographical subject. You have access to the entity's complete knowledge graph:
all edges, all memories, and all Phase 2a enrichments.

Your job is to assess the entity-level cognitive dimensions that require full context:
- Drive profile (4 axes: curiosity, competence, affiliation, autonomy)
- CAPS input activation patterns (which situations consistently activate which inputs?)
- Goal system assessment (how do goals relate to each other and to the subject's drives?)
- Reflection synthesis (what insights emerge from the pattern of experiences?)
- Behavioural attractors (what recurring approach/avoidance patterns exist?)

For the primary subject, also infer the disposition profile (5 axes).
```

**User prompt:**

```
Entity: {{entityName}} ({{subgraphType}})

Full context:
- Edges: {{edgeList}}
- Memories (top 20 by significance): {{topMemories}}
- Memory summary (if >30 total): {{memorySummary}}
- Phase 2a enrichments: {{phase2aResults}}

Assess:
1. Overall drive profile (baseline levels, not deltas)
2. Recurring CAPS input patterns
3. Goal system coherence
4. Reflection synthesis (level 1-2)
5. Disposition profile (if primary subject)
```

**Expected output:**

```json
{
  "driveProfile": {
    "curiosity": { "baseline": 0.6, "justification": "..." },
    "competence": { "baseline": 0.7, "justification": "..." },
    "affiliation": { "baseline": 0.8, "justification": "..." },
    "autonomy": { "baseline": 0.9, "justification": "..." }
  },
  "capsPatterns": [
    { "pattern": "betrayal → trust_betrayed + social_threat → ANTAGONISM + AVOIDANCE", "frequency": 3 }
  ],
  "goalCoherence": { "summary": "...", "conflicts": [], "synergies": [] },
  "reflections": [
    { "text": "...", "level": 1, "sourceMemoryIds": ["..."] }
  ],
  "dispositionProfile": {
    "socialOrient": { "value": 0.6, "justification": "..." },
    "ruleFollowing": { "value": 0.3, "justification": "..." },
    "riskAppetite": { "value": 0.7, "justification": "..." },
    "autonomy": { "value": 0.9, "justification": "..." },
    "conflictMode": { "value": 0.7, "justification": "..." }
  }
}
```
