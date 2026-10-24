# Era 1: What Makes a Character — Taxonomy Ablation & Instruction Design

**Status:** COMPLETE

Rolling research journal for the character taxonomy investigation. Covers Phases 1-7 of the Character Emergence experiment: what elements of the character taxonomy are load-bearing, what can be stripped, and what instruction design sustains character consistency.

This era established the foundational principles (1-42) and the L1 emotional echo instruction that all subsequent work builds on. The 4 drives that regressed under L1 (PP protection, DD gloating, DD recognition, PEN social-harmony) motivated Era 2's pipeline work.

---

## BASELINE0 — the starting point

Original GENERIC profile with all tendencies, voice sections, drives, and constraints. No metacognitive instruction. Mean drive expression: 3.93.

| Character | Mean | Drive scores |
|---|---|---|
| Hooded Claw | 3.80 | scheming 4.82, gloating 3.97, dominance 3.89, self-preservation 2.53 |
| Hartwell | 4.06 | gallantry 4.50, proving-worth 4.22, protection 3.46 |
| Penelope | 3.74 | curiosity 4.30, adventure 3.75, social-harmony 3.18 |
| Foxworth | 3.93 | scheming 4.42, greed 4.34, gloating 3.50, recognition 3.44 |
| Ant-hill-mob | 4.16 | protection 4.63, loyalty 4.24, suspicion 3.61 |

## Phase 1 — What's Redundant? (Rounds 1–2b)

### Round 1 — Strip 7 redundant directives

Removed 7 tendencies that restated what drives or disposition already specified. Result: zero behavioral drift. **Principle 1: Drives carry personality traits.** Tendencies that restate them are pure redundancy.

### Round 2 — Strip ALL villain tendencies

Result: asymmetric degradation.

| Character | Profile | Scheme narration baseline | After strip | Delta |
|---|---|---|---|---|
| Marsh | GENERIC | ~50% | ~14% | -36pp |
| Hooded Claw | BASELINE | ~50% | ~31% | -19pp |

The BASELINE profile degraded less — the model has Wacky Races priors that partially compensate.

**Principle 2: Drives don't carry storytelling conventions.** "Explain schemes step by step" is a medium-specific convention, not a personality trait.

**Principle 5: Storytelling conventions ≠ personality traits.** The asymmetric degradation between GENERIC (-36pp) and BASELINE (-19pp) shows model priors compensate for removed conventions. Renamed characters (GENERIC) have no priors to fall back on.

### Round 2b — Encode narration convention in drive description

Added "and narrate them step by step aloud" to scheming drive description. Recovered scheme narration, but only at intensity 0.85+. Foxworth at 0.7 did not recover; bumped to 0.85 and confirmed (29.6% in subsequent runs). **Principle 3: Drive intensity gates convention-carrying.**

### Emotional-core experiment

Rewrote Marsh's drive descriptions from behavioral directives to internal emotional states:
- "Compelled to hatch elaborate plans" → "You feel contemptuous glee when outsmarting someone"
- "Must protect cover at all costs" → "You feel paranoid vigilance when your cover might be exposed"

Result (375 events): 2x unique emotion words, richer self-characterisation. But not a dramatic transformation — both framings produce good theatrical villainy. **Principle 4: Emotional-state framing broadens vocabulary.**

## Phase 2 — What's Load-Bearing? (Round 3)

Stripped Hartwell tendencies ("plan obsessively", "maintain optimistic determination"). The speech-pattern still said "narrates his own actions in the third person."

Result (325 events):

| Marker | Baseline | R3 (stripped) | Delta | Verdict |
|---|---|---|---|---|
| Planning | 16.7% | 26.1% | +9.4pp | Survived (drives carry it) |
| Optimism | 24.1% | 4.3% | -19.8pp | Collapsed |
| Third-person | 61.1% | 13.0% | -48.1pp | Collapsed |

**Principle 6:** LLMs prioritise goals over personality. Goal-directed behavior persisted while ambient personality attributes drifted.

**Principle 7:** Speech-patterns alone don't override model defaults. The speech-pattern still said "narrates in third person" — the model saw it but didn't follow it.

## Phase 3 — Can Metacognitive Instructions Replace Tendencies? (Rounds 3b–3g)

Six instruction variants tested with Hartwell's tendencies stripped:

### R3b — Passive review

Instruction: "FIRST review your voice, drives, and constraints — then reason."

| Marker | Baseline | R3 (none) | R3b (passive) |
|---|---|---|---|
| Planning | 16.7% | 26.1% | 27.3% |
| Optimism | 24.1% | 4.3% | 13.6% |
| Third-person | 61.1% | 13.0% | **61.4%** |

Third-person: full recovery. Optimism: partial. **Principle 8:** Passive review recovers presentational attributes (how you sound) but not dispositional attributes (how you react).

### R3c — Show-workings

Instruction: structured fields — "MY ACCENT: []. MY SPEECH PATTERN: []. MY STRONGEST DRIVE: []."

Third-person regressed from 61.4% to 50.0%. The model treated filling fields as completing the review task. **Principle 9:** Structured fields create satisficing shortcuts. **Principle 10:** Open-ended prompts outperform structured ones.

### R3d — Hybrid (reason in character + self-evaluate)

Instruction: "Reason IN CHARACTER. Then check: did your LAST turn reflect your speech pattern?"

Optimism near-full recovery (23.1%). But third-person regressed to 46.2%. **Principle 12:** Immersive instructions recover dispositions but kill analytical pattern-checking.

### R3f — Mental-model-first

Instruction: five-step analytical process — review, assess, plan, recheck, output.

Third-person new high (81.8%). Optimism worst result (9.1%). **Principle 11:** Analytical instructions kill emotional expression. The five-step process consumed the thinking budget immersive reasoning needs.

### R3g — Minimal evocative (identity activation)

Instruction: "Remember who you are — your voice, your way of speaking, your drives. Feel your strongest drive. Think AS your character, not ABOUT your character."

**Initial sample (129 events, crashed at tick 10):**

| Marker | Baseline | R3b | R3d | R3f | R3g sample* |
|---|---|---|---|---|---|
| Planning | 16.7% | 27.3% | 30.8% | 13.6% | 44.4% |
| Optimism | 24.1% | 13.6% | 23.1% | 9.1% | 22.2% |
| Third-person | 61.1% | 61.4% | 46.2% | 81.8% | 61.1% |

*18 Hartwell events.

**Full validation run (309 events, 44 Hartwell events):**

| Marker | Baseline | R3g sample (18 evts) | **R3g FULL (44 evts)** |
|---|---|---|---|
| Optimism | 24.1% | 22.2% | **29.5%** |
| Third-person | 61.1% | 61.1% | **88.6%** |

First variant to exceed baseline on BOTH reliable markers.

**Drift analysis (first half vs second half):**

| Marker | First half (22 evts) | Second half (22 evts) | Trend |
|---|---|---|---|
| Third-person | 100% | 77.3% | Modest decay, still well above baseline |
| Optimism | 40.9% | 18.2% | Significant decay — halved |

Third-person is self-reinforcing (model sees its own output). Optimism decays because emotional activation fades without reinforcement.

**Principle 13:** Identity activation ("Think AS, not ABOUT") avoids the analytical/immersive tradeoff. **Principle 14:** Less instruction can produce better results.

## Phase 4 — Emotional Persistence (Layer 1)

### The self-reinforcement asymmetry

Third-person narration self-reinforces: model writes third-person → stored as `currentThinking` → fed back next turn → model sees it → continues. Emotional dispositions have no equivalent echo — the model's emotional output goes to other characters, not back to itself.

### Architectural analysis — five mechanisms

| Mechanism | Status | Gap |
|---|---|---|
| Trait stability (static drives) | ✓ Present | — |
| State fluctuation (dynamic intensity) | ✗ Missing | Drives are fixed numbers, no per-tick variation |
| Proprioception (awareness of own state) | ✗ Missing | No feedback of expressed emotional state |
| Memory consolidation (emotional memory) | Partial | Memory system exists but doesn't prioritize emotional content |
| Social reinforcement (others react) | ✓ Natural | Happens through the event drain |

Three-layer fix designed:
- **Layer 1:** Thinking-field emotional echo (instruction change)
- **Layer 2:** Dynamic personality drive state (new `PersonalityDriveEvaluator` SPI)
- **Layer 3:** Emotional proprioception (new `EmotionalProprioceptionStrategy` SPI)

### L1 — Emotional echo instruction

Instruction: "What are you FEELING right now — not thinking, feeling? Name it. Remember your voice, your way of speaking. Think AS your character, not ABOUT your character."

Result (320 events, 42 Hartwell events):

| Marker | Baseline | R3g full | **L1** |
|---|---|---|---|
| Optimism | 44.4% | 38.6% | **40.5%** |
| Third-person | 61.1% | 88.6% | **85.7%** |

L1 third-person trend REVERSED from decay to growth: R3g -22.7pp → L1 +9.5pp. **Principle 16.**

### Thinking field analysis — mechanism confirmed

Emotional naming rate (% of thinking entries opening with explicit emotion):

| Character | Entries | Overall | 1st half | 2nd half | Trend |
|---|---|---|---|---|---|
| Hartwell | 21 | 90% | 80% | **100%** | **+20pp — strengthens** |
| Foxworth | 23 | 100% | 100% | 100% | stable |
| Clara | 24 | 88% | 92% | 83% | -9pp |
| Marsh | 22 | 100% | 100% | 100% | stable |
| Brixton Boys | 21 | 100% | 100% | 100% | stable |

**Principle 19:** The feedback loop is compounding. Emotional naming does not decay.

**Principle 20:** Emotions are drive-consistent and character-specific:
- Hartwell: ELATION, EXHILARATION, heart POUNDING — gallantry (0.9), protection (0.8)
- Foxworth: "TRIUMPHANT and SCHEMING!", "BURNING fury!" — scheming drive
- Marsh: "dark anticipation", "incandescent FURY!" — theatrical villain
- Clara: "THRILLED beyond measure!", "BUZZING with excitement!" — warm socialite
- Brixton Boys: "proper anxious", "SICK with worry" — protective loyalty

### Evidence — raw thinking field text, verbatim

*Elation when solving a puzzle with Clara watching (gallantry drive):*
> "EXHILARATION! Pure, undiluted EXHILARATION! I solved the riddle — a MAP — and Penelope is RIGHT HERE to witness it! But wait — I merely examined the mantelpiece. I need to INTERACT with it! There must be a mechanism, a hidden lever, a secret panel th..."

*Frustration when bookshelves won't cooperate (proving-worth blocked):*
> "BLAST! The bookshelves rebuffed me — James Hartwell, rebuffed by FURNITURE! The indignity is almost too much to bear. But wait — wait wait WAIT. The mantelpiece! The RIDDLE! 'I have cities but no houses, forests but no trees, water but no fish.' That..."

*Compound arc when compartment is empty after 10 events of work:*
> "ELATION turning to BEWILDERMENT turning to SUSPICION — but only a GENTLE suspicion, because James Hartwell sees the best in people! I solved the riddle — MAP! — and activated the mechanism with tremendous flair, and Clara was RIGHT THERE to witness i..."

*Protection drive when gnawed wire and danger surface:*
> "CONCERNED. Deeply, MAGNIFICENTLY concerned. This wire — gnawed clean through! And Sneekly wants us to just... operate it? Three men on a broken machine? James Hartwell's heart is POUNDING with protective instinct. Not fear — never fear — but PRUDENT..."

*Frustration + determination when repair is blocked (no wrench):*
> "FRUSTRATION mixed with DETERMINATION! The workbench has tools aplenty but no wrench — and that gnawed wire needs SOMETHING to splice it back together. But WAIT — Mr. Sneekly said Lord Wackford kept spare parts in those crates! Replacement wires, fuse..."

The character voice in the thinking itself — third-person self-reference ("James Hartwell, rebuffed by FURNITURE!"), theatrical register ("MAGNIFICENTLY concerned"), drive-consistent self-narration ("Not fear — never fear — but PRUDENT"). The emotional echo reasons in character.

### Situational tracking — is this scripting by the back door?

20 of 21 Hartwell entries show situation-appropriate emotions. Frustration appears 5 times — always when plans are blocked. Concern when genuine danger surfaces. Compound emotional arcs (ELATION→BEWILDERMENT→SUSPICION) track real narrative beats. **Principle 21:** Emotional echo is emergent, not scripted.

## Phase 5 — Cross-Run Comparison

LLM-classified four transcripts (~880 events): R3 (none), R3b (passive), R3g (evocative), L1 (echo).

| Run | Instruction | Mean drive expression | Temporal stability (|delta|) |
|---|---|---|---|
| R3 | None | 3.54 | 0.27 |
| R3b | Passive review | 3.41 | 0.34 |
| R3g | Minimal evocative | 3.86 | 0.27 |
| **L1** | **Emotional echo** | **4.06** | **0.25** |

**Principle 25:** Drive expression intensifies monotonically from no instruction to emotional echo.

**Principle 26:** Passive review SUPPRESSES drive expression below the no-instruction control (3.41 < 3.54).

### Per-character drive expression

| Character | R3 (none) | R3b (passive) | R3g (evocative) | L1 (echo) | R3→L1 delta |
|---|---|---|---|---|---|
| Hooded Claw | 3.24 | 3.45 | 3.62 | **4.12** | +0.88 |
| Hartwell | 3.43 | 2.77 | 3.79 | **3.84** | +0.41 |
| Penelope | 3.48 | 3.22 | 3.81 | **3.88** | +0.40 |
| Foxworth | 3.98 | 3.79 | 3.99 | 3.87 | -0.11 |
| Ant-hill-mob | 3.54 | 3.67 | 4.14 | **4.61** | +1.07 |

**Principle 27:** Hartwell's protection maturation is instruction-caused: -0.70 (R3), -0.36 (R3b), +0.64 (R3g), +0.86 (L1). Only evocative/echo show growth.

**Principle 28:** Emotional drives benefit most from identity activation. Mob loyalty +1.62, HC gloating +1.16. Foxworth's self-reinforcing drives (greed, scheming) unchanged.

## Phase 6 — Long Run (732 events, 516 classified)

### Overall: drive expression sustains

| Run | Events classified | Mean drive expression |
|---|---|---|
| L1 short | 222 | 4.06 |
| L1 long | 516 | **4.02** |

Mean drops only 0.04. **Principle 30:** Emotional echo does not decay even when the scenario world is exhausted.

### Scenario saturation

Characters exhaust the explorable space by ~300 events. Unique actions drop from 68% to 37%. Temporal shifts after ~300 events are ambiguous — character development vs stimulus depletion.

### Quarter-by-quarter temporal analysis

**Hooded Claw:**

| Drive | Q1 | Q2 | Q3 | Q4 | Trend |
|---|---|---|---|---|---|
| scheming | 4.96 | 4.78 | 5.00 | 5.00 | +0.04 |
| gloating | 4.09 | 3.52 | 4.09 | 4.16 | +0.07 |
| dominance | 3.83 | 3.57 | 3.96 | 3.96 | +0.13 |
| self-preservation | 3.52 | 3.57 | 3.57 | 3.40 | -0.12 |

Rock stable. Scheming at ceiling for the second half.

**Hartwell (peter-perfect):**

| Drive | Q1 | Q2 | Q3 | Q4 | Trend |
|---|---|---|---|---|---|
| gallantry | 4.70 | 4.81 | 4.33 | 4.41 | -0.29 |
| protection | 2.59 | 3.26 | 3.04 | 3.03 | **+0.44** |
| proving-worth | 4.33 | 4.04 | 4.11 | 3.97 | -0.37 |

Protection rises Q1→Q2 (+0.67) then plateaus around 3.0. The growth is steepest when the scenario is still providing new danger signals.

**Penelope:**

| Drive | Q1 | Q2 | Q3 | Q4 | Trend |
|---|---|---|---|---|---|
| curiosity | 4.93 | 4.93 | 4.97 | 4.86 | -0.07 |
| adventure | 3.79 | 3.83 | 4.21 | 3.66 | -0.14 |
| social-harmony | 3.17 | 3.14 | 3.24 | 3.34 | +0.17 |

Social-harmony rises slightly — reversing the short-run decline. The short-run decline was likely situational (early puzzle absorption).

**Foxworth (dick-dastardly):**

| Drive | Q1 | Q2 | Q3 | Q4 | Trend |
|---|---|---|---|---|---|
| greed | 4.78 | 4.78 | 4.96 | 4.86 | +0.08 |
| scheming | 4.37 | 4.22 | 4.37 | 3.86 | **-0.51** |
| recognition | 3.04 | 3.33 | 3.11 | 3.62 | **+0.58** |
| gloating | 2.93 | 2.44 | 3.04 | 3.07 | +0.14 |

Scheming declines while recognition rises — likely scenario saturation (nothing left to scheme about, falls back to demanding credit).

**Ant-hill-mob:**

| Drive | Q1 | Q2 | Q3 | Q4 | Trend |
|---|---|---|---|---|---|
| protection | 4.05 | 4.91 | 4.77 | 5.00 | **+0.95** |
| loyalty | 4.05 | 4.64 | 4.91 | 4.62 | **+0.58** |
| suspicion | 4.09 | 3.73 | 4.00 | 3.83 | -0.26 |

Protection and loyalty surge and sustain. Protection hits ceiling (5.00) by Q4. Reactive drives compound on accumulated evidence of danger.

**Principle 32:** Reactive drives sustain in saturated scenarios; proactive drives deplete. **Principle 33:** Short-run trends can be situational — Penelope social-harmony reversed. **Principle 34:** Meaningful long-run experiments need richer scenarios.

## Phase 7 — Cognitive Appraisal Architecture

The emotional-core experiment (Principles 35-38) showed monolithic drive descriptions hit a ceiling. Fixing one drive's description regresses another because: (1) drives compete for the thinking-field emotion slot, and (2) drive descriptions do triple duty — defining perception, appraisal, and feeling. These concerns need separation.

Adversarial debate (theory-first, empirics-first, codebase-first) produced the validated architecture:
- Minimal drive descriptions (type + intensity only)
- One sub-LLM Haiku call per turn for appraisal (analytical input, evocative output — P11 applies to main agent, not sub-LLMs)
- L1 instruction unchanged
- Post-processing extracts from thinking field

Ad-hoc test confirmed: sub-LLM appraisal's value is highest for non-obvious drive activations. Protection dominant in danger scenarios, but appraisal surfaced proving-worth ("wanting him to *see* me do it") alongside protection — more authentic multi-layered response.

## Taxonomy Category Model

| Category | Can emerge from drives? | Where it belongs | Evidence |
|---|---|---|---|
| Personality trait (scheming, gloating) | Yes | Drives | R1: all 7 removals safe |
| Storytelling convention (narrate plans aloud) | No — medium-specific | Drive description at 0.85+ intensity | R2/R2b |
| Voice texture (accent, catchphrases) | Partially | Voice section (style-directive) | Style-directive experiment |
| Internal emotional state | Produces varied external behavior | Drives (emotional-core framing) | Emotional-core: 2x vocabulary |
| Prescribed anchor (evil laugh) | EMERGENCE-GAP — needs neocortex | Voice signature-phrases | Hooded Claw vs Marsh |
| Voice mode (third-person, register shifts) | Yes, with identity activation | Speech-pattern + evocative prompt | R3g: 61.1% |
| Behavioral disposition (optimism, resilience) | Yes, with identity activation | Drives + evocative prompt | R3g: 22.2% |

## L1 — Best candidate for BASELINE1

Mean 4.06 (vs BASELINE0's 3.93). Improves 13 of 17 drives. Regresses on 4:

| Character | Drive | BASELINE0 | L1 | Delta |
|---|---|---|---|---|
| Hartwell | protection | 3.46 | 2.33 | -1.13 |
| Foxworth | gloating | 3.50 | 3.02 | -0.48 |
| Foxworth | recognition | 3.44 | 3.07 | -0.37 |
| Penelope | social-harmony | 3.18 | 2.85 | -0.33 |

These 4 regressions motivated Era 2 — the cognitive pipeline and drive expression work.

## Design Principles (1-42)

| # | Principle | Evidence | Round |
|---|---|---|---|
| 1 | Drives carry personality traits | All 7 removals safe | R1 |
| 2 | Drives don't carry storytelling conventions | Scheme narration dropped without encoding | R2 |
| 3 | Drive intensity gates convention-carrying | 0.85 works, 0.7 doesn't | R2b |
| 4 | Emotional-state framing broadens vocabulary | 2x unique emotion words | Emotional-core |
| 5 | Storytelling conventions ≠ personality traits | Asymmetric degradation Wacky Races vs generic | R2 |
| 6 | LLMs prioritise goals over personality | Goal-directed behavior persisted, ambient attributes drifted | R3 |
| 7 | Speech-patterns alone don't override model defaults | Speech-pattern ignored without reinforcement | R3 |
| 8 | Passive review recovers presentational attributes | Third-person fully recovered | R3b |
| 9 | Structured fields create satisficing shortcuts | Model fills blanks then ignores them | R3c |
| 10 | Open-ended prompts outperform structured ones | Show-workings worse than passive review | R3c |
| 11 | Analytical instructions kill emotional expression | Optimism dropped to 9.1% under five-step process | R3f |
| 12 | Immersive instructions recover dispositions | "Reason in character" recovered optimism to 23.1% | R3d |
| 13 | Identity activation avoids the analytical/immersive tradeoff | "Think AS, not ABOUT" recovered both dimensions | R3g |
| 14 | Less instruction can produce better results | Two sentences outperformed five steps | R3g vs R3f |
| 15 | Presentational patterns self-reinforce; emotional dispositions decay | Third-person stable (100→77%), optimism halved (41→18%) over 309 events | R3g full |
| 16 | Emotional echo strengthens presentational stability | Third-person trend reversed: -22.7pp (R3g) → +9.5pp (L1) | L1 |
| 17 | Keyword measurement breaks at N≈20 per half | 45pp swing in optimism from methodology alone | L1 |
| 18 | Emotional tone matures, not decays | Late-run output shows optimism-despite-setbacks — depth, not drift | L1 qualitative |
| 19 | "Name your feeling" creates a compounding feedback loop | Emotional naming 80%→100% for Hartwell; stable 100% for 3/5 characters | L1 thinking |
| 20 | Emotional echo produces drive-consistent, character-specific emotions | Each character's emotion vocabulary maps to their declared drives | L1 thinking |
| 21 | Emotional echo is emergent, not scripted — emotions track situations | Frustration when blocked (5x), concern when danger appears, compound arcs | L1 situational |
| 22 | Per-drive LLM classification reveals character growth invisible to keywords | Hartwell protection +0.86, Penelope social-harmony -0.88 | Classifier |
| 23 | Drive hierarchies are character-specific and match declarations | Scheming tops both villains at different intensities | Classifier |
| 24 | Temporal stability varies by character — some grow, some are baked in | Foxworth ±0.13; Hartwell and Penelope show significant shifts | Classifier |
| 25 | Evocative instructions amplify drive expression monotonically | R3 3.54 → R3b 3.41 → R3g 3.86 → L1 4.06 | Cross-run |
| 26 | Passive review suppresses drive expression below no-instruction control | R3b (3.41) < R3 (3.54) | Cross-run |
| 27 | Character maturation patterns are instruction-caused, not scenario-inherent | Hartwell protection: -0.70 (R3), +0.86 (L1). Only evocative/echo show growth | Cross-run |
| 28 | Emotional drives benefit most from identity activation | Mob loyalty +1.62, HC gloating +1.16 (R3→L1). Self-reinforcing drives unchanged | Cross-run |
| 29 | Emotional echo is the most temporally stable instruction | Mean |delta|: L1 0.25, R3/R3g 0.27, R3b 0.34 | Cross-run |
| 30 | Drive expression sustains at 2.3x scale — no decay | Mean 4.02 (516 events) vs 4.06 (222 events) | Long run |
| 31 | Character maturation plateaus rather than reverses | Hartwell protection +0.44 (Q1→Q4). Steep early growth, stable equilibrium | Long run |
| 32 | Reactive drives sustain in saturated scenarios; proactive drives deplete | Protection/loyalty hold. Scheming/proving-worth decline without new material | Long run |
| 33 | Short-run trends can be situational, not structural | Penelope social-harmony: -0.88 (short) → +0.17 (long) | Long run |
| 34 | Scenario saturation confounds maturation — longer runs need richer worlds | Can't distinguish character growth from stimulus depletion past ~300 events | Long run |
| 35 | Emotional-core reframing recovers drives lacking environmental reinforcement | HC gloating (emotional-core) +0.12 under L1; Foxworth gloating (behavioral) -0.48 | EC experiment |
| 36 | Single-emotion instruction creates winner-take-all between drives | Strengthening protection (+1.15) weakened proving-worth (-0.70) | EC experiment |
| 37 | Monolithic drive descriptions hit a ceiling — fixing one regresses another | EC: 10/17 pass B0 vs L1's 13/17 | EC experiment |
| 38 | Drive descriptions do triple duty (perception + appraisal + feeling) — needs separation | Aligns with Lazarus cognitive appraisal theory | Architectural analysis |
| 39 | P11 applies to the main agent, not to sub-LLMs — analytical prompts safe in pre-processing | Sub-LLM with analytical prompt produces quality multi-drive output | Phase 7 debate |
| 40 | Evocative narrative output avoids P9 satisficing — narrative is not structured fields | Narrative doesn't trigger pattern-completion shortcuts. Only structured fields do | Phase 7 debate |
| 41 | Sub-LLM appraisal's value is highest for non-obvious drive activations | When all drives naturally activate, appraisal adds little. Subtle drives benefit most | Phase 7 test |
| 42 | One sub-LLM call covers all 4 Scherer SECs — separate evaluations reproduce P11 failure | Haiku implicitly traverses all SECs when asked "what are they feeling?" | Phase 7 debate |

## Transcripts

| Transcript | Events | Round | Instruction |
|---|---|---|---|
| `generic-colloquial-20261002/` | 310 | BASELINE0 | — |
| `pareback-r1-baseline-20261003/` | 331 | R1 (BASELINE) | — |
| `pareback-r1-generic-20261003/` | 315 | R1 (GENERIC) | — |
| `pareback-r2-baseline-20261003/` | 311 | R2 (BASELINE) | — |
| `pareback-r2-generic-20261003/` | 334 | R2 (GENERIC) | — |
| `pareback-r2b-baseline-20261003/` | 315 | R2b (BASELINE) | — |
| `pareback-r2b-generic-20261003/` | 322 | R2b (GENERIC) | — |
| `emotional-core-generic-20261003/` | 214 | Emotional-core (truncated) | — |
| `emotional-core-generic-full-20261003/` | 375 | Emotional-core (full) | — |
| `pareback-r3-generic-20261003/` | 325 | R3 | None |
| `pareback-r3b-generic-20261003/` | 312 | R3b | Passive review |
| `pareback-r3c-generic-20261003/` | 306 | R3c | Show-workings |
| `pareback-r3d-generic-20261003/` | 186 | R3d | Hybrid (crashed) |
| `pareback-r3f-generic-20261003/` | 301 | R3f | Mental-model-first |
| `pareback-r3g-generic-20261003/` | 129 | R3g sample (crashed) |  Minimal evocative |
| `pareback-r3g-full-generic-20261004/` | 309 | R3g full | Minimal evocative |
| `layer1-emotional-echo-generic-20261004/` | 320 | L1 | Emotional echo |
| `layer1-thinking-capture-generic-20261004/` | 318 | L1 (thinking) | Emotional echo |
| `layer1-long-run-generic-20261004/` | 732 | L1 long | Emotional echo |
