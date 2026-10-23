# Unified Personality Model: Cross-Mapping Synthesis

**Research Date:** 2026-10-07  
**Purpose:** Integrated model connecting psychological theories for cognitive architecture implementation

## Executive Summary

This document synthesizes cross-mappings between nine major psychological models to create a unified dimensional framework for modeling personality, psychopathology, and behavior in LLM-based cognitive architectures. The research identifies validated empirical relationships that enable us to build a coherent developmental pathway: **childhood experiences → attachment patterns → maladaptive schemas → personality traits → relational patterns → moment-to-moment emotional/behavioral responses**.

## 1. AMPD ↔ Big Five: The Core Dimensional Alignment

### Key Finding
The DSM-5 Alternative Model for Personality Disorders (AMPD) is structurally aligned with the Five-Factor Model (FFM), with four of five domains showing direct correspondence:

| AMPD Domain | FFM Domain | Relationship |
|-------------|-----------|--------------|
| Negative Affectivity | Neuroticism | Convergent (high-high) |
| Detachment | Extraversion | Inverse (high-low) |
| Antagonism | Agreeableness | Inverse (high-low) |
| Disinhibition | Conscientiousness | Inverse (high-low) |
| Psychoticism | Openness | Complex/debated |

### The 25 PID-5 Facets Organized by Domain

**Negative Affectivity (7 facets):**
- Emotional lability
- Anxiousness
- Separation insecurity
- Submissiveness
- Hostility
- Perseveration
- Depressivity

**Detachment (6 facets):**
- Withdrawal
- Intimacy avoidance
- Anhedonia
- Depressivity (shared)
- Restricted affectivity
- Suspiciousness

**Antagonism (6 facets):**
- Manipulativeness
- Deceitfulness
- Grandiosity
- Attention seeking
- Callousness
- Hostility (shared)

**Disinhibition (5 facets):**
- Irresponsibility
- Impulsivity
- Distractibility
- Risk taking
- Rigid perfectionism (reversed)

**Psychoticism (3 facets):**
- Unusual beliefs and experiences
- Eccentricity
- Cognitive and perceptual dysregulation

### Facet-Level FFM Profile Correlations (Samuel & Widiger 2008 Meta-Analysis)

Key NEO-PI-R facets by predictive strength for personality pathology:

**Highest cross-disorder discriminators:**
- **Trust (A)**: Paranoid (−.45), Schizotypal (−.31), Avoidant (−.29), Borderline (−.29)
- **Self-Consciousness (N)**: Avoidant (.56), Dependent (.42)
- **Angry Hostility (N)**: Borderline (.48), Paranoid (.41), Schizotypal (.29), Antisocial (.27)
- **Depressiveness (N)**: Borderline (.50), Avoidant (.53), Dependent (.41), Schizotypal (.39)
- **Modesty (A)**: Narcissistic (−.37)
- **Deliberation (C)**: Antisocial (−.38), Borderline (−.27)

**Openness limitation:** Only one Openness facet (Actions) reached r ≥ .20 across all disorders (Avoidant, −.20), suggesting Openness/Psychoticism has minimal role in standard personality pathology.

## 2. Schema Therapy ↔ Attachment Theory

### Attachment Styles (Bartholomew Four-Category Model)

**Model structure:** Two dimensions (model of self × model of other)

| Style | Self Model | Other Model | Characteristics |
|-------|-----------|-------------|-----------------|
| **Secure** | Positive | Positive | Comfortable with intimacy and autonomy |
| **Preoccupied** | Negative | Positive | Anxious, clingy, seeks validation |
| **Dismissive-Avoidant** | Positive | Negative | Self-reliant, emotionally distant |
| **Fearful-Avoidant** | Negative | Negative | Fears rejection, avoids intimacy |

### The 18 Early Maladaptive Schemas (Young)

**Domain I: Disconnection and Rejection** (linked to insecure attachment)
1. **Abandonment/Instability** → Anxious/Preoccupied attachment
2. **Mistrust/Abuse** → Disorganized attachment
3. **Emotional Deprivation** → Avoidant attachment (dismissive or fearful)
4. **Defectiveness/Shame** → Fearful-Avoidant attachment
5. **Social Isolation/Alienation** → Fearful-Avoidant attachment

**Domain II: Impaired Autonomy and Performance**
6. **Dependence/Incompetence** → Preoccupied attachment
7. **Vulnerability to Harm or Illness** → Anxious attachment
8. **Enmeshment/Undeveloped Self** → Preoccupied attachment
9. **Failure**

**Domain III: Impaired Limits**
10. **Entitlement/Grandiosity**
11. **Insufficient Self-Control/Self-Discipline**

**Domain IV: Other-Directedness**
12. **Subjugation** → Preoccupied attachment
13. **Self-Sacrifice** → Preoccupied attachment
14. **Approval-Seeking/Recognition-Seeking** → Preoccupied attachment

**Domain V: Over-Vigilance and Inhibition**
15. **Negativity/Pessimism**
16. **Emotional Inhibition** → Dismissive-Avoidant attachment
17. **Unrelenting Standards/Hypercriticalness**
18. **Punitiveness**

### Empirical Findings (Simard, Moss & Pascuzzo 2011)

- **Insecure ambivalent (childhood)** and **preoccupied (adulthood)** attachment showed significantly more schemas across all domains
- Effects were not domain-specific — insecure attachment predicted EMS broadly
- Secure attachment protective against schema formation
- Key mechanism: "high anxiety over abandonment, negative self-view, and explicit manifestations of personal distress"

## 3. Schema Therapy ↔ AMPD

### Key Research (Bach & Bernstein 2019)

**Conceptual alignment:** Schema therapy and AMPD are "largely compatible"

**Core personality functioning (AMPD Criterion A) ↔ Healthy Adult mode:**
- Sense of identity, self-worth
- Emotion regulation
- Capacity for intimacy
- Goal-directed behavior

**AMPD trait domains ↔ Schema modes and underlying schemas:**

| AMPD Domain | Schema Modes | Underlying Schemas |
|-------------|--------------|-------------------|
| **Negative Affectivity** | Vulnerable Child, Anxious Child | Abandonment, Vulnerability, Emotional Deprivation |
| **Detachment** | Detached Protector | Social Isolation, Mistrust/Abuse, Emotional Deprivation |
| **Antagonism** | Overcompensator, Self-Aggrandizer | Entitlement, Mistrust, Defectiveness (compensated) |
| **Disinhibition** | Impulsive/Undisciplined Child | Insufficient Self-Control, Entitlement |
| **Psychoticism** | (Less direct mapping) | Mistrust/Abuse (paranoid themes) |

**Clinical utility:** Schemas inform treatment focus (what to address), while AMPD severity informs treatment intensity (how much structure/support needed).

## 4. Psychopathy Models ↔ AMPD

### Triarchic Model Mapping (Drislane, Sellbom et al. 2019)

The triarchic model captures psychopathy through three biobehavioral dimensions, each mapped to specific PID-5 items:

#### **Boldness (15 items)** — Social dominance, fearlessness, stress immunity
| PID-5 Domain | Facets | Direction |
|--------------|--------|-----------|
| Antagonism | Attention Seeking, Grandiosity, Manipulativeness | High |
| Negative Affect | Anxiousness, Submissiveness | Low (reversed) |
| Disinhibition | Risk-Taking | High (venturesome) |
| Detachment | Withdrawal | Low (reversed) |

#### **Meanness (21 items)** — Callous disregard, lack of empathy, emotional coldness
| PID-5 Domain | Facets | Direction |
|--------------|--------|-----------|
| Antagonism | Callousness (primary), Grandiosity | High |
| Negative Affect | Restricted Affectivity, low Emotional Lability | High/Low |
| Detachment | Intimacy Avoidance, Withdrawal | High |

#### **Disinhibition (19 items)** — Poor impulse control, disregard for consequences
| PID-5 Domain | Facets | Direction |
|--------------|--------|-----------|
| Disinhibition | Impulsivity, Irresponsibility, Risk-Taking | High |
| Negative Affect | Hostility | High |
| Detachment | Suspiciousness | High |
| Antagonism | Deceitfulness | High |

### PCL-R ↔ AMPD Mapping

**AMPD psychopathy specifier:**
- High Antagonism (manipulativeness, deceitfulness, callousness)
- High Disinhibition (impulsivity, irresponsibility)
- Low Negative Affectivity (specifically low anxiousness)
- Correlates with PCL-R at r = .88 (vs. r = .59 for traditional DSM-II ASPD)

**Convergence limitations:** AMPD captures interpersonal-affective and disinhibitory factors but shows "poorer convergence with boldness/fearless dominance" — the triarchic Boldness construct is underrepresented in standard AMPD.

### Dark Triad/Tetrad ↔ AMPD

**Dark Triad components:**
- **Narcissism** → High Antagonism (Grandiosity, Attention Seeking), Low Modesty
- **Machiavellianism** → High Antagonism (Manipulativeness, Deceitfulness)
- **Psychopathy** → See triarchic mapping above

**Dark Tetrad (+Sadism):**
- **Sadism** → High Antagonism (Callousness), High Hostility, but with added pleasure-from-cruelty component not fully captured in AMPD

## 5. Interpersonal Circumplex ↔ Big Five

### IPC Structure

**Two orthogonal axes:**
- **Agency** (vertical): Dominance ↔ Submissiveness
- **Communion** (horizontal): Warmth/Nurturance ↔ Coldness/Distance

**Eight octants** (clockwise from top):
- **PA**: High Agency, High Communion (Assured-Dominant)
- **BC**: High Agency, Neutral Communion (Arrogant-Calculating)
- **DE**: High Agency, Low Communion (Cold-Hearted)
- **FG**: Neutral Agency, Low Communion (Aloof-Introverted)
- **HI**: Low Agency, Low Communion (Unassured-Submissive)
- **JK**: Low Agency, Neutral Communion (Unassuming-Ingenuous)
- **LM**: Low Agency, High Communion (Warm-Agreeable)
- **NO**: Neutral Agency, High Communion (Gregarious-Extraverted)

### FFM Mapping (Wiggins & Trapnell 1996)

**Agency dimension:**
- Primary: Extraversion (r ≈ .50–.70)
- Secondary: Agreeableness (negative, r ≈ −.30)
- Agency = dominance-seeking, assertive Extraversion + low communal Agreeableness

**Communion dimension:**
- Primary: Agreeableness (r ≈ .40–.60)
- Secondary: Extraversion (positive, r ≈ .30)
- Communion = warm, affiliative Agreeableness + sociable Extraversion

**NEO-PI-R facet fan:** Facets of E and A distribute in a "fanned" pattern around the upper-right quadrant:
- **Warmth** (E1): Agreeable form of Extraversion → high communion, moderate agency
- **Assertiveness** (E3): Disagreeable form of Extraversion → high agency, low communion
- **Excitement Seeking** (E5): Disagreeable form of Extraversion → moderate agency, low communion

### IPC ↔ AMPD Mapping (inferred)

| IPC Quadrant | AMPD Profile |
|--------------|--------------|
| High Agency, Low Communion (DE) | High Antagonism, Low Detachment |
| Low Agency, High Communion (LM) | High Negative Affectivity (Submissiveness), Low Antagonism |
| Low Agency, Low Communion (HI) | High Detachment, High Negative Affectivity |
| High Agency, High Communion (PA) | Low pathology (healthy assertiveness + warmth) |

## 6. PAD Emotional Model ↔ Big Five / AMPD

### PAD Dimensions (Mehrabian 1996)

**Three orthogonal emotional state dimensions:**
- **Pleasure-Displeasure (P)**: Positive vs. negative emotional valence
- **Arousal-Nonarousal (A)**: Physical/mental activity and alertness
- **Dominance-Submissiveness (D)**: Control vs. lack of control

### Big Five → PAD Mapping (Mehrabian 1996)

**Extraversion:**
- Primary: High Dominance
- Secondary: High Pleasure
- Profile: Dominant + Pleasant

**Agreeableness:**
- Primary: High Pleasure
- Secondary: High Arousal, Low Dominance (submissive)
- Profile: Pleasant + Arousable + Submissive
- Note: Resembles dependency with submissive quality

**Conscientiousness:**
- Equal: High Pleasure + High Dominance
- Profile: Pleasant + Dominant (no arousal component)

**Emotional Stability (low Neuroticism):**
- Equal: High Pleasure + Low Arousal (unarousable)
- Profile: Pleasant + Unarousable
- Note: Lacks the dominance feature of Conscientiousness

**Sophistication (Openness/Culture):**
- Primary: High Dominance
- Secondary: High Arousal
- Profile: Dominant + Arousable

**Variance explained:** PAD scales explained approximately 75% of the reliable variance in Extraversion, Emotional Stability, and Agreeableness.

### AMPD → PAD Baseline Predictions (inferred)

| AMPD Domain | P (Pleasure) | A (Arousal) | D (Dominance) |
|-------------|--------------|-------------|---------------|
| Negative Affectivity | Low | High | Low |
| Detachment | Low | Low | Variable |
| Antagonism | Variable | Variable | High |
| Disinhibition | Variable | High | Variable |
| Psychoticism | Low | High | Variable |

**Specific facets:**
- **Anxiousness** (Negative Affectivity): Low P, High A, Low D
- **Callousness** (Antagonism): Low P (to others' distress), Low A, High D
- **Grandiosity** (Antagonism): High P (self-focused), Variable A, High D
- **Restricted Affectivity** (Detachment): Low P, Low A, Variable D

### Emotional Response Patterns in PAD Space

PAD enables modeling of **personality-driven appraisal patterns**:
- High Neuroticism individual encounters threat → Low P, High A, Low D (anxious response)
- High Antagonism individual encounters challenge → Variable P, Moderate A, High D (aggressive/controlling response)
- High Detachment individual encounters social opportunity → Low P, Low A, Low D (withdrawn response)

## 7. HiTOP: The Unified Dimensional Framework

### HiTOP Hierarchical Structure (Kotov et al. 2017)

**From specific to general:**

```
Level 1: Symptoms/Signs (most specific)
    ↓
Level 2: Narrow Components/Traits
    ↓
Level 3: Syndromes (e.g., MDD, GAD, BPD)
    ↓
Level 4: Subfactors
    ↓
Level 5: Spectra (6 main)
    ↓
Level 6: Superspectra (3)
    ↓
Level 7: p-factor (general psychopathology, most general)
```

### The Six Spectra

**1. Internalizing (Negative Affectivity)**
- Subfactors: Fear, Distress, Eating Pathology, Sexual Problems
- Fear: Panic disorder, agoraphobia, social phobia, specific phobia
- Distress: MDD, dysthymia, GAD, PTSD

**2. Thought Disorder (Psychoticism)**
- Unusual beliefs, perceptual aberrations
- Disorganized thinking and behavior

**3. Disinhibited Externalizing**
- Impulsivity, irresponsibility
- Substance use disorders
- Antisocial behavior (impulsive type)

**4. Antagonistic Externalizing**
- Manipulativeness, deceitfulness
- Callousness, narcissism
- Antisocial behavior (predatory type)

**5. Detachment**
- Social withdrawal, anhedonia
- Restricted affect, intimacy avoidance

**6. Somatoform**
- Somatic symptoms, health anxiety
- Conversion symptoms

### The Three Superspectra

**Emotional Dysfunction:**
- Combines: Internalizing + Somatoform
- Represents: Broad negative affectivity and emotional dysregulation

**Externalizing:**
- Combines: Disinhibited Externalizing + Antagonistic Externalizing
- Represents: Impulse control problems and norm-violating behavior

**Psychosis:**
- Combines: Thought Disorder + Detachment
- Represents: Reality distortion and social disconnection

### p-factor (General Psychopathology)

**Definition:** Common variance across all psychopathology
**Interpretation:** Shared vulnerability — impairment in core personality functioning, general distress, global dysfunction

### HiTOP ↔ AMPD Integration

**HiTOP spectra map directly onto AMPD domains:**

| HiTOP Spectrum | AMPD Domain |
|----------------|-------------|
| Internalizing | Negative Affectivity |
| Detachment | Detachment |
| Antagonistic Externalizing | Antagonism |
| Disinhibited Externalizing | Disinhibition |
| Thought Disorder | Psychoticism |
| Somatoform | (Not in AMPD) |

**p-factor ↔ AMPD Criterion A:** Both capture global impairment in personality functioning (self and interpersonal).

## 8. The Developmental Pathway: Childhood → Personality → Psychopathology

### Cascade Model (Empirical Support)

**Stage 1: Infant Temperament (birth–2 years)**
- **Dimensions** (Rothbart):
  - Negative Emotionality → Neuroticism
  - Surgency/Approach → Extraversion
  - Effortful Control → Conscientiousness, Agreeableness
- **Biological basis:** Genetic + prenatal influences

**Stage 2: Attachment Formation (6 months–3 years)**
- **Mechanism:** Caregiver responsiveness + infant temperament → attachment style
- **Critical finding:** Adverse childhood experiences (ACEs) predict attachment security over and above genetic risk
- **Styles formed:** Secure, Anxious/Preoccupied, Avoidant, Disorganized

**Stage 3: Schema Development (early childhood–adolescence)**
- **Mechanism:** Repeated patterns of unmet needs + attachment working models → crystallized schemas
- **Domain formation:**
  - Insecure attachment (any type) → Domain I (Disconnection/Rejection) schemas
  - Anxious/Preoccupied attachment → Abandonment, Subjugation, Approval-Seeking
  - Avoidant attachment → Emotional Deprivation, Emotional Inhibition
  - Disorganized attachment → Mistrust/Abuse
- **Moderator:** Child temperament influences susceptibility to schema formation

**Stage 4: Personality Trait Consolidation (adolescence–early adulthood)**
- **Mechanism:** Schemas + temperament + ongoing experiences → stable trait profiles
- **Empirical support:** Longitudinal studies show temperament (ages 10–16) predicts Big Five (ages 14–26)
- **Pathways:**
  - High schemas + negative temperament → High Neuroticism, High Detachment
  - Compensation strategies → High Antagonism (overcompensation for Defectiveness)

**Stage 5: Relational Patterns (adulthood)**
- **Mechanism:** Traits + schemas + attachment → interpersonal circumplex position
- **Examples:**
  - Anxious attachment + high Neuroticism → Low Agency, High Communion (submissive-warm)
  - Avoidant attachment + high Detachment → Low Agency, Low Communion (withdrawn)
  - Narcissistic schemas + low Agreeableness → High Agency, Low Communion (dominant-cold)

**Stage 6: Psychopathology Risk (across lifespan)**
- **Mechanism:** Personality pathology (AMPD) + life stress → clinical syndromes (HiTOP)
- **Cascade dynamics:**
  - Early insecurity → emotion dysregulation → social problems → psychopathology
  - Each stage compounds risk for the next
- **p-factor accumulation:** More stages with dysfunction → higher general psychopathology

### Key Developmental Principles

1. **Continuity:** Temperament → personality traits show moderate stability (r ≈ .30–.50 over decades)
2. **Cascade effects:** Early dysfunction spreads to multiple domains over time
3. **Gene × Environment:** Biology sets range, environment determines position within range
4. **Equifinality:** Multiple pathways to same outcome (e.g., high Neuroticism from genes OR schemas)
5. **Multifinality:** Same starting point can lead to different outcomes (temperament moderated by environment)

## 9. Master Cross-Mapping Table

### Legend
- **→** : Strong empirical correlation (r ≥ .40)
- **⇢** : Moderate correlation (r = .20–.39)
- **⤏** : Weak/theoretical link (r < .20 or no direct data)
- **⊗** : Inverse relationship (negative correlation)

---

### AMPD Domain: **Negative Affectivity**

| **PID-5 Facet** | **FFM Equivalent** | **Schema(s)** | **Attachment** | **Triarchic** | **Dark Triad** | **IPC** | **PAD** | **HiTOP** |
|----------------|-------------------|--------------|---------------|--------------|---------------|---------|---------|-----------|
| **Emotional Lability** | Neuroticism (N6 Vulnerability) → | Abandonment, Emotional Deprivation | Anxious/Preoccupied | ⊗ Boldness (low) | — | Low Agency, Variable Communion | Low P, High A, Low D | Internalizing (Distress) |
| **Anxiousness** | Neuroticism (N1) → | Abandonment, Vulnerability to Harm | Anxious/Preoccupied | ⊗ Boldness (low) | — | Low Agency, Low Communion | Low P, High A, Low D | Internalizing (Fear) |
| **Separation Insecurity** | Neuroticism (⇢ N6) → | Abandonment, Dependence | Anxious/Preoccupied | — | — | Low Agency, High Communion | Low P, High A, Low D | Internalizing (Distress) |
| **Submissiveness** | ⊗ Extraversion (⊗E3 Assertiveness) ⇢, ⊗ low Dominance | Subjugation, Dependence | Preoccupied | ⊗ Boldness (low) | — | Low Agency, High Communion | Variable P, Variable A, Low D | Internalizing (subfactor) |
| **Hostility** | Neuroticism (N2 Angry Hostility) →, ⊗ Agreeableness (⊗A4 Compliance) | Mistrust/Abuse, Punitiveness | Disorganized ⇢ | ⤏ Disinhibition | Psychopathy (Meanness ⇢) | Variable (can be high or low agency) | Low P, High A, Variable D | Antagonistic Externalizing ⇢ |
| **Perseveration** | Neuroticism (⇢ rumination) | Unrelenting Standards, Negativity/Pessimism | — | — | — | — | Low P, High A, Variable D | Internalizing (Distress) |
| **Depressivity** | Neuroticism (N6 Vulnerability, depressive facet) → | Defectiveness/Shame, Failure, Emotional Deprivation | Insecure (any) → | — | — | Low Agency, Low Communion | Low P, Low A, Low D | Internalizing (Distress) → |

---

### AMPD Domain: **Detachment**

| **PID-5 Facet** | **FFM Equivalent** | **Schema(s)** | **Attachment** | **Triarchic** | **Dark Triad** | **IPC** | **PAD** | **HiTOP** |
|----------------|-------------------|--------------|---------------|--------------|---------------|---------|---------|-----------|
| **Withdrawal** | ⊗ Extraversion (⊗E1 Warmth →, ⊗E2 Gregariousness →) | Social Isolation, Emotional Deprivation | Avoidant (Fearful or Dismissive) → | ⊗ Boldness (low sociability) | — | Low Agency, Low Communion → | Low P, Low A, Low D | Detachment → |
| **Intimacy Avoidance** | ⊗ Extraversion (⊗E1 Warmth →) | Mistrust/Abuse, Defectiveness/Shame | Avoidant (Dismissive or Fearful) → | Meanness ⇢ | Psychopathy (affective detachment) | Low Communion → | Low P, Variable A, Variable D | Detachment → |
| **Anhedonia** | ⊗ Extraversion (⊗E6 Positive Emotions →), Neuroticism (Depressivity) | Emotional Deprivation, Social Isolation | Avoidant ⇢ | — | — | Low Agency, Low Communion | Low P, Low A, Low D | Detachment, Internalizing (overlap) |
| **Restricted Affectivity** | ⊗ Extraversion (⊗E6 Positive Emotions ⇢), ⊗ Agreeableness (⊗warmth) | Emotional Inhibition, Mistrust/Abuse | Avoidant (Dismissive) → | Meanness → | Psychopathy (callousness overlap) | Low Communion → | Low P, Low A, Variable D | Detachment → |
| **Suspiciousness** | ⊗ Agreeableness (⊗A1 Trust →) | Mistrust/Abuse → | Disorganized, Fearful-Avoidant | Disinhibition ⇢ | Psychopathy ⇢ | Low Communion, Variable Agency | Low P (toward others), High A, Variable D | Detachment, Thought Disorder ⇢ |

---

### AMPD Domain: **Antagonism**

| **PID-5 Facet** | **FFM Equivalent** | **Schema(s)** | **Attachment** | **Triarchic** | **Dark Triad** | **IPC** | **PAD** | **HiTOP** |
|----------------|-------------------|--------------|---------------|--------------|---------------|---------|---------|-----------|
| **Manipulativeness** | ⊗ Agreeableness (⊗A2 Straightforwardness →) | Entitlement, Mistrust/Abuse | Dismissive-Avoidant ⇢ | Boldness ⇢, Disinhibition ⇢ | Machiavellianism →, Psychopathy ⇢ | High Agency, Low Communion | Variable P, Variable A, High D | Antagonistic Externalizing → |
| **Deceitfulness** | ⊗ Agreeableness (⊗A2 Straightforwardness →) | Entitlement, Mistrust/Abuse | — | Disinhibition → | Machiavellianism →, Psychopathy ⇢ | High Agency, Low Communion | Variable P, Variable A, High D | Antagonistic Externalizing → |
| **Grandiosity** | ⊗ Agreeableness (⊗A5 Modesty →) | Entitlement/Grandiosity → | Dismissive-Avoidant ⇢ | Boldness → | Narcissism → | High Agency, Variable Communion | High P (self), Variable A, High D | Antagonistic Externalizing → |
| **Attention Seeking** | Extraversion (E assertive/gregarious ⇢), ⊗ Agreeableness (⊗modesty) | Approval-Seeking → | Preoccupied ⇢ | Boldness → | Narcissism → | High Agency, High Communion ⇢ | High P (when attended), High A, High D | Antagonistic Externalizing ⇢ |
| **Callousness** | ⊗ Agreeableness (⊗A3 Altruism →, ⊗warmth) | Mistrust/Abuse, Emotional Inhibition | Avoidant (Dismissive), Disorganized | Meanness → | Psychopathy →, Sadism → | High Agency, Low Communion → | Low P (to others), Low A, High D | Antagonistic Externalizing → |

---

### AMPD Domain: **Disinhibition**

| **PID-5 Facet** | **FFM Equivalent** | **Schema(s)** | **Attachment** | **Triarchic** | **Dark Triad** | **IPC** | **PAD** | **HiTOP** |
|----------------|-------------------|--------------|---------------|--------------|---------------|---------|---------|-----------|
| **Irresponsibility** | ⊗ Conscientiousness (⊗C3 Dutifulness →, ⊗C8 Self-Discipline ⇢) | Insufficient Self-Control, Entitlement | — | Disinhibition → | Psychopathy ⇢ | Variable | Variable P, Variable A, Low D | Disinhibited Externalizing → |
| **Impulsivity** | Neuroticism (N5 Impulsiveness ⇢), ⊗ Conscientiousness (⊗C6 Deliberation →) | Insufficient Self-Control → | — | Disinhibition → | Psychopathy ⇢ | Variable | Variable P, High A, Variable D | Disinhibited Externalizing → |
| **Distractibility** | ⊗ Conscientiousness (⊗C5 Competence ⇢) | Failure ⇢ | — | Disinhibition ⇢ | — | Low Agency (poor self-regulation) | Variable P, High A, Low D | Disinhibited Externalizing ⇢ |
| **Risk Taking** | Extraversion (E5 Excitement Seeking ⇢), ⊗ Conscientiousness (⊗deliberation) | Insufficient Self-Control, Entitlement | — | Boldness → (venturesome), Disinhibition ⇢ (reckless) | Psychopathy ⇢ | High Agency ⇢ | Variable P, High A, High D | Disinhibited Externalizing ⇢ |
| **Rigid Perfectionism** (reversed facet) | Conscientiousness (C4 Achievement Striving ⇢, C1 Order ⇢) | Unrelenting Standards → | — | — | — | High Agency | Low P (never satisfied), High A, High D | Internalizing (OC features) ⇢ |

---

### AMPD Domain: **Psychoticism**

| **PID-5 Facet** | **FFM Equivalent** | **Schema(s)** | **Attachment** | **Triarchic** | **Dark Triad** | **IPC** | **PAD** | **HiTOP** |
|----------------|-------------------|--------------|---------------|--------------|---------------|---------|---------|-----------|
| **Unusual Beliefs/Experiences** | Openness (O1 Fantasy ⇢ SIFFM-specific) | Mistrust/Abuse (paranoid themes) | Disorganized ⇢ | — | — | Variable | Low P, High A (vigilance), Variable D | Thought Disorder → |
| **Eccentricity** | Openness (variable, weak) | Social Isolation ⇢ | — | — | — | Low Communion ⇢ | Variable | Thought Disorder → |
| **Cognitive/Perceptual Dysregulation** | ⊗ Conscientiousness (⊗competence ⇢), Neuroticism ⇢ | Vulnerability to Harm ⇢ | Disorganized ⇢ | — | — | Variable | Low P, High A, Low D | Thought Disorder → |

---

### Additional Cross-Mappings

**Schemas → IPC (Inferential)**
- **Abandonment, Subjugation, Approval-Seeking** → Low Agency, High Communion (submissive-warm quadrant)
- **Mistrust/Abuse, Emotional Deprivation** → Low Communion (cold/distant)
- **Entitlement, Grandiosity** → High Agency, Low Communion (dominant-cold quadrant)
- **Emotional Inhibition, Social Isolation** → Low Agency, Low Communion (withdrawn quadrant)

**Temperament → Attachment (Developmental)**
- **High Negative Emotionality (infant)** + inconsistent caregiving → Anxious/Preoccupied attachment
- **High Negative Emotionality** + rejecting caregiving → Fearful-Avoidant attachment
- **Low Negative Emotionality** + rejecting caregiving → Dismissive-Avoidant attachment
- **Any temperament** + severe trauma/abuse → Disorganized attachment

---

## 10. Integration for Cognitive Architecture

### Unified Dimensional Space

Our cognitive architecture can use **AMPD/PID-5 as the primary trait representation** because:
1. It's empirically grounded in the FFM
2. It's dimensional (continuous, not categorical)
3. It integrates pathology seamlessly (no artificial normal/abnormal boundary)
4. It maps cleanly to HiTOP for psychopathology modeling
5. It maps to triarchic psychopathy for antisocial/Dark Triad characters

### Parameterization Strategy

**Character definition requires:**

1. **Trait profile** (25 PID-5 facets, standardized scores)
   - Derived from: FFM + pathology level + character concept
   - Enables: Baseline behavioral tendencies, coping styles

2. **Schema profile** (18 EMS, strength ratings 0–10)
   - Derived from: Backstory (childhood experiences) + attachment history
   - Enables: Memory-triggered appraisals, relationship expectations, core fears

3. **Attachment style** (4-dimensional: model of self, model of other, anxiety, avoidance)
   - Derived from: Early caregiving experiences (backstory)
   - Enables: Relationship formation patterns, intimacy responses

4. **PAD baselines** (P, A, D resting states)
   - Derived from: Trait profile (see mapping table)
   - Enables: Emotional starting point for appraisals

5. **IPC position** (Agency, Communion scores)
   - Derived from: Trait profile (E, A facets) + schema compensation patterns
   - Enables: Interpersonal style, dominance/warmth in interactions

### Appraisal → Emotion → Behavior Chain

**Input:** Event description

**Step 1: Schema-driven appraisal**
- Active schemas filter event interpretation
- Example: Abandonment schema → interprets neutral goodbye as rejection

**Step 2: Trait-moderated emotional response**
- Trait profile determines PAD shift magnitude
- High Emotional Lability → large P/A swings
- High Restricted Affectivity → dampened A response

**Step 3: PAD state calculation**
- Baseline PAD + appraisal-driven delta → current PAD state

**Step 4: Behavior generation**
- IPC position + current PAD + coping mode → action tendencies
- High Agency + High Arousal + Punitive Parent mode → aggressive confrontation
- Low Agency + High Arousal + Vulnerable Child mode → anxious withdrawal

**Step 5: Memory consolidation**
- Event + appraisal + outcome → episodic memory with salience score
- Salience influenced by: Emotional intensity (A), schema relevance, surprise

### Character Profiles: Worked Examples

**Character: Penelope Pitstop (BASELINE profile)**

| Dimension | Value | Rationale |
|-----------|-------|-----------|
| **AMPD Traits** | | |
| Negative Affectivity | 65 | Anxious, vulnerable, emotionally labile |
| - Anxiousness | 75 | High fear, worry |
| - Emotional Lability | 70 | Reactive to threats |
| - Submissiveness | 80 | Defers to others |
| Detachment | 20 | Socially engaged, warm |
| Antagonism | 15 | Trusting, non-manipulative |
| Disinhibition | 30 | Generally responsible |
| Psychoticism | 10 | Conventional thinking |
| **Schemas (top 3)** | | |
| Abandonment/Instability | 8/10 | Core fear of being left alone |
| Vulnerability to Harm | 7/10 | Expects catastrophe |
| Dependence/Incompetence | 6/10 | Doubts own capability |
| **Attachment** | Anxious/Preoccupied | Negative self, positive other |
| **PAD Baseline** | Low P (40), High A (70), Low D (30) | Anxious, hypervigilant, powerless |
| **IPC Position** | Low Agency, High Communion | Submissive-warm quadrant |

**Behavioral prediction:** When threatened → activates Vulnerable Child mode → seeks rescuer (high Communion), expresses distress (high Arousal), does not self-rescue (low Agency).

---

**Character: Dick Dastardly (ANTISOCIAL profile)**

| Dimension | Value | Rationale |
|-----------|-------|-----------|
| **AMPD Traits** | | |
| Negative Affectivity | 45 | Elevated Hostility, low Anxiousness |
| - Anxiousness | 20 | Fearless |
| - Hostility | 75 | Irritable, vengeful |
| Detachment | 55 | Restricted affect, low intimacy |
| Antagonism | 85 | High across all facets |
| - Manipulativeness | 90 | Schemes constantly |
| - Deceitfulness | 95 | Chronic lying |
| - Callousness | 80 | No empathy |
| - Grandiosity | 70 | Overestimates abilities |
| Disinhibition | 75 | Impulsive, irresponsible |
| - Impulsivity | 80 | Acts without planning |
| - Irresponsibility | 75 | Disregards consequences |
| **Schemas (top 3)** | | |
| Mistrust/Abuse | 9/10 | Expects betrayal, strikes first |
| Entitlement/Grandiosity | 8/10 | Rules don't apply to him |
| Punitiveness | 7/10 | Others deserve suffering |
| **Attachment** | Dismissive-Avoidant | Positive self, negative other |
| **Triarchic Profile** | Meanness (High), Disinhibition (High), Boldness (Moderate) | |
| **PAD Baseline** | Variable P (context), Moderate A (60), High D (80) | Dominant, aroused by conflict |
| **IPC Position** | High Agency, Low Communion | Dominant-cold quadrant |

**Behavioral prediction:** When thwarted → activates Overcompensator mode → schemes manipulation (high Antagonism), acts impulsively when frustrated (high Disinhibition), shows no remorse (Callousness).

---

## 11. Research Gaps and Limitations

### Well-Established Mappings (r ≥ .40)
- AMPD ↔ Big Five domain level
- FFM facets ↔ DSM-IV PDs (meta-analytic)
- Triarchic psychopathy ↔ PID-5 items (direct scale construction)
- Attachment styles ↔ Schema Domain I (conceptual + longitudinal)
- HiTOP spectra ↔ AMPD domains (structural alignment)

### Moderate Evidence (r = .20–.39, or conceptual with some empirical support)
- Schema therapy modes ↔ AMPD traits (Bach et al. conceptual analysis)
- IPC ↔ Big Five facets (Wiggins & Trapnell domain-level work)
- PAD ↔ Big Five (Mehrabian 1996, but limited replication)
- Temperament → Big Five (longitudinal correlations, but moderate r)

### Weak/Speculative Links
- **Openness/Psychoticism relationship:** Most contested mapping; varies by instrument (SIFFM vs. NEO)
- **PAD ↔ AMPD:** No direct empirical studies found; inferred from Big Five bridge
- **Schemas ↔ IPC:** Theoretical inference, no direct measurement
- **Dark Tetrad Sadism ↔ AMPD:** Emerging research, limited validated mapping

### Missing Research
1. **Direct PID-5 ↔ 18 EMS correlation matrix** — Bach discusses conceptual links, but no facet-level empirical table published
2. **PAD state changes from personality traits** — Mehrabian shows baseline PAD, but not dynamic appraisal-driven shifts
3. **Attachment styles ↔ HiTOP spectra** — Attachment rarely included in HiTOP empirical studies
4. **Developmental cascade validation** — Most evidence is cross-sectional or short-term longitudinal; few studies span infancy → adulthood with all constructs measured

## 12. Implementation Recommendations

### For Wacky Manor Cognitive Architecture

**Phase 1: Core parameterization (current)**
- Define each character via PID-5 25-facet profile
- Derive PAD baselines from trait mappings (Table 6 formulas)
- Compute IPC position from E/A facets

**Phase 2: Schema layer (next)**
- Add EMS strength ratings (0–10) for top 5–8 schemas per character
- Implement schema-triggered appraisals in memory retrieval
- Link schema activation → PAD state shifts

**Phase 3: Attachment patterns (integration)**
- Define attachment style (4-category or dimensional)
- Implement relationship formation rules based on attachment
- Model intimacy avoidance vs. anxious clinging in social interactions

**Phase 4: Dynamic modes (advanced)**
- Implement schema therapy mode shifts (Vulnerable Child, Overcompensator, etc.)
- Mode determines active coping strategy + appraisal filter
- Triggered by: emotional intensity, schema activation, social context

### Validation Strategy

**Comparative evaluation:**
1. Generate behavior for same scenario across characters
2. Human raters assess "in-character" fit
3. Compare BASELINE (current) vs. GENERIC (simplified) vs. AMPD-FULL (all 25 facets)

**Expected differentiation:**
- BASELINE: Moderate differentiation (personality archetypes)
- AMPD-FULL: High differentiation (facet-level nuance)
- AMPD+Schemas: Highest differentiation (memory-triggered individual responses)

---

## References

### Primary Sources Cited

**AMPD ↔ Big Five:**
- Gore, W. L., & Widiger, T. A. (2013). The DSM-5 dimensional trait model and five-factor models of general personality. *Journal of Abnormal Psychology*, *122*(3), 816–821.
- Samuel, D. B., & Widiger, T. A. (2008). A meta-analytic review of the relationships between the five-factor model and DSM-IV-TR personality disorders: A facet level analysis. *Clinical Psychology Review*, *28*(8), 1326–1342. [PMC2614445](https://pmc.ncbi.nlm.nih.gov/articles/PMC2614445/)

**Schemas ↔ Attachment:**
- Simard, V., Moss, E., & Pascuzzo, K. (2011). Early maladaptive schemas and child and adult attachment: A 15-year longitudinal study. *Psychology and Psychotherapy: Theory, Research and Practice*, *84*(4), 349–366. [PubMed](https://pubmed.ncbi.nlm.nih.gov/22903880/)

**Schemas ↔ AMPD:**
- Bach, B., & Bernstein, D. P. (2019). Schema therapy conceptualization of personality functioning and traits in ICD-11 and DSM-5. *Current Opinion in Psychiatry*, *32*(1), 38–49. [PubMed](https://pubmed.ncbi.nlm.nih.gov/30299307/)

**Triarchic Psychopathy ↔ AMPD:**
- Drislane, L. E., Sellbom, M., Brislin, S. J., et al. (2019). Improving characterization of psychopathy within the DSM-5 AMPD: Creation and validation of PID-5 Triarchic scales. *Personality Disorders: Theory, Research, and Treatment*, *10*(6), 511–521. [PMC6817378](https://pmc.ncbi.nlm.nih.gov/articles/PMC6817378/)

**IPC ↔ Big Five:**
- Wiggins, J. S., & Trapnell, P. D. (1996). A dyadic-interactional perspective on the five-factor model. In J. S. Wiggins (Ed.), *The five-factor model of personality: Theoretical perspectives* (pp. 82–162). Guilford Press.

**PAD ↔ Big Five:**
- Mehrabian, A. (1996). Analysis of the Big-five Personality Factors in terms of the PAD Temperament Model. *Australian Journal of Psychology*, *48*(2), 86–92. [DOI](https://doi.org/10.1080/00049539608259510)

**HiTOP:**
- Kotov, R., Krueger, R. F., Watson, D., et al. (2017). The Hierarchical Taxonomy of Psychopathology (HiTOP): A dimensional alternative to traditional nosologies. *Journal of Abnormal Psychology*, *126*(4), 454–477. [APA PDF](https://www.apa.org/pubs/journals/features/abn-abn0000258.pdf)

**Developmental Pathways:**
- Shiner, R. L., & Caspi, A. (2003). Personality differences in childhood and adolescence: Measurement, development, and consequences. *Journal of Child Psychology and Psychiatry*, *44*(1), 2–32.
- Caspi, A., Roberts, B. W., & Shiner, R. L. (2005). Personality development: Stability and change. *Annual Review of Psychology*, *56*, 453–484.

**Schema Therapy Foundation:**
- Young, J. E., Klosko, J. S., & Weishaar, M. E. (2003). *Schema therapy: A practitioner's guide*. Guilford Press.

**Attachment Theory:**
- Bartholomew, K., & Horowitz, L. M. (1991). Attachment styles among young adults: A test of a four-category model. *Journal of Personality and Social Psychology*, *61*(2), 226–244.

---

## Appendix: Quick Reference Tables

### A. Big Five → AMPD Domain Mapping

| FFM | AMPD | Relationship |
|-----|------|--------------|
| Neuroticism | Negative Affectivity | High N → High Negative Affectivity |
| Extraversion | Detachment | Low E → High Detachment |
| Agreeableness | Antagonism | Low A → High Antagonism |
| Conscientiousness | Disinhibition | Low C → High Disinhibition |
| Openness | Psychoticism | Weak/debated |

### B. Attachment → Schema Domain I

| Attachment Style | Primary Schemas |
|-----------------|-----------------|
| Secure | Low across all schemas |
| Anxious/Preoccupied | Abandonment, Dependence, Subjugation, Approval-Seeking |
| Dismissive-Avoidant | Emotional Deprivation, Emotional Inhibition |
| Fearful-Avoidant | Mistrust/Abuse, Defectiveness/Shame, Social Isolation |
| Disorganized | Mistrust/Abuse, Vulnerability to Harm |

### C. Triarchic → PID-5 Primary Facets

| Triarchic Dimension | PID-5 Facets (High) | PID-5 Facets (Low) |
|--------------------|---------------------|-------------------|
| Boldness | Attention Seeking, Grandiosity, Risk-Taking | Anxiousness, Submissiveness, Withdrawal |
| Meanness | Callousness, Restricted Affectivity, Intimacy Avoidance | Empathy (not in PID-5) |
| Disinhibition | Impulsivity, Irresponsibility, Hostility, Deceitfulness | (none reversed) |

### D. Big Five → PAD Baselines

| FFM | P | A | D |
|-----|---|---|---|
| High Extraversion | High | Moderate | High |
| High Agreeableness | High | High | Low |
| High Conscientiousness | High | — | High |
| Low Neuroticism (Emotional Stability) | High | Low | — |
| High Openness | — | High | High |

### E. IPC Quadrants → AMPD Profiles

| IPC Quadrant | Agency | Communion | AMPD Profile (High) |
|--------------|--------|-----------|-------------------|
| PA (Assured-Dominant) | High | High | Low pathology (healthy) |
| DE (Cold-Hearted) | High | Low | Antagonism, Detachment |
| HI (Unassured-Submissive) | Low | Low | Negative Affectivity, Detachment |
| LM (Warm-Agreeable) | Low | High | Negative Affectivity (Submissiveness) |

---

**End of Synthesis Document**
