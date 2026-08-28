# Identity and Memory — Where WHO Meets HOW

Identity isn't a separate concern from memory — it's the lens through which
memory operates. WHO you are determines what you notice, what you remember,
how you feel about what happened, what you're curious about, how you relate
to others, what you plan for, and how you reason about similar situations.

This document maps every integration point between eidos (identity) and
neocortex (cognition), with concrete type references and YAML configuration
surface for each.

---

## The Two Systems

| System | Repo | What it models | Nature |
|--------|------|---------------|--------|
| **Eidos** | casehub-eidos | WHO the agent IS — disposition, capabilities, goals, constraints, briefing | Declared, static, authored |
| **Neocortex** | casehub-neocortex | HOW the agent THINKS — memory weights, mood, curiosity, retrieval, extraction | Emergent, dynamic, runtime |

**Eidos types:** `AgentDescriptor`, `AgentDisposition` (5 axes: socialOrient,
ruleFollowing, riskAppetite, autonomy, conflictMode), `dispositionProfile`
(weighted cognitive function terms, e.g., Jungian Ni/Te/Fi/Se),
`AgentCapability`, goals, constraints, vocabulary URIs.

**Neocortex types:** `PersonalityWeights`, `MoodBaseline`, `MoodDecay`,
`CuriositySignalGenerator`, `MindMapExtractor`, `CbrQuery`, confidence decay
rates, retrieval modulation.

**The gap today:** Both carry personality configuration that affects agent
behaviour — but they're disconnected. Eidos `dispositionProfile` drives
routing in blocks. Neocortex `PersonalityWeights` drives retrieval. Neither
derives from the other.

---

## Integration Points

### 1. Attention & Encoding

**What eidos provides:** Disposition profile — an Ni-dominant agent is an
abstract pattern seeker; an Se-dominant agent notices concrete details.

**What neocortex does with it:** `MindMapExtractor` could bias its LLM
extraction prompt based on disposition. An analytical agent extracts more
relationship edges and structural patterns. An empathetic agent extracts
more affective annotations and emotional context.

**YAML surface:**
```yaml
cognitive:
  extraction:
    relationship-bias: 1.2    # derived from analytical disposition
    affect-sensitivity: 0.8   # derived from lower Fe
```

**Cognitive parallel:** Selective attention (Broadbent 1958) — personality
determines what passes through the attentional filter into memory encoding.

### 2. Memory Weighting

**What eidos provides:** Disposition profile with weighted cognitive functions.

**What neocortex does with it:** `PersonalityWeights` (Map<MemoryDomain, Double>)
determines which memory domains surface first during retrieval.

| Disposition | Memory domain weights |
|-------------|----------------------|
| Ni-dominant | reflection=1.5, experience=0.8 (prefers abstract over episodic) |
| Fe-dominant | relationship=1.4, engagement=1.3 (social focus) |
| Te-dominant | experience=1.3, reflection=1.0 (action-oriented) |
| Se-dominant | experience=1.5, mood=0.7 (present-focused, low introspection) |

**YAML surface:**
```yaml
cognitive:
  personality:
    derive-from: disposition    # auto-compute from eidos profile
    weights:                    # override any derived defaults
      relationship: 0.6        # explicit: independent agent, lower social weight
```

**Cognitive parallel:** Personality-cognition link (Chamorro-Premuzic 2014) —
personality traits predict cognitive style, which shapes memory retrieval bias.

### 3. Affective Interpretation

**What eidos provides:** Disposition axes — riskAppetite, conflictMode.

**What neocortex does with it:** The same event triggers different emotional
responses depending on identity. An agent with high riskAppetite tags
"startup acquisition" with `pleasure=0.7` (opportunity). A risk-averse agent
tags it `pleasure=-0.3` (threat).

`MoodBaseline` derives from disposition — anxious personalities (low
riskAppetite, high socialOrient) have lower baseline pleasure and higher
baseline arousal.

**YAML surface:**
```yaml
cognitive:
  mood:
    derive-from: disposition
    baseline: { pleasure: 0.4, arousal: 0.5, dominance: 0.6 }  # override
```

**Cognitive parallel:** Appraisal theory (Lazarus 1991) — emotional response
to events depends on the individual's appraisal of relevance and coping
capacity, which is personality-dependent.

### 4. Curiosity Direction

**What eidos provides:** Disposition axes + goals.

**What neocortex does with it:** `CuriositySignalGenerator` weights signal
categories based on disposition. Curiosity isn't just intensity — it's
WHERE the agent looks.

| Disposition | Curiosity bias |
|-------------|---------------|
| High autonomy | Boost STRUCTURAL signals — explore the unknown |
| High ruleFollowing | Boost QUALITY signals — validate what we know |
| High socialOrient | Boost CENTRALITY signals for relationship nodes |
| Goal: "strategic patterns" | Boost CENTRALITY + STRUCTURAL for cross-domain bridges |

**YAML surface:**
```yaml
cognitive:
  curiosity:
    derive-from: disposition
    category-weights:
      STRUCTURAL: 1.3     # high autonomy → explore broadly
      QUALITY: 0.8        # lower rule-following → less validation focus
      CENTRALITY: 1.2     # Ni → interested in bridge nodes
    proximity-scale: 10.0 # strategic thinker → broad planning horizon
```

**Cognitive parallel:** Epistemic curiosity typology (Litman 2005) —
diversive curiosity (broad exploration) vs specific curiosity (focused
gap-filling), correlated with Openness to Experience.

### 5. Social Cognition

**What eidos provides:** socialOrient, conflictMode.

**What neocortex does with it:** How the agent interprets `RelationshipEvent`
and `EngagementEvent` data. A cooperative agent (conflictMode=COOPERATIVE)
reads negative engagement as a signal to repair. A competitive agent reads
it as information about the opponent.

Trust formation speed (`AgentTrustProvider`) varies by disposition — high
socialOrient agents build trust faster, high autonomy agents are slower
to trust.

**YAML surface:**
```yaml
cognitive:
  social:
    derive-from: disposition
    trust-formation-rate: 0.7    # moderate — derived from socialOrient
    conflict-interpretation: repair  # derived from conflictMode
```

**Cognitive parallel:** Attachment theory (Bowlby 1969) — individual
differences in social cognition are rooted in internal working models
that shape trust formation and conflict response.

### 6. Prospective Focus

**What eidos provides:** Goals, capabilities, domain.

**What neocortex does with it:** What future events the agent focuses on.
A career-oriented agent (goal: "advance professionally") intensifies
curiosity around work-related future-dated nodes. A family-oriented agent
prioritises personal events.

`SubgraphType` weighting in proximity signals: disposition → which subgraph
types get elevated proximity scores.

**YAML surface:**
```yaml
cognitive:
  curiosity:
    subgraph-proximity-weights:
      PROJECT: 1.3        # career-focused → project events amplified
      PERSON: 0.9         # slightly lower personal focus
```

**Cognitive parallel:** Goal-directed prospection (Szpunar et al. 2014) —
future thinking is shaped by current goals and motivational state, not
random simulation.

### 7. Analogical Strategy

**What eidos provides:** ruleFollowing, riskAppetite.

**What neocortex does with it:** CBR retrieval parameters vary by identity.
An agent that values precedent (high ruleFollowing) uses stricter similarity
thresholds — only highly similar cases influence decisions. An innovative
agent (high riskAppetite) uses broader similarity and lower minSimilarity,
drawing analogies from more distant cases.

**YAML surface:**
```yaml
cognitive:
  cbr:
    derive-from: disposition
    min-similarity: 0.6          # moderate — calculated risk
    temporal-decay:
      half-life: P120D           # 4-month horizon
    retrieval-mode: HYBRID       # innovative agents might prefer SEMANTIC_ONLY
```

**Cognitive parallel:** Analogical reasoning style (Gentner & Smith 2012) —
conservative reasoners prefer literal similarity; creative reasoners exploit
structural alignment across distant domains.

### 8. Graph Structure Preference

**What eidos provides:** Disposition profile (holistic vs analytical thinking).

**What neocortex does with it:** How knowledge organises in the MindMap.
Some agents build dense, interconnected graphs (holistic thinkers — Ni/Fe).
Others build clean, categorised structures (systematic thinkers — Te/Si).

`DerivedEdgeRule` activation could be disposition-gated — some inference
rules fire for some personality types. A holistic thinker's rules discover
cross-domain connections; a systematic thinker's rules enforce categorisation.

**YAML surface:**
```yaml
cognitive:
  graph:
    derive-from: disposition
    inference-style: connective     # holistic — look for bridges
    # vs: categorical              # systematic — enforce clean structure
```

**Cognitive parallel:** Cognitive style (Riding & Rayner 1998) —
holistic-analytical dimension of information processing, determining whether
individuals prefer to see wholes or parts.

---

## The Composition Model

A single agent YAML composes identity + cognition with explicit derivation:

```yaml
# agent-analyst-01.yaml

descriptor:                      # eidos layer — WHO
  agentId: analyst-01
  name: Strategic Analyst
  disposition:
    socialOrient: independent
    ruleFollowing: moderate
    riskAppetite: calculated
    autonomy: high
    conflictMode: analytical
    dispositionProfile:
      - { term: ni, weight: 0.35 }
      - { term: te, weight: 0.30 }
      - { term: fi, weight: 0.20 }
      - { term: se, weight: 0.15 }
  goals:
    - identify strategic patterns
    - surface non-obvious connections

cognitive:                       # neocortex layer — HOW
  derive-from: descriptor        # derive defaults from identity

  personality:
    weights:
      reflection: 1.5            # auto: ni-dominant
      experience: 0.8            # auto: ni-dominant
      relationship: 0.6          # override: independent

  mood:
    baseline:
      pleasure: 0.4              # auto: calculated risk
      arousal: 0.5               # auto: moderate
      dominance: 0.6             # auto: high autonomy

  curiosity:
    category-weights:
      STRUCTURAL: 1.3            # auto: high autonomy
      QUALITY: 0.8               # auto: moderate ruleFollowing
      CENTRALITY: 1.2            # auto: ni
    proximity-scale: 10.0        # broad planning horizon

  extraction:
    relationship-bias: 1.2       # auto: analytical
    affect-sensitivity: 0.8      # auto: lower Fe

  cbr:
    min-similarity: 0.6          # auto: calculated risk
    temporal-decay:
      half-life: P120D

  memory:
    decay:
      default-half-life: P180D
      per-subgraph-type:
        CONCEPT: P730D           # strategic → long concept retention
        PROJECT: P60D            # but short project detail retention
```

---

## The Derivation Engine

The `derive-from: descriptor` directive triggers a derivation engine:

1. Reads the eidos `AgentDescriptor`
2. Applies derivation rules:
   - `dispositionProfile` → `PersonalityWeights` (weighted function mapping)
   - Disposition axes → `MoodBaseline` (personality-affect mapping)
   - Goals + disposition → curiosity category weights
   - ruleFollowing + riskAppetite → CBR retrieval parameters
   - Disposition profile → extraction biases
3. Produces default cognitive config
4. Explicit overrides in the `cognitive:` block take precedence

The derivation rules themselves are configurable — different platforms
may have different mappings from disposition to cognition. The default
rules implement the personality-cognition research consensus; domain-specific
deployments can override them.

**Implementation note:** The derivation engine is a pure function:
`AgentDescriptor → CognitiveDefaults`. No side effects, no state, fully
testable. It runs at YAML load time, not at runtime.

---

## Research Parallel

This integration mirrors the personality-cognition link in differential
psychology:

| Big Five trait | Eidos equivalent | Cognitive effect |
|---------------|-----------------|------------------|
| Openness to Experience | High autonomy + Ni-dominance | Broader curiosity, abstract encoding, connective graph structure |
| Neuroticism | Low riskAppetite + high arousal baseline | Lower mood baseline, higher affect sensitivity, vigilant curiosity |
| Conscientiousness | High ruleFollowing + Te-dominance | Stricter CBR thresholds, quality-focused curiosity, categorical graphs |
| Extraversion | High socialOrient + Se/Fe-dominance | Social memory weighting, fast trust formation, engagement focus |
| Agreeableness | Cooperative conflictMode + Fe | Repair-oriented conflict interpretation, relationship memory emphasis |

The eidos disposition axes don't map 1:1 to Big Five, but the principle is
the same: personality traits predict cognitive style, and cognitive style
shapes how memory operates.

**Research tags:** individual differences in cognition, personality-cognition
link, Chamorro-Premuzic 2014, cognitive style theory, Riding & Rayner 1998,
disposition-driven attention, personality-based information processing,
appraisal theory, Lazarus 1991, selective attention, Broadbent 1958,
epistemic curiosity, Litman 2005, analogical reasoning, Gentner & Smith 2012,
goal-directed prospection, Szpunar 2014, attachment theory, Bowlby 1969

---

## What This Enables

When identity and memory are properly integrated:

1. **Personality-coherent agents** — an agent's curiosity patterns, memory
   retrieval biases, emotional responses, and reasoning strategies all
   derive from the same identity declaration. No inconsistency between
   WHO the agent is and HOW it thinks.

2. **One YAML, one agent** — a single file declares both identity and
   cognition. Change the disposition profile and the entire cognitive
   system adapts. No manual synchronisation across config files.

3. **Principled variation** — different agents with different dispositions
   automatically produce different cognitive behaviours. An INTJ strategist
   and an ENFP explorer remember, feel, and reason differently — not
   because of hand-tuned parameters, but because their identities drive
   different cognitive defaults.

4. **Quarkmind personality generation** — the derivation engine IS the
   personality generator. Given an `AgentDescriptor`, it produces a
   complete cognitive profile. This closes the quarkmind issue for
   generating personalities from identity declarations.
