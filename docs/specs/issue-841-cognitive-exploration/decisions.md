# Decisions — #826 Recurring Thought Continuum

## D1: State persistence model

**Choice:** Pure derivation — zero new state
**Alternatives:**
- Accumulate on MindMap nodes — add recurrence-count/last-reactivation properties. More precise but blurs the "derived, not stored" principle.
- Hybrid derive + cache — derive from existing data, cache classification as transient property. Re-derived each cycle but still writes state.
**Rationale:** The issue spec explicitly says "Not a new store — derived from existing data." Existing stores already contain all the signals needed: experience memory frequency, affect trajectory, CAPS attractor strength, attention signals, confidence changes. Keeping the classifier stateless means it's a pure function of current system state — easier to test, no migration, no stale data.
**Trade-offs:** Possibly less precise for fast-changing states where inter-cycle granularity matters. Cannot detect patterns that span fewer observations than a consolidation cycle.
**Sources:** #826 issue body ("Not a new store — derived from existing data"), HabituationState architecture (in-memory only, per-session)
**Exploration:** quick
**Status:** captured

## D2: Classification output granularity

**Choice:** Discrete enum — ContinuumPosition: HEALTHY, FIXATION, OBSESSION, RUMINATION
**Alternatives:**
- Continuous score + zone — [0,1] intensity with zone thresholds. More nuanced but thresholds become arbitrary.
- Per-symptom profile — seven individual scores, no aggregation. Maximum fidelity but complex rendering logic.
**Rationale:** The four positions map to distinct behavioral regimes with different prompt rendering needs. A discrete enum gives clean switch logic in CuriosityPromptSection and SearchBudgetPromptSection. The underlying composite score can still be computed internally — the enum is the public API.
**Trade-offs:** Boundary effects at thresholds. An agent at 0.59 vs 0.61 gets different rendering. Mitigated by the fact that prompt rendering already uses qualitative language, not numeric precision.
**Sources:** #826 issue body (continuum table), SearchBudgetPromptSection convergence < 0.05 special path
**Exploration:** quick
**Status:** captured

## D3: Execution location

**Choice:** ConsolidationPhase — new phase at @Priority(20), between BehavioralSynthesisPhase@19 and MergeDetectionPhase@24
**Alternatives:**
- CognitionTickParticipant — DERIVED-phase, runs every tick. More responsive but heavier, and most inputs only change at consolidation time.
- Both — consolidation does full classification, tick reads cached result. Two components for one concept.
**Rationale:** The classifier reads experience memory frequency, affect trajectory, CAPS attractor strength — all of which are computed or updated during consolidation. Running after BehavioralSynthesisPhase@19 ensures CAPS attractors are fresh. Running before MergeDetection@24 allows merge to consider continuum state. Consolidation is the natural home for cross-tick pattern analysis.
**Trade-offs:** Classification only updates at consolidation frequency (idle guard ≥1 min). Between cycles, the agent's continuum position is stale. Acceptable because the phenomena being classified (recurring thought patterns) are inherently slow-changing.
**Sources:** ConsolidationScheduler architecture, BehavioralSynthesisPhase @Priority(19), MergeDetectionPhase @Priority(24)
**Exploration:** quick
**Status:** captured

## D4: Scope — include pathologicalThreshold wire-up

**Choice:** Include — wire SearchBudgetTickParticipant.computeConvergence() to use pathologicalThreshold from SearchBudgetConfig
**Alternatives:**
- Separate concern — keep #826 focused on continuum classifier + rendering. Cleaner scope but leaves the convergence=0 mechanism disconnected.
**Rationale:** The pathologicalThreshold is the bridge between habituation failure and convergence collapse. Without it, the continuum classifier can detect fixation patterns but SearchBudget convergence won't reflect them. The wire-up is small (few lines) and completes the circuit that makes the continuum observable in the agent's exploration behavior.
**Trade-offs:** Slightly larger scope. But the alternative is a classifier that detects states the system can't act on.
**Sources:** SearchBudgetTickParticipant.computeConvergence() (threshold declared but not used), SearchBudgetConfig.pathologicalThreshold (1.4 default)
**Exploration:** quick
**Status:** captured

## D5: Prompt rendering strategy

**Choice:** Modulate existing CuriosityPromptSection and SearchBudgetPromptSection
**Alternatives:**
- New standalone RecurringThoughtPromptSection — clearer separation but risks prompt bloat with three sections describing overlapping cognitive states.
- Conditional override — RecurringThoughtPromptSection replaces curiosity section at FIXATION+. Clean handoff but loses four-channel detail at the boundary.
**Rationale:** CuriosityPromptSection already renders four-channel curiosity with proportional deviation. SearchBudgetPromptSection already has a special path for convergence < 0.05. The continuum position changes tone and framing, not structure. Adding the continuum as a modulation parameter to these existing sections avoids a third section competing for the same conceptual space and keeps the prompt coherent.
**Trade-offs:** Existing sections become more complex (they now need to accept a ContinuumPosition parameter). The continuum's influence is distributed across two sections rather than concentrated in one.
**Sources:** CuriosityPromptSection (four-channel rendering, BlockTag.CURIOSITY(215)), SearchBudgetPromptSection (convergence < 0.05 fixation text)
**Exploration:** quick
**Status:** captured

## D6: Classifier architecture

**Choice:** Composite score from existing signals — RecurringThoughtScorer as weighted composite of seven symptoms, each derived from existing data sources
**Alternatives:**
- CAPS-first classification — feed topic characteristics into CAPS engine, use settling result as classifier. Reuses CAPS directly but requires synthesizing virtual experiences per topic and adds compute.
- Longitudinal delta detection — track deltas (confidence improving/stagnating, PAD stabilizing/oscillating). Captures temporal dynamics but needs inter-cycle history and delays classification for new topics.
**Rationale:** Maps each of the seven symptoms from the issue spec to a concrete, existing data source. Pure function of current state. Thresholds tunable per-agent via CognitiveDefaults. Incorporates trajectory insight (from AffectTrajectoryAnalyzer) as one symptom score without making the entire architecture trajectory-dependent. Straightforward to implement and test.
**Trade-offs:** Symptom-to-data-source mapping requires calibration. Some symptoms (attentional capture, gap closure resistance) may not have clean proxies — will need thoughtful approximation.
**Sources:** #826 issue body (seven symptoms table), AffectTrajectoryAnalyzer, BehavioralSynthesisPhase EMA, InformationGapDetector
**Exploration:** deep-analysis
**Status:** captured

## D7: Symptom-to-data-source mapping

**Choice:** Seven symptoms mapped to existing data sources:
- Thought recurrence → experience memory frequency (CaseMemoryStore, entity mention count in time window)
- Habituation failure → HabituationState novelty (in-session) + experience frequency decay (cross-session)
- Gap closure resistance → MindMap node confidence trajectory (confidence not increasing despite new information)
- Attentional capture → AttentionSignal concentration (proportion of recent signals from this topic vs others)
- Arousal trajectory → AffectTrajectoryAnalyzer PAD arousal slope
- Valence → MindMap node PAD pleasure dimension
- Action tendency → CAPS BehavioralAttractor category (approach/avoidance/withdrawal)
**Alternatives:**
- None formally proposed — the mapping follows directly from D1 (pure derivation) and the available data sources.
**Rationale:** Each symptom from the issue spec maps to exactly one existing data source. No new stores or state needed. The composite scorer weights these seven inputs to produce the ContinuumPosition.
**Trade-offs:** Attentional capture via signal proportion is approximate — signals are sparse and consolidation-frequency. Habituation failure cross-session proxy (experience frequency not decaying) is weaker than direct HabituationState persistence would be.
**Depends on:** D1 (pure derivation), D6 (composite scorer architecture)
**Sources:** #826 issue body (seven symptoms table), CaseMemoryStore.scan(), AffectTrajectoryAnalyzer, AttentionSignal/SignalCategory, BehavioralSynthesisPhase
**Exploration:** quick
**Status:** captured
