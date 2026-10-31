# Tiered Memory Architecture — Research & Design Direction

**Date:** 2026-10-31
**Issue:** casehubio/neocortex#737
**Status:** Research complete, empirical validation next

## Context

Issue #737 originally scoped as "three-tier context memory for context reset." Through design exploration, the scope expanded: the tiered memory architecture is a core neocortex capability, with context reset as one consumer among many (character reasoning, relationship tracking, planning, and potentially LLM research assistants).

## Architecture — Condensation Chains

Memory is not a separate summary tree bolted onto the MindMap. The MindMap *is* the tiered memory. Consolidation (sleep) already promotes, merges, and organizes nodes. The missing piece is content condensation — producing progressively compressed versions while keeping the chain linked.

### The condensation chain

Each consolidation cycle condenses active memories. The pre-condensation version is kept as a linked tier, not replaced in-place:

```
Active:     "people in the library, something about the inscription"
  → tier 1: "Clara, Muttley, DD in library. Clara examined inscription, nervous. DD dismissive."
    → tier 2: "Clara entered tick 42, found inscription. Said 'this looks ancient'. Muttley followed tick 44..."
      → transcript: raw text, ticks 42-78
```

Each node is standalone — carries enough context to be understood without walking ancestors. But parent links exist for drill-down when more detail is needed.

### Entity references at every tier

Every node in the chain carries: who (speaker/participants), where (room/location), when (tick range), what entities were mentioned (people, objects, landmarks). The summary text is for context injection; the entity references are the query index. Without entity refs on every node, you can't answer "what happened with Clara in the library" without scanning everything.

A persistent inverted entity index maps entity → all memory nodes referencing it, across all tiers.

### Immutable transcript backing

Full text (observations rendered for the LLM, LLM responses, dialogue) is persisted as the leaf layer. Never deleted. A `TranscriptRef` scheme anchors memory nodes to tick ranges in the immutable log. Every memory, at every tier, traces back to the exact words.

### Decay and eviction

Salience-driven forgetting modulates retrieval priority, not deletion:
- Emotionally significant memories keep deeper tiers warm longer
- Routine events evict to disk quickly
- The active MindMap stays bounded regardless of simulation length
- Evicted tiers survive on disk, reconstructable on demand

Eviction tiers:
- **Hot:** Active memory node — in MindMap, instant
- **Warm:** Recent tiers — in-memory cache, cheap lookup
- **Cold:** Evicted tiers — disk read, reconstruct from chain
- **Deep cold:** Full transcript — disk read + sub-agent synthesis

### Reconstructive recall

Recall is active reconstruction, not passive read. Walking the condensation chain deeper is analogous to "thinking harder" — each tier provides more detail. Cross-chain associations trigger during expansion ("Clara... the inscription... wait, Marsh was watching too"). Current cognitive state (beliefs, emotions) filters the reconstruction — this is not a bug, it's how memory works.

## Two reset scenarios

### 1. Sleep reset (narrative boundary) — already exists
- Periodic, aligned with the character's day cycle
- Consolidation runs during sleep, builds/extends the condensation chain
- Morning injection: condensed briefing of yesterday/this week/before
- Attention-weighted by temporal distance: today=detailed, yesterday=highlights, last week=condensed

### 2. Context reset (mechanical, mid-scene) — new
- Triggered when LLM context window fills up
- Can happen mid-conversation, mid-scene
- Injection must be scene-aware: who's here, what was just said, what I was doing
- Zero tolerance for gaps — character can't miss that DD was mid-sentence
- No natural break point — must resume seamlessly

Both are real. Sleep reset has time and a clean boundary. Context reset has neither — needs a perfect scene snapshot built from memories recorded in real-time.

## Codebase Audit — What Already Exists

### Experience recording (partial)
- `ExperienceRecorderCore` stores one action per tick
- Fields: agentId, tenantId, description (flat text), importance (0-1), metadata (room, thinking, target_agent)
- **Gap:** Description is a crude one-liner ("Hello there MOVE kitchen"). No entity extraction from text. Room is metadata string, not first-class. Full observation text consumed by LLM then discarded.

### Per-prompt tiered rendering (exists, not persisted)
- `TieredObservationRenderer` in agency — already runs on every character prompt
- VERBATIM (few events) → GROUPED (more events) → SUMMARISED (many events, LLM)
- `ObservationService` partitions by room/visibility
- `ManorObservationRenderer` runs compaction (dedup moves, supersede interactions)
- **Gap:** Output computed fresh each drain and discarded. Not stored as memory.

### Narrator pipeline (exists, not persisted)
- `NarratorAgent` runs async on virtual thread, ticking every 1 second
- `MechanicalCompactor` deduplicates, `NarratorSummariser` calls LLM
- Output goes to WebSocket/Qhorus for broadcast
- **Gap:** Summaries broadcast and vanish. Never stored as memory.

### Sub-thought extraction (exists, entity-aware)
- `SubThoughtAttributeKeys` — indexed attributes on Memory: type, text, entity, graduated, confidence
- `SubThoughtTypes` — 7 types: affect-observation, causal-inference, evaluative, intention, self-reflection, association, concern
- `SubThoughtConsolidationPhase` — graduates by (entity, type) pairs at threshold 3
- Creates `SubThoughtRef` anchoring back to source memories
- Detects concern escalation, contradictions, affect polarity shifts
- **This already works** and is the template pattern for condensation.

### Consolidation pipeline (10 phases)
- `ExperienceConsolidationPhase` — graduates memories → MindMap nodes, keeps `source-memory-id`
- `SubThoughtConsolidationPhase` — graduates sub-thoughts, entity-aware
- `MergeDetectionPhase` — merges duplicates (Jaro-Winkler + neighbor overlap)
- `CommunitySummaryPhase` — finds dense clusters via k-core, creates `Summary` nodes with `summarizes` edges. But summary is just a title, not LLM-condensed prose.
- `GoalPrioritizationPhase` — already does decay (dormant/abandoned by confidence + affect)
- Source memories kept after graduation — nothing deleted
- **Gap:** No content condensation phase. No `condensed-from` edge type. CommunitySummaryPhase has the right structure but doesn't produce real summaries.

### Summarisation engine (agency)
- `LevelEvent<E>` — record(E payload, long timestamp, EventLevel level, @Nullable String tenancyId). No speaker, room, or entity fields.
- `EventAccumulator`, `KeyedAccumulator` — accumulation and keying. KeyedAccumulator groups by arbitrary key extractor.
- Neocortex has a full copy under `io.casehub.neocortex.summarisation`
- **Gap:** LevelEvent is entity-blind. KeyedAccumulator could key by speaker/room if LevelEvent had those fields.

### Deliberation projections (agency, Qhorus SPI)
- `ConversationFold` — already entity-aware: sender, createdAt, topic
- `JudgmentFold`, `NegotiationFold` — track who said what
- These are already participant-aware — a different abstraction family from the summarisation accumulators

## Gap Summary

| Gap | Severity | Notes |
|-----|----------|-------|
| Experience recording too thin | High | One-line descriptions, no entity extraction |
| Per-prompt rendering not persisted | High | Already computing tiered summaries, throwing them away |
| Narrator summaries not stored | Medium | Already doing async LLM summarisation, discarding output |
| No transcript persistence | High | Full observation text consumed and discarded |
| No content condensation phase | High | CommunitySummaryPhase is the skeleton, needs LLM |
| No condensation chain (condensed-from edges) | High | No tiered versioning, no chain links |
| No persistent entity index | Medium | Sub-thought graduation builds per-run accumulation only |
| LevelEvent entity-blind | Medium | No speaker/room/entity fields on summarisation events |
| No context reset injection | High | New capability, builds from stored memories |

## Literature Grounding

### Primary reference
**TiMem** (Li et al., Jan 2026, ACL Findings, arXiv:2601.02845) — Temporal Memory Tree with 5 levels: raw observations → session summaries → episode summaries → cross-session patterns → persona representations. 52% reduction in recalled memory length, 75.3% accuracy on LongMemEval. Structurally closest to our condensation chain.

### Validating models
- **Spens & Burgess 2024** (Nature Human Behaviour) — Memory is generative reconstruction via hippocampal replay training generative models. Validates reconstructive recall.
- **ACT-R base-level activation** (Anderson et al.) — Decay: `B_i = ln(Σ t_j^(-d))`, d ≈ 0.5. Power law of forgetting. Directly implementable.
- **MemGPT/Letta** (Packer et al., 2023) — Three OS-inspired tiers: core/recall/archival. Maps to our eviction model.
- **CLS Theory** (McClelland, McNaughton & O'Reilly, 1995) — Complementary learning systems. Hippocampal fast-learning → neocortical slow-integration. Maps to our experience recording → consolidation pipeline.
- **PNAS 2022 hippocampal-neocortical model** — NREM = condensation, REM = association-building. Maps to consolidation phases.
- **Sleep-time Compute** (Lin et al., 2025) — Consolidation as async idle-time process. Validates async Haiku summarisation.
- **Active Systems Framework** (Diekelmann & Born 2010) — Episodic memories reactivated during sleep, transformed to become hippocampus-independent. Maps to condensation chain making memories less transcript-dependent.

### What we add beyond published work
- Entity-referenced indexing at every tier (TiMem doesn't have this)
- Lossless condensation chain with immutable transcript backing (TiMem doesn't keep full text)
- Reconstructive recall via sub-agents filtered by cognitive state
- Two reset modes: narrative (sleep) and mechanical (context window)
- Integration with existing MindMap consolidation (graduation, merge, community detection)

## Commercial Landscape (October 2026)

| Product | Architecture | What it does well | What it lacks |
|---------|-------------|-------------------|---------------|
| **Mem0** (~65k stars) | Flat fact extraction | Simple, fast, widely adopted | No hierarchy, no temporal reasoning |
| **Zep/Graphiti** | Temporal knowledge graph | Entity dedup, contradiction handling, bi-temporal | Not a memory system — no condensation, decay, reconstruction |
| **Letta/MemGPT** | OS-inspired 3-tier | Agent self-manages memory | Tiers are separate stores, not linked condensations |
| **MemPalace** (59k stars) | Verbatim ChromaDB | Never loses data | No condensation, no entity indexing, no decay |
| **OpenAI Dreaming** | Opaque async curation | Most automated | Black box |
| **Anthropic Memory** | Filesystem-based | Persistent, managed agents | Siloed, no tiering |

**No shipping system combines:** entity-indexed condensation chains, immutable transcript backing, reconstructive recall, cognitive-state-filtered retrieval, and salience-driven decay.

## Existing Research in Project

- `memory-retrieval-models-research.md` — Retrieval models (Park et al., somatic markers, affective scoring). Covers retrieval, not storage/tiering.
- `consolidation-trigger-literature-2026-10-22.md` — When to consolidate (CLS, SCM, EWC). Covers triggers, not condensation output.

## Next Steps

1. **Empirical test** — run a scenario with existing infrastructure, trigger context reset, observe what the character gets. Find real gaps vs assumed ones.
2. **Gap roadmap** — informed by test results, prioritize the gaps from the table above.
3. **Design spec** — module-by-module design for the gaps that matter, grounded in test findings and literature.

## Design Principles

- The MindMap IS the tiered memory — not a separate store
- Consolidation produces condensation chains, not in-place replacements
- Entity references are the query index, not an afterthought
- Full text is never lost — anchored, evictable, reconstructable
- Decay is retrieval priority, not deletion
- Mechanical computation over LLM inference where the model can be correctly specified
- Literature-first for model choices (ACT-R decay, CLS consolidation, reconstructive recall)
