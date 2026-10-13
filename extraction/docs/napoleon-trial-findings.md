# Napoleon Trial Run Findings — 2026-10-13

## Summary

The cognitive extraction pipeline generalised successfully to Napoleon Bonaparte's biography. The pipeline produced a corpus of comparable scale and quality to the Frida Kahlo baseline with **zero code changes beyond the pre-planned generality fixes** (3 hardcoded defaults, 2 prompt examples). All per-corpus adaptation was handled through configuration files (aliases.yaml, verify_schema.yaml) — exactly as the epic #551 generality standard requires.

Napoleon's corpus fills 1 of 2 zero-count OCC emotion gaps (gloating: 7 instances) and significantly strengthens cognitive distortion representation (fortune_telling: 101 vs 42, disqualifying_positive: 37 vs 11, should_statements: 32 vs 25). The happy-for gap remains unfilled.

## Structural Comparison

| Metric | Frida | Napoleon | Notes |
|--------|-------|----------|-------|
| Source words | ~7,000 | ~6,300 | Comparable input scale |
| Source passages | 15 | 20 | Napoleon has more life phases |
| Nodes | 132 | 189 | Napoleon has more places, orgs, concepts |
| Edges | 703 | 700 | Nearly identical edge volume |
| Memories | 261 | 327 | 25% more — longer biography + more entity synthesis |
| Entity enrichments | 20 | 29 | More named entities to enrich |
| Edge ratio (edges/nodes) | 5.3 | 3.7 | Napoleon's larger node set dilutes ratio |
| Dangling edges | — | 1.3% | Well under 10% threshold |

### Node Type Distribution

| Type | Frida | Napoleon |
|------|-------|----------|
| goal | 107 (81%) | 118 (62%) |
| person | 9 (7%) | 10 (5%) |
| place | 5 (4%) | 17 (9%) |
| organisation | 4 (3%) | 12 (6%) |
| activity | 3 (2%) | 12 (6%) |
| project | 0 | 5 (3%) |
| concept | 4 (3%) | 15 (8%) |

Napoleon's corpus has a more diverse entity type distribution — reflecting his political/military life spanning more organisations, places, and projects. Goals are still the dominant type but less overwhelmingly so.

### Memory Domain Distribution

| Domain | Frida | Napoleon |
|--------|-------|----------|
| experience | 105 (40%) | 106 (32%) |
| relationship | 15 (6%) | 11 (3%) |
| reflection | 78 (30%) | 119 (36%) |
| mood | 63 (24%) | 91 (28%) |

Napoleon generates more reflections (119 vs 78) — consistent with his self-reflective exile period and strategic self-analysis throughout life.

## Coverage Comparison

| Category | Frida | Napoleon |
|----------|-------|----------|
| Memory fields | 17/17 (100%) | 13/17 (76.5%) |
| Enrichment fields | 22/24 (91.7%) | 19/24 (79.2%) |
| Node fields | 15/27 (55.6%) | 15/27 (55.6%) |
| OCC emotions (unique types) | 34 | 41 |
| Action tendencies (unique types) | 16 | 13 |
| Cognitive distortions (unique types) | 16 | 21 |
| SEC dimensions | 8/8 | 8/8 |
| Sub-thought types | 7/7 | 7/7 |

Napoleon's memory field coverage is slightly lower (76.5% vs 100%) because entity-synthesis memories (185 of 327) lack some passage-level fields (related_entities, source_provenance, passage_id, mentioned_entities). This is a known characteristic of Phase 2b memories, not a Napoleon-specific issue — the same pattern exists in Frida but was masked by a smaller proportion of Phase 2b memories.

## Cognitive Model Gap Verification (vs #498)

### Zero-count gaps

| Dimension | #498 prediction | Napoleon result | Filled? |
|-----------|----------------|-----------------|---------|
| gloating | competitive rivalry | **7 instances** | **YES** |
| happy-for | vicarious joy for allies | **0 instances** | **NO** |

**Gloating** is well-filled: Napoleon's competitive rivalry with the Directory, his pleasure at Bernadotte's marginalisation, and his satisfaction when subordinates failed independently all produced genuine gloating instances.

**Happy-for** remains at zero. The decision review predicted this: Napoleon's documented attitude toward subordinates' successes was proprietary (their victories were extensions of HIS campaigns), not disinterested joy. This is a genuine gap that may require a different biography to fill — Darwin or Florence Nightingale would likely produce this emotion more reliably.

### Weak dimensions

| Dimension | Frida | Napoleon | Strengthened? |
|-----------|-------|----------|---------------|
| sorry-for | 1 | **5** | **YES** |
| hate | 5 | 3 | No — comparable |
| disgust | 9 | 5 | No — comparable |

**Sorry-for** is strengthened: Napoleon's empathy for soldiers during the retreat from Moscow, his retrospective sorrow at St Helena, and his genuine compassion for Joséphine during the divorce all produced sorry-for instances.

**Hate** and **disgust** are comparable to Frida rather than strengthened. Napoleon's hate was strategic (Talleyrand, Bernadotte) rather than sustained, and his disgust was contempt for weakness rather than moral revulsion.

### Cognitive distortion representation strength

| Distortion | Frida | Napoleon | Δ |
|------------|-------|----------|---|
| fortune_telling | 42 | **101** | **+140%** |
| disqualifying_positive | 11 | **37** | **+236%** |
| should_statements | 25 | **32** | **+28%** |

All three target distortions are significantly strengthened. Fortune-telling is the standout — Napoleon's strategic forecasting, his predictions about Russia, and his assumption that presence in Moscow would compel surrender all produced this distortion at high volume.

### Action tendency: interruption

| Tendency | Frida | Napoleon |
|----------|-------|----------|
| interruption | 0 | **0** |

The interruption action tendency was not extracted for Napoleon. This may be a vocabulary/classification issue — Napoleon's impulsive intervention behaviour may have been classified under "dominance" (111 instances) or "antagonism" (86 instances) rather than "interruption" specifically. The extraction prompt may need a clearer definition of interruption as a distinct tendency.

### Combined coverage

| Dimension | Frida alone | Combined |
|-----------|-------------|----------|
| OCC emotions (of 22 canonical) | 20/22 | **21/22** (happy-for still missing) |
| Action tendencies (of 9) | 8/9 | **8/9** (interruption still missing) |
| Cognitive distortions (of 12) | 12/12 | **12/12** (representation strengthened) |

## What Worked Without Changes

1. **Pipeline orchestration** — `run_pipeline.sh` ran end-to-end on Napoleon after the `--schema` parameter was added
2. **Chunking** (`chunk.py`) — handled Napoleon's passage format identically to Frida
3. **NLP baseline** (`nlp_extract.py`) — entity concordance worked correctly
4. **LLM gap sweep** (`llm_gap_sweep.py`) — identified 119 new entities across 21 passages
5. **Structural extraction** (`extract_structure.py`) — produced 189 nodes and 700 edges
6. **Cognitive enrichment** (`enrich_cognitive.py`) — 120/142 memories enriched (84.5%)
7. **Entity synthesis** (`enrich_entity.py`) — 29 entities enriched, 185 new memories generated
8. **Assembly** (`assemble_corpus.py`) — corpus assembled with LLM dedup
9. **Verification** (`verify_corpus.py`) — 0 CRITICALs after alias refinement
10. **Convergence loop** — converged after 1 pass (no new memories from convergence extraction)

## What Needed Per-Corpus Config

1. **`verify_schema.yaml`** — changed `converter.subject_name` to "Napoleon Bonaparte" and replaced entity aliases. All schema types, vocabularies, and enrichment contracts remained identical.
2. **`aliases.yaml`** — required extensive refinement after first run. Initial draft had ~50 aliases; final version has ~100+ aliases mapping entity name variants to canonical node names. This is the primary per-corpus adaptation work.
3. **`edge-corrections.yaml`** — remained empty. No factual errors needed correction in the Napoleon corpus (unlike Frida, which had 2 corrections).

## What Needed Code Changes

1. **`run_pipeline.sh`** — added `--schema` parameter with output-directory discovery convention
2. **`generate_overlay.py`** — made `--subject` required (removed "Frida Kahlo" default)
3. **`assemble_corpus.py`** — removed `'frida-kahlo'` subject fallback (fail on missing subject)
4. **`extract_structure.py`** — replaced Frida-specific prompt examples with generic ones
5. **`enrich_entity.py`** — replaced "Diego Rivera" prompt example with generic one

All 5 changes were small, targeted, and committed before the Napoleon trial run.

## Recommendations

1. **Happy-for gap** — Napoleon does not fill this emotion. Consider Darwin (documented disinterested joy at colleagues' discoveries) or Florence Nightingale (vicarious joy at patients' recovery) as a third biography candidate. File as issue.
2. **Interruption tendency** — neither Frida nor Napoleon produce this tendency. The extraction vocabulary may need clarification — Napoleon's impulsive intervention may be classified under dominance/antagonism instead. Review the action tendency taxonomy. File as issue.
3. **Per-corpus directory structure** — with two biographies, the flat layout (`output/`, `output-napoleon/`) works. If a third biography is added, restructure to `corpus/frida-kahlo/`, `corpus/napoleon/`, `corpus/<third>/` for cleaner organisation. File as issue if a third biography is planned.
4. **Memory field coverage** — Phase 2b (entity synthesis) memories lack passage-level fields. This is a pipeline design issue, not a per-corpus issue. Consider adding provenance fields to Phase 2b memories. File as issue.
5. **Memory post-processing for CAPS integration** — slot 196 is investigating why character drives (e.g. gloating) collapse through the cognitive pipeline even when extraction produces the raw material (Napoleon has 7 gloating instances). The fix involves CAPS topology and SituationClassifier improvements (situation-to-drive routing). Once that work lands, Napoleon's memories will likely need additional metadata (CAPS input activations, situation classifications, drive-routing hints) before re-import. Hold off on import until slot 196's CAPS routing fixes land, then add a post-processing step to enrich memories with the new metadata shape.
6. **Edge type vocabulary** — Napoleon's corpus produced several unknown edge types (married-to, affair-with, divorced-from, succeeded-by). Consider adding relationship-specific edge types to the valid list, or teaching the extraction to map these to existing types. File as issue.
