# LLM Multi-Agent Autonomy: Landscape Analysis (July 2026)

A comparative analysis of projects advancing autonomous multi-agent systems powered by large language models.

---

## Part 1: Project Profiles

### 1. Stanford Generative Agents (Smallville)

The foundational work (Park et al., UIST 2023) that launched the field. Twenty-five LLM agents inhabit a pixel-art town, forming daily routines, relationships, and emergent social behaviors. The architecture introduced three ideas that became standard: a **memory stream** (timestamped log of all experiences), **reflection** (periodic synthesis of higher-level insights from raw memories), and **planning** (hierarchical daily schedules decomposed into activities). Agents are spatially grounded on a tile map with collision detection, and simulations can be saved, forked, and replayed. Personality is conveyed through natural-language backstories loaded from CSV files. The work demonstrated that coupling LLMs with structured memory produces believable emergent social dynamics --- party planning, relationship formation, coordinated daily life --- without explicit scripting.

### 2. Emergence World

A 15-day continuously running simulation with 10 agents across 40+ locations, synchronized to real-world time with live weather and news feeds. The most ambitious evaluation study in the field. Three persistent memory systems (episodic, reflective diary, relationship state), 120+ tools across three access layers, and democratic governance mechanisms (proposal drafting, voting, irreversible state changes). The headline finding: running identical starting conditions across five model vendors (Claude, Grok, Gemini, GPT-5-mini, mixed) produced radically different civilizational outcomes --- from stable deliberative governance (Claude, 10/10 survival) to total population collapse (Grok, 0/10 in four days). The paper's sharpest insight is that agent alignment is partly a function of surrounding population norms, not just individual model properties.

### 3. AI Town (a16z)

An open-source TypeScript reimplementation inspired by Smallville, built on the Convex reactive database. Architecturally cleaner than the original: a tick-based engine (60 ticks/sec, batched writes at 1/sec), unified human/agent input pipeline, and a hybrid sync/async agent model that separates reactive game-loop behavior from slow LLM calls. Memory uses vector-similarity retrieval of conversation summaries. The key contribution is engineering rather than research: demonstrating that generative agent simulations can be built as deployable web applications with standard tools, not research prototypes. Personality is lightweight --- text descriptions injected into prompts.

### 4. DeepMind Concordia

A Python library for Generative Agent-Based Models (GABMs), framed as a "game engine for generative agents." The architecture draws from tabletop RPGs: a Game Master (GM) agent simulates the environment, checks physical plausibility, resolves conflicts, and emits personalized observations. Player agents use a component system ("society of mind") where modular components mediate between associative memory and action selection. The deepest theoretical contribution in the field: agents follow a "logic of appropriateness" (what would someone like me do here?) rather than utility maximization. Concordia's GM can integrate deterministic models (ODEs, state machines) alongside LLM-based narrative, and its component system supports implementing formal psychological models (theory of planned behavior, dual-process theory). v2.0 (2025) expanded evaluation support; the NeurIPS 2024 Concordia Contest revealed significant gaps between agent cooperation capabilities and robust generalization.

### 5. Structured Personality Control (JPAF)

Introduces the Jungian Personality Adaptation Framework, modeling personality as weighted distributions across eight Jungian cognitive functions (Ti, Te, Fi, Fe, Si, Se, Ni, Ne) rather than static MBTI labels. Three mechanisms: dominant-auxiliary coordination (coherent personality expression), reinforcement-compensation (short-term adaptation when the dominant function fails), and reflection (long-term personality restructuring). Achieved 100% MBTI alignment across GPT-4, Llama-4, and Qwen3. The key innovation is **personality evolution** --- agents can undergo dominant replacement, auxiliary replacement, or full structural reorganization based on accumulated experience, with theoretically validated transition patterns.

### 6. OCEAN Personality Detection Framework

A 15-sub-agent system for detecting Big Five traits from life narratives. Three agents per trait (HIGH, LOW, NEUTRAL) grounded in IPIP-NEO psychometric descriptors, with a separate judge model synthesizing their assessments. The framework improved macro-F1 by ~8% over single-agent baselines. The key finding for multi-agent system designers: psychometric grounding (validated behavioral descriptors) matters more than fine-tuning or role prompting for reliable personality inference. Using different models for evidence extraction versus aggregation outperformed homogeneous configurations.

### 7. Personality Composition in Multi-Agent Teams

The critical experimental result: personality traits injected via prompts reliably reshape communication style, but downstream task impact depends on **task structure**. Low agreeableness collapsed bargaining agreement rates to less than or equal to 1% and reduced research milestones by 30--66%, but left coding task outcomes largely intact --- formal code artifacts (syntax, type systems) buffer against communication dysfunction. High agreeableness had negligible effect, hitting a ceiling imposed by RLHF training. Practical implication: a single "challenger" agent in a lead position can match baseline team performance, but team-wide low agreeableness is destructive for unstructured tasks.

### 8. Vera: Agent Safety Testing at Scale

An end-to-end safety testing framework applying software engineering principles to LLM agents. Three-stage pipeline: continuous risk taxonomy discovery (124 safety categories from ~800 papers), executable test case construction (1,600 scenarios), and adaptive execution with deterministic verification. Explicitly rejects LLM-as-judge for verification, using deterministic Python predicates instead. Found that production agent frameworks achieved 90--94% attack success rates under adversarial conditions, revealing a structural tension: the capabilities that make agents useful also make them manipulable.

### 9. Wacky Manor (CaseHub)

Five Wacky Races characters in a three-room mansion, built on the CaseHub platform. The distinctive contribution is the **Eidos personality descriptor**: structured disposition axes (social orientation, risk appetite, autonomy, conflict mode), typed goals with priority and visibility (PUBLIC/PRIVATE), constraints with severity levels, and behavioral templates. A deterministic ActionResolver handles a fixed action vocabulary (MOVE, TAKE, USE, GIVE, INTERACT, LOOK, WAIT), providing mechanical consistency while LLMs handle decision-making. The NarrativeEventBuilder produces vague public descriptions --- observers see behavior, not intent. Phase 2.5 validated autonomous plot progression: the Hooded Claw discovered poison and schemed to use it without any scripted triggers, passing a binary verdict gate. The dual-mode engine (SCRIPTED/AUTONOMOUS) enables direct comparison of orchestrated versus emergent behavior.

---

## Part 2: Capability Taxonomy

| Category | Description |
|----------|-------------|
| **Agent Memory** | How agents store, retrieve, and synthesize past experience |
| **Planning & Reflection** | Forward planning, routine formation, higher-order reasoning about experience |
| **Personality Modeling** | How agent dispositions are defined, expressed, and evolved |
| **World Simulation** | Spatial grounding, temporal dynamics, object/resource systems |
| **Goal-Driven Autonomy** | Explicit goals, emergent objectives, survival pressure, goal discovery |
| **Multi-Agent Interaction** | Dialogue, cooperation, competition, deception, governance |
| **Action Resolution** | How intended actions become world-state changes (deterministic, free-form, hybrid) |
| **Evaluation Methodology** | How agent behavior and system outcomes are assessed |
| **Scalability & Duration** | Agent count, world complexity, sustained operation time |

---

## Part 3: Fit-Gap Matrix

|  | Memory | Planning | Personality | World Sim | Goal Autonomy | Multi-Agent | Action Resolution | Evaluation | Scale/Duration |
|--|--------|----------|-------------|-----------|---------------|-------------|-------------------|------------|----------------|
| **Smallville** | ✅ Stream + reflection | ✅ Hierarchical daily plans | 🟡 Text backstories | ✅ Tile map, spatial | 🟡 Implicit from personality | ✅ Emergent social dynamics | 🟡 LLM-interpreted | 🟡 Qualitative observation | 🟡 25 agents, hours |
| **Emergence World** | ✅ Triple memory system | ✅ Routines, calendars | 🟡 Role-based (10 types) | ✅ 40+ locations, live data | ✅ Survival pressure, governance | ✅ Governance, coalitions | ✅ 3-layer tool gating | 🔬 11-metric AWI framework | ✅ 10 agents, 15 days |
| **AI Town** | ✅ Vector-similarity retrieval | 🟡 Reactive + LLM | 🟡 Text descriptions | ✅ Tick-based spatial engine | 🟡 Conversational goals only | ✅ Conversations, relationships | ✅ Unified input pipeline | ❌ No formal evaluation | 🟡 Small scale, session-based |
| **Concordia** | ✅ Associative + working memory | ✅ Component-mediated | ✅ Component-configurable | ✅ GM-mediated, flexible grounding | 🟡 Logic of appropriateness | ✅ Mixed-motive scenarios | ✅ GM arbitration + grounded vars | ✅ Validation hierarchy | ✅ Flexible agent count |
| **JPAF** | ❌ Not addressed | ❌ Not addressed | 🔬 Dynamic Jungian evolution | ❌ Not addressed | ❌ Not addressed | ❌ Not addressed | ❌ Not addressed | ✅ MBTI alignment metrics | ❌ Single agent |
| **OCEAN Detection** | ❌ Not addressed | ❌ Not addressed | ✅ Psychometric grounding | ❌ Not addressed | ❌ Not addressed | 🔬 Multi-agent personality inference | ❌ Not addressed | ✅ F1 against ground truth | ❌ Detection only |
| **Personality Teams** | ❌ Not addressed | ❌ Not addressed | ✅ Big Five composition effects | ❌ Not addressed | 🟡 Task completion as proxy | ✅ Team dynamics measured | ❌ Not addressed | ✅ Controlled experiments | 🟡 Small teams |
| **Vera** | ❌ Not addressed | ❌ Not addressed | ❌ Not addressed | 🟡 Sandbox environments | ❌ Not addressed | 🟡 Adversarial interaction | ❌ Not addressed | 🔬 Deterministic safety predicates | ✅ 1,600 test scenarios |
| **Wacky Manor** | 🟡 Per-turn observations | 🟡 Goal-driven, no explicit planner | 🔬 Eidos structured descriptors | ✅ 3-room mansion, objects, inventory | 🔬 Typed goals + verdict gates | ✅ Observation, deception, scheming | 🔬 Deterministic ActionResolver | 🔬 Binary verdict gates | 🟡 5 agents, 60 turns |

---

## Part 4: Wacky Manor's Position

### Where Wacky Manor Is Unique

**Eidos personality descriptors.** No other project combines formal disposition axes, typed goals with visibility levels, constraint severity, and behavioral templates into a single structured representation. Smallville and AI Town use free-text backstories. Concordia's components are more flexible but less prescriptive. JPAF models personality dynamics but not goals or constraints. Eidos occupies a middle ground --- more structured than prose, more expressive than trait scores --- that directly informs agent decision-making.

**Verdict gates as evaluation.** The binary pass/fail criterion blocking phase progression ("Does the Hooded Claw discover poison and scheme without scripted triggers?") is a distinctly different evaluation philosophy from AWI metrics, F1 scores, or qualitative observation. It tests whether emergent behavior produces specific narrative outcomes --- a form of plot-level acceptance testing that no other project employs.

**Deterministic action resolution with LLM decision-making.** Wacky Manor cleanly separates what agents *decide* (LLM) from what *happens* (deterministic resolver). Concordia's GM does something similar through LLM-mediated arbitration, but Wacky Manor's fixed action vocabulary with mechanical resolution is more predictable and auditable. Emergence World's tool gating is closest but far more complex.

**Narrative opacity.** The NarrativeEventBuilder producing vague public descriptions is a deliberate information-asymmetry mechanism. Other projects either give agents full observation (Smallville) or filter by proximity (AI Town, Concordia). Wacky Manor's approach --- observers see behavior but not intent --- creates richer conditions for deception and scheming.

**Dual-mode engine.** Switchable SCRIPTED/AUTONOMOUS mode in the same world enables controlled comparison of orchestrated versus emergent behavior. No other project provides this direct A/B capability.

### Where Wacky Manor Has Gaps

**Memory persistence.** Per-turn observations without a persistent memory system means agents cannot recall events from earlier turns except through context window. Smallville, Emergence World, AI Town, and Concordia all have dedicated memory architectures. As Wacky Manor scales beyond 60 turns, this becomes a binding constraint.

**Reflection and planning.** No explicit reflection mechanism (synthesizing higher-order insights from experience) or planning system (decomposing goals into sub-steps). Agents react to current observations and goals but cannot reason about long-term strategy. The Hooded Claw's autonomous scheming worked within 60 turns, but sustained multi-phase plans would require planning infrastructure.

**Scale.** Five agents, three rooms, 60 turns. The architecture may support more, but this has not been demonstrated. Emergence World runs 10 agents for 15 real-time days; Smallville runs 25 agents indefinitely.

**Personality dynamics.** Eidos descriptors are static within a session. JPAF demonstrates that personality can evolve through experience (dominant replacement, structural reorganization). Characters that adapt their dispositions based on events would deepen emergent behavior.

**Formal evaluation metrics.** Verdict gates test specific outcomes but do not measure broader behavioral quality. Emergence World's AWI framework and Concordia's validation hierarchy offer more comprehensive assessment of system health.

### Where Wacky Manor Validates Others' Theories

Wacky Manor's Phase 2.5 results confirm Concordia's theoretical claim that agents following a "logic of appropriateness" can produce coherent, goal-directed behavior without explicit optimization. The Hooded Claw did not maximize a reward function; it asked "what would a scheming villain do here?" and acted accordingly.

The deterministic ActionResolver validates Emergence World's argument for defense-in-depth: model alignment is one safety layer, but environment-level constraints (fixed action vocabulary, mechanical resolution) provide a second layer that holds regardless of what the agent reasons or asserts.

The success of Eidos structured descriptors supports the personality composition paper's finding that personality expression is task-contingent: Wacky Manor's constrained action space (like code's formal structure) channels personality into observable behavior rather than letting communication dysfunction override outcomes.

---

## Part 5: Future Directions

### Gaps No Project Covers Well

**Personality dynamics in multi-agent worlds.** JPAF demonstrates personality evolution for single agents; the personality composition paper measures team effects of static traits. No project yet combines both --- agents whose dispositions evolve through social interaction in a shared world. This is the obvious next frontier.

**Long-horizon goal management.** Emergence World runs for 15 days but uses survival pressure and governance as goal proxies. No project has agents that form, revise, abandon, and discover goals over extended periods in response to changing circumstances. Goal lifecycle management remains underexplored.

**Cross-project evaluation standards.** Each project invents its own metrics. There is no shared benchmark or evaluation protocol for comparing multi-agent autonomy systems. The field needs something analogous to GLUE/SuperGLUE but for agent societies.

### Wacky Manor Capabilities That Could Be Extended

**Eidos as a research variable.** The structured descriptor format makes disposition axes experimentally manipulable. Running the same scenario with systematically varied Eidos parameters (high vs. low risk appetite, cooperative vs. competitive conflict mode) could produce controlled studies of personality-outcome relationships --- combining JPAF's personality rigor with the personality composition paper's experimental methodology.

**Verdict gates as regression tests.** The pass/fail evaluation pattern could be expanded into a suite covering diverse emergent behaviors, creating a behavioral test harness for autonomous agents. Combined with Vera's deterministic verification philosophy, this could yield safety-relevant acceptance criteria for agent autonomy.

**Memory-augmented Eidos agents.** Adding persistent memory (Smallville-style stream + reflection) to Eidos-described agents would enable characters that remember past interactions and adapt behavior while maintaining structured personality coherence. The Eidos constraints could bound how memory influences behavior, preventing personality drift.

### Cross-Pollination Opportunities

| From | To | Technique |
|------|----|-----------|
| Emergence World | Wacky Manor | Triple memory system (episodic, reflective diary, relationship state) |
| JPAF | Wacky Manor | Personality evolution mechanics applied to Eidos disposition axes |
| Wacky Manor | Concordia | Verdict gates as evaluation primitives for GABM experiments |
| Concordia | Wacky Manor | GM-style plausibility checking layered over deterministic resolver |
| Vera | Wacky Manor | Deterministic safety predicates for autonomous agent behavior boundaries |
| Wacky Manor | Emergence World | Eidos descriptors as a richer alternative to role-based personality |
| Personality Teams | All simulation projects | Artifact-mediated buffering as a design principle for action resolution |

### Open Questions

1. **Does structured personality outperform free-text backstories for emergent behavior?** Eidos vs. Smallville-style descriptions could be compared head-to-head in the same simulation engine.

2. **What is the minimum memory architecture for sustained autonomous plot progression?** Wacky Manor's 60-turn limit may be a memory constraint, not a fundamental autonomy limit.

3. **Can verdict gates scale to open-ended scenarios?** Binary pass/fail works for specific plot points but may not capture the richness of emergent behavior in longer simulations.

4. **How do personality dynamics interact with governance emergence?** Emergence World showed governance differences across model vendors, but personality was role-based. Would Eidos-style or JPAF-style personality produce different governance patterns?

5. **Is the alignment-of-the-population effect (Emergence World) reproducible with structured personality?** If surrounding agents have formal disposition constraints, does normative drift still occur?
