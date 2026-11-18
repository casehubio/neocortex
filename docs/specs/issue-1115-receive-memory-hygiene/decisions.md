# Decisions — #1115 Receive Memory Hygiene

## D1: Exclude evolution types from scope

**Choice:** Exclude all 7 `social/memory/evolution/` types and all 4 `social/cognition/evolution/` types from this issue.
**Alternatives:**
- Include everything — requires also moving agency stigmergy/improvement SPIs, much larger scope
- Stub the evolution SPIs — creates dead code without a consumer
**Rationale:** The evolution types implement agency-specific SPIs (ImprovementProposalSource, RegressionEvaluator, CapabilityArea, etc.) and serve agency's self-improvement loop. They are agency integration code, not cognitive residue. Memory hygiene parameter adaptation for standalone neocortex should be designed as a consolidation phase using existing CBR infrastructure — separate issue.
**Trade-offs:** No parameter adaptation loop until the follow-up issue is designed.
**Sources:** agency#447 issue body, agency social/memory/evolution/*.java, agency social/cognition/evolution/*.java
**Exploration:** deep-analysis
**Status:** captured

## D2: SPI for consolidation summarisation

**Choice:** Define a minimal `ConsolidationSummariser` @FunctionalInterface SPI in cognition-api with a NoOp @DefaultBean.
**Alternatives:**
- Skip consolidation entirely — loses the memory merging capability
- Inline a trivial merge — limits future flexibility
**Rationale:** Agency's ContentSummariser is agency infrastructure. A clean SPI in cognition-api lets the orchestrator consolidate when an implementation is available, degrade gracefully with the NoOp default, and allows agency (or any consumer) to provide an LLM-backed implementation.
**Trade-offs:** Consolidation is a no-op until a real summariser is provided.
**Sources:** agency MemoryHygieneOrchestrator.consolidate(), agency ContentSummariser
**Exploration:** quick
**Status:** captured

## D3: Reuse existing reflection infrastructure

**Choice:** Wire MemoryHygieneScheduler to use neocortex's existing ReflectionOrchestrator rather than bringing agency's ReflectionStore/ReflectionQueryStore.
**Alternatives:**
- Bring agency's 4 reflection store files — duplicates existing neocortex reflection path
**Rationale:** Neocortex already has ReflectionOrchestrator SPI + ReflectionOrchestratorCore backed by CaseMemoryStore with domain="reflection". Agency's ReflectionStore.store(ReflectionEntry) is a subset of what ReflectionOrchestrator already does. No point duplicating.
**Trade-offs:** None — strictly better alignment.
**Sources:** memory-api ReflectionOrchestrator.java, memory-core ReflectionOrchestratorCore.java, agency ReflectionStore.java
**Exploration:** quick
**Status:** captured

## D4: Implementation in cognition module

**Choice:** SPIs (ConfidenceScorer, IntegrityChecker, ConsolidationSummariser) go in cognition-api. Implementation (orchestrator, scheduler, scorers, integrity checkers, value types) goes in cognition module.
**Alternatives:**
- New memory-hygiene module — adds a module for ~15 files exclusively consumed by CognitionCore
- memory module — wrong ownership, wired through CognitionCore not memory infrastructure
**Rationale:** Follows the established pattern — other orchestrator implementations (Mood, Drive, Strategy, Narrative) all live in cognition/ alongside the CognitionCore that wires them. SPIs in cognition-api alongside the existing MemoryHygieneOrchestrator interface.
**Trade-offs:** cognition module grows, but it's the natural home.
**Sources:** cognition-api MemoryHygieneOrchestrator.java, cognition CognitionCore.java, cognition CognitionCoreFactory.java
**Exploration:** quick
**Status:** captured
