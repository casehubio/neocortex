# Decisions — #407 Psychology Cause-Effect Models

## D1: Network encoding architecture

**Choice:** Two-layer CAPS architecture — CAPS spreading activation network
for all psychological cause-effect chains with Rescorla-Wagner prediction
error rule for weight learning. No separate Bayesian network layer.
**Alternatives:**
- Pure decision trees — simpler but can't handle composition naturally;
  "many combinations all impacting each other" requires network structure
- Pure Bayesian network — good for individual chains and incremental
  updates but can't model cyclic interactions or behavioral attractors
  (DAG constraint, conditional independence assumption)
- Three-layer hybrid (Bayesian + CAPS + Bayesian updating) — original D1
  choice; rejected because Rescorla-Wagner provides equivalent principled
  weight updating without requiring a separate Bayesian layer, reducing
  architectural complexity. The Rescorla-Wagner prediction error rule is
  mathematically equivalent to Bayesian updating for the single-cue case.
**Rationale:** CAPS handles both the cause-effect encoding (spreading
activation) and the attractor formation (parallel constraint satisfaction).
Rescorla-Wagner + Pearce-Hall (§5.1) provides principled weight learning
natively within the CAPS framework — no separate Bayesian sub-networks
needed. This collapses three architectural layers into one unified system
with two storage layers (CAPS computation graph + MindMap behavioral
attractor output).
**Trade-offs:** Single-framework approach is simpler but loses conditional
independence reasoning that Bayesian networks provide. Mitigated by CAPS's
natural handling of dependencies through shared activation nodes.
**Sources:** Mischel & Shoda (1995) — CAPS theory. Shoda, LeeTiernan &
Mischel (2002) — CAPS as parallel constraint satisfaction network.
Rescorla & Wagner (1972) — prediction error learning. casehubio/neocortex#406
epic. casehubio/neocortex#408 — graph engine design.
**Exploration:** deep-analysis
**Status:** captured

## D2: Model selection — 6 models for full topology

**Choice:** Six psychological models composed into one unified network: Attachment theory (Bowlby/Ainsworth), BIS/BAS (Gray's RST), CBT (Beck's cognitive model), Trauma response models (fight/flight/freeze/fawn), Operant conditioning, and Social learning theory (Bandura).
**Alternatives:**
- Original 5 without Bandura — misses observational learning, which is a fundamentally different input mechanism (learning from watching others vs direct experience)
- Fewer models (3-4) — simpler topology but gaps in coverage
**Rationale:** These six cover: early relationship patterns (attachment), approach/avoidance motivation (BIS/BAS), belief formation (CBT), threat responses (trauma), learning from consequences (operant), and learning from observation (Bandura). CAPS itself becomes the architecture, not a content model. Models share mediating nodes (attachment working models = CBT core beliefs; Gray's FFFS = trauma fight/flight/freeze), enabling natural composition.
**Trade-offs:** Six models produce a larger topology with more connections to weight. Risk of redundancy between overlapping models (mitigated by shared-node composition).
**Sources:** Issue #407 scope. Mischel's CAPS unit types. ACE categories for situation vocabulary.
**Exploration:** quick
**Status:** captured

## D3: Deliverable structure — three parts

**Choice:** Three-part deliverable: (1) Meta-model — 7-step framework for translating any psychological model into CAPS network structure, (2) Full composed topology — all 6 models unified with named nodes, connections, initial weights, disposition modulation map, (3) Calibration roadmap — provenance classification and validation approaches for each weight.
**Alternatives:**
- Concrete topology only — directly actionable but can't extend to new models without re-deriving the approach
- Meta-model only — flexible but requires another step before implementation
**Rationale:** User specified "comprehensive in coverage." The meta-model ensures future models can be added; the full topology provides immediate input to #401 (schema) and #408 (engine). Calibration roadmap establishes the path from "best guess" weights to empirically validated ones.
**Trade-offs:** Larger deliverable. Meta-model requires abstracting from the worked example, which adds effort.
**Sources:** User direction ("both — meta-model with one full topology" + "add the 6th model")
**Exploration:** quick
**Status:** captured

## D4: Selection criterion — tree encodability

**Choice:** Primary selection criterion for which models make the cut is tree encodability — models with the clearest cause-effect chains that map directly to weighted graph structures.
**Alternatives:**
- Empirical grounding — prioritize published quantitative data
- Composability — prioritize models that combine well
**Rationale:** The downstream consumer (#408) needs mechanically encodable cause-effect chains. Models that are too fuzzy or require too much LLM interpretation defeat the purpose of a mechanical layer.
**Trade-offs:** May exclude psychologically important but hard-to-formalize models (e.g., psychodynamic theory).
**Sources:** User selection. Issue #407 key question 1.
**Exploration:** quick
**Status:** captured

## D5: Weight rigor — plausible defaults with calibration path

**Choice:** Use published effect sizes where available, fill gaps with psychologically plausible defaults. Document provenance (empirical/consensus/estimated) for every weight. Establish calibration path for estimated weights.
**Alternatives:**
- Strict empirical only — sparser deliverable, more gaps
- Practitioner-informed — richer but less formally validated
**Rationale:** Goal is end-to-end first, then calibrate. User: "best guesses is fine, but for each of these areas, once it's all end to end, we need to come back and figure out approaches and research to get them based on real data."
**Trade-offs:** Initial weights may be inaccurate. Calibration roadmap mitigates this.
**Sources:** User direction.
**Exploration:** quick
**Status:** captured

## D6: Execution scope — full research in-session

**Choice:** This session (and follow-ups as needed) produces everything: the research spec, conducts the actual literature review, derives the network topology with initial weights, and produces the calibration roadmap. Not just a spec for future research.
**Alternatives:**
- Spec only — defines what to research, separate session does the work
- Spec + skeleton — topology structure without empirical weights
**Rationale:** User wants end-to-end. "All of 1 and 2" — spec the approach AND conduct the research AND produce the topology.
**Trade-offs:** Large scope for a single session. May need continuation sessions for full weight derivation.
**Sources:** User direction.
**Exploration:** quick
**Status:** captured
