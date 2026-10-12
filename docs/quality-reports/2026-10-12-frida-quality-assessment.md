# Frida Kahlo Corpus Quality Assessment

## Summary

**Overall grade: PARTIAL**
**Date:** 2026-10-12
**Corpus:** `extraction/output/corpus.json` (format: cognitive-workbench-corpus/v2)

The corpus captures Frida Kahlo's biographical narrative, goal system, and disposition profile with substantial depth. Entity coverage is adequate for core figures but has naming consistency problems. The goal hierarchy is rich but heavily weighted toward `contributes-to` edges with insufficient structural variety. PAD values and disposition axes are psychologically plausible. The most significant gap is the complete absence of per-memory enrichments (OCC, SEC, drives, sub-thoughts, CAPS, gut feeling, habituation) -- only PAD values are populated at the memory level. Entity-level enrichments are thorough.

| Dimension | Grade |
|-----------|-------|
| Entity Coverage | PARTIAL |
| Goal Coherence | PARTIAL |
| Enrichment Plausibility | PARTIAL |
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

139 references in edges have no corresponding node. The most significant problems:
- **"Frida" vs "Frida Kahlo"**: 223 edges use "Frida", 38 use "Frida Kahlo" -- the node is named "Frida Kahlo" but most edges reference "Frida"
- **"Frida's father" vs "Guillermo Kahlo"**: Node is "Frida's father", edges reference "Guillermo Kahlo"
- **"Frida's mother" vs "Matilde Calderon"**: Same pattern
- **"Alejandro" vs "Alejandro Gomez Arias"**: Node uses "Alejandro", edges use the full name

This means the MindMap import will create dangling references or fail entity resolution for 139 edge endpoints.

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

**Grade: PARTIAL**

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

### Per-Memory Enrichments (critical gap)

**Zero memories have:** OCC emotions, SEC appraisals, drive impacts, sub-thoughts, CAPS inputs, gut feeling, habituation scores, action tendencies, or cognitive distortions.

This is the most significant quality gap. The memory-seeding pipeline (`LifeEventHandler`) is designed to import all these enrichment fields from the corpus. Their absence means:
- The CARMA appraisal system has no per-memory SEC/OCC data to learn from
- The CAPS engine has no input activations from biographical events
- Sub-thought decomposition cannot bootstrap from historical data
- Drive impact tracking starts from zero rather than biographical baselines

The entity-level enrichments (drive baselines, CAPS patterns, behavioural attractors) ARE populated and appear thorough, but per-memory enrichments are entirely missing.

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
| contributes-to | 1172 | 74.6% -- dominates |
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
| Others | 51 | 19 types |

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
4. **contributes-to saturation:** With 1172 edges, the contributes-to relationship has lost discriminative power. When a single goal contributes to 50 others, the relationship is not meaningful -- it indicates a flat "everything relates to everything" pattern.

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
| Edge-only references | 139 |
| Subgraph assignments | 0 (all "none") |

### Memory domain distribution

| Domain | Count |
|--------|-------|
| experience | 101 |
| reflection | 78 |
| mood | 67 |
| relationship | 15 |

### Issues

1. **No subgraph assignments:** All nodes have `subgraph: none`. MindMap expects PERSON, GOAL, CONCEPT, ACTIVITY, PLACE, ORGANISATION subgraph types. Without these, the TypeRegistry and SubgraphTypes infrastructure is unused.
2. **No node type field:** All 132 nodes have `type: unknown`. The corpus format should assign types (person, place, organisation, concept, goal, event).
3. **139 dangling edge references:** Entity names in edges don't match node names. This will cause entity resolution failures during import.
4. **Arousal compression:** PAD arousal values are compressed into a narrow high range (mean 0.732, stdev 0.14), reducing emotional differentiation.
5. **Edge-to-node ratio:** 1570 edges / 132 nodes = 11.9 edges per node. The contributes-to saturation inflates this -- without contributes-to, it's 398/132 = 3.0 which is reasonable.
6. **Goal dedup stats show 0 merges** at the initial pass, 5 at LLM pass, but 20 near-misses with JW >0.85 remain. The dedup threshold may be too conservative.

---

## Issues to File

### Systematic quality problems

1. **Per-memory enrichments missing (Critical):** Zero OCC, SEC, drive, sub-thought, CAPS, gut-feeling, habituation, action-tendency, or cognitive-distortion fields populated across 261 memories. The extraction/enrichment pipeline produces these in the YAML but they are not making it into the assembled corpus. This defeats the purpose of the enrichment pass.

2. **Entity naming inconsistency (High):** 139 edge references don't match node names. Primary pattern: short names in edges ("Frida", "Diego") vs full names in nodes ("Frida Kahlo", "Diego Rivera"), and descriptive names in nodes ("Frida's father") vs proper names in edges ("Guillermo Kahlo"). Needs entity resolution or name normalization in the assembly step.

3. **Missing subgraph and type assignments (High):** All nodes have `type: unknown` and `subgraph: none`. The MindMap import relies on these for TypeRegistry bootstrap, trait assignment, and subgraph-scoped operations. The assembly step should assign types based on traits (Personable -> person, Locatable -> place, etc.) and derive subgraphs.

4. **Contributes-to edge saturation (Medium):** 1172 of 1570 edges (74.6%) are contributes-to, with single goals having 50 outgoing edges. This makes the goal graph nearly fully connected and reduces its analytical value. The extraction should either be more selective or use a confidence threshold to prune weak contributions.

5. **Factual errors in edges (Medium):** At least 3 factually incorrect edges found in a sample of 25: married-to Frida->Alejandro (never married), parent-of inversions, dubious Matilde->Zapata collaboration. Systematic verification needed.

6. **Arousal value compression (Low):** Arousal mean 0.73 with stdev 0.14 suggests systematic high-arousal bias in the enrichment prompt. Calm biographical moments (childhood walks, quiet painting sessions) should have lower arousal.
