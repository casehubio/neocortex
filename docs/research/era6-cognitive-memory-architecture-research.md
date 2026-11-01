# Era 6: Cognitive Memory Architecture — Identity Across Context Boundaries

**Status:** Phase 2 COMPLETE, Phase 3 COMPLETE — empirical validation next

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

| Component | Status | Gap | Empirical |
|-----------|--------|-----|-----------|
| Per-prompt tiered rendering | Working, PERSISTED | — | CLOSED — #191 |
| Narrator async summarisation | Working, STORED | — | CLOSED — #192 |
| Sub-thought extraction | Working, entity-aware | Already good | CONFIRMED |
| 10 consolidation phases | Working | No content condensation | CONFIRMED |
| CommunitySummaryPhase | Working | Titles only, needs LLM content | CONFIRMED |
| MergeDetectionPhase | Working | Already good | CONFIRMED |
| Experience recording | Working, enriched | — | FIXED (#738) + CLOSED (#739) |
| Experience recall in scripted mode | Working | — | FIXED (#189) |
| Transcript persistence | IMPLEMENTED | TranscriptStore → ZipCorpusStore | Phase 3 |
| Temporal compaction | IMPLEMENTED | ObservationCompactionPhase (priority 50) | Phase 3 |
| Condensation chain edges | Missing | No `condensed-from` links | Phase 4 |
| Persistent entity index | Missing | Per-run accumulation only | Phase 4 |
| Context reset injection | IMPLEMENTED | ObservationSegmentCache → ObservationBuilder | Phase 3 |
| Node duplication | Bug | Cache/seed double-run | OPEN (#190) |

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

### Phase 1: Empirical test — COMPLETE (2026-10-31)

Ran 30-tick autonomous scenario with GENERIC profile, snapshotted full memory system state.

**Key findings:**
- Memory store is `InMemoryMemoryStore` (not NoOp) — transitive dependency provides it
- 93 MindMap nodes seeded per character (drives, facets, attachment, needs, schemas, formation memories, CAPS, social overlays)
- 31 new nodes earned at runtime in 30 ticks (drive adaptation, dialogue extraction, personality inference)
- Formation memories DO graduate via ExperienceConsolidationPhase
- All research doc gaps confirmed empirically

**Bugs found and fixed:**
- **#738 FIXED:** Domain mismatch — `ExperienceRecorderCore` stored in domain `"experience"`, `AgentExperienceService.recall()` queried domain `"manor"`. Memories accumulated but were invisible to recall. Fix: `MANOR_DOMAIN = ExperienceEvents.DOMAIN`.
- **#189 FIXED:** Scripted mode passed `null` for `experienceService` to `CharacterAgentLoop`. Fix: wire the real service through `runScripted()`.
- **#190 OPEN:** Node duplication from cache/seed interaction — cosmetic, lower priority.

**Detailed results:** `examples/wacky-manor/docs/eval/era6-memory-snapshot/FINDINGS.md`

### Phase 2: Persist what's already computed — COMPLETE (2026-10-31/11-01)

Empirically validated with 30-tick GENERIC scenario run. All persistence features confirmed working.

- examples#191 CLOSED — Tiered rendering persisted as ACTIVITY nodes in per-character `observations-<id>` subgraphs
- examples#192 CLOSED — Narrator summaries stored in shared `narrator-summaries` subgraph (9 nodes in 30-tick run)
- neocortex#739 CLOSED — Action enriched with first-class `room` and `targetAgent` fields (room 85%, target-agent 44% of memories)
- examples#193 CLOSED — Observer auto-registration fix for GENERIC profile. Root cause: `ObservationService.init()` pre-registered observers with original IDs; `remapCharacterIds()` changed all IDs; events silently dropped. Fix: `PartitionedObservationService.publishEvent()` auto-registers via `computeIfAbsent`

### Phase 3: Three-layer memory architecture — COMPLETE (2026-11-01)

Built the three-layer memory system for context-reset continuity. Each layer serves a different purpose at a different cost:

| Layer | Store | Content | Implementation |
|-------|-------|---------|----------------|
| **Leaf** | Corpus ZIP chain | Raw prompt+response per character per tick | `TranscriptStore` → `ZipCorpusStore` |
| **Derived** | MindMap ACTIVITY nodes | Tiered observations + condensed summaries | `ObservationCompactionPhase` |
| **Hot read** | Segment cache | Materialised view for injection | `ObservationSegmentCache` |

**Step 1 — Transcript persistence (leaf layer):** `TranscriptStore` wraps `ZipCorpusStore` from the neocortex corpus module. Appends JSON documents (`{tick, agentId, timestamp, prompt, response}`) per character per tick on virtual threads to stay off the tick critical path. Configurable via `manor.transcript.enabled` / `manor.transcript.storage-path`. Default storage: `~/claude/casehub/transcripts/<profile>/`.

**Step 2 — Temporal compaction (derived layer):** `ObservationCompactionPhase` implements `ConsolidationPhase`, runs at priority 50 during consolidation sleep. For each character's `observations-<id>` subgraph: keeps a recent window (default 10 nodes) untouched, groups older nodes in batches of 5, concatenates content with room tracking, erases originals. Condensed nodes carry a `Condensed` trait for idempotency. Mechanical concatenation — no LLM calls. After compaction, 300 ticks produces ~10 recent + ~16 condensed nodes instead of ~240 originals per character.

**Step 3 — Segment cache (hot-read layer):** `ObservationSegmentCache` reads MindMap observation nodes (condensed nodes for "Earlier" history, recent nodes for "Recently" section), produces a capped text summary per character. Token budget: `clearAtTokens / 40` (~2000 tokens at the 80k threshold). Refreshed per tick before observation assembly.

**Step 4 — Context-reset injection:** Segment cache text injected into `ObservationBuilder` as a "What You've Experienced" section, positioned between goals and recent activity. Provides narrative continuity across session context resets triggered at 80k tokens.

**Design note:** Phase 3 uses erase-and-replace for temporal compaction (older nodes erased, condensed node created) rather than the `condensed-from` edge chain originally envisioned. This is simpler and sufficient for the current use case where observation nodes have zero functional consumers. The full condensation chain with linked tiers and drill-down is Phase 4 work.

### Three-Way Tension — Hypothesis

Context window size optimisation involves three competing concerns:

1. **Instruction adherence** wants short context — the Barrett instruction is loudest when there are 2,000 tokens after it, not 80,000. Body vocabulary drops from 75% to 51% as context grows.
2. **Narrative continuity** wants long context — accumulated conversation history is how the character knows who Clara is and what Marsh was doing.
3. **Prompt caching** wants a stable prefix — LLM providers cache the beginning of prompts. Growing conversation history degrades cache hit rate every tick.

The standard approach (let context grow, then clear) loses on all three. The three-layer architecture resolves this: stable system prompt + slowly-changing segment cache form a cacheable prefix; variable per-tick observation is small and sits at the end; instructions stay near the generation point; continuity lives in the memory architecture, not the conversation history.

**Hypothesis:** The optimal clear threshold is where instruction effectiveness drops sharply (a "knee" in the degradation curve), NOT at the maximum context size.

**Planned experiment:** Run the same scenario at 40k / 60k / 80k / 120k thresholds. Measure body vocabulary percentage, prompt cache hit rate, and output quality at each. Expect a non-linear curve with a sharp knee.

### Phase 4: Full architecture
Condensation chain with `condensed-from` edge type and linked tiers (drill-down from gist to transcript). Persistent entity index. ACT-R-based decay. Reconstructive recall via sub-agents. Both reset modes (sleep + context) integrated.

## Key Issues

| Issue | Repo | Title | Status |
|:---:|------|-------|--------|
| #737 | neocortex | Three-tier context memory architecture | Phase 3 complete, empirical validation next |
| #738 | neocortex | Domain mismatch in experience recall | CLOSED |
| #739 | neocortex | Enrich experience recording with entity refs | CLOSED |
| #183 | examples | Context reset with async summariser | Phase 3 — segment cache wired, full async summariser deferred |
| #189 | examples | Scripted mode null experienceService | CLOSED |
| #190 | examples | Node duplication from cache/seed | Open (cosmetic, deferred) |
| #191 | examples | Persist tiered rendering output | CLOSED |
| #192 | examples | Store narrator summaries | CLOSED |
| #193 | examples | Observer auto-registration for GENERIC profile | CLOSED |
