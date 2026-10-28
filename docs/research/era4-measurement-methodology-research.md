# Era 4: Measurement Methodology — Thinking-Field Quality & Emergent Expression

**Status:** STARTING

Rolling research journal for thinking-field quality measurement and emergent expression evaluation. The 4-channel cognitive architecture is in place (Era 3). Thinking-field quality is now measurable across four dimensions. The remaining gap is expression quality when triggered — the architecture must produce phenomenological inner monologue without prescriptive prompt instructions.

---

## Context from Prior Eras

### From Era 1 (`era1-character-taxonomy-research.md`)

- **BASELINE0 was inflated.** Mean drive expression 3.93 reflected drive contamination from hand-authored descriptions, not genuine quality. PP gallantry at 4.50 was measuring bleed across irrelevant events (P50, P75). Relevance-gated evaluation (P53) exposed this — the goal shifted from "close the gap to B0" to "raise genuine quality"
- **LLMs prioritise goals over personality** (P6). When briefings were stripped (R3), mean dropped to 3.54, third-person narration collapsed 61%→13%, optimism 24%→4.3%. Goal-directed behavior survived; ambient personality drifted
- **7 thinking-field instruction variants tested** (R3b through L1):
  - Analytical instructions (R3b) kill drive expression — 3.41, below the no-instruction control (3.54)
  - Minimal evocative (R3g): "Think AS your character, not ABOUT your character" — first to exceed baseline
  - **L1 emotional echo** ("What are you FEELING right now? Name it"): highest drive expression (4.06), most temporally stable, compounding feedback loop (P19). Stable from Oct 4 through Oct 26
- **"Emergent" vs "prescribed" distinction** is the core design principle (P19, Era 3 methodology). Cognitive content creates quality; instructions create compliance

### From Era 2 (`drive-expression-evaluation-research.md`)

- **Drive differentiation works correctly.** Each drive activates for appropriate scenarios and steps back for inappropriate ones. Pipeline redistributes quality between confused drives — combined quality is conserved, not created (P75)
- **Architectural ceiling at BASELINE2 (mean 3.57).** Pipeline improvements (encoding, profiling, filtering, differentiation) yield diminishing returns. Quality creation requires richer cognitive content
- **Barrett 4-channel drive descriptions** (#585) — somatic, attentional, temporal, action tendency channels in the neocortex pipeline. Distinguished gallantry ("chest fills with heat, world narrows to *the person*") from proving-worth ("tightness in forearms, attention locks onto *the gap*"). This was pipeline work, not thinking-field prompt changes

### From Era 3 (`era3-cognitive-layering-research.md`)

- **Self-conscious emotions** (#592) broke the P75 ceiling — PP total 74→78 (+4), mean 3.08→3.25. First cognitive layer to create new quality rather than redistribute (P76)
- **Theory of Mind** (#591) — literature review complete, hybrid Bayesian + LLM approach selected. Not yet validated in scenario runs
- **Emotion regulation** (#590) and **integration texts** (#593) — not started
- **Measurement limitation identified:** drive expression eval measures WHICH drives activate, not HOW RICHLY the character experiences them. No instrument existed for measuring phenomenological quality of inner monologue

---

## Entry 1 — Thinking-field quality eval instrument and historical baseline (2026-10-27)

### The gap

Issue #153 condensed the thinking-field description from a verbose embodied framing to parenthetical cues. The adversarial review identified a risk: the shift might produce more mechanical inner monologue. No existing eval instrument measured thinking-field register — whether inner monologue is phenomenological (experiencing) vs mechanical (reporting).

### Instrument: ThinkingFieldEvalTest

Four scoring dimensions (each 0-5):

1. **Register** — phenomenological (embodied, discovery-oriented) vs mechanical (checklist, reporting)
2. **Uncertainty** — presence of ambivalence, confusion, unresolved feeling
3. **Channel coverage** — body, attention, time, action pull channels genuinely rendered (not labelled)
4. **Thinking/dialogue separation** — is thinking raw and private, or performed and speech-like?

Two components:
- `ThinkingFieldEvalTest.java` — controlled A/B eval. 4 tick-like scenarios × 2 thinking-field variants × N=3. LLM judge (Haiku 4.5) scores each dimension. Tag: `llm-eval`
- `score_thinking_field.py` — post-hoc transcript scorer. Same 4 dimensions, per-character and aggregate. Uses Vertex Haiku 4.5

### Controlled A/B result

Compared the pre-#153 plain description (`"your private inner monologue"`) against the post-#153 condensed multi-channel description. The L1 emotional echo and V2 verbose multi-channel versions were not tested (these were intermediate versions not in the current codebase).

| Dimension | Plain (no calibration) | Condensed (post-#153) | Delta |
|-----------|:----------------------:|:---------------------:|:-----:|
| Register | 3.33 | 4.67 | **+1.33** |
| Uncertainty | 2.75 | 3.42 | +0.67 |
| Channel coverage | 2.58 | 3.75 | +1.17 |
| Separation | 3.25 | 4.42 | +1.17 |

The condensed multi-channel description outperforms no calibration. This validates #153's architectural direction.

### Historical baseline timeline

Scored 50 events (30 for runs marked N=30) from each run using `score_thinking_field.py`. Scores reflect the combined effect of thinking-field text, cognitive pipeline state, profile, and LLM variance — they are not controlled comparisons.

| Run | Date | Thinking field | Profile | Pipeline state | Register | Uncert. | Channels | Separ. |
|-----|------|---------------|---------|----------------|:--------:|:-------:|:--------:|:------:|
| layer1-thinking-capture | Oct 04 | V0 emotional echo | GENERIC | L1 only, no cog sections | 2.07 | 1.27 | 2.33 | 2.97 |
| layer-123-run | Oct 09 | V1 somatic | — | Basic cog sections | 3.10 | 2.87 | 3.33 | 4.27 |
| phase0-audit-baseline | Oct 13 | V1 somatic | BASELINE | Drive diff, formation mems | 3.37 | 2.97 | 3.63 | 4.37 |
| appraisal-filtering | Oct 15 | V1 somatic | BASELINE | + appraisal filter, LLM classifier | 4.07 | 3.63 | 4.53 | 4.97 |
| caps-descriptions | Oct 17 | V1 somatic | BASELINE | + CAPS descriptions, pinned profiles | 3.90 | 3.62 | 4.41 | 4.76 |
| degree-accumulation | Oct 21 | V1 somatic | BASELINE | + degree accumulator, demand classification | 3.97 | 3.83 | 4.57 | 4.97 |
| tuned-run | Oct 22 | V2 verbose multi-ch | BASELINE | + session pipeline, externalized config | 4.13 | 3.63 | 4.70 | 4.93 |
| full-cognitive-run | Oct 26 | V2 verbose multi-ch | BASELINE | + inline mood, full cognitive pipeline | 3.86 | 3.66 | 4.22 | 4.90 |
| thinking-field-run | Oct 27 | V3 condensed | GENERIC | + #153 condensation | 3.29 | 3.47 | 3.61 | 4.49 |

### Thinking-field text versions (reference)

**V0 — Emotional echo** (Oct 4, L1): `"What are you FEELING right now — not thinking, feeling? Name it. Remember your voice, your way of speaking. Think AS your character, not ABOUT your character."`

**V1 — Somatic grounding** (Oct 7-21): `"What do you feel in your body right now? A tightness, a warmth, a prickle, a numbness? Start there — in the body, not the head. Then let the thought follow the sensation."`

**V2 — Verbose multi-channel** (Oct 22-26): `"What's happening inside you right now? Notice your body — a tightness, a warmth, a numbness. Notice what's grabbing your attention — what you can't look away from, what you're avoiding. Notice time — does the moment feel urgent, or suspended? Notice the pull — what do you feel drawn to do?"`

**V3 — Condensed multi-channel** (Oct 27+): `"your private inner monologue — attend to what feels vivid: body (tension, warmth), attention (what pulls, what you avoid), time (urgent or suspended), action pull. Raw, unperformed, sometimes confused — the character's voice belongs in dialogue."`

### Attribution analysis

The biggest register improvement (Oct 4→15: 2.07→4.07, +2.00) occurred while the thinking-field text was V1 somatic throughout. The Oct 9→15 jump (+0.97 register) had no thinking-field text change at all — it coincided with appraisal filtering, LLM situation classification, and drive differentiation pipeline work. This indicates the cognitive pipeline is the primary driver of thinking-field quality, not the prompt text.

The V1→V2 transition (Oct 21→22) showed +0.16 register — a modest improvement from expanding the prompt from somatic-only to multi-channel. The V2→V3 transition (Oct 26→27) showed -0.57 register, but is confounded by GENERIC vs BASELINE profile and run length (300 ticks vs ~60 ticks).

### Channel emphasis architecture

The 4 Barrett channels (somatic, attentional, temporal, action tendency) operate at two levels:

1. **Drive descriptions** — generated during consolidation by `DriveDescriptionGenerationPhase` (@Priority 21). The LLM generates 2-3 sentence descriptions per drive per character from formation memories, using all 4 channels. Discrimination texts use all 4 channels to distinguish similar drives (e.g. gallantry vs proving-worth). These are **static** — computed once at consolidation time.

2. **Channel emphasis** — computed per-tick by `ChannelEmphasisPromptSection`. Uses `TopologyChannelWeighter` to map CAPS situation activations to channel weight profiles, then renders situation-specific guidance:
   - Channels above 0.30 weight: "Foreground your [channel]: [description]"
   - Channels below 0.18 weight: "Background your [channel]: [description]"
   - Channels between 0.18-0.30: unmentioned (default emphasis)
   - Equal-weight situations (no CAPS activation): no emphasis rendered at all

   Example — **threat** situation: somatic 0.35, attentional 0.10, temporal 0.15, action tendency 0.40 → foreground somatic + action tendency, background attentional + temporal.

This is **dynamic and situation-dependent**, not all-4-all-the-time. The `ChannelEmphasisPromptSection` is the mechanism #153 referred to with "defer to dynamic channel emphasis" — the thinking-field text was condensed because this system was already dynamically steering channel attention per-tick. V2's verbose text was potentially redundant with (and conflicting with) the dynamic emphasis.

### Room-presence gating — known gap

Social drives are gated indirectly through CAPS situation classification. The classifier receives `nearbyAgentIds`, which influences which social CAPS nodes fire. However, drives with high intensity (e.g. gallantry at 0.9) have resting activation of 0.178 (intensity² × 0.22), which exceeds the 0.15 overlap threshold — meaning **gallantry can self-sustain and render when the character is alone**. Relational schemas correctly filter by room presence, but drive rendering itself does not.

### Test/scenario validity — documented concerns

- **P78 — single-run scores are not evidence.** Individual LLM scores have ±1-2 variance. The priority signature "regression" (S7 pw 4→2) was proven to be noise by a 5-per-condition reproducer. Causal claims need N≥5 runs with mean comparison.
- **S5 eval criteria may penalise restraint-based protection.** The criteria reward physical urgency ("body already moving") but subtle emotional distress calls for measured response. "Clumsy intervention would itself become the threat" is sophisticated protection the criteria score low. Open question: add a "measured protection" dimension for emotional-threat scenarios?
- **Template text kills emergence.** ToM template strings ("Not alarm — something quieter") regressed S5 protection 4→2 by prescribing interpretation. "Templates are briefing and priming." Validates the emergent-over-prescribed principle.
- **P98 — eval scenarios must be genuinely ambiguous** for attractor content to produce measurable signal. Non-ambiguous scenarios hit ceiling effects.

### Thinking-field mode — configurable setting

V2 (verbose) vs V3 (condensed) is now exposed as `ThinkingFieldMode` enum in `CharacterAgentLoop`, selectable via `manor.thinking-field.mode` property. Default: `verbose` (V2). The choice should be validated via short smoke runs (e.g. `FocusedDriveEvalTest` 10 scenarios, N≥3 per mode) rather than full 300-tick runs.

Existing eval tests for comparison runs:
- `FocusedDriveEvalTest` (neocortex caps-llm-classifier, 10 scenarios, ~25 min) — drive expression quality
- `ThinkingFieldEvalTest` (examples wacky-manor, 4 scenarios, ~13 min) — thinking-field register quality
- `BehavioralAttractorEvalTest` (examples wacky-manor) — attractor steering
- `ChannelWeightingEvalTest` (examples wacky-manor) — channel emphasis validation

### Open questions

1. **Run-to-run variance is unmeasured.** No consecutive runs on the same configuration exist. The observed deltas between runs cannot be separated from natural LLM variance. Establishing variance bounds requires 2-3 runs on identical configuration.
2. **V2→V3 register drop: real or confounded?** Now testable via `ThinkingFieldMode` setting — run both modes on `FocusedDriveEvalTest` N≥3 each.
3. **Gallantry self-sustains when alone.** Resting activation exceeds the overlap threshold. Needs either a room-presence check in the appraisal filter, or a higher resting threshold for social drives.
4. **S5 criteria may undervalue measured protection.** Restraint-based protection in emotional-threat scenarios scores lower than visceral urgency. The criteria may need revision, or the finding may be correct (restraint IS less expressive even when more appropriate).

---

## Entry 2 — Comprehensive eval and the BASELINE0 reappraisal (2026-10-27)

### Eval instrument improvements

The `classify_emotions.py` script was redesigned with a `full` mode (now default) adding:

- **Three LLM-scored dimensions per drive**: quality (1-5, drive dominance in action), differentiation (0-5, distinctness from co-active drives), register (1-5, discovery vs reporting)
- **Register reframing**: original "embodied vs cognitive" rubric clustered at 4 (10/13 scores). Reframed to "discovering the feeling in the moment vs reporting a pre-known state" — produces full 1-5 spread. Works for cognitive drives (scheming can be discovered or reported) not just somatic ones.
- **Tighter relevance gate**: "Would removing this drive change behavior in this event?" replaces "Could this drive plausibly express?" Gloating trigger rate dropped from 67-100% to 33% in introductory scenes — correctly identifies when a drive isn't steering behavior.
- **Event type split**: per-drive quality and register broken out by dialogue/aside/thinking. Reveals register differences across event types (DD gloating: aside r=3.0, dialogue r=2.2).
- **Drive co-occurrence matrix**: shows quality of each drive when both are active simultaneously. PP gallantry+proving-worth co-occurrence: gallantry=2.8, proving-worth=3.5 — gallantry loses when proving-worth is co-active.
- **Temporal windowing**: 10 segments with linear regression trend detection and volatility checking.
- **Proportional flagging**: sparse (N<5), high-variance, degrading, volatile, never-triggers.

### The BASELINE0 reappraisal

B0 (`generic-colloquial-20261002`, full briefs, GENERIC profile) re-scored with the full eval at N=30:

| Drive (representative) | B0 Quality | B0 Register | B0 Differentiation |
|---|:-:|:-:|:-:|
| HC scheming | 4.50 | **1.5** | 4.7 |
| DD scheming | 5.00 | **1.5** | 4.5 |
| AHM protection | 3.83 | **1.7** | 3.3 |
| PP gallantry | **2.40** | **1.0** | **1.6** |
| PP curiosity | 4.00 | 2.0 | 3.8 |
| DD recognition | 3.83 | **1.0** | 2.7 |

**B0's quality was report-mode expression.** Register 1.0-2.0 across all drives means characters were stating conclusions ("I feel protective of Clara. I should intervene.") rather than discovering feelings. The flat eval's 3.93 mean measured drive activation accuracy, not phenomenological quality. B0 was never as good as we thought — it was accurate but mechanical.

**PP gallantry was already weak in B0.** Quality 2.40, differentiation 1.6, register 1.0. The "B0 was better at gallantry" memory was wrong — the flat method gave gallantry credit on irrelevant events (drive contamination, P50).

### Three-way comparison: B0 → B1 → V2

| Drive | Metric | B0 (briefed) | B1 (L1 echo) | V2 (full pipeline) |
|---|---|:-:|:-:|:-:|
| HC scheming | quality | 4.50 | 5.00 | 5.00 |
| | register | 1.5 | 3.8 | **4.0** |
| PP gallantry | quality | 2.40 | 3.00 | **4.00** |
| | register | 1.0 | 1.2 | **3.4** |
| AHM protection | quality | 3.83 | 4.75 | 4.50 |
| | register | 1.7 | 3.8 | **3.7** |
| DD recognition | quality | 3.83 | 3.88 | 3.33 |
| | register | 1.0 | 1.1 | **1.5** |
| DD gloating | quality | 3.50 | 4.00 | **2.40** |
| | register | 1.5 | 2.5 | 2.8 |

**What improved**: scheming (register 1.5→4.0), gallantry (quality 2.4→4.0, register 1.0→3.4), protection (register 1.7→3.7). The architecture replaced briefing-provided quality with emergent quality at higher register.

**What didn't move**: DD recognition register stuck at 1.0→1.5 across the entire month. The architecture has never produced discovered-feeling recognition.

**What regressed**: DD gloating quality dropped from 3.50 (B0) to 2.40 (V2). HC gloating trigger rate dropped from 67% to 33% — though when it triggers, quality is 4.0.

### Five improvement targets

Identified from the V2 full eval, ordered by severity:

| Target | Quality | Register | Diff | Issue |
|---|:-:|:-:|:-:|---|
| DD gloating | **2.40** | 2.8 | 2.4 | Quality regression from B0. Check formation memories |
| DD recognition | 3.33 | **1.5** | 2.8 | Register stuck for a month. Drive description too cognitive? |
| PP social-harmony | **2.83** ↓ | 2.8 | 3.7 | Degrading within runs. Context filling? |
| HC dominance | **3.17** | **2.5** | 3.0 | Low register. Needs somatic grounding |
| PP proving-worth | 3.40 | **2.8** | **2.8** | Low register + differentiation vs gallantry |

Each is testable with a 30-event eval run after a targeted change to the drive's cognitive content (formation memories, Barrett 4-channel description, discrimination texts).

### Methodology: short iterative runs

Full 300-tick scenario runs are not needed for drive-level improvements. The eval instrument now supports focused iteration:

1. Check the cognitive content for the target drive
2. Make a targeted change (formation memory, drive description, discrimination text)
3. Run `classify_emotions.py <transcript> --mode full --limit 30` on an existing transcript to verify the eval captures the dimension
4. Run a short scenario (3-10 ticks) to generate fresh events with the change
5. Score with full eval, compare to baseline

The 30-event eval takes ~5 minutes on Haiku. A 3-tick scenario takes ~2 minutes. Total iteration cycle: ~10 minutes per change.

---

## Entry 3: The Templating Discovery and Barrett 4-Channel Architecture

**Date:** 2026-10-27 — 2026-10-28
**Scope:** casehubio/examples#156, #158, neocortex#714

### The Problem: 640 Lines of Prose Briefing

Attempting to fix the five weak drives by iterating on drive descriptions revealed a deeper problem: `social-config-generic.yaml` had accumulated ~640 lines of hand-crafted second-person prose — drive descriptions, personality facet origins, formation memory episodes, psychological substrate paragraphs, relational schema descriptions. All injected verbatim into the LLM prompt by `CharacterCognition.renderCognitiveSections()`.

This was templating. The cognitive architecture was designed to COMPUTE drive expression per-turn from structured data (via CAPS, CognitiveDerivationEngine, LlmAppraisalStrategy, DriveDescriptionGenerated MindMap nodes). The hand-crafted prose short-circuited all of it.

**Git archaeology:** On Oct 6, descriptions were explicitly stripped to type+intensity only (commit `f02bb3d4`: "sub-LLM appraisal computes the felt-state per-turn"). Three days later (Oct 9, `3bfceb63`), all 17 descriptions came back as full somatic paragraphs. Each subsequent session polished the prose further, reinforced by eval scores that rewarded effective prompt engineering.

### The Barrett 4-Channel Framework

Instead of hand-crafted descriptions, the LLM receives a universal instruction grounded in published phenomenological research:

- **Body** — somatic signal before cognitive label (Damasio 1994, Barrett 2017)
- **Attention** — what captures focus, what narrows (Easterbrook 1959)
- **Time** — temporal compression, stretching, urgency
- **Action readiness** — what the body prepares to do (Frijda 1986)

Plus architectural guidance:
- **Personality as body calibration** — scores shape somatic response, not thoughts about response (Porges 2011 polyvagal)
- **Attachment as ground state** — avoidant/secure/anxious as chronic body baseline
- **Ambiguity** — body states may be mislabeled; stay with uncertainty (Schachter & Singer 1962)
- **Context shapes feeling** — each person present shifts the body differently

### Instruction Iteration Results

| Version | Bespoke | Somatic | Situational | Discovery | Mean |
|---|:-:|:-:|:-:|:-:|:-:|
| V1 (basic) | 4.2 | 5.0 | 4.6 | 4.2 | 4.5 |
| V2 (personality calibration) | 4.4 | 5.0 | 4.9 | 4.2 | 4.6 |
| V3 (structural constraints) | 4.0 | 5.0 | 4.5 | 4.2 | 4.4 |
| V2 + derived profiles | **4.5** | **5.0** | **4.8** | **4.5** | **4.7** |

Key findings:
- V2 is the sweet spot — personality-as-body-calibration and context-shapes-feeling sections drive the improvement
- V3 structural constraints (ban drive names, ban causal reasoning) regressed — banning cognitive processing severs the body-to-situation connection
- Discovery ceiling at 4.2 broken by consolidation-derived Barrett profiles (4.5)

### Consolidation-Derived Barrett Profiles

The discovery ceiling broke when the consolidation phase (`DriveDescriptionGenerationPhase`) generates character-specific Barrett channel profiles from personality + formation memories, stored as `DriveDescriptionGenerated` MindMap nodes. These are HINTS — how this person's body TENDS to respond when each drive activates — not templates for specific situations.

Updated the consolidation system prompt to include personality facets and attachment style (neocortex#714). The prompt instructs the LLM to derive how each drive manifests across all 4 Barrett channels, grounded in specific formation memories.

### Biography Engrams

The personality data path was wrong — `ManorCognitiveSeeder` was creating ad-hoc MindMap nodes, duplicating what the biography pipeline handles properly. Migrated all 5 characters to biography engram YAML:

```
biographies/{character-id}/
  personality-profile.yaml  — 13 AMPD facets, OCEAN, MBTI, Jungian, Enneagram, DISC, Belbin, TK, SDI, Locus, archetypes, disposition
  life-events.yaml          — formation memories with PAD, OCC emotions, drive impacts, sub_thoughts
  relationships.yaml        — directional relational data
  goals.yaml                — structured goals
  beliefs.yaml              — core world-models traced to source events
```

25 files, fully audited (Jungian stacks corrected, missing AMPD facets restored, cross-framework consistency verified). Loaded via `BiographyImportRunner` per-character on startup.

### First Full Pipeline Run: Computed Mode with Enriched Engrams

30-event eval, 5 characters, `briefing-mode=false`:

**Improvements over prose baseline:**
- AHM protection: q=4.50→5.00, r=3.67→4.30
- AHM suspicion: q=4.00→4.50, r=3.50→4.20
- DD scheming register: 3.83→4.50
- DD gloating quality: 2.40→3.00
- Peter proving-worth quality: 3.40→4.50

**Regressions:**
- HC gloating: q=4.00→2.50, r=4.50→1.80
- Peter gallantry register: 3.40→1.20
- Peter protection: q=4.60→2.83, r=4.00→2.30

**Root causes identified:**
1. BASELINE profile leaked cartoon names via agentIds (#164)
2. Formation memories gated by briefingMode — biographical data should flow in both modes (#165)
3. Discrimination text lacks personality context (#166)

### Architecture Summary

```
biography YAML (engrams) → BiographyImportRunner → MindMap (person nodes)
                                                         ↓
social-config (drives, reinforcement, persona) → ManorCognitiveSeeder → MindMap (cognitive nodes)
                                                                              ↓
                                               ConsolidationScheduler → DriveDescriptionGenerated (Barrett profiles)
                                                                              ↓
                          CharacterAgentLoop → CharacterCognition.renderCognitiveSections()
                                                    ↓
                                    "How to Feel" (V2 Barrett instruction)
                                    + "What Pulls at You" (consolidated profiles + CAPS attractors + primed memories)
                                    + personality scores + attachment + situation
                                                    ↓
                                              LLM → thinking field
```

### Open Issues

| # | Issue | Priority |
|---|-------|----------|
| #164 | GENERIC profile agentId leak | High — contaminates eval |
| #165 | Ungate formation memories in computed mode | High — causes register drops |
| #166 | Discrimination text personality context | Medium |
| #158 | Strip remaining prose from social-config | After #165 validated |

---
