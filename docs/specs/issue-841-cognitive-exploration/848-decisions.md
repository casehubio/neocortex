## D1: How CuriosityDrive gets active goal node IDs

**Choice:** Pass a `Supplier<Set<String>>` to CuriosityDrive — a lazy supplier of active goal node IDs, injected at construction. CuriosityDrive calls `supplier.get()` in `evaluate()` and passes the result to `detectGaps()`.
**Alternatives:**
- Add MindMapStore to CuriosityDrive's constructor — simpler but couples the drive directly to MindMapStore, which it currently doesn't depend on
**Rationale:** CuriosityDrive lives in the `cognition` module and currently only depends on `mindmap-intelligence` types. A supplier keeps it decoupled from store dependencies while letting the wiring layer (CognitionCore / agency) reuse the existing goal-lookup pattern from GoalRelevanceModulationFactor.
**Trade-offs:** Slightly more indirection at construction time; the supplier's lifecycle (caching, refresh) must be managed by the caller.
**Sources:** CuriosityDrive.java:18-24 (current constructor), GoalRelevanceModulationFactor.java:50-65 (goal lookup pattern), CognitionCore.java:838-851 (curiosity wiring)
**Exploration:** quick
**Status:** captured

## D2: Goal proximity model — BFS inside InformationGapDetector

**Choice:** BFS inside InformationGapDetector with graduated `goalRelevance` field on InformationGapScore. Distance 1 → 1.0, distance 2 → 0.7, distance 3 → 0.4, beyond → 0.0. Replaces the existing 1-hop GOAL_ADJACENT classification and flat 1.5x boost with a richer model. goalRelevance > 0.0 implies goal adjacency.
**Alternatives:**
- Keep 1-hop classification in InformationGapDetector, add BFS post-processing in CuriosityDrive — simpler detector but duplicates BFS logic and means goalRelevance is populated outside the detector, unavailable to other consumers
**Rationale:** The detector already does 1-hop checking and applies the boost; multi-hop BFS is a natural evolution. Putting goalRelevance on InformationGapScore makes it available to all consumers (CuriosityChannelComputer, CuriosityPromptSection, CuriositySignalGenerator) without each needing its own BFS pass.
**Trade-offs:** InformationGapDetector becomes slightly heavier — BFS per node with goals. Mitigated by early termination (max depth 3) and the existing per-node edge traversal.
**Depends on:** D1 (CuriosityDrive must supply activeGoalNodeIds to detectGaps for this to activate)
**Sources:** InformationGapDetector.java:72-99 (existing scoreNode with 1-hop check), GoalRelevanceModulationFactor.java:76-101 (BFS pattern to reuse), InformationGapScore.java:17-46 (record to extend)
**Exploration:** quick
**Status:** captured

## D3: Goal relevance boosts gapIntensity inside InformationGapDetector

**Choice:** Replace the existing flat `gapIntensity *= 1.5` with `gapIntensity *= (1 + goalRelevance)` — distance 1 gets 2.0x, distance 2 gets 1.7x, distance 3 gets 1.4x, all capped at 1.0. The sorted output from detectGaps() naturally puts goal-relevant gaps higher, so top-5 truncation in CuriosityDrive and CuriosityChannelComputer works correctly without changes.
**Alternatives:**
- Separate goal-weighted scoring in CuriosityDrive — cleaner conceptual separation but the sorted output from detectGaps() wouldn't reflect goal bias, causing top-5 truncation to miss goal-relevant gaps that rank lower by raw intensity
**Rationale:** The existing pattern already boosts gapIntensity for GOAL_ADJACENT inside the detector. Graduated BFS refines the same mechanism. All downstream consumers benefit from the correct ranking without needing their own goal-weighting logic.
**Trade-offs:** gapIntensity now encodes both information-theoretic gap and goal relevance — consumers can't separate the two. Mitigated by goalRelevance being available as a separate field for those that need it.
**Depends on:** D2 (goalRelevance must be computed by BFS for the graduated multiplier to work)
**Sources:** InformationGapDetector.java:91-93 (existing 1.5x boost), CuriosityDrive.java:36-40 (top-5 averaging)
**Exploration:** quick
**Status:** captured

## D4: Remove GOAL_ADJACENT from GapType — goalRelevance replaces it

**Choice:** Remove GOAL_ADJACENT from GapType enum. classifyGap() always returns the information-theoretic type (PARTIAL_KNOWLEDGE, TIP_OF_TONGUE, CONNECTED_UNKNOWN). Goal relevance is expressed solely via the continuous `goalRelevance` field on InformationGapScore. A node can now be TIP_OF_TONGUE *and* goal-relevant — both dimensions visible.
**Alternatives:**
- Keep GOAL_ADJACENT as an additional enum value — backward compatible but consumers lose information (a GOAL_ADJACENT gap near the tip-of-tongue threshold can't express that dual nature)
**Rationale:** The current classifyGap() returns GOAL_ADJACENT and skips the information-theoretic classification. This masks useful information. With goalRelevance as a continuous field, the binary enum value is strictly less informative and causes information loss.
**Trade-offs:** Breaking change to GapType enum — any code switching on GOAL_ADJACENT must be updated. Mitigated by the enum being internal to neocortex with limited consumers (CuriosityDrive trigger string, CuriosityPromptSection rendering).
**Depends on:** D2 (goalRelevance field must exist on InformationGapScore)
**Sources:** InformationGapDetector.java:110-130 (classifyGap early-returns GOAL_ADJACENT), InformationGapScore.java:27-36 (GapType enum)
**Exploration:** quick
**Status:** captured
