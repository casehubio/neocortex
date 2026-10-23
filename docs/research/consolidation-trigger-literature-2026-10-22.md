# Consolidation Trigger Literature Review — 2026-10-22

## Question

How should memory consolidation be triggered in a cognitive agent? Specifically: should the trigger use temporal decay (CUSUM-style), and how long can consolidation be delayed before users notice a lag between events and behavioural changes?

## Prior Design

`DegreeAccumulator` — dual-rate CUSUM with decay (λ=0.85 fast, slower on slow channel). Borrowed from statistical process control. Parameters: allowance (noise filter), threshold (firing point), decay (temporal discount).

**Problem observed:** zero drive adaptation across 50 ticks. Decay suppresses slow-building personality changes when per-tick signal < allowance.

## Literature Findings

### 1. Park et al. (2023) — Generative Agents: Interactive Simulacra of Human Behavior. UIST '23.

Reflection (consolidation equivalent) uses **monotonic importance accumulation with no decay**. Each observation scored 1-10 importance. Reflection fires when cumulative importance exceeds ~150 (~15-20 interactions). No experience discounted by time.

**Relevance:** Direct precedent for monotonic accumulation in believable agents. Their Smallville evaluation showed users notice behavioural inconsistency within a few interactions — the basis for the 5-10 interaction threshold.

### 2. Shinde (2026) — SCM: Sleep-Consolidated Memory with Algorithmic Forgetting for Large Language Models. arXiv:2604.20943.

Four consolidation triggers, all monotonically increasing:
- Memory entropy > θe=0.9 (importance distribution too uniform → decision paralysis)
- Conflict density > θc=0.3 (contradictions in memory graph → incoherent behaviour)
- Time elapsed > τ=1 hour (hard ceiling safety net)
- Manual forcing

**Relevance:** Most specific work on trigger design. The entropy and conflict triggers map to urgency metrics we can compute from the existing appraisal pipeline. The time ceiling matches our existing `interval-ticks`.

### 3. McClelland, McNaughton & O'Reilly (1995) — Why There Are Complementary Learning Systems in the Hippocampus and Neocortex. Psychological Review, 102(3), 419-457.

Foundational CLS theory. Hippocampus = fast one-shot learning; neocortex = slow gradual integration. Transfer via interleaved replay prevents catastrophic forgetting. Consolidation frequency bounded by accumulation rate of new experiences.

### 4. Kumaran, Hassabis & McClelland (2016) — What Learning Systems do Intelligent Agents Need? Complementary Learning Systems Theory Updated. Trends in Cognitive Sciences, 20(7), 512-534.

**Key finding:** Replay frequency should be proportional to the **divergence between new experience and existing structure**. The more novel the experience, the more urgent the integration. This directly inverts the decay model — novelty/divergence should accelerate consolidation, not be eroded by time.

Also notes: neocortical learning can be rapid for information consistent with known structure, but requires careful interleaving for novel information.

### 5. Kirkpatrick et al. (2017) — Overcoming Catastrophic Forgetting in Neural Networks. PNAS, 114(13), 3521-3526.

Elastic Weight Consolidation (EWC). Interleaving data from multiple tasks during learning prevents forgetting. Relevant to consolidation frequency — more frequent, smaller consolidation events are better than rare, large batch processing.

### 6. Rolnick et al. (2019) — Experience Replay for Continual Learning. NeurIPS 2019.

CLEAR method for reducing catastrophic forgetting via replay. Off-policy learning + behavioural cloning from replay for stability, on-policy learning for plasticity. Confirms the general principle that replay frequency matters.

### 7. TMR Research — Targeted Memory Reactivation (various, 2022-2025)

Critical consolidation processes complete within <2 seconds of reactivation onset. Extended reactivation windows confer no additional benefit. Implication: consolidation events should be frequent and brief, not rare and exhaustive.

## Conclusion

**Decay is wrong for consolidation triggers.** Three independent lines (Park/Generative Agents, SCM, CLS theory) converge on monotonic accumulation. The right mechanism:

1. **Event count** (monotonic, no decay) — Park et al.'s baseline
2. **Divergence accumulation** (monotonic, weighted by novelty) — CLS 2016's key insight
3. **Time ceiling** (existing interval-ticks) — SCM's safety net

CUSUM with decay remains valid for CAPS situation classification (detecting shifts in behavioural patterns). Different mechanism for different job.

## Filed As

neocortex#661 — Replace decay-based consolidation trigger with monotonic divergence accumulation
