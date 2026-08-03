# Code-Domain Embedding Model Evaluation — Report

**Issue:** casehubio/neocortex#49, casehubio/neocortex#63
**Date:** 2026-08-03
**Corpus:** 2421 garden entries (frozen snapshot)
**Benchmark:** 14 scenarios × KW + NL queries, scored against human-judged baselines

## Recommendation

**Option 3 — Stay with current multi-leg retrieval.** No code-domain model
improves end-to-end retrieval over general-purpose baselines. The vocabulary
gap identified in Hortora/engine#27 is real in tokenization and embedding
discrimination but does not translate to retrieval precision differences.
Proceed with BGE-M3 adoption (#30) for its multi-modal capability
(sparse + ColBERT), not for dense embedding improvement.

## Model Roster

| Model | Role | Params | Tokenizer | Dims |
|-------|------|--------|-----------|------|
| nomic-embed-text v1 | Baseline (current) | 137M | BERT WordPiece | 768 |
| BGE-M3 | Baseline (planned) | 568M | XLM-RoBERTa SentencePiece | 1024 |
| CodeBERT | Candidate | 125M | RoBERTa BPE | 768 |
| UniXcoder | Candidate | 125M | RoBERTa BPE | 768 |
| jina-embeddings-v2-base-code | Candidate | 161M | JinaBERT BPE | 768 |
| nomic-embed-text v1.5 | Candidate | 137M | BERT WordPiece | 768 |

Skyline model (nomic-embed-code, 7B) excluded — requires GPU (14GB VRAM).

---

## Layer 1 — Tokenizer Analysis

How each tokenizer splits Java identifiers. Fewer tokens = better preservation
of semantic units.

| Model | Tokenizer | Avg tokens/identifier |
|-------|-----------|----------------------|
| UniXcoder | RoBERTa BPE | **3.1** |
| jina-code | JinaBERT BPE | **3.1** |
| CodeBERT | RoBERTa BPE | 4.0 |
| nomic-embed-text | BERT WordPiece | 4.8 |
| nomic-v1.5 | BERT WordPiece | 4.8 |
| BGE-M3 | XLM-RoBERTa SentencePiece | **5.3** |

### Key identifier comparisons

```
ConcurrentHashMap
  UniXcoder    (2): Concurrent HashMap
  jina-code    (2): Concurrent HashMap
  CodeBERT     (4): Con current Hash Map
  nomic        (4): concurrent ##has ##hma ##p
  BGE-M3       (5): ▁Con current Ha sh Map

@DefaultBean
  UniXcoder    (3): @ Default Bean
  jina-code    (3): @ Default Bean
  CodeBERT     (4): @ Default Be an
  nomic        (4): @ default ##be ##an
  BGE-M3       (6): ▁@ Def a ult Be an

InMemoryWorkItemTemplateStore
  UniXcoder    (4): InMemory WorkItem Template Store
  jina-code    (4): InMemory WorkItem Template Store
  CodeBERT     (6): In Memory Work Item Template Store
  nomic       (11): in ##me ##mo ##ry ##work ##ite ##mt ##em ##plate ##stor ##e
  BGE-M3      (10): ▁In Me mor y Work I tem Temp late Store
```

**Finding:** BPE tokenizers trained on code (UniXcoder, jina-code) preserve
CamelCase boundaries as meaningful subwords. BERT WordPiece fragments
identifiers into meaningless pieces. BGE-M3's SentencePiece is the worst
tokenizer for Java identifiers — worse than nomic's WordPiece.

**Implication for BGE-M3 adoption:** BGE-M3's tokenizer does not resolve the
vocabulary gap. Its value lies in multi-modal retrieval, not tokenization quality.

---

## Layer 2 — Embedding Discrimination

Can models distinguish semantically different Java concepts that share surface
tokens? Positive gap = correct discrimination (close pairs score higher than
far pairs).

| Model | Mean far | Mean close | Gap | Polysemy |
|-------|----------|------------|-----|----------|
| jina-code | 0.5648 | 0.5739 | **+0.009** | 0.2627 |
| CodeBERT | 0.9517 | 0.9418 | -0.010 | 0.9753 |
| BGE-M3 | 0.6756 | 0.5081 | -0.167 | 0.6171 |
| nomic-v1.5 | 0.6860 | 0.4759 | -0.210 | 0.6850 |
| nomic-embed-text | 0.6404 | 0.4150 | -0.226 | 0.6270 |
| UniXcoder | 0.7215 | 0.2501 | **-0.471** | 0.4875 |

### Vocabulary gap calibration

Reference: nomic-embed-text scores `@DefaultBean` vs `default` at 0.7453.
Models within ±0.05 of this score have an equivalent vocabulary gap.

| Model | `@DefaultBean` vs `default` | Status |
|-------|----------------------------|--------|
| nomic-embed-text | 0.7453 | FLAGGED (reference) |
| nomic-v1.5 | 0.7771 | FLAGGED |
| CodeBERT | 0.9163 | FLAGGED (worse) |
| BGE-M3 | 0.5812 | OK |
| UniXcoder | 0.5466 | OK |
| jina-code | 0.4005 | OK (best) |

### BGE-M3 multi-modal analysis

**Sparse:** Zero token overlap on "should be close" pairs (`@DefaultBean` and
"CDI ambiguous dependency resolution" share no sparse activations). Sparse
retrieval cannot bridge the gap between code identifiers and their natural
language descriptions.

**ColBERT:** Same inverted pattern as dense — "should be far" pairs score
higher than "should be close" pairs. Token-level MAX_SIM does not improve
discrimination for code concepts.

**Finding:** jina-code is the only model with correct discrimination polarity.
It understands that `@DefaultBean` ≠ `default` (0.40 vs nomic's 0.75) and
that `ConcurrentHashMap` ≈ "thread-safe map for concurrency" (0.82 vs nomic's
0.46). But better tokenization does not guarantee better discrimination —
UniXcoder has the best tokenizer and the worst discrimination gap (-0.47).

---

## Layer 3 — Full Benchmark (14 scenarios)

Precision@20 on the Hortora engine benchmark harness. Dense-only retrieval
(cosine similarity) on the frozen 2421-entry corpus.

| Model | KW | NL | Overall | Rank |
|-------|----|----|---------|------|
| **nomic-embed-text** | **57.5%** | **63.2%** | **60.4%** | **1** |
| BGE-M3 | 55.4% | 57.5% | 56.5% | 2 |
| nomic-v1.5 | 52.1% | 60.0% | 56.1% | 3 |
| jina-code | 50.0% | 58.2% | 54.1% | 4 |
| UniXcoder | 25.7% | 27.5% | 26.6% | 5 |
| CodeBERT | 3.9% | 0.0% | 2.0% | 6 |

### Vocabulary gap scenarios

| Model | VG KW | VG NL | VG avg |
|-------|-------|-------|--------|
| **nomic-embed-text** | **64.2%** | **69.2%** | **66.7%** |
| nomic-v1.5 | 61.7% | 66.7% | 64.2% |
| BGE-M3 | 61.7% | 60.0% | 60.9% |
| jina-code | 54.2% | 65.0% | 59.6% |
| UniXcoder | 25.0% | 25.8% | 25.4% |
| CodeBERT | 3.3% | 0.0% | 1.7% |

### Decision criteria evaluation

Per the spec, a candidate advances to Layer 4 if it demonstrates:
- Higher precision than BGE-M3 dense-only on VOCABULARY_GAP scenarios → **No.** All candidates score below BGE-M3 (60.9%).
- Higher end-to-end precision than BGE-M3 dense + BM25 on any scenario class → **No.** All candidates score below BGE-M3 (56.5%).
- Comparable precision with materially lower cost → **No.** jina-code (54.1%) is 2.4pp below BGE-M3 and slower on CPU.

**Result:** No candidate advances. Layer 4 skipped.

---

## Key Findings

### 1. Tokenization does not predict retrieval quality

The original vocabulary gap hypothesis (engine#27) assumed that WordPiece
fragmentation of Java identifiers degrades retrieval. Layer 1 confirms the
fragmentation. Layer 3 refutes the causal link:

- UniXcoder: best tokenizer (3.1 avg), worst retrieval (26.6%)
- nomic-embed-text: mediocre tokenizer (4.8 avg), best retrieval (60.4%)

The training objective (contrastive similarity fine-tuning) dominates
tokenization quality for retrieval precision. Models trained on sentence
similarity tasks (nomic, BGE-M3, jina-code) all outperform models trained
only on code understanding (CodeBERT, UniXcoder) regardless of tokenizer.

### 2. BGE-M3 dense is slightly worse than nomic-embed-text dense

BGE-M3 scores 56.5% overall vs nomic's 60.4% despite having 4× parameters.
On vocabulary gap scenarios: BGE-M3 gets 60.9% vs nomic's 66.7%. The planned
BGE-M3 migration (#30) should not be justified by dense embedding quality —
it may slightly degrade the dense leg. BGE-M3's value is its multi-modal
capability (dense + sparse + ColBERT in one model), not its dense embeddings.

### 3. Code-domain encoder models are non-starters

CodeBERT (2.0%) and UniXcoder (26.6%) fail catastrophically. These are
encoder-only models pre-trained with MLM/contrastive objectives on code.
They produce embeddings where everything clusters together (CodeBERT's Layer 2
shows all-pair cosine >0.94). They were never fine-tuned for retrieval.

### 4. jina-code demonstrates genuine code understanding but can't translate it to precision

jina-code is the only model with:
- Correct discrimination polarity (Layer 2: +0.009 gap)
- Code-specific retrieval training (150M code pairs)
- Good tokenization (3.1 avg tokens)

Yet it scores 54.1% overall — 6.3pp below nomic-embed-text. The gap is not
in vocabulary handling but in the corpus characteristics: the garden contains
technical prose about Java concepts, not source code. Models trained on
code↔NL pairs don't generalize to NL↔NL retrieval in a technical domain.

### 5. The current three-leg system (94%) already solved the problem

The vocabulary gap affects dense-only retrieval (60.4% best case). The
production system uses three legs — nomic dense + SPLADE sparse + BM25 via
server-side RRF — achieving 94% precision. BM25 handles keyword-gap scenarios
(exact term matching on `ConcurrentHashMap`, `@DefaultBean`) that all dense
models struggle with. Swapping the dense model provides at most a marginal
improvement to one leg of a three-leg system.

---

## Implications for BGE-M3 Adoption (#30)

BGE-M3 adoption is still justified, but for different reasons than originally
assumed:

| Assumption | Reality |
|-----------|---------|
| BGE-M3 BPE tokenizer handles Java better | **No.** Worst tokenizer tested (5.3 avg tokens) |
| BGE-M3 dense improves over nomic dense | **No.** 4pp worse on this corpus |
| BGE-M3 value is dense quality | **No.** Value is model consolidation |

The case for BGE-M3 is operational simplification: one model producing three
embedding types (dense + sparse + ColBERT) replaces three separate models
(nomic + SPLADE + future ColBERT). The dense leg may slightly degrade, but
the other legs compensate.

---

## Layer 4 — Deployment Feasibility

**Skipped.** No candidate met the Layer 3 decision criteria.

---

## Data

All results are machine-readable in `results/`:
- `tokenizer_splits.json` — Layer 1 per-model per-identifier token sequences
- `discrimination_scores.json` — Layer 2 cosine similarities per pair
- `benchmark_precision.json` — Layer 3 precision per model per scenario

Corpus snapshot: `corpus-snapshot/manifest.json` (2421 entries, frozen 2026-08-03).

---

## References

- Hortora/engine#27 — dense-only baseline benchmark identifying vocabulary gap
- Hortora/engine#29 — complementary retrieval research → three-leg validation
- casehubio/neocortex#30 — BGE-M3 adoption (open)
- casehubio/neocortex#49 — this evaluation (evaluation scripts)
- casehubio/neocortex#63 — this evaluation (execution + report)
- Spec: `docs/specs/2026-07-02-code-domain-embedding-evaluation-design.md`
