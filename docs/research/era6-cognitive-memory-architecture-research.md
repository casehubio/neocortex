# Era 6: Cognitive Memory Architecture — Identity Across Context Boundaries

**Status:** RESEARCH COMPLETE — empirical validation next

Era 5 proved characters express personality convincingly within a context window. Era 6 proves they maintain coherent identity across context boundaries — context resets, sleep cycles, and long-running simulations where the LLM context fills and must be cleared.

---

## What Era 5 Established

### The ceiling is architecture, not tuning

Barrett v5 pipeline validated: all drives Q≥4, 8 cognitive dimensions scoring 4-5 on Opus CLI. But 300-tick eval reveals systematic degradation:
- Echo loss: body vocabulary 75%→51% as context grows
- P22 analytical spike: 45%→75% when new characters arrive
- Root cause: Barrett instruction buried under growing context

More instruction tuning won't fix this. The context window has a finite size and the character's experience is unbounded. The fix is context management — periodic clearing with memory-based reconstruction.

## Era 6: The Problem

A character's LLM context fills up. It must be cleared. The character must continue as if nothing happened — same personality, same awareness of who's in the room, same emotional state, same ongoing plans.

This requires a memory system that can reconstruct the character's situational awareness from structured memory alone, without the LLM conversation history it just lost.

## The Architecture: Condensation Chains

The MindMap IS the tiered memory. Consolidation (sleep) already promotes, merges, and organizes nodes. The extension: each consolidation cycle condenses active memories. The pre-condensation version is kept as a linked tier, not replaced in-place. The result is a chain from high-level gist to full transcript.

Entity references at every tier. Immutable transcript backing. Salience-driven decay with eviction to disk, reconstructable on demand. Reconstructive recall via sub-agents, filtered through current cognitive state.

Full architecture documented in `tiered-memory-architecture-research-2026-10-31.md`.

## Two Context Boundary Types

1. **Sleep reset** (narrative, periodic) — consolidation runs during sleep, builds condensation chain, character wakes with a morning briefing. Already partially exists.
2. **Context reset** (mechanical, mid-scene) — context window full, must clear and resume seamlessly. Zero tolerance for coherence gaps. New capability.

## What Already Exists (Codebase Audit)

| Component | Status | Gap |
|-----------|--------|-----|
| Per-prompt tiered rendering | Working | Not persisted |
| Narrator async summarisation | Working | Not stored |
| Sub-thought extraction | Working, entity-aware | Already good |
| 10 consolidation phases | Working | No content condensation |
| CommunitySummaryPhase | Working | Titles only, needs LLM content |
| MergeDetectionPhase | Working | Already good |
| Experience recording | Working | Too thin — one-line descriptions |
| Transcript persistence | Missing | Full observation text discarded |
| Condensation chain edges | Missing | No `condensed-from` links |
| Persistent entity index | Missing | Per-run accumulation only |
| Context reset injection | Missing | New capability |

## Literature Grounding

| Model | Validates |
|-------|-----------|
| TiMem (Li et al., 2026) | 5-level temporal memory tree works for conversational agents |
| Spens & Burgess 2024 | Memory is generative reconstruction, not replay |
| ACT-R decay (Anderson) | Power-law forgetting, implementable as decay function |
| CLS Theory (McClelland 1995) | Fast episodic → slow semantic integration |
| MemGPT/Letta (Packer 2023) | OS-inspired hot/warm/cold tiers |
| Sleep-time Compute (Lin 2025) | Async idle-time consolidation viable |

## ERA6 Phases

### Phase 1: Empirical test
Run a scenario, snapshot memory state, assess what a character would know after context clear. Find real gaps vs assumed ones.

### Phase 2: Persist what's already computed
Store per-prompt tiered rendering and narrator summaries as memory nodes. Enrich experience recording with entity references.

### Phase 3: Condensation chain
`condensed-from` edge type. Extend CommunitySummaryPhase with LLM condensation. Build context reset injection from stored memories.

### Phase 4: Full architecture
Persistent entity index. ACT-R-based decay. Reconstructive recall via sub-agents. Both reset modes integrated.

## Key Issues

| Issue | Repo | Title |
|:---:|------|-------|
| #737 | neocortex | Three-tier context memory architecture |
| #183 | examples | Context reset with async summariser |
