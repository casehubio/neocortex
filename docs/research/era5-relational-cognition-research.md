# Era 5: Relational Cognition — WHO Shapes What You Feel

**Status:** STARTING

The cognitive pipeline differentiates WHAT agents feel (Barrett somatic channels) and HOW they express it (drive descriptions, thinking-field calibration). Era 4 brought this to ceiling: quality 3.92, register 3.74, differentiation 3.74 — all exceeding V2 prose baseline on aggregate metrics.

The ceiling is reactive drives. Gloating, gallantry, protection — drives that activate toward SPECIFIC people in SPECIFIC situations — regress because the pipeline is blind to WHO the agent interacts with. The Barrett instruction says "notice your body." The drive description says "warm pressure behind your sternum." Neither says "this activates because Reginald just failed and he is prey."

Era 5 opens with #477 relational schemas, which adds the WHO dimension.

---

## What Era 4 Established

### The hybrid that works

Label + degree + somatic prose. Three components, all required:

| Component | What it provides | Without it |
|-----------|-----------------|------------|
| Label (GLOATING) | Conceptual anchor — LLM knows what the drive IS | Somatic text is ambiguous (warm pressure = joy? pride?) |
| Degree (0.7) | Intensity calibration | All drives feel equally strong |
| Somatic prose | Immersion anchor — keeps character in felt experience | Label triggers metacognitive reasoning (Era 1 P13) |

Somatic prose alone: quality 3.76. Label + degree alone: simulation awareness leaks. Hybrid: quality 3.99, zero leaks.

### Constant vs reactive drives

| Drive type | Examples | Quality | Why |
|-----------|---------|---------|-----|
| Constant | Scheming (5.00), Curiosity (4.67), Suspicion (4.00) | Excellent | Always-on — body state is permanent |
| Reactive | Gloating (2.40), Gallantry (3.67), Protection (3.80) | Below V2 | Needs trigger — WHO failed? WHO is in danger? |

Targeted probes confirmed the code works: HC gloating 4.33, Peter gallantry 5.00 in ideal conditions. Quality degrades under long context + ambiguous triggers + drive competition.

### The immersion architecture

Every text surface the LLM sees was audited and made experiential:
- Section headers renamed (system-speak → lived experience)
- Action results made descriptive (game reports → memories)
- ActionType.verb() centralised (enum names never in text)
- ID rewrite at import (cartoon IDs eliminated from runtime)
- Thinking instruction ("you are inside this moment, not watching it")
- Zero cartoon contamination, zero simulation awareness in 300-tick run

### What's missing: the relational dimension

The Barrett channels (body, attention, time, action readiness) are GENERIC — same instruction every tick regardless of who is present. The drive descriptions are SOMATIC — they describe the body's pattern but not what triggers it.

V2 prose had embedded triggers: "When someone is struggling, your body responds..."

The computed pipeline has the body response but not the trigger. The character has to independently recognise "Clara is in danger" → "gallantry should activate" → "this body pattern applies." Under long context with competing drives, this connection fails.

---

## What #477 Relational Schemas Provides

Five layers, each operating at a different timescale:

### 1. Types + Storage (#716)

`RelationalSchema(targetId, role, trust, intimacy, utility, description)` on perspectival overlay nodes. The description field carries felt prose — "Predictably protective. Too simple to be a real threat." — not system labels.

### 2. Appraisal Wiring (#717)

`AppraisalContext` carries the relational schema and personality facets. The sub-LLM appraisal receives relationship context in natural language: "Relationship with [target]: Role: prey, Trust: 0.1..."

### 3. Two-Channel Prompt Architecture (#718)

`OtherModelPromptSection` renders only the phenomenological layer — empathic-sensing ("a tightness behind the words"), relational-understanding ("the bond is built on shared creative ambition"). The mechanistic layer feeds the appraisal pipeline only — never the prompt.

The agent experiences "I sense tension in them" rather than "my appraisal-sensitivity score for rejection is 0.8."

### 4. Dynamic Evolution (#719)

Trust, intimacy, and role evolve during consolidation, personality-conditioned:
- Callousness asymmetry — dampens trust gain, preserves trust loss
- Avoidance ceiling — dismissive-avoidant agents structurally cannot reach high trust
- Personality-gated role transitions — prey → tool (not ally) when Manipulativeness > 0.5

### 5. CAPS Biasing (#720)

Trust modulates CAPS input activations before appraisal runs. Kindness from prey dampens secure_attachment, boosts rejection. Same observation, different perceptual filtering. The biasing is sub-prompt — it shapes emotion, which shapes behaviour, which shapes text.

---

## Hypothesis: Relational Context Restores Reactive Drive Quality

The missing link between observation and feeling:

```
Era 4 (current):
  "Reginald is here" → Barrett: "notice your body" → Drive: "GLOATING (0.7): warm pressure"
  Connection: character must independently recognise trigger → fails under complexity

Era 5 (with #477):
  "Reginald is here" → Relational: "prey, trust 0.1, you sense his weakness"
                      → CAPS: prey presence dampens empathy, boosts schadenfreude
                      → Barrett: body responds to the relational signal
  Connection: system provides the relational trigger → character's body responds
```

### Expected impact on regressed drives

| Drive | Era 4 issue | #477 mechanism | Expected fix |
|-------|------------|----------------|-------------|
| HC gloating | No connection: failure → schadenfreude | Prey role + dampened empathy = gloating trigger | Gloating activates when prey fails |
| Peter gallantry | No connection: Clara present → protect | High trust + anxious attachment = protective activation | Gallantry activates when Clara is present |
| Peter protection | Same as gallantry | Same mechanism | Protection always-on toward trusted |
| DD greed | No connection: valuable object → desire | Low utility relationship with objects? | May need separate fix |

### What to measure

1. **Reactive drive quality** — HC gloating, Peter gallantry/protection vs V2 and Era 4 baselines
2. **Relational consistency** — does the character treat prey differently from ally?
3. **Drive × relationship interaction** — does gloating toward prey differ from gloating toward rival?
4. **Metacognitive awareness** — does the relational prompt leak system state? (#167 eval)
5. **Session degradation** — do long sessions lose relational grounding?
6. **Temporal evolution** — do trust/role changes manifest in drive expression over 300 ticks?

### Eval methodology

The DriveQualityEvalTest from Era 4 provides targeted probes. Era 5 extends it with:
- **Relational probes** — same drive, different target person (prey vs ally). Quality should differ.
- **Temporal probes** — same scenario at tick 10, 100, 200. Quality should hold or improve.
- **Competition probes** — multiple drives eligible, relationship determines which dominates.

---

## Open Questions

1. Does the relational context alone restore reactive drive quality, or do we also need explicit situational trigger conditions on drives?
2. How does the two-channel prompt architecture interact with the Barrett instruction? Are they additive or competing?
3. Does CAPS biasing create observable drive differentiation, or is it too subtle for the eval to detect?
4. The entity linking design (#721) — should relational references in text use standoff annotation for memory grounding?
5. The tagged block protocol (#486) — should relational context arrive in a separate tagged block with its own interpretation META rules?
6. Performance: relational context adds per-person sections to the prompt. With 5 characters in the room, how much does this increase prompt size and latency?

---

## Immediate Plan

1. Merge #477 to this slot (do NOT sync to canonical main until validated)
2. Delete consolidation cache (schemas change the consolidation output)
3. Run 30-event eval with relational schemas active
4. Compare reactive drive quality against Era 4 baseline
5. If reactive drives improve: run 300-tick validation
6. If not: investigate whether explicit trigger conditions are needed alongside relational context
