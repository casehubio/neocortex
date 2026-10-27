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

### Open questions

1. **Run-to-run variance is unmeasured.** No consecutive runs on the same configuration exist. The observed deltas between runs cannot be separated from natural LLM variance. Establishing variance bounds requires 2-3 runs on identical configuration.
2. **V2→V3 register drop: real or confounded?** Isolating requires a BASELINE run on V3 code, comparable to the Oct 26 V2 BASELINE run.
3. **Is V2's higher score genuine quality or coached compliance?** V2 prescribes an introspective process ("Notice your body", "Notice what's grabbing your attention"). V3 names channels as format hints. Per the emergent-vs-prescribed principle (P19, Era 3 methodology), V2 may inflate scores by telling the LLM how to think rather than providing cognitive content for it to draw on. If so, V3's lower scores may be the honest measure of what the architecture produces.

---
