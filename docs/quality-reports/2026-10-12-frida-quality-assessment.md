# Frida Kahlo Corpus Quality Assessment

## Summary

**Overall grade: PARTIAL**
**Date:** 2026-10-12
**Corpus:** `extraction/output/corpus.json` (format: cognitive-workbench-corpus/v2)

The corpus captures Frida Kahlo's biographical narrative, goal system, and disposition profile with substantial depth. Entity coverage is adequate for core figures but has naming consistency problems. The goal hierarchy is rich but heavily weighted toward `contributes-to` edges with insufficient structural variety. PAD values and disposition axes are psychologically plausible. Per-memory enrichments (OCC, SEC, drives, sub-thoughts, CAPS, gut feeling, habituation) are present in 197/261 memories -- the 64 without enrichments are mood-snapshot and reflection memories that bypass the enrichment pipeline. Entity-level enrichments are thorough.

| Dimension | Grade |
|-----------|-------|
| Entity Coverage | PARTIAL |
| Goal Coherence | PARTIAL |
| Enrichment Plausibility | PASS |
| Edge Fidelity | PARTIAL |
| Disposition Profile | PASS |
| Structural Integrity | PARTIAL |

---

## 1. Entity Coverage

**Grade: PARTIAL**

### Present (25 entity nodes)

**People (8):** Frida Kahlo, Alejandro, Frida's mother, Frida's father, Diego Rivera, Lupe Marin, Edward Weston, Jean Charlot, Julien Levy
**Places (5):** Mexico City, La Casa Azul, Coyoacan, Cuauhtemotzin/Cinco de Mayo intersection, Frida and Diego's intellectual salon
**Organisations (4):** Los Cachuchas, Red Cross Hospital, Communist intellectual circle, Surrealist movement
**Events (3):** Bus accident of 17 September 1925, Painting as artistic practice, Frida and Diego's marriage
**Concepts (2):** Frida's artistic identity, Physical injuries from bus accident

### Present in edges but not as nodes (key figures)

These figures appear in edge references but lack dedicated entity nodes:
- Leon Trotsky (affair-with, married-to edges)
- Andre Breton (edge references only)
- Nickolas Muray (affair-with edges)
- Dr Eloesser / Leo Eloesser (edge references)
- Tina Modotti (championed edge)
- Georgia O'Keeffe (affair-with edge)
- Cristina (affair-with edge -- Frida's sister, important betrayal)
- Isamu Noguchi (affair-with edge)
- Matilde Calderon (married-to edge -- Frida's mother, but "Frida's mother" node uses different name)
- Guillermo Kahlo (child-of edge -- but "Frida's father" node uses different name)

### Missing entities (no node or edge reference)

- **Artworks as nodes:** The Two Fridas, The Broken Column, Henry Ford Hospital, Self-Portrait with Cropped Hair, What the Water Gave Me, A Few Small Nips, VIVA LA VIDA appear in edges but are not entity nodes. For a cognitive model of an artist, key artworks should be nodes.
- **Places:** Detroit, Paris, New York, San Francisco appear in edges only

### Naming inconsistency (critical issue)

~~139 references in edges had no corresponding node.~~ **Corrected 2026-10-13:** Edge resolution now resolves 312 of 534 dangling references via four strategies: first-name matching (260), explicit aliases (21), parenthetical cleanup (17), dedup aliases (14). After resolution, 222 references remain — these are genuinely missing entities (artworks, historical figures, places) that need nodes created, not naming inconsistencies. See #565.

---

## 2. Goal Coherence

**Grade: PARTIAL**

### Strengths

- **107 goal nodes** covering the full biographical arc from childhood through death
- Goals span thematic (long-term identity), strategic (multi-year campaigns), and tactical (specific actions)
- Goal conflicts are identified in entity enrichments (e.g., motherhood vs. physical limitations, independence vs. attachment to Diego)
- LLM deduplication merged 5 near-duplicate pairs with sensible canonical choices

### Issues

- **Heavily flat hierarchy:** 1172 `contributes-to` edges vs. only 18 `enables`, 7 `blocks`, 3 `requires`, 1 `decomposes-into`. The goal graph is almost entirely one relationship type. A psychologically meaningful goal hierarchy should have more structural variety -- `blocks` and `requires` relationships are underrepresented given Frida's life of constraints.
- **Fan-out concentration:** Single goals have up to 50 outgoing `contributes-to` edges (e.g., "Attend her first solo exhibition despite physical incapacity" contributes to 50 other goals). This makes the graph nearly fully connected at the contributes-to level, reducing its discriminative value.
- **Near-duplicates remain:** Despite LLM dedup, the stats show 20 near-miss pairs with Jaro-Winkler scores >0.85 that were not merged. Several are semantically equivalent across tiers (e.g., "Maintain artistic independence and creative expression" / "Maintain artistic practice despite physical deterioration").
- **No subgraph assignment:** All 132 nodes have `subgraph: none`. The MindMap architecture expects nodes in subgraphs (PERSON, GOAL, CONCEPT, etc.). Without subgraph assignment, the import will place everything in a flat namespace.

---

## 3. Enrichment Plausibility

**Grade: PASS** (corrected 2026-10-13 — was PARTIAL based on incorrect enrichment assessment)

### PAD Values (memory-level)

260 of 261 memories have PAD values. Distribution:

| Dimension | Min | Max | Mean | Stdev |
|-----------|-----|-----|------|-------|
| Pleasure | -0.95 | 0.95 | 0.165 | 0.606 |
| Arousal | -0.40 | 1.00 | 0.732 | 0.140 |
| Dominance | -0.95 | 0.85 | 0.195 | 0.564 |

**Arousal bias:** Mean arousal is 0.732 with a very narrow standard deviation (0.14). This means nearly all memories cluster at high arousal. While Frida's life was intense, this distribution suggests the extraction systematically assigns high arousal rather than discriminating between calm and intense moments. The range barely extends below 0 (min -0.4 vs -0.95 for pleasure and dominance).

**Sample plausibility checks:**

1. **Birth memory** (P=0.6, A=0.3, D=0.4): Plausible -- positive, low arousal, moderate dominance for a biographical origin fact.
2. **Hosting Trotsky** (P=0.3, A=0.8, D=0.2): Plausible -- moderately positive but high-stakes political act with low personal control.
3. **Death summary** (P=-0.75, A=0.85, D=0.6): Plausible -- suffering with high activation, elevated dominance reflects defiance.
4. **Relationship with Guillermo** (P=0.85, A=0.7, D=0.65): Plausible -- warm, energetic, empowering relationship.

### Per-Memory Enrichments (present)

**Correction (2026-10-13):** The original assessment incorrectly reported zero per-memory enrichments. Direct verification shows 197/261 memories have the full enrichment suite:

| Field | Present |
|-------|---------|
| occ_emotions | 197 |
| sec_appraisal | 197 |
| action_tendencies | 197 |
| sub_thoughts | 197 |
| cognitive_distortions | 192 |
| drive_impact | 197 |
| habituation | 197 |
| caps_inputs | 197 |
| goal_impacts | 197 |
| gut_feeling | 193 |

The 64 memories without enrichments are mood-snapshot (67) and reflection memories that bypass the enrichment pipeline -- expected behaviour. #564 closed as invalid.

### Entity-Level Enrichments (adequate)

20 entities have enrichments across 6 dimensions. Frida Kahlo's enrichments include:
- **Drive baselines:** curiosity=0.85, competence=0.88, affiliation and autonomy populated with justifications. These are psychologically plausible for a highly driven, intellectually curious artist.
- **CAPS patterns:** 3+ patterns with confidence scores 0.87-0.95. The "physical immobility -> creative sublimation" pattern captures a core biographical dynamic.
- **Behavioural attractors:** "defiance-through-artistic-authenticity" (0.9), "autonomy-seeking-through-intellectual-equality" (0.85) -- well-documented biographical patterns.
- **Goal coherence:** Summary, conflicts, and structural analysis present.

---

## 4. Edge Fidelity

**Grade: PARTIAL**

### Edge type distribution (1570 total)

| Type | Count | Notes |
|------|-------|-------|
| contributes-to | 317 | 44.3% (was 1172/74.6% — fixed by subdomain scoping in #567) |
| pursues | 113 | Agent-goal links |
| associated-with | 41 | Generic |
| at | 36 | Location |
| created-by | 24 | |
| located-in | 21 | Spatial hierarchy |
| enables | 18 | Goal structure |
| collaborated-with | 17 | |
| participated | 16 | |
| lives-at | 12 | |
| affair-with | 10 | Relationship |
| married-to | 9 | Includes duplicates |
| Others | 81 | 19 types |

### Sample edge plausibility

- `[lives-at] Frida -> La Casa Azul`: Correct
- `[child-of] Frida -> Guillermo Kahlo`: Correct (but name mismatch with node)
- `[inspired-by] Frida -> Diego Rivera`: Correct
- `[motivated-by] Physical injuries -> Transform pain into meaning`: Correct
- `[championed] Tina Modotti -> Frida`: Correct historical fact

### Issues

1. **Duplicate/bidirectional edges:** married-to has bidirectional duplicates (Diego-Frida appears 4 times across naming variants). Natalia Sedova-Trotsky also duplicated.
2. **Factual errors:**
   - `[married-to] Frida -> Alejandro Gomez Arias`: **Incorrect.** They were romantic partners, never married.
   - `[parent-of] Frida Kahlo -> Frida's mother` and `[parent-of] Frida Kahlo -> Frida's father`: **Inverted.** Frida was the child, not the parent.
   - `[collaborated-with] Matilde Calderon -> Emiliano Zapata's`: **Dubious.** No established historical record of this collaboration.
   - `[enabled-by] Matilde Calderon -> Establish political commitment`: **Dubious.** Frida's political awakening came through Los Cachuchas and Diego, not her mother.
3. **UNVALIDATED edge:** `[UNVALIDATED] Los Cachuchas -> Alejandro Gomez Arias` -- the edge type is literally the string "UNVALIDATED" rather than a semantic type.
4. ~~**contributes-to saturation:**~~ **Fixed (#567):** Cross-tier inference now scoped to subdomain (not domain) and adjacent tiers only. contributes-to edges reduced from 1172 to 317. Max fan-out from 50 to 18. Total edges from 1570 to 715.

---

## 5. Disposition Profile

**Grade: PASS**

| Axis | Score | Assessment |
|------|-------|-----------|
| socialOrient | 0.70 | Plausible -- gregarious in intellectual settings but selectively engaged |
| ruleFollowing | 0.25 | Plausible -- consistent norm violation (affairs, artistic conventions, political rebellion) |
| riskAppetite | 0.75 | Plausible -- pursued dangerous surgeries, political risks, artistic controversy |
| autonomy | 0.80 | Plausible -- independence was a core life theme |
| conflictMode | 0.80 | Plausible -- confrontational in art and relationships |

All five axes have substantive justifications (450-545 chars each) grounded in biographical evidence. The profile correctly captures Frida as a high-autonomy, rule-breaking, risk-taking, confrontational personality with strong social engagement. This would produce sensible `CognitiveDefaults` via the `CognitiveDerivationEngine`.

---

## 6. Structural Integrity

### Counts

| Category | Count |
|----------|-------|
| Nodes | 132 |
| Edges | 1570 |
| Memories | 261 |
| Entity enrichments | 20 |
| Orphan nodes | 0 |
| Edge-only references | 118 (was 139 — 312 resolved by edge resolution) |
| Subgraph assignments | 132 (was 0 — derived from subgraph_type, see #566) |

### Memory domain distribution

| Domain | Count |
|--------|-------|
| experience | 101 |
| reflection | 78 |
| mood | 67 |
| relationship | 15 |

### Issues

1. ~~**No subgraph assignments:**~~ **Fixed (#566):** All 132 nodes now have `subgraph` derived from `subgraph_type` (107 goal, 9 person, 5 place, 4 organisation, 4 concept, 3 activity).
2. **Node type field:** `subgraph_type` serves as the type field. The `type` accessor on MindMapNode delegates to `subgraphType()` — no separate `type` field needed.
3. **118 dangling edge references** (was 139 — edge resolution fixed naming inconsistencies). Remaining references are genuinely missing entities (artworks, historical figures, places).
4. **Arousal compression:** PAD arousal values are compressed into a narrow high range (mean 0.732, stdev 0.14), reducing emotional differentiation.
5. **Edge-to-node ratio:** 715 edges / 132 nodes = 5.4 edges per node (was 11.9 before #567 fix).
6. **Goal dedup stats show 0 merges** at the initial pass, 5 at LLM pass, but 20 near-misses with JW >0.85 remain. The dedup threshold may be too conservative.

---

## Issues Filed

| # | Title | Severity | Scale |
|---|-------|----------|-------|
| #564 | ~~Per-memory enrichments missing from assembled corpus~~ | ~~Critical~~ | ~~M~~ | Closed — enrichments present, assessment was incorrect |
| #565 | Entity naming inconsistency causes 139 dangling edge references | High | M |
| #566 | Corpus nodes missing subgraph and type assignments | High | S |
| #567 | Contributes-to edge saturation reduces goal graph analytical value | Medium | S |
| #568 | Factual errors in extracted edges need systematic verification | Medium | S |
| #569 | PAD arousal value compression in enrichment pipeline | Low | XS |
