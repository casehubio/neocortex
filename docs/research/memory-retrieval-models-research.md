# Appraisal-Driven Memory Retrieval — Research Findings

**Date:** 2026-10-07
**Purpose:** Deterministic mechanism for selective memory retrieval during emotional appraisal

## Key Finding

No single validated model provides fully deterministic emotion-driven retrieval. But a **hybrid multi-signal approach** combining tag filtering + PAD affective proximity + fixed importance tiers is deterministic and implementable.

## Implementable Mechanism

### Step 1: Tag-Based Filtering (Deterministic)
Each formation memory tagged with:
- `situation_type`: rejection, threat, achievement, loss, kindness, authority-challenge
- `person_present`: who was involved
- `schema`: which EMS this memory relates to (abandonment, mistrust, entitlement, etc.)
- PAD values (P, A, D)

Filter: `situation_type` matches current appraisal + `person_present` matches current interaction.

### Step 2: Affective Proximity Scoring (Deterministic)
```
pad_distance = |P_memory - P_current| + |A_memory - A_current| + |D_memory - D_current|
affective_score = 1 / (1 + pad_distance)
```

### Step 3: Multi-Signal Composite Score (Deterministic)
```
score = 0.2·recency + 0.3·importance + 0.2·tag_overlap + 0.3·affective_score

recency = 0.995^(hours_since_activation)
importance = fixed tier (formation=10, schema-activation=8, episode=5, observation=2)
tag_overlap = jaccard(tags_current, tags_memory)
```

### Step 4: Activation Threshold (Deterministic)
From Affective Episodic Memory System (PMC8550857):
```
activation(t) = importance · affective_score · recency · exp(-t²/γ)
γ based on arousal (high arousal → slower decay)
Retrieve if activation > threshold
```

## Source Models

### Most Implementable
1. **Affective Episodic Memory System** (PMC8550857, 2021) — CA3 pattern completion with emotion-based grouping. Fully specified equations.
2. **Generative Agents** (Park et al. 2023) — recency × importance × relevance. Reference design for LLM agents.
3. **REMT** (Frontiers AI, 2026) — Mood-modulated graph retrieval with affective proximity formula.
4. **CA3 Pattern Completion** (Rolls 2013) — Hebbian attractor network. Deterministic for fixed weights.

### Theoretical Foundations
5. **Bower's Mood-Congruent Memory** (1981) — spreading activation concept
6. **Encoding Specificity** (Tulving 1973) — context matching principle
7. **Somatic Markers** (Damasio) — body state as retrieval cue
8. **Scherer's CPM** — SEC dimensions for situation tagging
9. **McGaugh (2004)** — arousal enhances consolidation → higher baseline activation

## Determinism Guarantee

Same situation → same memory IF:
- Tags are rule-based (not LLM-generated)
- PAD values are fixed
- Importance tiers are fixed (not LLM-scored)
- No random sampling

## What We Need to Add to Formation Memories

Currently have: age, episode, P, A, D.
Need to add: `situation_type`, `schema`, `person_present` tags for deterministic filtering.

## References

Bower (1981) Mood and memory, American Psychologist;
Collins & Loftus (1975) Spreading activation theory of semantic processing;
Conway & Pleydell-Pearce (2000) Self-Memory System;
Damasio (1994) Somatic marker hypothesis;
McGaugh (2004) Amygdala modulates memory consolidation;
Park et al. (2023) Generative Agents, arXiv:2304.03442;
PMC8550857 (2021) Affective Episodic Memory System for Virtual Creatures;
Rolls (2013) CA3 pattern completion mechanisms;
Scherer (2001) Component Process Model;
Tulving & Thomson (1973) Encoding specificity principle.
