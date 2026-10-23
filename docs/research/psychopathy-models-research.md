# Psychopathy Models Research Report
## Comprehensive Analysis of PCL-R, Triarchic Model, Dark Triad/Tetrad, and Cross-Mappings to AMPD and Big Five

**Date:** 2026-10-07
**Purpose:** Inform cognitive architecture design for LLM character personalities, particularly antagonist/villain characters resistant to prosocial drift

---

## Executive Summary

This report synthesizes research on psychopathy dimensional models to support cognitive architecture development. Key insights:

1. **Psychopathy is multidimensional** — PCL-R's four factors (Interpersonal, Affective, Lifestyle, Antisocial) and the Triarchic Model (Boldness, Meanness, Disinhibition) provide complementary views
2. **Affective inversion is real but nuanced** — Psychopaths don't simply flip kindness → contempt, but rather: (a) fail to process distress cues as aversive via VIM dysfunction, (b) strategically exploit trust as opportunity, (c) view warmth as weakness signaling exploitability
3. **Two subtypes exist** — Primary (fearless, low anxiety, hereditary) vs. Secondary (anxious, high neuroticism, experiential)
4. **Cross-mappings are robust** — Strong empirical links between all major models allow translation across frameworks

---

## 1. PCL-R Four-Factor Model (Hare, 2003)

### Structure

The PCL-R comprises 20 items organized into four correlated factors:

**Factor 1: Interpersonal** (Items 1, 2, 4, 5)
- Glib/superficial charm
- Grandiose sense of self-worth
- Pathological lying
- Conning/manipulative behavior

**Factor 2: Affective** (Items 6, 7, 8, 16)
- Lack of remorse or guilt
- Shallow affect
- Callous/lack of empathy
- Failure to accept responsibility for actions

**Factor 3: Lifestyle** (Items 3, 9, 13, 14, 15)
- Need for stimulation/proneness to boredom
- Parasitic lifestyle
- Lack of realistic long-term goals
- Impulsivity
- Irresponsibility

**Factor 4: Antisocial** (Items 10, 12, 18, 19, 20)
- Poor behavioral controls
- Early behavior problems
- Juvenile delinquency
- Revocation of conditional release
- Criminal versatility

**Note:** Items 11 (promiscuous sexual behavior) and 17 (many short-term relationships) don't load on any factor but count toward total score.

### Relationship to Traditional Two-Factor Model

The four-factor model refines the traditional structure:
- **PCL-R Factor 1** (interpersonal/affective) splits into Interpersonal + Affective factors
- **PCL-R Factor 2** (lifestyle/antisocial) splits into Lifestyle + Antisocial factors

### Interpersonal Manifestations

**Interpersonal facet behaviors:**
- Smooth talking, engaging, charming, slick, verbally facile
- Never shy, self-conscious, or afraid to say anything
- Opinions on everything; boasting about skills and accomplishments
- Lying as natural communication mode, not nervousness-driven

**Affective facet behaviors:**
- Deep indifference to feelings, rights, or suffering of others
- No guilt or remorse for harm caused
- Emotions are shallow, short-lived, performative
- Externalizes blame; never accepts responsibility

---

## 2. Triarchic Model (Patrick, Fowles & Krueger, 2009)

### Three Phenotypic Constructs

**Boldness**
- Definition: Nexus of social dominance, emotional resiliency, and venturesomeness
- Components: Confidence, social assertiveness, fearlessness, stress immunity, social potency
- Temperamental basis: Low trait fear, high reward sensitivity
- Interpersonal style: Dominant, unflappable, socially assured

**Meanness**
- Definition: Dysaffiliated agency — aggressive resource seeking without regard for others
- Components: Deficient empathy, lack of affiliative capacity, **contempt toward others**, predatory exploitativeness, empowerment through cruelty/destructiveness
- Temperamental basis: Low affiliative capacity, low empathy
- Interpersonal style: Callous, contemptuous, exploitative

**Disinhibition**
- Definition: General propensity toward impulse control problems
- Components: Impulsiveness, weak behavioral restraint, hostility/mistrust, emotion dysregulation, lack of planfulness
- Temperamental basis: Poor impulse control, high negative emotionality (in some variants)
- Interpersonal style: Impulsive, irresponsible, reactive

### Construct Relationships

- Disinhibition + Meanness = classic psychopathy profile
- Disinhibition + Boldness = secondary psychopathy (anxious, reactive)
- Meanness + Boldness = primary psychopathy (fearless, callous)
- All three can co-occur but are conceptually separable

### Developmental Origins

**Boldness:** Low fear reactivity (heritable temperament) + early experiences of mastery/dominance
**Meanness:** Low affiliative capacity (heritable) + harsh/neglectful caregiving + lack of socialization
**Disinhibition:** Poor effortful control (heritable) + chaotic environment + trauma/adversity

---

## 3. Dark Triad and Dark Tetrad

### Dark Triad (Paulhus & Williams, 2002)

Three subclinical malevolent traits:

**Narcissism**
- Grandiosity, arrogance, excessive self-love
- Need for attention and affirmation
- Entitlement, superiority

**Machiavellianism**
- Calculating, cunning, manipulative interpersonal style
- Cynical, misanthropic worldview
- **Strategic long-term planning** (distinguishes from psychopathy's impulsivity)
- Patient influence, delayed gratification
- Understands morality but doesn't value it

**Psychopathy (subclinical)**
- Callousness, impulsivity, thrill-seeking
- Lack of empathy, shallow affect
- Short-term exploitation

**Common Core:** Low Agreeableness (only Big Five trait consistently correlated with all three)

### Dark Tetrad: Adding Sadism (Buckels et al., 2013)

**Sadism** — the fourth dimension:
- Intrinsic pleasure from others' suffering
- Appetite for cruelty as rewarding in itself
- **Key distinction:** Sadists ENJOY cruelty; psychopaths are INDIFFERENT to it
- Predicts unprovoked aggression beyond the Dark Triad
- Uniquely predicts willingness to work for opportunity to harm innocents

**Unique Variance:** Sadism adds predictive power for:
- Delinquent behavior in adolescents (beyond Dark Triad)
- Harmful behavior toward living creatures
- Brutal dispositions
- Criminal recidivism

---

## 4. Affective Inversion: How Psychopaths Process Prosocial Cues

### The Violence Inhibition Mechanism (VIM) — Blair (2005, 2013)

**Normal Function:**
1. Distress cues (sad/fearful expressions) activate VIM
2. VIM triggers increased autonomic activity + attention
3. Aversive emotional response to others' distress
4. Through socialization: association between action → distress → aversion
5. Result: Moral development (avoiding actions that cause distress becomes internally motivated)

**VIM Dysfunction in Psychopathy:**
- Reduced autonomic arousal to sad/fearful expressions
- Impaired recognition of fear and sadness (but NOT anger)
- Distress cues don't trigger aversive response
- Failed moral socialization: no internal brake on harmful actions

**Neural Basis:**
- Amygdala dysfunction (stimulus-reinforcement learning)
- Ventromedial prefrontal cortex (vmPFC) / orbitofrontal cortex (OFC) impairment
- Anterior cingulate cortex (ACC) dysfunction
- Impaired aversive conditioning, passive avoidance learning, operant extinction

### Baskin-Sommers Attention Bottleneck Model

**Key Finding:** Psychopaths don't have global emotional deficits — they have **selective attention allocation deficits**

- **Exaggerated attention bottleneck** filters information serially, not simultaneously
- When threat/distress cues are **goal-relevant** → normal emotional response
- When threat/distress cues are **peripheral to current goal** → blunted response

**Implication:** Psychopaths can process distress cues normally when attending to them directly, but fail to notice them when focused elsewhere. This creates:
- Advantage: Superior focus, filtering of distractors
- Disadvantage: Miss important contextual emotional information

### Strategic Exploitation of Trust and Kindness

**Trust Game Studies:**
- Psychopaths show **strategic defection** on low-value partners
- Conditional exploitation based on perceived utility
- Manipulation of cooperative norms to extract resources
- Proficient at reading others' reactions and adjusting behavior to maximize manipulation success

**Interpretation of Prosocial Cues:**
- Kindness → signal of exploitability (not inherent threat)
- Trust → opportunity for advantage (not social contract)
- Warmth → weakness to be leveraged (not invitation to reciprocate)
- Empathy → gullibility deserving exploitation

**Key Mechanism:** Not simple inversion (kind → bad), but **reinterpretation through instrumental lens**:
- "They're being kind" → "They're vulnerable"
- "They trust me" → "They're naive"
- "They care about me" → "I have leverage"

### Contempt as Core Affective Response

From Psychology Today: "At the heart of the psychopath are spite and contempt"
- Contempt toward those perceived as inferior or weak
- Spite: pleasure from harming others, even at personal cost
- Rejection of others as "contemptible and unworthy"
- Downtrodden fate of victims seen as deserved

---

## 5. Developmental Origins of Psychopathic Traits

### Callous-Unemotional (CU) Traits in Childhood (Frick, Viding)

**Stability:**
- Moderately stable from childhood to adolescence (r = .4 to .6)
- Four trajectories: Stable High (4.7%), Increasing (7.3%), Decreasing (13.4%), Stable Low (74.6%)

**Genetic vs. Environmental Contributions:**
- **Genetic factors:** Drive both stability AND change across age
- **Nonshared environmental factors:** Drive change only
- Heritability estimates: .45 to .67 (moderate to high)

**Temperamental Precursors (STAR Model):**

Two inherited temperaments create risk pathway:

1. **Fearlessness**
   - Central developmental precursor
   - Difficulty recognizing/learning from threat, distress, punishment signals
   - Impaired aversive conditioning

2. **Low Affiliative Reward**
   - Reduced pleasure from social connection
   - Low interpersonal emotional sensitivity
   - Diminished capacity for bonding

**Environmental Moderation:**
- **Harsh/threatening environment** + fearless temperament = HIGH CU risk
- **Low affiliative input** (neglect, coldness) + low affiliative capacity = HIGH CU risk
- **Positive parenting** = PROTECTIVE even with high genetic/temperamental risk
- **Heritable ≠ unchangeable** — parenting interventions effective

**Is There a Purely Experiential Pathway?**

**Primary Psychopathy:** Strong hereditary component, low anxiety, fearless dominance
- Appears even in favorable environments
- Driven by neurobiological deficits (VIM, amygdala)

**Secondary Psychopathy:** Higher environmental influence, anxious, neurotic
- Associated with trauma, adversity, chaos
- May represent reactive adaptation to hostile environment
- High Neuroticism + low Agreeableness + low Conscientiousness

**Conclusion:** No pure experiential pathway for PRIMARY psychopathy (requires temperamental substrate), but secondary variant shows stronger environmental loading.

---

## 6. Cross-Model Mapping Tables

### 6.1 Triarchic ↔ PCL-R Four Factors

| Triarchic Dimension | Primary PCL-R Facet | Secondary PCL-R Facets | Notes |
|---------------------|---------------------|------------------------|-------|
| **Boldness** | Interpersonal | — | Charm, grandiosity, manipulation; predicts PCL-R Factor 1 Interpersonal facet |
| **Meanness** | Affective | — | Callousness, lack of remorse, shallow affect; predicts PCL-R Factor 1 Affective facet |
| **Disinhibition** | Lifestyle | Antisocial | Impulsivity, irresponsibility, lack of goals; predicts PCL-R Factor 2 |
| **All three** | — | Antisocial | All Triarchic scales contribute separately to PCL-R Antisocial facet |

**Summary:** 
- PCL-R Factor 1 = Meanness + Boldness (interpersonal/affective)
- PCL-R Factor 2 = Disinhibition + Meanness (lifestyle/antisocial)

---

### 6.2 Triarchic ↔ Big Five Domains & Facets

| Triarchic | Big Five Domain Correlations | Big Five Facet Patterns |
|-----------|------------------------------|-------------------------|
| **Boldness** | **High Extraversion**<br>**Low Neuroticism**<br>Low Agreeableness (moderate) | **E+:** Gregariousness, Assertiveness, Activity, Excitement-Seeking<br>**N−:** Low Anxiety, Low Self-Consciousness, Low Vulnerability, Low Depression<br>**A−:** Moderate negative |
| **Meanness** | **Very Low Agreeableness**<br>Low Extraversion (moderate)<br>Low Openness (moderate) | **A−:** ALL Agreeableness facets (except Modesty moderate)<br>**E−:** Low Warmth<br>**O−:** Low Feelings (emotional deficits) |
| **Disinhibition** | **Very Low Conscientiousness**<br>**High Neuroticism**<br>Low Agreeableness (moderate) | **C−:** Low Dutifulness, Low Deliberation, Low Self-Discipline<br>**N+:** High Angry Hostility, High Impulsiveness<br>**N−:** Low Anxiety, Low Depression (in primary variant) |

**Variance Explained by Big Five:**
- Boldness: 67%
- Disinhibition: 53%
- Meanness: 44%

**Simple Formula:**
- Boldness ≈ **Low N + High E**
- Meanness ≈ **Low A**
- Disinhibition ≈ **Low C + High N** (secondary) OR **Low C + Low N** (primary)

---

### 6.3 Triarchic ↔ AMPD (PID-5 Facets)

#### Boldness (15 items from PID-5)

| AMPD Domain | PID-5 Facets | Direction |
|-------------|--------------|-----------|
| **Antagonism** | Attention Seeking, Grandiosity, Manipulativeness | Positive |
| **Negative Affect** | Anxiousness, Submissiveness | Negative (reverse-coded) |
| **Disinhibition** | Risk-Taking | Mixed (some positive, some negative) |
| **Detachment** | Anhedonia | Positive |
| **Detachment** | Withdrawal | Negative (reverse-coded) |

**Convergent validity:** r = .79 with TriPM Boldness, r = .78 with PPI-R Fearless Dominance

#### Meanness (21 items from PID-5)

| AMPD Domain | PID-5 Facets | Direction |
|-------------|--------------|-----------|
| **Antagonism** | **Callousness** (11 items — largest component), Grandiosity | Positive |
| **Negative Affect** | Restricted Affectivity (5 items), Hostility | Positive |
| **Negative Affect** | Emotional Lability | Negative (reverse-coded) |
| **Detachment** | Withdrawal, Intimacy Avoidance | Positive/Mixed |

**Convergent validity:** r = .75 with TriPM Meanness, r = −.68 with Empathic Concern

#### Disinhibition (19 items from PID-5)

| AMPD Domain | PID-5 Facets | Direction |
|-------------|--------------|-----------|
| **Disinhibition** | Impulsivity (5 items), Irresponsibility (7 items), Risk-Taking | Positive |
| **Negative Affect** | Hostility, Suspiciousness | Positive |
| **Antagonism** | Deceitfulness | Positive |

**Convergent validity:** r = .73 with TriPM Disinhibition, r = .79 with PPI-R Self-Centered Impulsivity

**Intercorrelations:**
- Meanness–Disinhibition: r = .48–.51
- Boldness–Meanness: r = .20–.27
- Boldness–Disinhibition: r = .00–.26 (weak/nonsignificant)

---

### 6.4 Dark Triad ↔ Big Five

| Dark Trait | Big Five Profile | Facet Highlights |
|------------|------------------|------------------|
| **Narcissism** | **High E, Low A**<br>Low N (grandiose) OR High N (vulnerable) | E+: Assertiveness<br>A−: Low Modesty, Low Compliance<br>N varies by subtype |
| **Machiavellianism** | **Very Low A**<br>Moderate to High C (strategic planfulness) | A−: ALL Agreeableness facets (r = −.47)<br>C+: High Deliberation (long-term planning)<br>C−: Low Straightforwardness |
| **Psychopathy** | **Very Low A, Low C**<br>High N (secondary) OR Low N (primary)<br>Mixed E | A−: ALL Agreeableness facets (r = −.25)<br>C−: Low Dutifulness, Low Deliberation<br>N+: Angry Hostility, Impulsiveness<br>E+: Excitement-Seeking, Assertiveness |

**Only Agreeableness is consistently negative across all three** (r = −.25 to −.47)

---

### 6.5 Dark Triad ↔ AMPD

| Dark Trait | AMPD Profile | Notes |
|------------|--------------|-------|
| **Narcissism (Grandiose)** | **High Antagonism** (.57)<br>Moderate Psychoticism | Antagonism (β = .44) only strong predictor<br>Grandiosity, Manipulativeness, Attention-Seeking facets |
| **Narcissism (Vulnerable)** | **High Negative Affect** (.57)<br>Moderate Disinhibition (.34)<br>Moderate Psychoticism (.38) | Negative Affect (β = .52) only strong predictor<br>Anxiousness, Emotional Lability, Submissiveness facets |
| **Machiavellianism** | **High Antagonism**<br>Low Negative Affect | Antagonism: Manipulativeness, Deceitfulness facets<br>Negative relationship with anxiousness |
| **Psychopathy** | **High Antagonism**<br>**High Disinhibition**<br>**High Detachment**<br>Low Negative Affect | Callousness (Antagonism)<br>Impulsivity, Irresponsibility (Disinhibition)<br>Withdrawal, Intimacy Avoidance (Detachment) |
| **Sadism** | Limited research; likely **High Antagonism (Callousness)**<br>Possibly Psychoticism | Sadism shares antagonistic core but adds intrinsic pleasure from cruelty |

**Network Analysis Finding:** Antagonism is the central hub connecting all Dark Triad/Tetrad traits

---

### 6.6 AMPD Domain Structure (5 Domains, 25 Facets)

| Domain | Core Facets | Additional Facets |
|--------|-------------|-------------------|
| **Negative Affectivity** | Emotional Lability, Anxiousness, Separation Insecurity | Hostility, Perseveration, Submissiveness, (Restricted Affectivity*) |
| **Detachment** | Withdrawal, Anhedonia, Intimacy Avoidance | Depressivity, Suspiciousness |
| **Antagonism** | **Manipulativeness, Deceitfulness, Grandiosity** | **Attention Seeking, Callousness**, Hostility* |
| **Disinhibition** | Distractibility, Impulsivity, Irresponsibility | Risk-Taking, (Rigid Perfectionism*) |
| **Psychoticism** | Unusual Beliefs/Experiences, Eccentricity, Cognitive/Perceptual Dysregulation | — |

*Facets marked with asterisk have been proposed for different domain placement in some research

**Antagonism facet behavioral descriptions:**
- **Manipulativeness:** Using subterfuge to influence/control others; seduction, charm, glibness
- **Deceitfulness:** Dishonesty, fraudulence; embellishment, fabrication
- **Grandiosity:** Believing oneself superior; condescension; overt/covert entitlement
- **Attention Seeking:** Engaging in behavior to be focus of others' attention; exhibitionism
- **Callousness:** Lack of concern for others' feelings/problems; willingness to exploit for gain

---

### 6.7 Primary vs. Secondary Psychopathy (Karpman Distinction)

| Dimension | Primary Psychopathy | Secondary Psychopathy |
|-----------|---------------------|----------------------|
| **Core Trait** | Affective deficit (low fear, low anxiety) | Behavioral dyscontrol (high negative emotionality) |
| **Etiology** | **Heritable temperament** (fearlessness, low affiliative capacity) | **Environmental trauma/adversity** + poor impulse control |
| **Big Five** | **Low N, Low A, Low C**<br>High E (social dominance) | **High N, Low A, Low C**<br>Variable E |
| **AMPD** | High Antagonism (Callousness, Manipulativeness)<br>High Disinhibition<br>**Low Negative Affect** | High Antagonism<br>High Disinhibition<br>**High Negative Affect** (Anxiousness, Hostility) |
| **Triarchic** | **High Boldness + High Meanness**<br>Low to moderate Disinhibition | High Meanness + High Disinhibition<br>**Low Boldness** |
| **PCL-R** | High Factor 1 (Interpersonal/Affective)<br>Moderate Factor 2 | Moderate Factor 1<br>High Factor 2 (Lifestyle/Antisocial) |
| **Emotional Response** | Fearless, calm, low anxiety/guilt | Anxious, hostile, reactive distress |
| **Interpersonal** | Charming, dominant, controlled exploitation | Impulsive aggression, reactive hostility |
| **Developmental** | Early onset, stable, poor treatment response | Later onset, variable course, better treatment response |

---

## 7. Relationship Formation and Interpersonal Behavior

### Attachment Patterns

**Psychopathy → Avoidant Attachment**
- Denial of importance of close relationships
- Avoidance of genuine emotional closeness (threatens control)
- Fear of vulnerability (contradicts need for dominance)
- Fear of intimacy correlates with psychopathy severity

### Relationship Motivations

**NOT:** Mutual emotional connection, intimacy, long-term bonding

**INSTEAD:** Stimulation, status, resources, control
- Superficial bonds easily discarded
- Partners selected for utility, not affection
- Strategic approach: "What can this relationship offer me?"

### Exploitation Dynamics

**Strategic vs. Blanket Exploitation:**
- NOT indiscriminate defection
- Conditional exploitation based on **partner value assessment**
- Factors in exploitation decision:
  - Future interaction likelihood
  - Partner's social status/resources
  - Ease of deception
  - Risk of discovery/consequences

**Trust Game Patterns:**
- Reduced trust toward strangers (avoid being exploited)
- Strategic cooperation when beneficial
- Manipulation of cooperative norms (fake reciprocity to extract resources)
- Proficient at reading reactions and adjusting tactics

### Intimacy Deficits

- Shallow emotional bonds
- Fragile, transient connections
- Cannot form deep bonds (empathy deficit)
- Intimate behaviors without underlying connection
- Commitment/emotional investment avoided

---

## 8. Implications for Cognitive Architecture

### 1. Dimensional Representation (Not Binary)

Model psychopathy traits as **continuous dimensions**, not presence/absence:
- Boldness: 0–100 (social dominance, fearlessness, stress immunity)
- Meanness: 0–100 (callousness, contempt, exploitativeness)
- Disinhibition: 0–100 (impulsivity, irresponsibility, hostility)

### 2. Appraisal Modification via Dimensional Profiles

**Meanness dimension** modifies appraisal of prosocial events:

| Event Type | Normal Appraisal | High Meanness Appraisal |
|------------|------------------|-------------------------|
| Someone shows kindness | Warmth, gratitude, reciprocal impulse | Weakness signal, exploitability cue, contempt |
| Someone trusts you | Responsibility, social contract | Opportunity, naivety, leverage |
| Someone shows vulnerability | Empathy, protective impulse | Exploitable resource, contempt |
| Someone cooperates | Reciprocal fairness norm | Strategic opportunity, potential manipulation |

**Boldness dimension** modifies threat appraisal:
- Low fear → reduced aversive conditioning
- High dominance → interpret challenges as contests to win
- Stress immunity → maintain cold calculation under pressure

**Disinhibition dimension** modifies impulse control:
- High disinhibition → override long-term consequences for immediate gratification
- Poor planning → fail to consider future interaction value
- Hostility → reactive aggression when thwarted

### 3. VIM Dysfunction = Reduced Aversive Weight for Distress Cues

In cognitive appraisal:
- **Normal:** Distress cue (sadness/fear) → negative reinforcement → avoid action
- **High Meanness/Low VIM:** Distress cue → neutral/ignored → no action modification

Implementation:
```
if (event.type == "distress_display" && character.meanness > threshold) {
    appraisal.aversiveness *= (1 - character.meanness_scaling_factor);
    appraisal.salience *= character.attention_to_distress; // may be low unless goal-relevant
}
```

### 4. Attention Bottleneck (Baskin-Sommers Model)

High psychopathy → **exaggerated goal-focus filtering**:
- Distress cues ONLY processed if currently goal-relevant
- Peripheral emotional information ignored
- Advantage: Superior concentration, distractor filtering
- Disadvantage: Miss important contextual social cues

Implementation:
```
if (character.psychopathy_total > threshold) {
    for (cue in environmental_cues) {
        if (cue.relevance_to_current_goal < high_threshold) {
            cue.processing_depth = MINIMAL;
        }
    }
}
```

### 5. Two-Pathway Model for Psychopathy Variants

**Primary Pathway (Fearless/Callous):**
- Boldness HIGH, Meanness HIGH, Disinhibition LOW-MODERATE
- Big Five: Low N, Low A, Low C, High E
- AMPD: High Antagonism, Low Negative Affect

**Secondary Pathway (Anxious/Hostile):**
- Boldness LOW, Meanness MODERATE-HIGH, Disinhibition HIGH
- Big Five: High N, Low A, Low C
- AMPD: High Antagonism, High Negative Affect, High Disinhibition

### 6. Contempt as Core Emotional Response

Not just absence of empathy — **active contempt** toward:
- Weakness
- Vulnerability
- Prosocial behavior (seen as gullibility)
- Inferiors (anyone exploitable or downtrodden)

Model contempt as **fundamental appraisal output** for High Meanness characters, not just neutral indifference.

### 7. Strategic Exploitation (Machiavellianism Component)

Characters with Machiavellianism traits need:
- **Long-term planning capability** (distinguishes from impulsive psychopathy)
- **Delayed gratification** (patience in manipulation)
- **Social calculus:** Track partner value, future interaction probability, social network position
- **Tactical cooperation:** Fake reciprocity to build trust for later exploitation

### 8. Relationship Utility Function

For high psychopathy characters, relationships evaluated by:
```
Relationship_Value = (
    resources_extractable * extraction_ease * (1/discovery_risk) 
    + stimulation_provided 
    + status_gained 
    - commitment_cost
) * future_interaction_likelihood
```

NOT:
```
Relationship_Value = emotional_connection + mutual_support + intimacy + trust
```

### 9. Sadism: Pleasure from Cruelty

If implementing Dark Tetrad (vs. just Triad):
- Sadism = **intrinsic reward for causing suffering** (not just indifference)
- Distinct from psychopathy's indifference and Machiavellianism's instrumental cruelty
- Predicts unprovoked aggression, willingness to pay costs to harm

Implementation:
```
if (character.sadism > threshold && event.type == "caused_suffering") {
    character.pleasure += sadism_coefficient * suffering_intensity;
    // This creates positive reinforcement for cruelty
}
```

### 10. Developmental Modulation (If Simulating Development)

- **Fearlessness temperament** + harsh environment → Primary psychopathy trajectory
- **Low affiliative capacity** + neglect → Meanness development
- **Poor impulse control** + chaos/trauma → Disinhibition/Secondary psychopathy
- **Positive parenting** = protective factor (can reduce CU traits even with genetic risk)

---

## 9. Key Research Gaps and Uncertainties

1. **Precise affective inversion mechanism:** Research shows psychopaths EXPLOIT kindness and VIEW it as weakness, but the exact cognitive/affective transformation pathway (kindness perception → contempt generation → exploitation motivation) lacks detailed process models.

2. **Sadism neural correlates:** While Dark Tetrad behavioral research is robust, neural mechanisms distinguishing sadism's pleasure-from-cruelty from psychopathy's indifference are understudied.

3. **Cross-cultural validity:** Most research is WEIRD (Western, Educated, Industrialized, Rich, Democratic) samples. Psychopathy dimensional structure may vary across cultures.

4. **Gender differences:** Most PCL-R research on male offender samples. Female psychopathy may have different facet emphasis (relational aggression vs. physical).

5. **Change mechanisms:** What interventions (if any) can modify psychopathic traits in adults? Developmental research shows parenting effects in childhood, but adult malleability unclear.

---

## 10. Recommended Implementation Priority

For cognitive architecture modeling **antagonist characters resistant to prosocial drift:**

### Tier 1 (Essential)
1. **Triarchic dimensions** (Boldness, Meanness, Disinhibition) as core trait axes
2. **Meanness-driven appraisal modification:** kindness → exploitability signal
3. **VIM dysfunction:** reduced aversive response to distress cues
4. **Strategic exploitation:** trust/cooperation viewed through instrumental utility lens
5. **Contempt as fundamental affective response** (not just absence of empathy)

### Tier 2 (Important)
6. **Attention bottleneck:** goal-focused filtering reduces peripheral emotional processing
7. **Primary vs. Secondary subtypes:** fearless vs. anxious variants
8. **Relationship utility function:** resources/status/stimulation, NOT intimacy/connection
9. **Machiavellianism:** long-term strategic planning for exploitation

### Tier 3 (Optional/Refinement)
10. **Sadism dimension:** if modeling characters who enjoy cruelty (beyond indifference)
11. **Developmental pathways:** if simulating character backstory/origin
12. **Attachment style:** avoidant attachment patterns in relationships

---

## Sources

### PCL-R and Four-Factor Model
- [Psychopathy Checklist - Wikipedia](https://en.wikipedia.org/wiki/Psychopathy_Checklist)
- [Capturing the Four-Factor Structure of Psychopathy in College Students](https://www.tandfonline.com/doi/full/10.1080/00223890701268074)
- [Hare 2016 — Psychopathy, the PCL-R, and Criminal Justice](https://www.hare.org/Hare2016CPAgoldmedalaward.pdf)
- [Structural models of psychopathy](https://link.springer.com/article/10.1007/s11920-005-0026-3)
- [Items and Factors in the Hare PCL-R](https://www.researchgate.net/figure/tems-and-Factors-in-the-Hare-PCL-R-Interpersonal-Affective_tbl1_225071934)
- [Relations Between Psychopathy Facets and Externalizing](https://pmc.ncbi.nlm.nih.gov/articles/PMC2242628/)
- [The Psychopathy Checklist - Revised (PCL-R)](https://www.clintools.com/victims/resources/assessment/personality/psychopathy_checklist.html)
- [The 20 Traits of a Psychopath on the PCL-R](https://scienceinsights.org/the-20-traits-of-a-psychopath-on-the-pcl-r/)

### Triarchic Model
- [Triarchic conceptualization of psychopathy (Patrick, Fowles, Krueger, 2009)](https://pubmed.ncbi.nlm.nih.gov/19583890/)
- [Triarchic Model of Psychopathy: Origins, Operationalizations, and Observed Linkages (Patrick, 2015)](https://onlinelibrary.wiley.com/doi/10.1111/jopy.12119)
- [Clarifying the Content Coverage of Differing Psychopathy Inventories through Reference to the Triarchic Psychopathy Measure](https://pmc.ncbi.nlm.nih.gov/articles/PMC4100942/)
- [Development and Validation of Triarchic Construct Scales from the Psychopathic Personality Inventory](https://ncbi.nlm.nih.gov/pmc/articles/PMC4147944)
- [An examination of the Triarchic Model of psychopathy's nomological network: A meta-analytic review](https://www.sciencedirect.com/science/article/abs/pii/S0272735818305282)

### Dark Triad/Tetrad
- [Dark triad - Wikipedia](https://en.wikipedia.org/wiki/Dark_triad)
- [Dark Triad | Psychology Today](https://www.psychologytoday.com/us/basics/dark-triad)
- [Dark Tetrad | Psychology Today](https://www.psychologytoday.com/us/basics/dark-tetrad)
- [The Dark Tetrad: analysis of profiles and relationship with the Big Five](https://pmc.ncbi.nlm.nih.gov/articles/PMC10891063/)
- [The Dark Triad of Personality (Paulhus & Williams, 2002)](https://www.simplypsychology.org/dark-triad-personality.html)
- [Behavioral Confirmation of Everyday Sadism (Buckels et al., 2013)](https://pubmed.ncbi.nlm.nih.gov/24022650)
- [Multistudy Report Screening for Dark Personalities: The Short Dark Tetrad (SD4)](https://www.erinbuckels.com/uploads/EJPA.2020.on.SD4.pdf)

### Emotional Processing and VIM
- [Fine Cuts of Empathy and the Amygdala (Blair, 2008)](https://journals.sagepub.com/doi/10.1080/17470210701508855)
- [The Empathic Brain of Psychopaths](https://www.frontiersin.org/journals/psychology/articles/10.3389/fpsyg.2020.00695/full)
- [Applying a cognitive neuroscience perspective to the disorder of psychopathy (Blair, 2005)](https://pubmed.ncbi.nlm.nih.gov/16262996/)
- [Emotion processing in Psychopathy Checklist — assessed psychopathy](https://www.sciencedirect.com/science/article/abs/pii/S0272735813001062)
- [Psychopathic traits influence amygdala–anterior cingulate cortex connectivity](https://academic.oup.com/scan/article/13/5/525/4969497)
- [Electrophysiological study of the violence inhibition mechanism](https://www.sciencedirect.com/science/article/abs/pii/S0191886917300521)
- [The psychopathic individual: A lack of responsiveness to distress cues (Blair, 1997)](https://onlinelibrary.wiley.com/doi/abs/10.1111/j.1469-8986.1997.tb02131.x)

### Attention Bottleneck Model (Baskin-Sommers)
- [Altering the Cognitive-Affective Dysfunctions of Psychopathic and Externalizing Offender Subtypes](https://pmc.ncbi.nlm.nih.gov/articles/PMC4426343/)
- [Psychopathy-Related Differences in Selective Attention Are Captured by an Early Event-Related Potential](https://pmc.ncbi.nlm.nih.gov/articles/PMC3387525/)
- [Psychopathy is associated with an exaggerated attention bottleneck](https://link.springer.com/article/10.3758/s13415-021-00891-z)
- [Differentiating emotional processing and attention in psychopathy with functional neuroimaging](https://link.springer.com/article/10.3758/s13415-016-0493-5)
- [The interplay of attention and emotion: top-down attention modulates amygdala activation in psychopathy](https://link.springer.com/article/10.3758/s13415-013-0172-8)

### Callous-Unemotional Traits and Development
- [Callous–Unemotional Traits and Developmental Pathways (Frick, 2003)](https://onlinelibrary.wiley.com/doi/10.1111/jopy.12114)
- [Callous-Unemotional Traits in Children](https://www.psychologicalscience.org/observer/callous-unemotional-traits-in-children)
- [Research Review: The importance of callous‐unemotional traits (Frick, 2008)](https://acamh.onlinelibrary.wiley.com/doi/abs/10.1111/j.1469-7610.2007.01862.x)
- [Callous-Unemotional Behaviors in Early Childhood: Genetic and Environmental Contributions](https://pmc.ncbi.nlm.nih.gov/articles/PMC5472508/)
- [Heritable temperament pathways to early callous-unemotional behaviour](https://www.cambridge.org/core/journals/the-british-journal-of-psychiatry/article/heritable-temperament-pathways-to-early-callousunemotional-behaviour/DBC0164057571E9A3B0BB1763EC103E2)
- [The Genetic Underpinnings of Callous-Unemotional Traits](https://pmc.ncbi.nlm.nih.gov/articles/PMC6756755/)
- [Predictors and outcomes of joint trajectories of callous-unemotional traits (Fontaine, 2011)](https://pubmed.ncbi.nlm.nih.gov/21341879/)
- [Etiology of different developmental trajectories of callous-unemotional traits](https://pubmed.ncbi.nlm.nih.gov/20610135/)

### Psychopathy and Big Five
- [Psychopathy and the Five Factor Model in a Noninstitutionalized Sample: A Domain and Facet Level Analysis](https://www.researchgate.net/publication/225880185_Psychopathy_and_the_Five_Factor_Model_in_a_Noninstitutionalized_Sample_A_Domain_and_Facet_Level_Analysis)
- [Psychopathy from the perspective of the five-factor model](https://psycnet.apa.org/record/2012-10423-007)
- [Correlations between psychopathy factors and the Big Five](https://www.researchgate.net/figure/Correlations-between-psychopathy-factors-and-the-Big-Five_tbl1_267955573)
- [Psychopath Big 5 Personality: Traits, Correlations, and Insights](https://www.ourmental.health/psychopaths/psychopathy-and-the-big-five-personality-traits-understanding-the-connection)

### AMPD and PID-5
- [Locating psychopathy within the domain space of personality pathology](https://www.sciencedirect.com/science/article/abs/pii/S0191886920303135)
- [Improving Characterization of Psychopathy within the DSM-5 AMPD: Creation and Validation of PID-5 Triarchic Scales](https://pmc.ncbi.nlm.nih.gov/articles/PMC6817378/)
- [Personality Inventory for DSM-5 – Short Form (PID-5-SF)](https://novopsych.com/assessments/diagnosis/personality-inventory-for-dsm-5-short-form-pid-5-sf/)
- [Alternative DSM-5 model for personality disorders - Wikipedia](https://en.wikipedia.org/wiki/Alternative_DSM-5_model_for_personality_disorders)

### Dark Triad and Big Five
- [Examining Associations between the Dark Triad and the Big Five Personality Traits](https://www.researchgate.net/publication/337889195_Examining_Associations_between_the_Dark_Triad_and_the_Big_Five_Personality_Traits)
- [The Dark Triad of Personality (Paulhus & Williams, 2002)](https://www2.psych.ubc.ca/~dpaulhus/research/SELF-ENHANCEMENT/downloads/ARTICLES/JRP.02.pdf)
- [Big Five and Dark Triad Correlations](https://www.researchgate.net/figure/Big-Five-and-Dark-Triad-Correlations_tbl1_337260251)

### Dark Triad and AMPD
- [Dark ladies: Maladaptive personality domains, alexithymia, and the dark triad in women](https://research.tilburguniversity.edu/en/publications/dark-ladies-maladaptive-personality-domains-alexithymia-and-the-d)
- [Assessing dark triad dimensions from the perspective of moral disengagement and DSM-5 AMPD traits](https://pubmed.ncbi.nlm.nih.gov/31916786/)
- [Welcome to the Jangle: Comparing the Empirical Profiles of the "Dark" Factor and Antagonism](https://doi.org/10.1177/10731911221124847)

### Narcissism Subtypes
- [Narcissism between facets and domains (grandiose vs. vulnerable)](https://link.springer.com/article/10.1007/s12144-019-0147-1)
- [Assessing Grandiose vs Vulnerable Narcissism in the DSM-5 AMPD](https://www.researchgate.net/publication/362746974_Assessing_Grandiose_vs_Vulnerable_Narcissism_in_the_DSM-5_Alternative_Model_For_Personality_Disorders)
- [Grandiose and Vulnerable Narcissism and the DSM–5 Pathological Personality Trait Model](https://www.researchgate.net/publication/224976682_Grandiose_and_Vulnerable_Narcissism_and_the_DSM-5_Pathological_Personality_Trait_Model)

### Machiavellianism
- [Machiavellianism (psychology) - Wikipedia](https://en.wikipedia.org/wiki/Machiavellianism_(psychology))
- [Machiavellianism: Meaning, Traits & The Dark Triad Explained](https://selectpsychology.co.uk/blog/psychology/machiavellianism-traits/)
- [Development and preliminary validation of a five factor model measure of Machiavellianism](https://pubmed.ncbi.nlm.nih.gov/30047746/)
- [Proposing a Multidimensional Machiavellianism Conceptualization](https://www.researchgate.net/publication/233627956_Proposing_a_Multidimensional_Machiavellianism_Conceptualization)

### Trust, Cooperation, and Exploitation
- [The effect of psychopathy on cooperative strategies in an iterated Prisoner's Dilemma](https://www.nature.com/articles/s41598-019-38796-0)
- [How psychopathic traits affect individuals' trust decisions and outcome evaluations](https://pmc.ncbi.nlm.nih.gov/articles/PMC12210483/)
- [Successful and selective exploitation in psychopathy: Convincing others and gaining trust](https://www.sciencedirect.com/science/article/abs/pii/S0191886920305857)
- [Take the Money and Run: Psychopathic Behavior in the Trust Game](https://www.frontiersin.org/journals/psychology/articles/10.3389/fpsyg.2016.01866/full)
- [The strategy of psychopathy: primary psychopathic traits predict defection on low-value relationships](https://pmc.ncbi.nlm.nih.gov/articles/PMC3619474/)

### Reward Processing and Social Information
- [Psychopathy-related traits and the use of reward and social information: a computational approach](https://www.frontiersin.org/journals/psychology/articles/10.3389/fpsyg.2013.00952/full)
- [Scenario-specific aberrations of social reward processing in dimensional schizotypy and psychopathy](https://www.nature.com/articles/s41598-022-18863-9)
- [Exploring when to exploit: the cognitive underpinnings of foraging-type decisions in relation to psychopathy](https://www.nature.com/articles/s41398-025-03245-2)

### Contempt and Interpersonal Perception
- [At the Heart of the Psychopath Are Spite and Contempt | Psychology Today](https://www.psychologytoday.com/us/blog/fulfillment-at-any-age/201903/at-the-heart-of-the-psychopath-are-spite-and-contempt)
- [People Who Seem "Nice" But Are Actually Psychopaths Display These 3 Subtle Behaviors](https://thoughtcatalog.com/shahida-arabi/2024/02/people-who-seem-nice-but-are-actually-psychopaths-display-these-3-subtle-behaviors/)
- [Psychopathy | Noba](https://nobaproject.com/modules/psychopathy)

### Relationship Formation and Attachment
- [Psychopath in Love: Understanding Romantic Dynamics](https://www.ourmental.health/psychopaths/exploring-romantic-relationships-with-psychopaths)
- [Exploring the relationship between psychopathy and close relationships](https://link.springer.com/article/10.1007/s12144-024-06458-8)
- [Psychopathic Traits and Romantic Attachment: The Mediating Role of Emotion Dysregulation](https://pmc.ncbi.nlm.nih.gov/articles/PMC11411507/)
- [Primary and secondary psychopathic traits: The role of attachment and cognitive emotion regulation strategies](https://www.sciencedirect.com/science/article/abs/pii/S0191886921004852)
- [The relationship between psychopathic traits and attachment behavior](https://www.sciencedirect.com/science/article/abs/pii/S0191886911002492)

### Primary vs. Secondary Psychopathy
- [Fearless but anxious? A systematic review on the utility of fear and anxiety levels to classify subtypes of psychopathy](https://onlinelibrary.wiley.com/doi/full/10.1002/bsl.2544)
- [A summary of Karpman's depiction of primary and secondary psychopathy](https://www.researchgate.net/figure/A-summary-of-Karpmans-depiction-of-primary-and-secondary-psychopathy-depicting-distinct_fig4_272412683)
- [Primary and secondary psychopathic-traits and their relationship to perception and experience of emotion](https://www.sciencedirect.com/science/article/abs/pii/S0191886908001189)
- [Two Subtypes of Psychopathic Violent Offenders That Parallel Primary](https://www.antoniocasella.eu/archipsy/Skeem_2007.pdf)

### Triarchic and Big Five Integration
- [Components of the Triarchic Model of Psychopathy and the Five-Factor Model Domains Share Largely Overlapping Nomological Networks](https://pubmed.ncbi.nlm.nih.gov/31304764/)
- [The Triarchic Psychopathy Model is Embedded Within the Five Factor Model](https://link.springer.com/article/10.1007/s10862-023-10080-6)
- [Concurrent and developmental correlates of psychopathic traits using a triarchic psychopathy model approach](https://pmc.ncbi.nlm.nih.gov/articles/PMC5687302/)

---

**End of Report**
