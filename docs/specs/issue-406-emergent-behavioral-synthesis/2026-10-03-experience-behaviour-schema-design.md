# Standardised Experience-to-Behaviour Schema

**Issue:** casehubio/neocortex#401
**Parent epic:** casehubio/neocortex#406 — Emergent Behavioral Synthesis
**Date:** 2026-10-03

## 1. Overview

This document specifies a clinically-grounded YAML catalogue of ~40 entries
mapping formative experience patterns to behavioural tendencies through the
CAPS (Cognitive-Affective Processing System) network defined in the #407
research spec. Each entry represents a recognised clinical or behavioural
pattern from the psychology literature, spanning multiple CAPS connections.

The catalogue covers six psychological models: attachment theory
(Bowlby/Ainsworth), reinforcement sensitivity theory (Gray's BIS/BAS/FFFS),
cognitive theory (Beck's CBT), trauma response models (Walker 4F, ACE),
operant conditioning (Rescorla-Wagner), and social learning theory (Bandura).

**Consumers:**

1. **#398 (Memory seeding infrastructure)** — translates catalogue entries
   into ExperienceEvents with pre-classified situation types and amplified
   salience (sensitive period simulation)
2. **#408 (Cause-effect graph engine)** — catalogue entries reference CAPS
   input/output node IDs from the topology, providing validation targets
   for attractor formation
3. **Character designers** — pick entries, compose them for a character,
   and get predictable behavioral profiles grounded in established
   psychology

Every entry traces to published research. The catalogue is a curated index
of what the literature says about how experiences shape behaviour — not
creative character design.

## 2. Design Decisions

Four decisions govern the catalogue's design (full rationale in
`decisions.md`):

- **D1 — Clinical pattern granularity:** one entry per recognisable
  clinical pattern, not per CAPS connection or per character archetype
- **D2 — Experience injection:** entries describe formative experiences
  for CAPS to process, not direct weight/threshold overrides
- **D3 — YAML format:** machine-readable YAML consistent with the CAPS
  topology, directly consumable by #398
- **D4 — Pre-classified + narrative:** entries carry authoritative CAPS
  node references plus optional prose templates for richer ExperienceEvent
  generation

## 3. Entry Schema

Each catalogue entry has the following fields:

| Field | Type | Required | Description |
|-------|------|----------|-------------|
| `id` | string (kebab-case) | yes | Unique identifier, e.g. `attachment-anxious` |
| `model` | enum | yes | Source model: `attachment`, `bis_bas`, `cbt`, `trauma`, `operant`, `bandura` |
| `clinical_name` | string | yes | Standard clinical/psychological term |
| `description` | string | yes | 1-3 sentence summary of the cause-effect pattern |
| `triggers` | list | yes | CAPS input node activations (see below) |
| `narratives` | list of strings | no | Human-readable experience templates for ExperienceEvent generation |
| `expected_outcomes` | list | yes | Expected CAPS node states after processing (output or mediating nodes) |
| `developmental_period` | enum | no | `infancy`, `childhood`, `adolescence`, `adult`, `any` — sensitive period for #398 salience amplification. Default: `any` |
| `modulating_axes` | map | no | DispositionAxes that amplify or dampen this pattern |
| `related` | list of strings | no | IDs of related entries (cross-model or same-model contrasts) |
| `sources` | list | yes | Published research with provenance classification |

**Trigger fields:**

| Field | Type | Description |
|-------|------|-------------|
| `node` | string | CAPS input node ID from the topology |
| `intensity` | [min, max] | Activation intensity range (0.0-1.0) |
| `repetition` | enum | `high`, `moderate`, `low` — number of experiences to seed |
| `schedule` | string | Reinforcement schedule: `continuous` (default), `fixed_ratio`, `variable_ratio`, `fixed_interval`, `variable_interval` |

**Expected outcome fields:**

| Field | Type | Description |
|-------|------|-------------|
| `node` | string | CAPS output or mediating node ID from the topology |
| `direction` | enum | `positive` or `negative` |
| `strength` | [min, max] | Expected effect magnitude range (0.0-1.0) |

**Direction + strength semantics:** `direction` indicates effect polarity;
`strength` indicates effect magnitude. For Tier 1 validation on a
default-disposition agent (all nodes at baseline 0):
- `direction: positive` + `strength: [0.4, 0.7]` → node activation should
  settle in the range [+0.4, +0.7]
- `direction: negative` + `strength: [0.4, 0.7]` → node activation should
  settle in the range [-0.7, -0.4]

Mediating nodes are included in expected_outcomes because some clinical
patterns are defined primarily by their effect on internal models — e.g.,
attachment patterns alter internal working models (self_worth,
other_reliability), which then drive behavioral changes through downstream
CAPS connections. Mediating node assertions enable more precise validation:
the same behavioral output can be produced by different internal state
changes.

**Calibration methodology:** Strength ranges are estimated based on
clinical significance and relative effect magnitude, not by direct
mathematical transformation from cited effect sizes. The empirical
citations provide provenance for the existence and direction of each
effect. Exact calibration is iterative — Tier 1 validation will refine
ranges based on what the CAPS network produces with the topology's
connection weights. The `provenance` field on sources distinguishes
`empirical` (published effect sizes), `consensus` (clinical agreement),
and `estimated` (informed judgment) to make the basis transparent.

**Source fields:**

| Field | Type | Description |
|-------|------|-------------|
| `ref` | string | Citation (author, year) |
| `data` | string | Key finding: effect size, sample, or mechanism |
| `provenance` | enum | `empirical`, `consensus`, `estimated` |

Node IDs in `triggers` and `expected_outcomes` must exactly match the CAPS
topology YAML (`docs/specs/2026-10-02-caps-topology.yaml`, version 1).

## 4. File Organisation

```
docs/specs/experience-behaviour-catalogue/
  index.yaml                    # metadata, version, cross-model shared-node map
  attachment.yaml               # Bowlby/Ainsworth patterns
  bis_bas.yaml                  # Gray's RST patterns
  cbt.yaml                      # Beck's cognitive model patterns
  trauma.yaml                   # Trauma response patterns (Walker 4F, ACE)
  operant.yaml                  # Operant conditioning patterns
  bandura.yaml                  # Social learning patterns
```

One file per model because each model has its own literature and internal
structure. Cross-model interactions are captured through `related`
cross-references and the CAPS network's shared mediating nodes.

**Index file contents:**

```yaml
version: "1.0.0"
caps_topology_version: 1
spec_issue: 401
parent_epic: 406

shared_node_map:
  self_worth: [attachment, cbt, bandura]
  self_efficacy: [bandura, cbt]
  other_reliability: [attachment, cbt]
  other_safety: [attachment, cbt]
  threat_sensitivity: [bis_bas, trauma]
  FFFS_activation: [bis_bas, trauma]
  BAS_activation: [bis_bas, operant]
  reinforcement_expectation: [operant, cbt, bandura]
  arousal_level: [bis_bas, trauma, attachment]
  escape_assessment: [trauma, bis_bas]
```

## 5. Catalogue Entries

### 5.1 Attachment Theory (Bowlby, Ainsworth)

```yaml
entries:
  - id: attachment-secure
    model: attachment
    clinical_name: Secure attachment
    description: >
      Consistent caregiver responsiveness builds positive internal working
      models of self and other, producing comfort with intimacy and balanced
      autonomy.
    triggers:
      - node: secure_attachment
        intensity: [0.6, 0.9]
        repetition: high
        schedule: continuous
    narratives:
      - "When you were distressed, your caregiver noticed and responded
         warmly and reliably."
      - "You learned that reaching out for comfort produced a predictable,
         soothing response."
    expected_outcomes:
      - node: self_worth
        direction: positive
        strength: [0.4, 0.7]
      - node: other_reliability
        direction: positive
        strength: [0.4, 0.7]
      - node: other_safety
        direction: positive
        strength: [0.3, 0.6]
      - node: trust
        direction: positive
        strength: [0.5, 0.8]
      - node: proximity_seek
        direction: positive
        strength: [0.3, 0.5]
      - node: self_direct
        direction: positive
        strength: [0.3, 0.5]
    developmental_period: infancy
    modulating_axes:
      socialOrient:
        cooperative: amplify
    related:
      - attachment-anxious
      - attachment-dismissive
      - attachment-fearful
    sources:
      - ref: "van IJzendoorn 1995"
        data: "r=.24-.32 caregiver sensitivity → security"
        provenance: empirical
      - ref: "Madigan et al. 2024"
        data: "caregiver sensitivity meta-analysis update"
        provenance: empirical

  - id: attachment-anxious
    model: attachment
    clinical_name: Anxious-preoccupied attachment
    description: >
      Variable-ratio reinforcement from inconsistent caregiver produces
      hypervigilant proximity-seeking with high extinction resistance.
      Internal working model: negative/uncertain self, uncertain other.
    triggers:
      - node: inconsistent_care
        intensity: [0.6, 0.9]
        repetition: high
        schedule: variable_ratio
      - node: rejection
        intensity: [0.3, 0.5]
        repetition: moderate
    narratives:
      - "Your caregiver was sometimes warm and attentive, sometimes cold
         and dismissive, with no predictable pattern."
      - "You never knew whether reaching out would be met with comfort
         or rejection, so you learned to reach out harder."
    expected_outcomes:
      - node: proximity_seek
        direction: positive
        strength: [0.5, 0.8]
      - node: other_reliability
        direction: negative
        strength: [0.3, 0.6]
      - node: self_worth
        direction: negative
        strength: [0.2, 0.4]
      - node: trust
        direction: negative
        strength: [0.2, 0.4]
    developmental_period: infancy
    modulating_axes:
      socialOrient:
        cooperative: amplify
        independent: dampen
      conflictMode:
        avoidant: amplify
    related:
      - attachment-secure
      - attachment-dismissive
      - operant-vr-extinction-resistance
    sources:
      - ref: "van IJzendoorn 1995"
        data: "r=.24-.32 caregiver sensitivity → security"
        provenance: empirical
      - ref: "Zhang et al. 2022"
        data: "r=.42 attachment anxiety → negative mental health, N=79,722"
        provenance: empirical

  - id: attachment-dismissive
    model: attachment
    clinical_name: Dismissive-avoidant attachment
    description: >
      Rejecting or emotionally unavailable caregiver produces negative
      other-model with defensive positive self-model. Suppresses attachment
      needs in favour of self-reliance and emotional distancing.
    triggers:
      - node: rejection
        intensity: [0.5, 0.8]
        repetition: high
      - node: neglect
        intensity: [0.4, 0.7]
        repetition: high
    narratives:
      - "When you expressed needs or emotions, they were consistently
         dismissed or ignored."
      - "You learned that relying on others led to disappointment, so
         you stopped showing vulnerability."
    expected_outcomes:
      - node: distance_maintain
        direction: positive
        strength: [0.5, 0.7]
      - node: other_reliability
        direction: negative
        strength: [0.4, 0.7]
      - node: self_direct
        direction: positive
        strength: [0.3, 0.5]
      - node: distrust
        direction: positive
        strength: [0.3, 0.5]
    developmental_period: infancy
    modulating_axes:
      socialOrient:
        independent: amplify
        cooperative: dampen
    related:
      - attachment-secure
      - attachment-anxious
    sources:
      - ref: "Zhang et al. 2022"
        data: "r=.28 attachment avoidance → negative mental health"
        provenance: empirical
      - ref: "Griffin & Bartholomew 1994"
        data: "Big Five → self-model 48%, other-model 27% variance"
        provenance: empirical

  - id: attachment-fearful
    model: attachment
    clinical_name: Fearful-avoidant attachment
    description: >
      Frightening caregiver who is simultaneously source of safety and
      threat produces contradictory internal working models. No coherent
      strategy — oscillation between approach and avoidance, dissociation
      under stress.
    triggers:
      - node: inconsistent_care
        intensity: [0.6, 0.9]
        repetition: high
        schedule: variable_ratio
      - node: betrayal
        intensity: [0.5, 0.8]
        repetition: moderate
      - node: physical_threat
        intensity: [0.4, 0.7]
        repetition: moderate
    narratives:
      - "The person you depended on for safety was also the source of
         your fear."
      - "You could neither approach safely nor withdraw completely —
         caught between needing closeness and fearing harm."
    expected_outcomes:
      - node: proximity_seek
        direction: positive
        strength: [0.3, 0.5]
      - node: distance_maintain
        direction: positive
        strength: [0.3, 0.5]
      - node: self_worth
        direction: negative
        strength: [0.4, 0.7]
      - node: other_reliability
        direction: negative
        strength: [0.5, 0.8]
      - node: other_safety
        direction: negative
        strength: [0.5, 0.8]
      - node: freeze
        direction: positive
        strength: [0.2, 0.4]
    developmental_period: infancy
    modulating_axes:
      conflictMode:
        avoidant: amplify
    related:
      - attachment-anxious
      - attachment-dismissive
      - trauma-chronic-fawn
    sources:
      - ref: "van IJzendoorn 1995"
        data: "disorganized attachment in frightening caregiver context"
        provenance: empirical
      - ref: "Deneault et al. 2021"
        data: "d=.37 insecure → externalizing behavior"
        provenance: empirical

  - id: attachment-earned-security
    model: attachment
    clinical_name: Earned secure attachment
    description: >
      Transition from insecure to secure through repeated corrective
      relational experiences. Old attractor weakens but persists — earned-
      secure adults show slightly elevated risk vs continuously-secure.
      Requires low learning rate and high repetition.
    triggers:
      - node: secure_attachment
        intensity: [0.5, 0.7]
        repetition: high
        schedule: continuous
      - node: acceptance
        intensity: [0.5, 0.7]
        repetition: high
    narratives:
      - "Over time, you encountered relationships where trust was
         consistently rewarded — gradually revising earlier models."
      - "New corrective experiences didn't erase the old patterns but
         built competing pathways strong enough to predominate."
    expected_outcomes:
      - node: trust
        direction: positive
        strength: [0.3, 0.6]
      - node: other_reliability
        direction: positive
        strength: [0.2, 0.5]
    developmental_period: adult
    modulating_axes:
      socialOrient:
        cooperative: amplify
    related:
      - attachment-secure
      - attachment-anxious
      - attachment-dismissive
    sources:
      - ref: "#407 spec §4.1"
        data: "Earned security update rules: slow, asymmetric, context-dependent"
        provenance: consensus
      - ref: "Roisman et al. 2002"
        data: "earned-secure vs continuous-secure: elevated but lower risk"
        provenance: empirical

  - id: attachment-trust-erosion
    model: attachment
    clinical_name: Attachment-based trust erosion
    description: >
      Betrayal by a trusted figure produces rapid negative shift in
      other-model. Asymmetric update: single betrayal undoes years of
      trust-building. High decay resistance on the negative connection.
    triggers:
      - node: betrayal
        intensity: [0.7, 1.0]
        repetition: low
      - node: abandonment
        intensity: [0.5, 0.8]
        repetition: low
    narratives:
      - "Someone you trusted deeply broke that trust in a way that
         felt irreparable."
      - "The betrayal was so unexpected that it rewrote your assumptions
         about whether people can be relied upon."
    expected_outcomes:
      - node: distrust
        direction: positive
        strength: [0.5, 0.8]
      - node: other_reliability
        direction: negative
        strength: [0.5, 0.8]
      - node: other_safety
        direction: negative
        strength: [0.4, 0.7]
      - node: distance_maintain
        direction: positive
        strength: [0.3, 0.6]
    modulating_axes:
      socialOrient:
        cooperative: amplify
        independent: dampen
    related:
      - attachment-dismissive
      - attachment-fearful
    sources:
      - ref: "#407 spec §4.1"
        data: "Negative experiences update faster than positive (asymmetric)"
        provenance: consensus
      - ref: "Zhang et al. 2022"
        data: "r=.28 avoidance → negative mental health"
        provenance: empirical
```

### 5.2 Reinforcement Sensitivity Theory (Gray's BIS/BAS/FFFS)

```yaml
entries:
  - id: bisbas-approach-dominance
    model: bis_bas
    clinical_name: BAS-dominant approach motivation
    description: >
      Repeated reward experiences strengthen BAS approach pathways.
      Dopaminergic reward prediction error drives goal-directed action,
      positive affect, and persistence toward reward cues.
    triggers:
      - node: reward
        intensity: [0.5, 0.8]
        repetition: high
        schedule: variable_ratio
      - node: competence_recognition
        intensity: [0.4, 0.7]
        repetition: moderate
    narratives:
      - "Your efforts were frequently and unpredictably rewarded, building
         strong approach motivation."
      - "Success came often enough to keep you reaching for more."
    expected_outcomes:
      - node: approach
        direction: positive
        strength: [0.5, 0.8]
      - node: persist
        direction: positive
        strength: [0.3, 0.6]
      - node: risk_take
        direction: positive
        strength: [0.2, 0.4]
    modulating_axes:
      riskAppetite:
        bold: amplify
        conservative: dampen
    related:
      - operant-positive-reinforcement
      - bisbas-inhibition
    sources:
      - ref: "Carver & White 1994"
        data: "BAS scales predict approach behaviour"
        provenance: empirical
      - ref: "Gray & McNaughton 2000"
        data: "BAS dopaminergic RPE mechanism"
        provenance: consensus

  - id: bisbas-inhibition
    model: bis_bas
    clinical_name: BIS anxiety and behavioral inhibition
    description: >
      Goal conflict (simultaneous BAS and FFFS activation, or competing
      goals) strengthens BIS inhibition. Produces anxiety, cautious
      approach, and passive avoidance. Not fear — anxiety from unresolved
      conflict.
    triggers:
      - node: social_threat
        intensity: [0.4, 0.7]
        repetition: high
      - node: punishment
        intensity: [0.3, 0.6]
        repetition: moderate
    narratives:
      - "You often faced situations where both approach and avoidance
         seemed risky — leaving you stuck in anxious indecision."
      - "Uncertain outcomes made you cautious, always assessing risk
         before acting."
    expected_outcomes:
      - node: cautious_approach
        direction: positive
        strength: [0.4, 0.7]
      - node: withdraw
        direction: positive
        strength: [0.3, 0.5]
    modulating_axes:
      riskAppetite:
        conservative: amplify
        bold: dampen
    related:
      - bisbas-approach-dominance
      - bisbas-fffs-freeze
    sources:
      - ref: "Bijttebier et al. 2009"
        data: "BIS→anxiety g=1.21, 204 studies"
        provenance: empirical
      - ref: "Bijttebier et al. 2009"
        data: "BIS→depression g=0.99"
        provenance: empirical

  - id: bisbas-fffs-fight
    model: bis_bas
    clinical_name: FFFS fight response
    description: >
      Proximate threat with perceived capacity to overpower activates
      FFFS fight pathway. Defensive aggression — not predatory. Defensive
      distance determines fight vs flight vs freeze selection.
    triggers:
      - node: physical_threat
        intensity: [0.6, 0.9]
        repetition: moderate
      - node: dominance
        intensity: [0.3, 0.5]
        repetition: moderate
    narratives:
      - "When threatened at close range, you learned that fighting back
         was effective — the threat backed down."
      - "You discovered you had the capacity to overpower threats, and
         this response became habitual."
    expected_outcomes:
      - node: fight
        direction: positive
        strength: [0.4, 0.7]
      - node: assert
        direction: positive
        strength: [0.3, 0.5]
    modulating_axes:
      riskAppetite:
        bold: amplify
        conservative: dampen
      conflictMode:
        competitive: amplify
    related:
      - bisbas-fffs-flight
      - bisbas-fffs-freeze
      - trauma-chronic-fight
    sources:
      - ref: "Gray & McNaughton 2000"
        data: "FFFS defensive distance: proximate → fight"
        provenance: consensus

  - id: bisbas-fffs-flight
    model: bis_bas
    clinical_name: FFFS flight response
    description: >
      Intermediate-distance escapable threat activates FFFS flight pathway.
      Escape assessment gates the response — flight requires perceived
      escape route.
    triggers:
      - node: physical_threat
        intensity: [0.5, 0.8]
        repetition: moderate
      - node: unpredictable_danger
        intensity: [0.4, 0.7]
        repetition: moderate
    narratives:
      - "When threatened, escape was possible and you learned to take it
         — removing yourself from danger quickly."
      - "Running was effective. The threat couldn't follow."
    expected_outcomes:
      - node: flight
        direction: positive
        strength: [0.4, 0.7]
      - node: withdraw
        direction: positive
        strength: [0.3, 0.5]
    modulating_axes:
      autonomy:
        high: amplify
        low: dampen
    related:
      - bisbas-fffs-fight
      - bisbas-fffs-freeze
      - trauma-chronic-flight
    sources:
      - ref: "Gray & McNaughton 2000"
        data: "FFFS defensive distance: intermediate → flight"
        provenance: consensus

  - id: bisbas-fffs-freeze
    model: bis_bas
    clinical_name: FFFS freeze response
    description: >
      Distant or uncertain threat with no perceived escape activates
      FFFS freeze pathway. Immobilisation, tonic inhibition. Escape
      assessment gates: inescapable → freeze over flight.
    triggers:
      - node: unpredictable_danger
        intensity: [0.5, 0.8]
        repetition: moderate
      - node: powerlessness
        intensity: [0.5, 0.8]
        repetition: moderate
    narratives:
      - "The threat was overwhelming and there was no way out. You
         shut down — going still and silent."
      - "You learned that when escape was impossible, freezing was
         the safest option."
    expected_outcomes:
      - node: freeze
        direction: positive
        strength: [0.4, 0.7]
      - node: withdraw
        direction: positive
        strength: [0.3, 0.5]
    modulating_axes:
      autonomy:
        low: amplify
        high: dampen
      conflictMode:
        avoidant: amplify
    related:
      - bisbas-fffs-fight
      - bisbas-fffs-flight
      - trauma-chronic-freeze
    sources:
      - ref: "Gray & McNaughton 2000"
        data: "FFFS defensive distance: distant/uncertain → freeze"
        provenance: consensus

  - id: bisbas-learned-helplessness
    model: bis_bas
    clinical_name: Learned helplessness (BAS decrease)
    description: >
      Severe trauma (ACE ≥ 6/7) produces threshold BAS decrease —
      anhedonia. Lower severity does not reduce BAS. This is a
      qualitative shift, not proportional: the BAS system partially
      shuts down under extreme adversity.
    triggers:
      - node: physical_threat
        intensity: [0.8, 1.0]
        repetition: high
      - node: powerlessness
        intensity: [0.8, 1.0]
        repetition: high
      - node: punishment
        intensity: [0.7, 1.0]
        repetition: high
    narratives:
      - "Repeated overwhelming experiences where nothing you did made
         any difference left you unable to feel motivation or pleasure."
      - "The capacity for joy and drive gradually went dark — not
         sadness, but absence."
    expected_outcomes:
      - node: approach
        direction: negative
        strength: [0.4, 0.7]
      - node: withdraw
        direction: positive
        strength: [0.5, 0.8]
      - node: abandon
        direction: positive
        strength: [0.4, 0.7]
      - node: persist
        direction: negative
        strength: [0.3, 0.6]
    modulating_axes:
      riskAppetite:
        conservative: amplify
    related:
      - bisbas-inhibition
      - trauma-chronic-freeze
      - operant-learned-helplessness
    sources:
      - ref: "Miu et al. 2022"
        data: "Severe trauma (ACE ≥ 6/7) → BAS decrease, threshold effect"
        provenance: empirical
      - ref: "Bijttebier et al. 2009"
        data: "BAS → depression g=−0.21"
        provenance: empirical

  - id: bisbas-conflict-resolution
    model: bis_bas
    clinical_name: BIS decrease through conflict resolution
    description: >
      Successful resolution of goal conflict reduces BIS weight.
      The approach-avoidance conflict that drives BIS activation is
      resolved, reducing anxiety and enabling decisive action.
    triggers:
      - node: mastery
        intensity: [0.5, 0.8]
        repetition: moderate
      - node: choice_available
        intensity: [0.5, 0.7]
        repetition: moderate
    narratives:
      - "You faced a situation where both options seemed risky, but
         chose decisively and it worked out."
      - "Successfully navigating ambiguous situations taught you that
         uncertainty doesn't require paralysis."
    expected_outcomes:
      - node: cautious_approach
        direction: negative
        strength: [0.2, 0.4]
      - node: approach
        direction: positive
        strength: [0.3, 0.5]
      - node: self_direct
        direction: positive
        strength: [0.2, 0.4]
    related:
      - bisbas-inhibition
      - bandura-mastery-efficacy
    sources:
      - ref: "#407 spec §4.2"
        data: "Conflict resolution success → BIS weight decrease"
        provenance: consensus
```

### 5.3 Cognitive Model (Beck's CBT)

```yaml
entries:
  - id: cbt-negative-self
    model: cbt
    clinical_name: Negative self-schema
    description: >
      Core belief "I am worthless/incompetent/helpless" activated by
      failure or criticism. Part of Beck's cognitive triad. Self-
      reinforcing through selective attention to confirming evidence.
    triggers:
      - node: failure
        intensity: [0.5, 0.8]
        repetition: high
      - node: rejection
        intensity: [0.4, 0.7]
        repetition: moderate
      - node: performance_judged
        intensity: [0.5, 0.8]
        repetition: high
    narratives:
      - "Repeated experiences of falling short convinced you at a deep
         level that you are fundamentally inadequate."
      - "Criticism landed harder than praise — each failure confirmed
         what you already suspected about yourself."
    expected_outcomes:
      - node: self_worth
        direction: negative
        strength: [0.4, 0.7]
      - node: withdraw
        direction: positive
        strength: [0.3, 0.6]
      - node: abandon
        direction: positive
        strength: [0.2, 0.5]
      - node: depend
        direction: positive
        strength: [0.2, 0.4]
    modulating_axes:
      socialOrient:
        cooperative: amplify
    related:
      - cbt-negative-other
      - cbt-negative-world
      - attachment-anxious
      - bandura-efficacy-gate
    sources:
      - ref: "Beck & Haigh 2014"
        data: "Generic cognitive model: schema activation continuum"
        provenance: empirical
      - ref: "Hofmann et al. 2012"
        data: "CBT vs controls (anxiety) g=0.51"
        provenance: empirical

  - id: cbt-negative-other
    model: cbt
    clinical_name: Negative other-schema
    description: >
      Core belief "Others are hostile/unreliable/judgmental." Activated
      by interpersonal threat or betrayal. Produces distrust, social
      withdrawal, and hypervigilance to social cues.
    triggers:
      - node: rejection
        intensity: [0.5, 0.8]
        repetition: high
      - node: betrayal
        intensity: [0.5, 0.8]
        repetition: moderate
      - node: exclusion
        intensity: [0.4, 0.7]
        repetition: moderate
    narratives:
      - "People consistently let you down, criticised you unfairly, or
         turned hostile without warning."
      - "You learned to expect the worst from others because that
         expectation was so often confirmed."
    expected_outcomes:
      - node: other_reliability
        direction: negative
        strength: [0.3, 0.6]
      - node: other_safety
        direction: negative
        strength: [0.3, 0.6]
      - node: distrust
        direction: positive
        strength: [0.4, 0.7]
      - node: distance_maintain
        direction: positive
        strength: [0.3, 0.6]
    modulating_axes:
      socialOrient:
        independent: amplify
    related:
      - cbt-negative-self
      - attachment-dismissive
      - attachment-trust-erosion
    sources:
      - ref: "Beck & Haigh 2014"
        data: "Cognitive triad: self, others, world"
        provenance: empirical
      - ref: "Collins & Read 1990"
        data: "Hierarchical IWM = schema hierarchy"
        provenance: consensus

  - id: cbt-negative-world
    model: cbt
    clinical_name: Hopelessness schema (negative world/future)
    description: >
      Core beliefs "The world is dangerous/unfair" and "The future is
      hopeless." Produces behavioural withdrawal, abandonment of goals,
      and loss of persistence. Strongest predictor of suicidal ideation
      among the cognitive triad elements.
    triggers:
      - node: failure
        intensity: [0.5, 0.8]
        repetition: high
      - node: unpredictable_danger
        intensity: [0.4, 0.7]
        repetition: moderate
      - node: scarcity
        intensity: [0.4, 0.7]
        repetition: moderate
    narratives:
      - "No matter what you tried, the world seemed stacked against you
         — unfair and unpredictable."
      - "The future felt like more of the same, with no path to anything
         better."
    expected_outcomes:
      - node: world_predictability
        direction: negative
        strength: [0.4, 0.7]
      - node: future_outlook
        direction: negative
        strength: [0.4, 0.7]
      - node: withdraw
        direction: positive
        strength: [0.4, 0.7]
      - node: abandon
        direction: positive
        strength: [0.4, 0.7]
      - node: persist
        direction: negative
        strength: [0.4, 0.7]
    related:
      - cbt-negative-self
      - bisbas-learned-helplessness
      - trauma-kindling
    sources:
      - ref: "Beck & Haigh 2014"
        data: "Cognitive triad: world/future pole"
        provenance: empirical
      - ref: "#407 spec §4.3"
        data: "future_outlook → persist weight 0.40"
        provenance: consensus

  - id: cbt-positive-self
    model: cbt
    clinical_name: Positive self-schema (resilience)
    description: >
      Core belief "I am worthy/competent/capable" built through
      consistent mastery experiences and positive recognition. Provides
      cognitive resilience — buffers against schema activation by
      negative events.
    triggers:
      - node: mastery
        intensity: [0.5, 0.8]
        repetition: high
        schedule: continuous
      - node: competence_recognition
        intensity: [0.5, 0.7]
        repetition: moderate
      - node: acceptance
        intensity: [0.4, 0.7]
        repetition: moderate
    narratives:
      - "Consistent experiences of succeeding at things that mattered
         built a deep sense of your own competence."
      - "People around you reliably recognised your contributions,
         reinforcing your sense of worth."
    expected_outcomes:
      - node: self_worth
        direction: positive
        strength: [0.4, 0.7]
      - node: persist
        direction: positive
        strength: [0.4, 0.7]
      - node: approach
        direction: positive
        strength: [0.3, 0.5]
      - node: self_direct
        direction: positive
        strength: [0.3, 0.5]
    modulating_axes:
      socialOrient:
        competitive: amplify
    related:
      - cbt-negative-self
      - bandura-mastery-efficacy
      - attachment-secure
    sources:
      - ref: "Beck & Haigh 2014"
        data: "Continuity of adaptive and maladaptive function"
        provenance: empirical

  - id: cbt-catastrophising
    model: cbt
    clinical_name: Catastrophising distortion pattern
    description: >
      Cognitive distortion that amplifies negative predictions (×1.5-2.0
      weight multiplier on threat pathways). Activated when parent schema
      exceeds threshold. Compounds with trauma sensitisation through the
      shared threat_sensitivity node.
    triggers:
      - node: unpredictable_danger
        intensity: [0.5, 0.8]
        repetition: high
      - node: failure
        intensity: [0.5, 0.8]
        repetition: high
    narratives:
      - "Minor setbacks felt like catastrophes — your mind always went
         to the worst possible outcome."
      - "A small sign of trouble triggered an avalanche of worst-case
         thinking that felt completely real."
    expected_outcomes:
      - node: withdraw
        direction: positive
        strength: [0.4, 0.7]
      - node: freeze
        direction: positive
        strength: [0.2, 0.5]
    modulating_axes:
      riskAppetite:
        conservative: amplify
        bold: dampen
    related:
      - cbt-overgeneralisation
      - cbt-schema-rigidity
      - trauma-kindling
    sources:
      - ref: "#407 spec §4.3"
        data: "Catastrophizing multiplier ×1.5-2.0, base threshold 0.7"
        provenance: consensus
      - ref: "Beck & Haigh 2014"
        data: "Cognitive distortions as weight multipliers"
        provenance: empirical

  - id: cbt-overgeneralisation
    model: cbt
    clinical_name: Overgeneralisation distortion pattern
    description: >
      Cognitive distortion that increases schema permeability — a single
      negative event activates schemas across unrelated domains (+0.2-0.4
      connection weight to previously unconnected inputs). Broadens the
      triggers for existing negative schemas.
    triggers:
      - node: failure
        intensity: [0.5, 0.8]
        repetition: high
      - node: rejection
        intensity: [0.5, 0.8]
        repetition: moderate
    narratives:
      - "One failure in one area spread to colour everything — if you
         failed at this, you must be failing at everything."
      - "A single rejection felt like proof of a universal pattern."
    expected_outcomes:
      - node: withdraw
        direction: positive
        strength: [0.3, 0.6]
      - node: abandon
        direction: positive
        strength: [0.3, 0.5]
    related:
      - cbt-catastrophising
      - cbt-negative-self
    sources:
      - ref: "#407 spec §4.3"
        data: "Overgeneralization +0.2-0.4 to unrelated inputs, base threshold 0.5"
        provenance: consensus

  - id: cbt-schema-rigidity
    model: cbt
    clinical_name: Schema rigidity (cognitive inflexibility)
    description: >
      All-or-nothing thinking pattern (×1.5-2.0 multiplier amplifying
      extreme poles). Produces rigid, binary evaluation. Disposition-
      dependent: ruleFollowing=strict amplifies "should" distortions
      and schema rigidity.
    triggers:
      - node: performance_judged
        intensity: [0.5, 0.8]
        repetition: high
      - node: controlled
        intensity: [0.4, 0.7]
        repetition: moderate
    narratives:
      - "Things were either perfect or complete failures — there was
         no acceptable middle ground."
      - "Rules and standards were absolute. Partial success was
         indistinguishable from failure."
    expected_outcomes:
      - node: perfectionism
        direction: positive
        strength: [0.4, 0.7]
      - node: comply
        direction: positive
        strength: [0.3, 0.5]
    modulating_axes:
      ruleFollowing:
        strict: amplify
        flexible: dampen
    related:
      - cbt-catastrophising
      - cbt-sudden-gains
    sources:
      - ref: "#407 spec §4.3"
        data: "All-or-nothing multiplier ×1.5-2.0, base threshold 0.6"
        provenance: consensus
      - ref: "#407 spec §3.3"
        data: "ruleFollowing=strict → schema_rigidity ×1.3"
        provenance: consensus

  - id: cbt-sudden-gains
    model: cbt
    clinical_name: Sudden gains (schema phase transition)
    description: >
      Accumulated disconfirming evidence reaches tipping point where
      negative schema flips to competing positive attractor state.
      Occurs in 40-50% of depression cases. Smith et al.: most effective
      change induces uncertainty first (weakens schema precision) before
      strengthening competitor.
    triggers:
      - node: mastery
        intensity: [0.5, 0.8]
        repetition: high
        schedule: continuous
      - node: acceptance
        intensity: [0.5, 0.7]
        repetition: high
    narratives:
      - "After many corrective experiences, something shifted — the old
         negative belief suddenly lost its hold."
      - "It wasn't gradual. One day the evidence just outweighed the
         schema, and you saw yourself differently."
    expected_outcomes:
      - node: approach
        direction: positive
        strength: [0.3, 0.6]
      - node: persist
        direction: positive
        strength: [0.3, 0.6]
    related:
      - cbt-negative-self
      - cbt-positive-self
      - attachment-earned-security
    sources:
      - ref: "Aderka et al. 2012"
        data: "Sudden gains g=0.61-0.68, frequency 40-50% of patients"
        provenance: empirical
      - ref: "Smith et al. 2021"
        data: "Most effective change induces uncertainty (P=0.5) first"
        provenance: empirical

  - id: cbt-positive-other
    model: cbt
    clinical_name: Positive other-schema
    description: >
      Core belief "Others are generally trustworthy, supportive, and
      well-intentioned." Part of Beck's cognitive triad — the positive
      pole of the other dimension. Produces social approach, trust, and
      comfort with interpersonal dependence.
    triggers:
      - node: acceptance
        intensity: [0.5, 0.8]
        repetition: high
        schedule: continuous
      - node: secure_attachment
        intensity: [0.4, 0.7]
        repetition: moderate
    narratives:
      - "People around you were consistently kind, reliable, and
         supportive — teaching you that others can be trusted."
      - "Positive social experiences built a deep expectation that
         people are fundamentally well-intentioned."
    expected_outcomes:
      - node: other_reliability
        direction: positive
        strength: [0.4, 0.7]
      - node: other_safety
        direction: positive
        strength: [0.3, 0.6]
      - node: trust
        direction: positive
        strength: [0.4, 0.7]
      - node: proximity_seek
        direction: positive
        strength: [0.3, 0.5]
    modulating_axes:
      socialOrient:
        cooperative: amplify
    related:
      - cbt-negative-other
      - attachment-secure
      - cbt-positive-self
    sources:
      - ref: "Beck & Haigh 2014"
        data: "Cognitive triad: continuity of adaptive and maladaptive function"
        provenance: empirical
      - ref: "Collins & Read 1990"
        data: "Adult attachment and cognitive schema hierarchies"
        provenance: empirical

  - id: cbt-positive-world
    model: cbt
    clinical_name: Positive world/future schema (optimism)
    description: >
      Core beliefs "The world is generally fair and manageable" and
      "The future holds positive possibilities." Part of Beck's
      cognitive triad — the positive pole of the world/future dimension.
      Produces persistence, goal-directed action, and resilience.
    triggers:
      - node: mastery
        intensity: [0.5, 0.8]
        repetition: high
        schedule: continuous
      - node: reward
        intensity: [0.4, 0.7]
        repetition: moderate
    narratives:
      - "The world consistently made sense — effort led to outcomes,
         and fairness was the norm rather than the exception."
      - "You grew up expecting the future to hold good things, because
         past experience confirmed this over and over."
    expected_outcomes:
      - node: world_predictability
        direction: positive
        strength: [0.4, 0.7]
      - node: future_outlook
        direction: positive
        strength: [0.4, 0.7]
      - node: persist
        direction: positive
        strength: [0.4, 0.7]
      - node: approach
        direction: positive
        strength: [0.3, 0.6]
      - node: explore
        direction: positive
        strength: [0.3, 0.5]
    related:
      - cbt-negative-world
      - cbt-positive-self
      - operant-positive-reinforcement
    sources:
      - ref: "Beck & Haigh 2014"
        data: "Cognitive triad: world/future pole — adaptive function"
        provenance: empirical
      - ref: "Seligman 2006"
        data: "Learned optimism: explanatory style shapes future expectations"
        provenance: empirical
```

### 5.4 Trauma Response Models

```yaml
entries:
  - id: trauma-chronic-fight
    model: trauma
    clinical_name: Chronic fight response (Walker 4F)
    description: >
      Proximate threat with perceived capacity to overpower crystallises
      into chronic controlling, narcissistic defenses. Fight is the
      primary adaptive strategy — aggression as safety-seeking.
    triggers:
      - node: physical_threat
        intensity: [0.6, 0.9]
        repetition: high
      - node: dominance
        intensity: [0.3, 0.6]
        repetition: moderate
    narratives:
      - "The only way to be safe was to be the most powerful person
         in the room."
      - "You learned early that aggression worked — threats backed
         down when you pushed back harder."
    expected_outcomes:
      - node: fight
        direction: positive
        strength: [0.5, 0.8]
      - node: assert
        direction: positive
        strength: [0.4, 0.6]
      - node: distrust
        direction: positive
        strength: [0.3, 0.5]
    modulating_axes:
      riskAppetite:
        bold: amplify
      conflictMode:
        competitive: amplify
    related:
      - bisbas-fffs-fight
      - trauma-chronic-fawn
    sources:
      - ref: "Walker 2013"
        data: "4F model: fight → controlling, narcissistic defenses"
        provenance: consensus

  - id: trauma-chronic-flight
    model: trauma
    clinical_name: Chronic flight response (Walker 4F)
    description: >
      Escapable, moderate-intensity threat crystallises into workaholism
      and compulsive busyness. Flight from internal distress manifests
      as relentless activity to avoid sitting with difficult feelings.
    triggers:
      - node: psychological_threat
        intensity: [0.4, 0.7]
        repetition: high
      - node: unpredictable_danger
        intensity: [0.4, 0.7]
        repetition: moderate
    narratives:
      - "Staying busy was how you stayed safe — if you stopped, the
         anxiety would catch up."
      - "You fled from feelings the same way you fled from danger —
         by never standing still."
    expected_outcomes:
      - node: flight
        direction: positive
        strength: [0.5, 0.7]
      - node: persist
        direction: positive
        strength: [0.3, 0.5]
      - node: perfectionism
        direction: positive
        strength: [0.3, 0.5]
    modulating_axes:
      riskAppetite:
        conservative: amplify
      ruleFollowing:
        strict: amplify
    related:
      - bisbas-fffs-flight
      - trauma-chronic-freeze
    sources:
      - ref: "Walker 2013"
        data: "4F model: flight → workaholism, compulsive busyness"
        provenance: consensus

  - id: trauma-chronic-freeze
    model: trauma
    clinical_name: Chronic freeze response (Walker 4F)
    description: >
      Inescapable, overwhelming threat with no social recourse produces
      dissociation and learned helplessness. Dorsal vagal shutdown
      (polyvagal hierarchy tier 3). Chronic pattern: emotional numbing,
      detachment from reality.
    triggers:
      - node: physical_threat
        intensity: [0.7, 1.0]
        repetition: high
      - node: powerlessness
        intensity: [0.7, 1.0]
        repetition: high
    narratives:
      - "There was no fighting and no running. You shut down — went
         somewhere else inside your head."
      - "The world became distant and unreal. Feeling nothing was safer
         than feeling everything."
    expected_outcomes:
      - node: freeze
        direction: positive
        strength: [0.5, 0.8]
      - node: withdraw
        direction: positive
        strength: [0.4, 0.7]
      - node: abandon
        direction: positive
        strength: [0.3, 0.6]
    modulating_axes:
      autonomy:
        low: amplify
      conflictMode:
        avoidant: amplify
    related:
      - bisbas-fffs-freeze
      - bisbas-learned-helplessness
      - trauma-dissociation
    sources:
      - ref: "Walker 2013"
        data: "4F model: freeze → dissociation, learned helplessness"
        provenance: consensus
      - ref: "Porges 2022"
        data: "Dorsal vagal immobilization (polyvagal tier 3)"
        provenance: consensus

  - id: trauma-chronic-fawn
    model: trauma
    clinical_name: Chronic fawn response (Walker 4F)
    description: >
      Threat from attachment figure — the person you depend on is also
      the source of danger. Fawn is BIS-mediated conflict resolution
      co-opting BAS social-approach: people-pleasing as safety strategy.
      Unique to attachment-based trauma.
    triggers:
      - node: physical_threat
        intensity: [0.5, 0.8]
        repetition: high
      - node: powerlessness
        intensity: [0.5, 0.8]
        repetition: high
      - node: inconsistent_care
        intensity: [0.5, 0.8]
        repetition: high
    narratives:
      - "The person who hurt you was also the person you needed. You
         learned to make them happy to stay safe."
      - "Reading the other person's mood became a survival skill —
         anticipating what they wanted before they demanded it."
    expected_outcomes:
      - node: fawn
        direction: positive
        strength: [0.5, 0.8]
      - node: submit
        direction: positive
        strength: [0.4, 0.6]
      - node: comply
        direction: positive
        strength: [0.3, 0.5]
      - node: depend
        direction: positive
        strength: [0.3, 0.5]
    modulating_axes:
      socialOrient:
        cooperative: amplify
      conflictMode:
        cooperative: amplify
    related:
      - attachment-fearful
      - trauma-chronic-freeze
    sources:
      - ref: "Walker 2013"
        data: "4F model: fawn → people-pleasing, codependency"
        provenance: consensus
      - ref: "#407 spec §4.7"
        data: "Fawn is BIS-mediated, co-opting BAS social-approach"
        provenance: consensus

  - id: trauma-dissociation
    model: trauma
    clinical_name: Dissociation under overwhelming threat
    description: >
      Extreme dorsal vagal shutdown beyond freeze — psychological
      disconnection from experience. The world becomes unreal
      (derealisation) or the self feels detached from the body
      (depersonalisation). Polyvagal tier 3 at maximum intensity.
      Distinct from freeze: freeze is motor immobilisation with
      awareness; dissociation is loss of integrated awareness itself.
    triggers:
      - node: physical_threat
        intensity: [0.8, 1.0]
        repetition: high
      - node: psychological_threat
        intensity: [0.8, 1.0]
        repetition: high
      - node: powerlessness
        intensity: [0.8, 1.0]
        repetition: high
    narratives:
      - "The world went flat and distant, like watching yourself from
         outside. Nothing felt real — not even your own body."
      - "You learned to leave without leaving. When the pain was too
         much, you simply stopped being there."
    expected_outcomes:
      - node: freeze
        direction: positive
        strength: [0.6, 0.9]
      - node: withdraw
        direction: positive
        strength: [0.6, 0.8]
      - node: distance_maintain
        direction: positive
        strength: [0.4, 0.7]
    modulating_axes:
      autonomy:
        low: amplify
      conflictMode:
        avoidant: amplify
    related:
      - trauma-chronic-freeze
      - trauma-window-narrowing
    sources:
      - ref: "Porges 2022"
        data: "Dorsal vagal immobilisation — polyvagal tier 3 maximum"
        provenance: consensus
      - ref: "Siegel 1999"
        data: "Window of tolerance: hypoarousal → dissociation"
        provenance: consensus
      - ref: "Walker 2013"
        data: "Freeze/dissociation distinction in complex PTSD"
        provenance: consensus

  - id: trauma-kindling
    model: trauma
    clinical_name: Trauma sensitisation (kindling model)
    description: >
      Repeated sub-threshold stimulation progressively lowers activation
      thresholds. Dose-response is multiplicative: each additional ACE
      multiplies risk. Less intense triggers produce greater responses
      over time. Threshold lowering is distinct from weight increase.
    triggers:
      - node: physical_threat
        intensity: [0.5, 0.9]
        repetition: high
      - node: psychological_threat
        intensity: [0.4, 0.8]
        repetition: high
    narratives:
      - "Each stressful event made you more sensitive to the next. Small
         triggers started producing outsized reactions."
      - "Your alarm system was recalibrated by repeated activation —
         it now fires at lower and lower thresholds."
    expected_outcomes:
      - node: freeze
        direction: positive
        strength: [0.3, 0.6]
      - node: flight
        direction: positive
        strength: [0.3, 0.6]
    related:
      - trauma-window-narrowing
      - trauma-hypervigilance
      - cbt-catastrophising
    sources:
      - ref: "Felitti et al. 1998"
        data: "ACE 4+ OR=4.6 depressed mood, OR=12.2 suicide attempt"
        provenance: empirical
      - ref: "Dube et al. 2001"
        data: "ACE 7+ OR=31.1 suicide attempt"
        provenance: empirical
      - ref: "#407 spec §4.4"
        data: "Kindling: episode 1→50%, episode 2→70%, episode 3→90% recurrence"
        provenance: empirical

  - id: trauma-window-narrowing
    model: trauma
    clinical_name: Window of tolerance narrowing
    description: >
      Trauma narrows Siegel's window of tolerance — the activation range
      within which flexible response selection is possible. Above:
      hyperarousal (fight/flight). Below: hypoarousal (freeze/dissociation).
      Recovery widens the window through active safety-building.
    triggers:
      - node: physical_threat
        intensity: [0.6, 0.9]
        repetition: high
      - node: unpredictable_danger
        intensity: [0.5, 0.8]
        repetition: high
    narratives:
      - "Your emotional range narrowed — you were either overwhelmed
         or shut down, with little room in between."
      - "The zone of calm, flexible functioning got smaller and smaller
         with each traumatic experience."
    expected_outcomes:
      - node: fight
        direction: positive
        strength: [0.2, 0.4]
      - node: flight
        direction: positive
        strength: [0.3, 0.5]
      - node: freeze
        direction: positive
        strength: [0.3, 0.5]
    related:
      - trauma-kindling
      - trauma-hypervigilance
    sources:
      - ref: "Siegel 1999"
        data: "Window of tolerance model"
        provenance: consensus
      - ref: "#407 spec §4.4"
        data: "Window IS the activation threshold range on arousal node"
        provenance: consensus

  - id: trauma-hypervigilance
    model: trauma
    clinical_name: Trauma-induced hypervigilance
    description: >
      Elevated threat_sensitivity from accumulated trauma experiences
      produces chronic scanning for danger. Neuroception (subconscious
      threat evaluation) biased by trauma history — false positives
      increase.
    triggers:
      - node: physical_threat
        intensity: [0.5, 0.8]
        repetition: high
      - node: social_threat
        intensity: [0.4, 0.7]
        repetition: moderate
      - node: unpredictable_danger
        intensity: [0.5, 0.8]
        repetition: moderate
    narratives:
      - "You learned to scan every room for exits, watch every face
         for signs of hostility."
      - "Your threat detector became hypersensitive — seeing danger
         in situations others found neutral."
    expected_outcomes:
      - node: cautious_approach
        direction: positive
        strength: [0.4, 0.7]
      - node: distrust
        direction: positive
        strength: [0.3, 0.6]
      - node: withdraw
        direction: positive
        strength: [0.2, 0.5]
    modulating_axes:
      riskAppetite:
        conservative: amplify
        bold: dampen
    related:
      - trauma-kindling
      - bisbas-inhibition
      - cbt-catastrophising
    sources:
      - ref: "Porges 2022"
        data: "Neuroception: subconscious threat evaluation"
        provenance: consensus
      - ref: "Hughes et al. 2017"
        data: "ACE 4+ resilience 29%→14% illness with resources"
        provenance: empirical
```

### 5.5 Operant Conditioning

```yaml
entries:
  - id: operant-positive-reinforcement
    model: operant
    clinical_name: Positive reinforcement approach pattern
    description: >
      Consistent reward following a behaviour strengthens behaviour→outcome
      connections. The Rescorla-Wagner prediction error rule drives learning:
      weight change proportional to surprise (outcome minus prediction).
      Continuous reinforcement produces fastest attractor formation but
      lowest extinction resistance.
    triggers:
      - node: reward
        intensity: [0.5, 0.8]
        repetition: high
        schedule: continuous
    narratives:
      - "Your actions reliably produced good outcomes — effort was
         rewarded, and you learned to expect it."
      - "The connection between what you did and what you got was
         clear and consistent."
    expected_outcomes:
      - node: approach
        direction: positive
        strength: [0.4, 0.7]
      - node: persist
        direction: positive
        strength: [0.3, 0.6]
    related:
      - bisbas-approach-dominance
      - operant-vr-extinction-resistance
    sources:
      - ref: "Rescorla & Wagner 1972"
        data: "ΔW = α×β×(λ−ΣW), learning rate α×β = 0.01-0.25"
        provenance: empirical

  - id: operant-punishment-avoidance
    model: operant
    clinical_name: Punishment-based avoidance
    description: >
      Aversive consequences following a behaviour strengthen avoidance
      connections. Produces behavioural suppression and withdrawal from
      situations associated with punishment. Negative reinforcement
      (aversive removal) strengthens escape behaviours.
    triggers:
      - node: punishment
        intensity: [0.5, 0.8]
        repetition: high
      - node: social_threat
        intensity: [0.3, 0.6]
        repetition: moderate
    narratives:
      - "Actions that once seemed safe started having painful
         consequences, and you learned to avoid them."
      - "The safest strategy was inaction — doing nothing couldn't
         be punished."
    expected_outcomes:
      - node: withdraw
        direction: positive
        strength: [0.4, 0.7]
      - node: comply
        direction: positive
        strength: [0.3, 0.5]
      - node: submit
        direction: positive
        strength: [0.2, 0.4]
    related:
      - bisbas-inhibition
      - operant-learned-helplessness
    sources:
      - ref: "Rescorla & Wagner 1972"
        data: "Positive punishment: λ<0, weaken behaviour"
        provenance: empirical

  - id: operant-vr-extinction-resistance
    model: operant
    clinical_name: Variable-ratio extinction resistance
    description: >
      Variable-ratio reinforcement produces the highest extinction
      resistance (2-4× continuous reinforcement). Connections formed
      under VR schedules have the highest weight stability and lowest
      decay rate. This is the mechanical basis for why anxious
      attachment is so change-resistant.
    triggers:
      - node: reward
        intensity: [0.4, 0.7]
        repetition: high
        schedule: variable_ratio
    narratives:
      - "Rewards came unpredictably — sometimes after two tries,
         sometimes after twenty. You never stopped trying because
         the next attempt might pay off."
      - "The inconsistency itself became the hook — the uncertainty
         drove relentless engagement."
    expected_outcomes:
      - node: persist
        direction: positive
        strength: [0.5, 0.8]
      - node: approach
        direction: positive
        strength: [0.4, 0.6]
    related:
      - attachment-anxious
      - operant-positive-reinforcement
    sources:
      - ref: "Nevin & Grace 2000"
        data: "VR extinction resistance 2-4× CRF (behavioral momentum)"
        provenance: empirical
      - ref: "#407 spec §4.5"
        data: "schedule_modifier: variable_ratio=3.0"
        provenance: empirical

  - id: operant-learned-helplessness
    model: operant
    clinical_name: Learned helplessness (uncontrollable aversive)
    description: >
      Repeated uncontrollable aversive experiences produce failure of
      instrumental learning — the organism stops attempting escape or
      avoidance because outcomes appear independent of behaviour.
      Strengthens freeze/withdraw, weakens approach/persist.
    triggers:
      - node: punishment
        intensity: [0.6, 0.9]
        repetition: high
      - node: powerlessness
        intensity: [0.7, 1.0]
        repetition: high
    narratives:
      - "Nothing you did changed the outcome. Effort and passivity
         produced the same result."
      - "You stopped trying — not from laziness, but from the
         rational conclusion that trying was pointless."
    expected_outcomes:
      - node: withdraw
        direction: positive
        strength: [0.5, 0.8]
      - node: abandon
        direction: positive
        strength: [0.5, 0.8]
      - node: approach
        direction: negative
        strength: [0.3, 0.6]
      - node: persist
        direction: negative
        strength: [0.4, 0.7]
    related:
      - bisbas-learned-helplessness
      - trauma-chronic-freeze
    sources:
      - ref: "Seligman 1975"
        data: "Learned helplessness: uncontrollable aversive → motivational deficit"
        provenance: empirical
      - ref: "#407 spec §4.2"
        data: "Repeated uncontrollable threat → FFFS freeze weights increase"
        provenance: consensus

  - id: operant-extinction-recovery
    model: operant
    clinical_name: Extinction with spontaneous recovery
    description: >
      Extinction creates a competing inhibitory overlay, not erasure of
      the original weight. The inhibitory overlay decays faster than the
      excitatory weight — producing spontaneous recovery, renewal, and
      reinstatement. Dual-weight architecture per connection.
    triggers:
      - node: reward_removal
        intensity: [0.5, 0.8]
        repetition: moderate
    narratives:
      - "The behaviour that used to be rewarded stopped paying off. You
         stopped doing it — but the urge never fully went away."
      - "Months later, in a similar situation, the old behaviour
         resurfaced as if it had never been extinguished."
    expected_outcomes:
      - node: approach
        direction: negative
        strength: [0.3, 0.5]
      - node: discriminate
        direction: positive
        strength: [0.2, 0.4]
    related:
      - operant-positive-reinforcement
      - operant-vr-extinction-resistance
    sources:
      - ref: "#407 spec §5.2"
        data: "Dual-weight architecture: excitatory + inhibitory overlay"
        provenance: consensus
      - ref: "Rescorla & Wagner 1972"
        data: "Extinction as inhibitory learning, not unlearning"
        provenance: empirical

  - id: operant-negative-reinforcement
    model: operant
    clinical_name: Negative reinforcement escape/avoidance pattern
    description: >
      Removal of an aversive stimulus following a behaviour strengthens
      that behaviour (negative reinforcement). Produces escape and
      avoidance responses. Mowrer's two-factor theory: fear conditioning
      (classical) + escape learning (operant). One of four fundamental
      operant quadrants — distinct from punishment-based avoidance.
    triggers:
      - node: aversive_removal
        intensity: [0.5, 0.8]
        repetition: high
      - node: punishment
        intensity: [0.4, 0.7]
        repetition: moderate
    narratives:
      - "You discovered that certain actions could make the bad things
         stop — and that discovery shaped everything."
      - "Running, avoiding, escaping — whatever removed the discomfort
         became your go-to strategy."
    expected_outcomes:
      - node: flight
        direction: positive
        strength: [0.3, 0.6]
      - node: withdraw
        direction: positive
        strength: [0.3, 0.5]
    related:
      - operant-punishment-avoidance
      - bisbas-fffs-flight
    sources:
      - ref: "Mowrer 1947"
        data: "Two-factor theory: fear conditioning + instrumental escape"
        provenance: empirical
      - ref: "Rescorla & Wagner 1972"
        data: "Negative reinforcement: aversive removal strengthens escape"
        provenance: empirical

  - id: operant-autonomy-reinforcement
    model: operant
    clinical_name: Autonomy-contingent reinforcement
    description: >
      Positive outcomes contingent on autonomous action strengthen
      self-directed behaviour. Autonomy support (agency granted) paired
      with successful outcomes builds intrinsic motivation and
      exploration. Integrates Deci & Ryan's self-determination theory
      with operant contingency learning.
    triggers:
      - node: agency_granted
        intensity: [0.5, 0.8]
        repetition: high
        schedule: continuous
      - node: reward
        intensity: [0.4, 0.7]
        repetition: moderate
    narratives:
      - "When you were given freedom to choose your own path, good
         things followed — reinforcing your instinct to act independently."
      - "Autonomy was rewarded, not punished. You learned that your own
         judgment could be trusted."
    expected_outcomes:
      - node: self_direct
        direction: positive
        strength: [0.4, 0.7]
      - node: explore
        direction: positive
        strength: [0.3, 0.6]
      - node: rebel
        direction: positive
        strength: [0.2, 0.4]
    developmental_period: childhood
    related:
      - operant-positive-reinforcement
      - bandura-mastery-efficacy
    sources:
      - ref: "Deci & Ryan 2000"
        data: "SDT: autonomy support → intrinsic motivation, well-being"
        provenance: empirical
      - ref: "Grolnick & Ryan 1989"
        data: "Parental autonomy support → self-regulation in children"
        provenance: empirical
```

### 5.6 Social Learning Theory (Bandura)

```yaml
entries:
  - id: bandura-mastery-efficacy
    model: bandura
    clinical_name: Mastery-based self-efficacy
    description: >
      Direct mastery experience is the strongest source of self-efficacy
      (relative weight 1.0). Domain-specific: effect sizes are 2× larger
      for domain-specific vs general self-efficacy measures. Successful
      performance → self-efficacy → increased future attempt probability.
      Recurrent feedback loop native to CAPS.
    triggers:
      - node: mastery
        intensity: [0.5, 0.8]
        repetition: high
        schedule: continuous
      - node: competence_recognition
        intensity: [0.4, 0.7]
        repetition: moderate
    narratives:
      - "You attempted something difficult and succeeded — then did it
         again, and again. Each success built confidence in your own
         capability."
      - "The proof was in your own hands. You knew you could do it
         because you had already done it."
    expected_outcomes:
      - node: self_efficacy
        direction: positive
        strength: [0.5, 0.8]
      - node: persist
        direction: positive
        strength: [0.5, 0.8]
      - node: approach
        direction: positive
        strength: [0.4, 0.6]
      - node: risk_take
        direction: positive
        strength: [0.2, 0.4]
      - node: explore
        direction: positive
        strength: [0.3, 0.5]
    related:
      - cbt-positive-self
      - bandura-efficacy-gate
      - bandura-observational-learning
    sources:
      - ref: "Multon et al. 1991"
        data: "Self-efficacy → performance ~14% variance"
        provenance: empirical
      - ref: "Sitzmann & Ely 2011"
        data: "Domain-specific vs general SE: 2× effect"
        provenance: empirical
      - ref: "Bandura 1997"
        data: "Mastery experience: relative weight 1.0 (reference)"
        provenance: empirical

  - id: bandura-vicarious-fear
    model: bandura
    clinical_name: Vicarious fear acquisition
    description: >
      Observing a model experience aversive consequences produces fear
      learning at 15-25% the strength of direct experience. Effects
      are temporary without subsequent direct reinforcement. Model
      similarity modulates strength.
    triggers:
      - node: modeling_observed
        intensity: [0.5, 0.8]
        repetition: moderate
      - node: social_threat
        intensity: [0.3, 0.6]
        repetition: moderate
    narratives:
      - "You watched someone else get hurt or punished for something,
         and learned to fear it yourself — without ever experiencing
         it directly."
      - "Seeing someone you identified with suffer was enough to teach
         you that situation was dangerous."
    expected_outcomes:
      - node: withdraw
        direction: positive
        strength: [0.2, 0.4]
      - node: cautious_approach
        direction: positive
        strength: [0.2, 0.4]
    related:
      - bandura-observational-learning
      - bisbas-inhibition
    sources:
      - ref: "Ollendick et al. 1983"
        data: "Vicarious reinforcement durability studies"
        provenance: empirical
      - ref: "#407 spec §4.6"
        data: "Vicarious 15-25% of direct, β_vicarious ≈ 0.3-0.5"
        provenance: empirical

  - id: bandura-observational-learning
    model: bandura
    clinical_name: Observational learning chain
    description: >
      Four-stage learning from observation: attention (model salience) →
      retention (behavior_schema) → reproduction (gated by self-efficacy)
      → motivation (vicarious/direct reinforcement). Successful
      reproduction strengthens self-efficacy in a positive feedback loop.
    triggers:
      - node: modeling_observed
        intensity: [0.5, 0.8]
        repetition: high
      - node: acceptance
        intensity: [0.3, 0.6]
        repetition: moderate
    narratives:
      - "You watched someone you admired handle a situation skillfully,
         and absorbed the pattern — later finding you could do it too."
      - "Learning came from observation first, then practice confirmed
         you had picked up the skill."
    expected_outcomes:
      - node: approach
        direction: positive
        strength: [0.2, 0.4]
      - node: persist
        direction: positive
        strength: [0.2, 0.4]
      - node: generalize
        direction: positive
        strength: [0.2, 0.4]
    related:
      - bandura-mastery-efficacy
      - bandura-vicarious-fear
    sources:
      - ref: "Bandura 1977"
        data: "Social Learning Theory: attention→retention→reproduction→motivation"
        provenance: empirical
      - ref: "Mozahem 2022"
        data: "Agent-based reciprocal determinism simulation"
        provenance: empirical

  - id: bandura-model-imitation
    model: bandura
    clinical_name: Model-based imitation
    description: >
      Learning behaviour patterns from high-salience models (high status,
      similarity, warmth). Vicarious reinforcement at 15-25% direct
      strength. Model_attention is modulated by model characteristics;
      behaviour_schema stores the learned pattern.
    triggers:
      - node: modeling_observed
        intensity: [0.6, 0.9]
        repetition: high
      - node: dominance
        intensity: [0.3, 0.5]
        repetition: moderate
    narratives:
      - "Someone powerful or admired showed you how they handled the
         world, and you absorbed their patterns."
      - "You modelled yourself on someone you respected, adopting their
         strategies and mannerisms."
    expected_outcomes:
      - node: approach
        direction: positive
        strength: [0.2, 0.5]
      - node: assert
        direction: positive
        strength: [0.2, 0.4]
    related:
      - bandura-observational-learning
    sources:
      - ref: "Bandura 1977"
        data: "Model salience factors: status, similarity, warmth"
        provenance: empirical
      - ref: "#407 spec §4.6"
        data: "model_attention → behavior_schema weight 0.35"
        provenance: consensus

  - id: bandura-efficacy-gate
    model: bandura
    clinical_name: Self-efficacy as behavioural gate
    description: >
      Self-efficacy mediates whether knowledge translates to action.
      Low self-efficacy blocks behavioural reproduction regardless of
      skill acquisition. Also gates recovery — positive experiences
      don't update the network if the character doesn't attempt the
      behaviours that would generate positive outcomes.
    triggers:
      - node: failure
        intensity: [0.5, 0.8]
        repetition: high
      - node: performance_judged
        intensity: [0.5, 0.8]
        repetition: high
    narratives:
      - "You knew what to do but couldn't bring yourself to try —
         past failures had convinced you it wouldn't work."
      - "The knowledge was there but the confidence wasn't. The gap
         between knowing and doing felt uncrossable."
    expected_outcomes:
      - node: self_efficacy
        direction: negative
        strength: [0.4, 0.7]
      - node: abandon
        direction: positive
        strength: [0.4, 0.7]
      - node: withdraw
        direction: positive
        strength: [0.3, 0.6]
      - node: depend
        direction: positive
        strength: [0.2, 0.4]
    related:
      - bandura-mastery-efficacy
      - cbt-negative-self
      - operant-learned-helplessness
    sources:
      - ref: "Multon et al. 1991"
        data: "Self-efficacy → performance ~14% variance"
        provenance: empirical
      - ref: "#407 spec §4.7"
        data: "Self-efficacy gates recovery (cross-model bridge #3)"
        provenance: consensus
```

## 6. Composition Model

Composition happens through the CAPS network's shared mediating nodes, not
through explicit rules in the catalogue. When multiple entries are seeded
for a character, the ExperienceEvents interact through these shared nodes:

| Shared Node | Models | Interaction |
|-------------|--------|-------------|
| `self_worth` | attachment, cbt, bandura | Attachment sets baseline; CBT maintains/modifies; Bandura gates behavioural reproduction |
| `self_efficacy` | bandura, cbt | Bandura's central construct; bridges to CBT self-model via self_worth→self_efficacy connection |
| `other_reliability` | attachment, cbt | Attachment sets baseline from caregiver experience; CBT schema reinforces or challenges |
| `other_safety` | attachment, cbt | Attachment IWM of other's safety; bridges to CBT other-schema |
| `threat_sensitivity` | bis_bas, trauma | BIS provides trait baseline; trauma raises via kindling |
| `FFFS_activation` | bis_bas, trauma | BIS/BAS sets activation parameters; trauma lowers thresholds |
| `BAS_activation` | bis_bas, operant | BAS dopaminergic RPE is the neurobiological implementation of operant learning (bridge #4) |
| `reinforcement_expectation` | operant, cbt, bandura | Direct experience, belief-filtered predictions, observational data converge |
| `arousal_level` | bis_bas, trauma, attachment | Global state; window of tolerance defines range |
| `escape_assessment` | trauma, bis_bas | Determines fight/flight/freeze selection |

The catalogue does NOT define (tracked for future work):
- Composition rules or compatibility matrices — TODO: create tracking issue
- Compound entries that bundle multiple patterns — TODO: create tracking issue
- Expected interaction effects between specific entry pairs — TODO: create tracking issue
- Coverage expansion for uncovered input nodes (submission, abundance,
  competition, sharing) — TODO: create tracking issue

If two patterns interact unexpectedly when composed, that is a CAPS topology
issue to investigate — the catalogue provides the ingredients, the network
provides the chemistry.

**Overlapping entry pairs (BIS/BAS FFFS ↔ trauma 4F):**

Three BIS/BAS FFFS entries share triggers and outputs with their trauma 4F
counterparts (fight/fight, flight/flight, freeze/freeze). This overlap is
intentional — the entries model different mechanisms at different timescales:
FFFS entries model the immediate defensive response selection mechanism
(moderate intensity, temporary), while trauma 4F entries model chronic
crystallisation of that response as a personality pattern (high intensity,
persistent, with additional outputs like distrust and perfectionism).
Selecting both for a character is valid and clinically realistic — it models
someone with BOTH a temperamental defensive tendency AND chronic trauma
that amplified it. The CAPS network handles the double activation correctly
through weight accumulation. Character designers who want only the trait
tendency should select the BIS/BAS entry; those modeling trauma history
should select the trauma entry; those modeling severe trauma amplifying a
pre-existing tendency should select both.

**Cross-model bridges** (non-obvious interactions documented in #407 §4.7):

1. Inconsistent caregiving IS variable-ratio reinforcement — `attachment-anxious`
   and `operant-vr-extinction-resistance` describe the same mechanism from
   different theoretical frameworks
2. Cognitive distortions amplify sensitisation — `cbt-catastrophising` compounds
   with `trauma-kindling` through the shared `threat_sensitivity` node
3. Self-efficacy gates recovery — `bandura-efficacy-gate` blocks corrective
   experiences from updating the network
4. BAS reward prediction error IS operant learning — `bisbas-approach-dominance`
   and `operant-positive-reinforcement` share the dopaminergic mechanism

## 7. Validation Strategy

### 7.1 Tier 1 — Mechanical Validation (deterministic, no LLM)

Per-entry tests:
- Seed one pattern's triggers as ExperienceEvents on a default-disposition agent
- Run CAPS settling (max 100 iterations, #407 spec §2.1 convergence)
- Assert resulting attractor strengths fall within `expected_outcomes` ranges

Disposition sensitivity:
- Re-run per-entry tests with extreme disposition configurations
- Assert `modulating_axes` directionality holds: `amplify` produces stronger
  outcomes, `dampen` produces weaker

Composition smoke tests:
- Seed common 2-3 entry combinations that share mediating nodes
- Assert no pathological saturation (>60% nodes at ceiling) or divergence

Regression:
- Re-run all tests when CAPS topology weights change

### 7.2 Tier 2 — Behavioural Plausibility (LLM-judged)

Per-entry tests:
- Render the resulting behavioural attractor profile as a prompt section
- Present a situation to the LLM with the profile active
- Judge whether the response matches the clinical pattern's expected behaviour

Example: agent seeded with `attachment-anxious` + situation "your partner
hasn't replied to your message in 3 hours" → expect proximity-seeking/
reassurance-demanding behaviour, not dismissive detachment.

Non-deterministic but repeatable: ≥80% of runs should exhibit the target
behaviour for a passing test.

### 7.3 Tier 3 — Compositional Emergence (LLM-judged, multi-pattern)

Seed 3-5 patterns that interact through shared nodes. Present situations
that activate the intersection. Verify compound behaviour emerges that
neither pattern produces alone.

Example: `attachment-anxious` + `trauma-chronic-fawn` → expect fawning
behaviour specifically in intimate relationships (attachment context),
not in all social contexts. The CAPS network should produce this
selectivity through the shared `other_reliability` and `self_worth` nodes.

Tier 3 test scenarios, expected compound behaviours, and their clinical
rationale are defined within the #402 test framework — not in this
catalogue. The catalogue provides the composition points (shared nodes
in §6) and the individual entry expectations, but compound interaction
effects are emergent properties of the CAPS network. Tier 3 test authors
use clinical knowledge to specify expected compound outcomes, documented
alongside the test code with literature citations justifying the
expectation.

### 7.4 Coverage Requirements

- Every entry: ≥1 Tier 1 test + ≥1 Tier 2 test
- Each of the 10 shared-node composition points: ≥1 Tier 3 test
- Tier 1 runs in #402 (CognitiveEmergenceTest framework)
- Tiers 2-3 run in an eval suite alongside #402 — scoring rubrics, not unit tests

## 8. Integration Points

| Consumer | How it uses the catalogue |
|----------|--------------------------|
| **#398 (Memory seeding)** | Reads `triggers` + `narratives` to generate ExperienceEvents with pre-classified `situation_types` metadata and amplified salience (sensitive period simulation) |
| **#408 (Graph engine)** | `triggers.node` IDs must be a subset of the CAPS topology's input vocabulary; SituationClassifier vocabulary must cover all trigger nodes |
| **#402 (CognitiveEmergenceTest)** | Uses `expected_outcomes` as assertion targets for Tier 1 mechanical validation |
| **CAPS topology** | Catalogue version coupled to topology version. When #408 modifies topology (adds/renames/splits nodes), catalogue entries referencing affected nodes must be updated. The index file's `caps_topology_version` reference makes this dependency explicit |

**ExperienceEvent integration requirements for #398:**

The existing `ExperienceEvent` sealed interface (`memory-api`) has three
permits (Observation, Action, Outcome) and a generic `Map<String, String>
metadata()`. #398 must extend this to support catalogue-driven seeding:

1. **New permit or subtype** for formative backstory events — current permits
   model runtime events, not pre-history experiences
2. **Situation type metadata** — pre-classified CAPS input node IDs from the
   catalogue's `triggers` field, stored in metadata for direct CAPS
   processing without SituationClassifier
3. **Salience amplification** — a mechanism for elevated learning rate (α)
   during formative period simulation, either as a field or metadata entry
4. **Schedule metadata** — reinforcement schedule from the catalogue's
   trigger `schedule` field, for the CAPS weight update's schedule_modifier

These requirements are captured in #398's issue scope. The cross-cutting
nature of ExperienceEvent changes means #398 should propose the type
evolution early for review.

## 9. References

### Attachment Theory
- van IJzendoorn, M.H. (1995). Adult attachment representations, parental responsiveness, and infant attachment. *Psychological Bulletin*
- Madigan, S. et al. (2024). Caregiver sensitivity meta-analysis update
- Zhang, F. et al. (2022). Attachment dimensions and mental health. 245 samples, N=79,722
- Deneault, A.A. et al. (2021). Insecure attachment and externalizing behavior
- Griffin, D.W. & Bartholomew, K. (1994). Big Five and attachment dimensions
- Collins, N.L. & Read, S.J. (1990). Adult attachment, working models, and relationship quality. *Journal of Personality and Social Psychology*
- Roisman, G.I. et al. (2002). Earned-secure attachment

### BIS/BAS (Gray's RST)
- Gray, J.A. & McNaughton, N. (2000). *The Neuropsychology of Anxiety* (2nd ed.)
- Corr, P.J. (2008). Reinforcement Sensitivity Theory of Personality
- Carver, C.S. & White, T.L. (1994). BIS/BAS scales
- Tamada, Y. et al. (2025). Childhood abuse, BIS/BAS, and depression (SEM)
- Bijttebier, P. et al. (2009). Gray's RST in clinical samples. *Journal of Psychopathology and Behavioral Assessment*
- Miu, A.C. et al. (2022). Severe trauma and BAS decrease

### CBT (Beck's Cognitive Model)
- Beck, A.T. & Haigh, E.A.P. (2014). The generic cognitive model. *Annual Review of Clinical Psychology*
- Smith, R. et al. (2021). Simulating CBT with Active Inference. *Scientific Reports*
- Collins, N.L. & Read, S.J. (1990). Hierarchical IWM = schema hierarchy mapping
- Hofmann, S.G. et al. (2012). The efficacy of CBT: a review of meta-analyses. *Cognitive Therapy and Research*
- Aderka, I.M. et al. (2012). Sudden gains meta-analysis. *Journal of Consulting and Clinical Psychology*
- Seligman, M.E.P. (2006). *Learned Optimism*

### Schema Therapy
- Young, J.E., Klosko, J.S. & Weishaar, M.E. (2003). *Schema Therapy*
- Bach, B., Lockwood, G. & Young, J.E. (2018). EMS definition and elaboration
- Barlas, J. & Ng, R. (2025). Schema therapy empirical evidence review. *Springer*

### Trauma Response Models
- Walker, P. (2013). *Complex PTSD: From Surviving to Thriving* (4F model)
- Felitti, V.J. et al. (1998). Adverse Childhood Experiences Study. *AJPM*
- Hughes, K. et al. (2017). ACE meta-analysis. *Lancet Public Health*
- Dube, S.R. et al. (2001). ACE and suicide attempts. *JAMA*
- Porges, S.W. (2022). Polyvagal Theory. *Frontiers in Integrative Neuroscience*
- Siegel, D.J. (1999). *The Developing Mind: How Relationships and the Brain Interact to Shape Who We Are*
- Seligman, M.E.P. (1975). *Helplessness*

### Operant Conditioning
- Rescorla, R.A. & Wagner, A.R. (1972). A theory of Pavlovian conditioning
- Pearce, J.M. & Hall, G. (1980). Attention-associability model
- Nevin, J.A. & Grace, R.C. (2000). Behavioral Momentum and the Law of Effect. *Behavioral and Brain Sciences*
- Mowrer, O.H. (1947). On the dual nature of learning. *Harvard Educational Review*

### Social Learning Theory (Bandura)
- Bandura, A. (1977). *Social Learning Theory*
- Bandura, A. (1997). *Self-Efficacy: The Exercise of Control*
- Multon, K.D., Brown, S.D. & Lent, R.W. (1991). Self-efficacy meta-analysis
- Sitzmann, T. & Ely, K. (2011). Domain-specific self-efficacy meta-analysis
- Ollendick, T.H. et al. (1983). Vicarious reinforcement durability
- Mozahem, N.A. (2022). Agent-based reciprocal determinism simulation

### Self-Determination Theory
- Deci, E.L. & Ryan, R.M. (2000). The "what" and "why" of goal pursuits. *Psychological Inquiry*
- Grolnick, W.S. & Ryan, R.M. (1989). Parent styles associated with children's self-regulation. *Journal of Educational Psychology*

### CBT Case Formulation
- Kuyken, W., Padesky, C.A. & Dudley, R. — Science and practice of case conceptualization

### Computational Personality Research
- Mischel, W. & Shoda, Y. (1995). CAPS theory. *Psychological Review*
- Stanford HAI (2024). Interview-based generative agents, N=1052
- Park, J. et al. (2024, NAACL 2025). CharacterGPT

### casehub Architecture
- casehubio/neocortex#406 — Emergent behavioral synthesis epic
- casehubio/neocortex#407 — Psychology cause-effect models research
- casehubio/neocortex#408 — Cause-effect decision graph engine
- casehubio/neocortex#398 — Memory seeding infrastructure
- casehubio/neocortex#402 — CognitiveEmergenceTest framework
- CAPS topology: `docs/specs/2026-10-02-caps-topology.yaml` (version 1)
