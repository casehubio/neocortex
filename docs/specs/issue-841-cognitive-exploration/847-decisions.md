# Decisions — #847 Curiosity → Search Budget Coupling

## D1: Modulation magnitudes are configurable per-agent

**Choice:** Add channel modulation multipliers to SearchBudgetConfig, derived per-agent by CDE
**Alternatives:**
- Fixed offsets — simpler, fewer config fields, but all agents respond identically to channel data
- Hybrid (single scaling factor) — one knob instead of per-rule, but can't differentiate rules
**Rationale:** Personality should shape HOW MUCH curiosity channels influence search — a highly curious agent's channels should dominate, a methodical one's barely register
**Trade-offs:** More config fields, more CDE derivation logic
**Sources:** SearchBudgetTickParticipant existing drive bonuses pattern, CognitiveDerivationEngine pathways
**Exploration:** quick
**Status:** captured

## D2: Channel state cached in CuriosityDrive

**Choice:** CuriosityDrive computes and caches CuriosityChannel via lastChannel() accessor alongside existing lastGaps()
**Alternatives:**
- CuriosityChannelComputer gains per-agent ConcurrentHashMap caching — duplicates caching, requires passing deprivationBias
- New CuriosityState holder — cleaner separation but more indirection and types
**Rationale:** CuriosityDrive already caches lastGaps() (volatile), runs in DriveOrchestrator before DERIVED phase. Single source of last-computed curiosity state
**Trade-offs:** CuriosityDrive gains a dependency on CuriosityChannelComputer (it's in mindmap-intelligence, CuriosityDrive is in cognition — cognition already depends on mindmap-intelligence)
**Sources:** CuriosityDrive.lastGaps() pattern, CognitionCore.configureCuriosity()
**Exploration:** quick
**Status:** captured

## D3: Description phase renders default tendencies, not live state

**Choice:** SearchBudgetDescriptionGenerationPhase derives likely channel patterns from SearchBudgetConfig + CognitiveDefaults personality
**Alternatives:**
- Live channel at consolidation — captures what agent is curious about NOW, but stale between runs
- Both layers — default prose (consolidation) + live channel description (tick-time) — most complete but most complex
**Rationale:** Consolidation-time prose describes personality disposition, not transient state. Consistent with how the phase works today (static SearchBudgetConfig only)
**Trade-offs:** Prompt won't describe current channel state — but SearchBudgetPromptSection handles tick-time rendering
**Sources:** SearchBudgetDescriptionGenerationPhase.buildUserPrompt(), CuriosityDescriptionGenerationPhase pattern
**Exploration:** quick
**Status:** captured

## D4: Nested ChannelModulationConfig record

**Choice:** Dedicated ChannelModulationConfig record with 8 magnitude fields, nullable on SearchBudgetConfig
**Alternatives:**
- Flat on SearchBudgetConfig — same fields added directly, inflates to 19 fields
- Per-parameter weights — 4 fields (one per budget param), can't differentiate focus vs comprehension effects
**Rationale:** Clean separation — channel modulation is conceptually distinct from base sigmoid tuning. Adding/removing rules doesn't touch base config. Nullable gives graceful degradation
**Trade-offs:** Extra type, slightly more wiring
**Sources:** SearchBudgetConfig (11 existing fields), SchererAppraisalConfig nested pattern
**Exploration:** quick
**Status:** captured

## D5: Continuous gradient for comprehension and gap proximity modulation

**Choice:** Comprehension and gap proximity scale modulation magnitudes linearly — laterality += (1 − comprehension) × magnitude, convergence += comprehension × magnitude, depth += gapProximity × magnitude
**Alternatives:**
- Threshold-gated (comprehension < 0.3 or > 0.7) — simpler mental model but arbitrary thresholds and discontinuous behavior
**Rationale:** Smooth, no arbitrary thresholds, both ends of each spectrum contribute proportionally. Focus type (SPECIFIC/DIVERSIVE) and hedonic type (I_TYPE/D_TYPE) remain discrete since they're already enums
**Trade-offs:** At mid-range comprehension (0.5), both laterality and convergence get partial modulation — but this is arguably correct (partial understanding warrants both lateral exploration and some synthesis)
**Depends on:** D4 (nested ChannelModulationConfig provides the magnitude values)
**Sources:** CuriosityChannel.comprehension() [0,1], CuriosityChannel.gapProximity() [0,1]
**Exploration:** quick
**Status:** captured
