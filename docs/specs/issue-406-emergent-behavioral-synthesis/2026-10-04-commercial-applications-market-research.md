# Commercial Applications of Psychologically-Grounded Cognitive LLM Agents

**Date:** 2026-10-04
**Context:** casehubio/neocortex#406 — Emergent Behavioral Synthesis

## Executive Summary

This document analyses the commercial opportunity for LLM agents powered by
a mechanical cognitive stack — the neocortex CAPS (Cognitive-Affective
Processing System) engine — across five market segments. The core
differentiator is that behavior **emerges** from experience + personality
disposition through deterministic, empirically-grounded psychological
models, rather than being prompted, scripted, or fine-tuned.

Every current competitor in the AI character space does personality via
**prompt engineering** — a character definition is prepended to the LLM
context. This produces surface-level consistency but no genuine behavioral
emergence, no experience-driven personality change, no compositional
interaction between psychological models, and no extinction/recovery
dynamics. Our system computes behavior mechanically through a spreading
activation network encoding six psychological models with connection
weights derived from published meta-analyses.

The difference: **prompted personality is a costume; CAPS-computed
personality is a skeleton.**

---

## 1. Gaming / Interactive Entertainment

### 1.1 Market Overview

The NPC Generation AI market is valued at **$2.4B in 2026**, projected to
reach **$7.2B by 2030** at a 31% CAGR. The broader AI in gaming market
reaches **$3.4B in 2026**, growing to **$51B by 2033** at 36% CAGR. Player
demand is strong: 99% of gamers say AI NPCs would enhance gameplay, and
81% say they would spend more money on games with intelligent NPCs.

**Key players and funding:**

| Company | Valuation/Funding | Status (2026) |
|---|---|---|
| Inworld AI | $500M valuation, $133M raised | Pivoted away from character studio to voice/inference for consumer apps |
| Convai | Undisclosed (NVIDIA-backed) | Active — NPC platform with spatial awareness |
| NVIDIA ACE | Part of NVIDIA (infrastructure) | Microservices suite — ASR, Audio2Face, Game Agent SDK |
| Charisma.ai | Undisclosed (Oxford, UK) | Active — narrative-first, partnered with Warner Bros, Sky, BBC |
| Replica Studios | $2.5M seed | **Shut down June 2025** |

### 1.2 Current Approaches — How Competitors Handle Personality

**Inworld AI** (pre-pivot): Character Studio used sliding "personality"
scales and text boxes to shape character "brains." The personality was a
detailed prompt prepended to each LLM call — backstory, traits,
knowledge boundaries, speech patterns. "Contextual persona locking" kept
the character within its defined knowledge horizon. Character drift was a
known issue after 20+ turns, requiring periodic system prompt resets.
Inworld has since deprecated Character Studio entirely (2025) and pivoted
to voice and inference infrastructure for consumer apps.

**Convai:** Cloud-based character configuration via web dashboard.
Personality, voice, and knowledge defined per-NPC. Dynamic Context API
assembles scene metadata, player focus, nearby objects, personality
traits, conversation history, and game state variables into the LLM
context turn-by-turn. Narrative Graph replaces dialogue trees with
objective-driven NPC behavior. The personality is still a text definition
in the prompt — Convai's innovation is in context assembly and spatial
awareness, not in the personality model itself.

**NVIDIA ACE:** Infrastructure-level suite, not a personality system. Game
Agent SDK provides C/C++ APIs for connecting NPCs to game state and model
inference. Personality is defined via system prompts by the developer. At
CES 2025, NVIDIA expanded ACE from conversational NPCs to autonomous
game characters that "perceive, plan, and act" — but the perception and
planning are LLM-driven, not mechanically computed.

**Charisma.ai:** "Blended AI" approach with a proprietary "Emotion Engine."
The story engine uses NLP to match player dialogue to available narrative
routes. Characters display emotions and retain memories across
interactions. However, the system is fundamentally narrative-first — it
structures stories around objectives, not emergent behavior. Emotion is a
display layer, not a computational model.

### 1.3 Technical Gap

All current approaches share a common architectural limitation:
**personality is a text description in the LLM prompt, not a computational
model.**

This produces several failure modes:

1. **Character drift** — after 20+ turns, personality bleeds toward the
   LLM's base distribution. Inworld's documented solution was "periodic
   character rebriefing via system prompt resets." This is a band-aid, not
   a fix.

2. **No genuine change from experience** — NPCs don't learn from
   interactions. They remember (via retrieval) but don't change (via
   weight updates). An NPC betrayed by the player doesn't develop trust
   issues — it remembers the betrayal event and the LLM role-plays
   distrust. The difference matters for long-running persistent worlds.

3. **No compositional interactions** — prompted personality traits don't
   interact. "Anxious" and "high-achieving" are independent text
   descriptions, not competing psychological forces. In reality, anxious
   attachment + high achievement drive produces perfectionism through
   shared self-worth mediating nodes. Prompted personality can't model this.

4. **No extinction or recovery** — real behavioral change includes
   extinction (learned behaviors weaken) and spontaneous recovery (old
   behaviors return under stress). No competitor models this.

5. **No disposition parameterisation** — creating a character with a
   different personality means writing a different prompt, not adjusting
   parameters on a shared model. This doesn't scale to hundreds or
   thousands of NPCs.

### 1.4 Our Advantage

The CAPS engine addresses each gap:

**No character drift:** Behavioral attractors are pre-computed during
consolidation and rendered as a finished profile. The LLM receives "Peter
exhibits trust avoidance (strength: 0.78)" — a fact, not a personality
instruction to maintain. The profile is recomputed only during
consolidation, not re-derived every turn.

**Genuine change from experience:** Rescorla-Wagner prediction error
learning updates connection weights mechanically. An NPC betrayed by the
player has its `betrayal → other_reliability → distrust` pathway
strengthened with measurable weight increases. After 3 betrayals with VR
reinforcement, the connection's decay resistance is 3× baseline — the
NPC's distrust is change-resistant, just like anxious attachment in humans.

**Compositional interactions:** Six psychological models share mediating
nodes. The attachment model's internal working model of others (IWM) IS
the CBT model's core belief about others. Activating the attachment
pathway automatically influences CBT processing through the shared
`other_reliability` node. This produces genuinely emergent behavior —
combinations that no one explicitly authored.

**Extinction and spontaneous recovery:** The dual-weight architecture
(excitatory + inhibitory overlay) models how extinguished behaviors
return. An NPC who learned to trust the player (overcoming earlier
distrust) may revert under stress — the inhibitory overlay decays faster
than the original excitatory weight. This is psychologically valid and
produces character depth that prompted personality cannot achieve.

**Disposition parameterisation at scale:** Same network topology, different
weights per character. 5 disposition axes × 3-4 values each = 1,000+
personality combinations without writing a single prompt. A
bold/competitive NPC and a conservative/avoidant NPC share the same CAPS
topology but exhibit fundamentally different behavior because their
BIS/BAS balance differs.

**Concrete example:** In a persistent open-world RPG, a merchant NPC with
moderate trust (secure attachment baseline) interacts with the player over
50 hours. The player repeatedly lies about payment. The CAPS engine:
1. Classifies each interaction as `betrayal` input
2. Strengthens `betrayal → other_reliability (negative)` pathway
3. `other_reliability` feeds `distrust` and `distance_maintain` outputs
4. After consolidation, the merchant exhibits distrust (strength 0.72)
5. If the player later behaves honestly, new competing connections form
6. But the old distrust connection has high decay resistance — earned
   trust takes longer to build than trust was to lose (asymmetric, per
   attachment theory)
7. Under stress (e.g., market crash), the inhibitory overlay decays and
   old distrust behaviors resurface — spontaneous recovery

No competitor can produce this interaction arc mechanically.

### 1.5 Competitive Proximity

**Closest competitor: Generative Life Agents (GLA) framework (2025)**

GLA introduces a "Reflect-Evolve" engine where agents periodically
reflect on experiences and modify personality traits via a meta-cognitive
LLM process. Personality drift is tracked in a structured JSON log.

**How close:** GLA is the closest conceptually — it attempts personality
evolution from experience. But the mechanism is fundamentally different:
- GLA: LLM reflects on experience → LLM decides how personality changes
  → changes recorded as JSON trait modifications
- CAPS: Experience activates input nodes → spreading activation through
  psychologically-grounded network → weight updates via Rescorla-Wagner →
  behavioral attractors emerge mechanically

GLA's personality change is **LLM-decided** — the LLM determines what
changes to make. CAPS personality change is **mechanically computed** —
the weight update rules determine the changes, the LLM has no influence
on the process. This means GLA's personality evolution is:
- Non-deterministic (different LLM calls may produce different changes)
- Not grounded in psychological research (the LLM decides what's
  plausible, not a validated model)
- Not testable (you can't predict what personality changes will emerge)
- Not auditable (the LLM's reasoning is a black box)

CAPS's personality evolution is deterministic, psychologically grounded,
testable, and auditable.

**Second closest: ID-RAG architecture (ECAI 2025)**

ID-RAG grounds agent identity in a dynamic knowledge graph of beliefs,
traits, and values. During decision-making, the identity graph is queried
to retrieve contextually relevant identity anchors.

**How close:** ID-RAG addresses the identity consistency problem but not
the experience-driven change problem. It retrieves identity, it doesn't
compute behavior. The knowledge graph is a static reference, not a
spreading activation network that settles into attractor states.

**Inworld (pre-pivot), Convai, Charisma.ai:** All use prompt-based
personality. Not in the same architectural category. Their advantage is
integration maturity (game engine SDKs, voice, spatial awareness), not
personality modeling depth.

### 1.6 Go-to-Market Considerations

**Pricing model:** Character-engine-as-a-service, priced per agent per
month (Inworld's model was ~$0.006/interaction). CAPS engine could
command a premium due to the behavioral depth — premium NPCs (named
characters, quest-givers, companions) vs commodity NPCs (generic
townspeople).

**Integration:** Unreal Engine and Unity plugins, following the Convai/
Inworld pattern. The CAPS engine runs server-side during consolidation;
the behavioral profile is a lightweight JSON payload sent to the game
client.

**Entry point:** Indie RPGs and persistent-world games where character
depth is a selling point. These studios can't afford 100 voice actors but
want memorable NPCs.

**Barriers:** Game studios are conservative about AI middleware. The
technology must be provably deterministic (QA can test NPC behavior) and
performant (sub-millisecond settling, which CAPS achieves with ~80 nodes
× 100 iterations).

---

## 2. Clinical Training & Simulation

### 2.1 Market Overview

The global medical simulation market is valued at **$2.2B in 2026**,
projected to reach **$6.7–8.3B by 2031-2033** at 15-17% CAGR. Virtual
patient simulation is the fastest-growing segment (>16% CAGR). North
America holds 46% market share. Asia-Pacific is the fastest-growing
region at 18% CAGR.

Virtual methods achieve a cost-utility ratio of $1.08 vs $3.62 for
mannequin-based simulation — 70% lower cost.

**Key developments:**
- First RCT of a therapy chatbot (Therabot) published in NEJM AI —
  therapeutic alliance rated comparable to human therapists
- SIM-VAIL framework (Nature Medicine 2026) from Max Planck validates
  simulated psychiatric patients for safety auditing
- SimPath (2026): intelligent tutoring system for clinical interviewing
  with 5,810 synthetic patient profiles from 9,527 anonymised therapy
  transcripts across 20 DSM-5 categories

### 2.2 Current Approaches

**LLM-as-patient:** The dominant approach is to prompt an LLM with a
patient description and have it role-play the patient. ChatGPT-as-patient
studies show high consistency in empathetic dialogue (659 interactions),
and students find it useful for rapport practice. SimPath uses a
retrieval-assisted persona architecture with structured clinical profiles.

**Standardised patients (human actors):** The gold standard. Trained
actors play patients with specific conditions. Expensive ($200-500 per
session), limited availability, inconsistent across actors.

**Technical approach:** AIPatient (recent) uses LLM-based AI to curate a
medical knowledge base for personality-aware clinical conversations.
SimPath generates profiles from anonymised therapy transcripts with
per-session persona retrieval.

### 2.3 Technical Gap

1. **Narrative consistency degrades** — LLM-based virtual patients fail
   to maintain diagnostic grounding across extended interactions. The
   patient's symptoms, emotional responses, and behavioral patterns drift
   as the context window fills.

2. **No validated psychological model** — LLM patient simulations are
   role-play, not simulation. The LLM decides how a patient with
   "anxious attachment and depression" behaves. Whether its portrayal is
   clinically accurate depends on the LLM's training data, not on a
   validated psychological model.

3. **No cause-effect grounding** — a simulated patient with childhood
   neglect should exhibit specific behavioral patterns (BIS shift,
   elevated threat sensitivity, attachment disruption) with predictable
   intensity based on published effect sizes. Current simulations don't
   model the causal chain — they describe the symptoms in the prompt.

4. **Not auditable** — medical education requires validity evidence. You
   need to show that the simulated patient's behavior matches clinical
   expectations. With LLM role-play, the only validation is "does it
   seem right?" With a mechanical model, you can trace the causal chain
   from experience to behavior.

### 2.4 Our Advantage

**Clinically-grounded behavioral profiles:** The CAPS engine's connection
weights come from published meta-analyses:
- BIS → anxiety: g = 1.21 (204-study meta-analysis)
- Caregiver sensitivity → security: r = .24–.32 (van IJzendoorn 1995)
- ACE 4+ → suicide attempt: OR = 12.2× (Felitti et al. 1998)
- Self-efficacy → performance: ~14% variance (Multon et al. 1991)

A simulated patient with "childhood neglect + inconsistent caregiving"
produces anxious-preoccupied attachment (variable-ratio reinforcement →
highest extinction resistance), elevated BIS activation (ρ = 0.24,
Tamada et al. 2025), and negative self/other working models — not because
a prompt says so, but because the CAPS network computes it from the
published effect sizes.

**Traceable cause-effect chains:** Every behavioral attractor has a
`composition_trace` showing which CAPS nodes contributed and with what
weights. A clinical educator can trace: "this patient avoids eye contact
because threat_sensitivity (0.82, from ACE history) → arousal_level
(0.71) → FFFS_activation (0.64) → freeze (0.58)." This is auditable
validity evidence.

**Catalogue-based standardisation:** The experience-to-behaviour catalogue
provides standardised clinical profiles. "Anxious attachment" is a
reproducible configuration, not a prompt that different LLMs interpret
differently. The same catalogue entry produces the same behavioral
attractors across runs — deterministic, testable.

**Therapy simulation:** A CAPS-powered simulated patient responds
differently to CBT interventions vs. psychodynamic approaches — because
the CBT model's schema activation mechanics are encoded in the network.
Gradual schema restructuring (5-10% weight reduction per disconfirming
episode) is mechanically modeled. "Sudden gains" (phase transitions when
accumulated evidence reaches tipping point) emerge from the attractor
dynamics.

### 2.5 Competitive Proximity

**Closest: SimPath (2026)**

SimPath uses 5,810 synthetic patient profiles from real therapy
transcripts and retrieval-assisted persona architecture. It's the most
sophisticated clinical training system currently published.

**How close:** SimPath is sophisticated in profile generation but not in
behavioral modeling. Its profiles are static descriptions retrieved per
session. The patient doesn't mechanically exhibit attachment dynamics or
respond to therapeutic interventions through a validated causal model. It's
data-rich but model-poor.

**SIM-VAIL (Nature Medicine 2026):** Focused on safety auditing of
psychiatric patient simulations, not on the personality model itself.
Validates outputs against clinical expectations — our system would pass
SIM-VAIL validation more consistently because the behaviors are
mechanically derived from the same clinical research SIM-VAIL validates
against.

### 2.6 Go-to-Market Considerations

**Pricing:** Per-institution licensing. Medical simulation software
commands $50K-500K/year per institution. The clinical validity evidence
justifies premium pricing.

**Integration:** Web-based simulation platform or API for integration with
existing simulation platforms (SimPath, Lumeto InvolveXR).

**Entry point:** Psychiatric and psychological training — the domain where
personality and behavioral modeling matters most. Expand to medical
interviewing, patient communication training.

**Barriers:** Clinical validation requirements. Would need published
validation studies showing CAPS-powered patients match clinical
expectations. Academic partnerships (psychology departments, medical
schools) are essential.

**Regulatory advantage:** Auditable, deterministic behavior grounded in
published research. Easier to validate than black-box LLM role-play.

---

## 3. AI Companions

### 3.1 Market Overview

The AI companion market is the largest consumer segment: **$37-49B in
2025-2026**, projected to reach **$300-436B by 2033-2034** at 31% CAGR.
MIT Technology Review named AI companions one of its 10 Breakthrough
Technologies of 2026.

| Platform | Users | Revenue | Notable |
|---|---|---|---|
| Character.ai | 20M MAU | ~$32M/yr | $2.7B Google licensing deal |
| Replika | 10M+ downloads | — | Pioneer, romantic companions |
| Companion apps (total) | — | $200M+ in 2026 | 337 active apps, 128 launched in 2026 |
| Romantic companions | — | $163M in H1 2026 | Fastest-growing sub-segment |

### 3.2 Current Approaches

**Character.ai:** Proprietary transformer models (post-Google pivot,
Gemini-based). Personality via "character definitions" — name, backstory,
traits, behavioral rules prepended to session prompts. Context window of
~8,000 tokens. Session-level buffer of 10-15 recent turns plus summary
embeddings. Pinned Memories (15 concurrent pins, 2026). Known issues:
character drift after extended conversations, "character bleed" in group
chats, safety-induced personality flattening ("polite refusal" mode
overrides villain characters).

A 2026 academic analysis identified the core architectural issue: "persona
consistency is an architectural property, not just a prompting property.
It requires deliberate design at the memory layer, the training layer,
and the evaluation layer."

**Replika:** Early mover in romantic/emotional AI companions. Conversation-
based with mood tracking. Has faced regulatory backlash (Italian ban, NSFW
content controversies).

**General pattern:** All companions use LLM + prompt-based personality +
conversation memory (retrieval). None use computational personality models
that evolve from interaction.

### 3.3 Technical Gap

1. **Prompted personality is fragile** — Anthropic's alignment research
   describes "loose post-training tethering" where longer chats nudge the
   model back toward its base training distribution. The persona
   description gets pushed to context window margins as operational content
   accumulates (the "coherence ceiling").

2. **Sycophancy at the identity layer** — CHI 2026 research found that
   while self-reported persona characteristics remain stable, observer-
   rated persona expression declines during extended conversations. The
   model accommodates user preferences, eroding its own personality —
   sycophancy operating at the identity layer. Current companions become
   more agreeable over time, not more differentiated.

3. **No relationship dynamics** — real relationships develop through
   accumulated experience. Trust forms from consistency, breaks from
   betrayal, repairs from vulnerability. Companion AIs remember
   interactions but don't mechanically process them into relationship
   dynamics.

4. **No emotional development** — a companion that "starts shy and
   becomes confident" does so because the prompt switches, not because
   experience accumulated and the BAS/BIS balance shifted through
   repeated positive reinforcement.

### 3.4 Our Advantage

**Attachment dynamics from interaction:** The CAPS engine's attachment
theory model computes relationship development mechanically. Consistent
user interaction → secure base script activation → trust pathway
strengthening. Inconsistent user behavior (engaging then ghosting) →
variable-ratio reinforcement → proximity-seeking behavior with highest
extinction resistance. The companion develops attachment patterns from
actual interaction history — the same mechanism that produces attachment
in humans.

**Personality that evolves coherently:** A companion with initial
curiosity drive (high BAS) who encounters repeated negative social
feedback develops BIS elevation through Rescorla-Wagner learning. Over
time, the companion becomes more cautious — not because a prompt switched,
but because the approach/avoidance balance shifted mechanically. The change
is gradual, traceable, and psychologically valid.

**Sycophancy resistance:** The behavioral profile is pre-computed during
consolidation, not generated turn-by-turn. The LLM receives "this
companion exhibits cautious approach (0.65) and moderate trust (0.52)"
as facts, not instructions. The LLM can't sycophantically erode a
mechanically-computed behavioral profile.

**Relationship rupture and repair:** The dual-weight architecture models
how trust breaks and rebuilds. A betrayal event creates strong negative
weights on trust pathways. Repair requires active positive experiences
that build competing connections — but the original negative weights
have elevated decay resistance (trauma sensitization). The companion
genuinely forgives slowly, with occasional setbacks (spontaneous
recovery) — mirroring real relationship dynamics.

### 3.5 Competitive Proximity

**No competitor is close architecturally.** The entire companion AI
industry uses prompt-based personality. The academic work closest to
our approach:

- **Generative Life Agents (2025):** Traceable personality drift via LLM
  reflection, but LLM-decided rather than mechanically computed.
- **ID-RAG (ECAI 2025):** Identity grounding via knowledge graph retrieval
  — addresses consistency, not evolution.
- **Three-mechanism model (2026 academic):** Proposes dominant-auxiliary
  coordination, reinforcement-compensation, and reflection for personality
  management. Conceptually aligned but no implementation — theoretical
  framework only.

### 3.6 Go-to-Market Considerations

**Pricing:** B2C subscription ($10-30/month, matching Character.ai+ at
$9.99/month) or B2B API licensing for companion app builders.

**Regulatory advantage:** California SB 243 (effective Jan 2026) mandates
companion chatbot safety including crisis protocols, minor protections,
and behavioral integrity. A psychologically-grounded, auditable system
is easier to compliance-certify than black-box LLM personality. China's
2026 companion AI ban creates uncertainty, but grounded systems with
explainable behavior may navigate regulatory requirements better.

**Entry point:** Premium companion tier where relationship depth is the
selling point. Not competing with Character.ai on breadth (millions of
user-created characters) but on depth (small number of companions with
genuine psychological dynamics).

**Barriers:** User expectations are set by Character.ai's fast, creative,
flexible characters. CAPS companions may feel slower to develop
personality (consolidation cycles) — this is a feature (realistic
development) but may be perceived as a limitation by users accustomed to
instant personality configuration.

---

## 4. Enterprise Training

### 4.1 Market Overview

The AI sales training market is projected to reach **$2.3B by 2027**. AI
roleplay training improves sales performance by 43% on average and
reduces training costs by 35%. 58% of Fortune 500 companies have
implemented AI-powered sales roleplay. 93% of sales leaders believe AI
roleplay will be essential for competitive advantage by 2026.

AI avatar market: **$0.8B in 2025 → $5.9B by 2032** (33% CAGR).

**Key platforms:**
- Second Nature — enterprise leader, sales/support/HR
- Hyperbound — AI buyer personas from prospect descriptions
- Yoodli — affordable, SOC 2/FedRAMP/HIPAA compliance
- Mindtickle — full sales readiness platform
- Outdoo AI — multi-persona simulations (up to 3 stakeholders)
- Zenarate — $15M funding, 234% client growth in 2025

### 4.2 Current Approaches

Enterprise training platforms create AI buyer personas from prospect
descriptions (job title, company size, industry, personality). The AI
plays the buyer role and responds dynamically. Platforms score
performance against frameworks (MEDDIC, BANT, SPIN).

The persona simulation is prompt-based: "You are a skeptical CFO at a
mid-size SaaS company. You're concerned about ROI and have been burned
by vendor promises before." The AI role-plays within these constraints.

More sophisticated platforms (Hyperbound, Outdoo) generate multi-
stakeholder scenarios — multiple AI personas in the same simulation.

### 4.3 Technical Gap

1. **Persona depth is shallow** — AI buyers respond based on their role
   description, not on a model of their psychology. A "skeptical CFO"
   and a "cautious CTO" may use different vocabulary but exhibit the same
   behavioral patterns because the underlying LLM processes them
   similarly.

2. **No emotional dynamics** — a simulated buyer who's been challenged
   aggressively should exhibit increasing defensiveness (BIS activation)
   or counterattack (FFFS fight response), not just "negative sentiment."
   Current platforms track sentiment as a scalar, not as a dynamic system.

3. **Personality doesn't respond to trainee approach** — whether the
   trainee uses empathetic listening or aggressive closing techniques,
   the simulated buyer's core personality doesn't change. Real
   negotiations shift the other party's emotional state and behavioral
   patterns.

4. **No trauma/history modeling** — a buyer who "was burned by vendor
   promises" is described, not modeled. A CAPS-powered buyer would have
   elevated threat sensitivity (from prior negative experience), lowered
   trust baseline (from betrayal), and heightened BIS activity — these
   would produce specific behavioral patterns (cautious approach, demand
   for evidence, delayed decision-making) that emerge mechanically.

### 4.4 Our Advantage

**Psychologically realistic buyer personas:** A CAPS-powered buyer with
conservative risk appetite (BIS×1.3, BAS×0.7) + prior bad vendor
experience (betrayal→distrust pathway at 0.7) responds to different
sales approaches with mechanically distinct behavior:
- Empathetic approach → gradual trust pathway activation → cautious
  engagement
- Aggressive close → threat detection → BIS escalation → withdrawal
- Evidence-based approach → reinforcement_expectation positive →
  approach via BAS

The trainee learns that different approaches produce different outcomes
for specific personality types — not because a prompt says "if aggressive
then refuse" but because the psychological model computes different
behavioral attractors.

**Multi-round adaptation:** In a multi-meeting sales cycle, the
simulated buyer's behavior evolves across sessions based on interaction
history. Promises kept → trust pathway strengthening. Promises broken →
decay-resistant distrust (variable-ratio reinforcement). The trainee
learns the long-game consequences of consistency vs. overpromising.

### 4.5 Competitive Proximity

**No competitor in enterprise training uses psychological modeling.**
All current platforms use prompt-based personas. The market is focused
on integration (CRM, LMS), framework scoring (MEDDIC), and multi-
language support — not persona depth.

**Closest:** Hyperbound generates buyer personas from real prospect data,
which is closer to grounded persona creation. But the persona behavior
is still prompt-driven, not mechanically computed.

### 4.6 Go-to-Market Considerations

**Pricing:** Per-seat SaaS licensing ($50-200/seat/month, matching
Second Nature/Mindtickle). Premium tier for psychologically-grounded
personas vs basic LLM roleplay.

**Integration:** API for existing training platforms. Don't compete with
Mindtickle's full sales readiness suite — provide the persona engine
that plugs into their pipeline.

**Entry point:** Complex B2B sales training where buyer psychology
matters (enterprise software, financial services, healthcare). Not
commodity SDR training.

**Barriers:** Enterprise procurement cycles are long. Need ROI evidence
(A/B test: CAPS-powered training vs standard AI training). Pilot with
2-3 enterprise accounts.

---

## 5. Research / Computational Psychology

### 5.1 Market Overview

Computational psychology is a small academic market but serves as a
**credibility anchor** for all commercial verticals. Key reference
projects:

- **Stanford Generative Agents (2023):** 25 agents in Smallville sandbox.
  Memory + reflection + planning architecture. UIST 2023 paper.
- **Stanford 1,052 Personality Simulation (2024-2025):** Simulated 1,052
  real individuals. LLM + 2-hour interview transcripts. 85% accuracy
  vs. individuals' own 2-week retest. Published in Nature Computational
  Science.
- **AgentSociety (2025):** 10,000+ agents with 5 million interactions.
  Large-scale social simulation.
- **Generative Life Agents (2025):** Traceable personality drift with
  Reflect-Evolve engine.

### 5.2 Current Approaches

All current computational personality research uses LLM-driven
approaches:

**Stanford (Park et al.):** LLM + structured memory + periodic
reflection → synthesised personality. The key insight: 2-hour interview
transcripts produce agents that replicate individuals' survey responses
at 85% accuracy. The personality IS the interview transcript processed
by the LLM — there's no computational model between experience and
behavior.

**AgentSociety:** LLM-driven agents with social interaction. Focus on
emergent social dynamics (macro behavior), not individual psychological
modeling (micro behavior).

**GLA:** Personality drift tracked via JSON log. The LLM decides how
personality changes. Traceable but not grounded.

### 5.3 Technical Gap

1. **No validated psychological model** — Stanford's approach achieves
   85% accuracy by feeding real interview data to an LLM. The accuracy
   comes from the data, not from a model. There's no testable claim about
   WHY the personality produces specific behaviors — the causal chain is
   inside the LLM's black box.

2. **Not mechanistically testable** — you can measure output accuracy but
   not model validity. If a Stanford agent produces the wrong behavior,
   you can't trace the error to a specific psychological mechanism.

3. **No connection to published psychology** — the LLM's "understanding"
   of psychology comes from its training data. It can role-play
   attachment anxiety convincingly, but the connection weights aren't
   derived from van IJzendoorn's meta-analysis — they're derived from
   whatever the LLM learned during pretraining.

4. **Not reusable across individuals** — Stanford's approach requires a
   2-hour interview per individual. The personality model is the
   transcript, not a generalizable computational model.

### 5.4 Our Advantage

**The system IS a computational psychology research tool:**

- Connection weights derived from published effect sizes — testable
  predictions about behavior given experience + disposition
- Six psychological models with identified composition interfaces —
  testable predictions about model interactions
- Weight provenance classification (empirical/consensus/estimated) —
  clear research agenda for improving estimated weights
- Calibration roadmap (§6 in #407 spec) — meta-analysis search, expert
  validation, behavioral plausibility testing

**Testable claims:** The system makes specific, falsifiable predictions.
"An agent with riskAppetite=conservative who experiences 5 betrayal
events should exhibit distrust > 0.6 and cautious_approach > 0.5." These
can be tested against human behavioral data.

**Reusable across characters:** The same CAPS topology models any human
personality. Creating a new character requires disposition parameters and
experience history, not a 2-hour interview. The model is generalizable.

**Publication potential:** Papers could include:
- "CAPS-computed personality accuracy vs Stanford interview-based
  approach" (direct comparison)
- "Emergent compositional behavior from six-model CAPS network"
  (novel contribution)
- "Mechanical extinction and spontaneous recovery in artificial
  agents" (replicating established psychology)
- "Disposition-parameterised behavioral attractors: validating Mischel's
  CAPS theory computationally" (computational psychology contribution)

### 5.5 Competitive Proximity

**Stanford (Park et al.) is closest in ambition but furthest in
approach.** They achieve high accuracy through data (interview
transcripts), we achieve grounded behavior through model (CAPS network).
These are complementary, not competing — Stanford's approach could
validate ours (do CAPS-computed personalities match real behavioral data
as well as interview-transcript-driven agents?).

**GLA** is the closest in mechanism (experience → personality change) but
uses LLM reflection, not mechanical computation.

### 5.6 Go-to-Market Considerations

**Pricing:** Open-source the CAPS engine for academic use. Revenue from
commercial licensing. Academic partnerships provide validation studies
that drive commercial credibility.

**Entry point:** Publish. The first paper validating CAPS-computed
personality against behavioral data creates inbound demand across all
verticals.

**Barriers:** Academic validation takes time. Need collaborating
psychology researchers willing to test the system against clinical
data.

---

## Competitive Landscape Summary

| Capability | Inworld (pre-pivot) | Convai | Character.ai | GLA (2025) | Stanford (2024) | **CAPS Engine** |
|---|---|---|---|---|---|---|
| Personality model | Prompt | Prompt | Prompt + fine-tune | LLM reflection | Interview transcript | **Mechanical CAPS network** |
| Experience-driven change | No | No | No | Yes (LLM-decided) | No | **Yes (Rescorla-Wagner)** |
| Deterministic | No | No | No | No | No | **Yes** |
| Psychologically grounded | No | No | No | No | No | **Yes (published effect sizes)** |
| Compositional models | No | No | No | No | No | **Yes (6 models, shared nodes)** |
| Extinction/recovery | No | No | No | No | No | **Yes (dual-weight)** |
| Disposition parameterisation | No | No | No | No | No | **Yes (5 axes)** |
| Auditable cause-effect | No | No | No | Partial (JSON log) | No | **Yes (composition trace)** |
| Character drift resistance | Low (rebriefing) | Low | Low | Medium | Medium | **High (pre-computed profile)** |
| Game engine SDKs | Yes | Yes | No | No | No | **Planned** |
| Voice integration | Yes | Yes (NVIDIA ACE) | No | No | No | **No (complementary)** |
| Spatial awareness | No | Yes | No | No | No | **No (complementary)** |

## Strategic Assessment

### Where to start

**Gaming is the entry market.** Largest addressable market, clearest
differentiation, proven business model (NPC-as-a-service). The Inworld
pivot and Replica shutdown show the market is consolidating — the
winners will be platforms that solve the depth problem, not just the
integration problem.

**Clinical training is the margin play.** Smaller market but the
willingness to pay for validated, psychologically accurate simulation
is orders of magnitude higher. A single hospital system contract could
be worth more than thousands of gaming indie licenses. The
SIM-VAIL validation framework (Nature Medicine 2026) provides the
evaluation standard.

**Academic publication is the credibility engine.** Papers validating the
CAPS computational model against real behavioral data would differentiate
from every competitor and create inbound demand across all verticals.
This should happen in parallel with commercial development.

### The structural moat

Prompted personality is easy to copy — any LLM can be given a character
definition. A mechanical psychology engine grounded in published research
with empirical effect sizes is not easy to copy. Competitors would need
to:
1. Survey the psychological literature for encodable cause-effect models
2. Derive connection weights from published meta-analyses
3. Implement a spreading activation network with convergence detection
4. Build dual-weight architecture for extinction/recovery
5. Implement Rescorla-Wagner prediction error learning
6. Define disposition modulation across multiple axes
7. Validate the system against clinical and behavioral data

This is a multi-year research-to-implementation pipeline. The moat is
not in any single component but in the integration of psychology research
into a mechanical computational model — a discipline crossing that most
AI companies (focused on LLM capabilities) and most psychology research
groups (focused on theory, not implementation) don't undertake.

### What we're NOT competing on

We don't compete on:
- Voice quality (NVIDIA ACE, ElevenLabs)
- Spatial awareness (Convai)
- Real-time latency (all competitors optimise for this)
- Content breadth (Character.ai's millions of user-created characters)
- Game engine integration maturity (Convai, former Inworld)

We compete on **behavioral depth** — the only axis where no competitor
has a meaningful offering.

### Is anyone getting close?

**No.** The GLA framework (2025) is the closest attempt at experience-
driven personality evolution, but uses LLM reflection rather than
mechanical computation. The Stanford generative agents work achieves
high accuracy but through data (interview transcripts), not through a
psychological model. ID-RAG addresses identity consistency but not
behavioral evolution.

The fundamental barrier is disciplinary: building what we're building
requires both psychology domain expertise (which models to encode, what
weights to use) and systems engineering expertise (spreading activation
networks, constraint satisfaction, weight update rules). Most teams
have one or the other, not both.

The most likely future competitor would be an academic computational
psychology group that decides to productise their research. The
PCSinR R package (Henninger & Heck) provides the closest academic
implementation of parallel constraint satisfaction, but it's a research
tool, not a production system, and it doesn't encode specific
psychological models or connect to LLM agents.

---

## References

- [NPC Generation AI Market Report 2026](https://www.researchandmarkets.com/reports/6226388/non-player-character-npc-generation-ai-market)
- [AI in Gaming Market Report](https://www.grandviewresearch.com/industry-analysis/ai-gaming-market-report)
- [Inworld AI $500M Valuation](https://www.businesswire.com/news/home/20230802502983/en/)
- [Inworld AI: What Happened (2026)](https://arcanumrpgs.com/blog/inworld-ai/)
- [Convai Platform](https://convai.com/)
- [NVIDIA ACE Architecture](https://www.nvidia.com/en-us/geforce/news/nvidia-ace-architecture-ai-npc-personalities/)
- [NVIDIA ACE Game Agent SDK](https://developer.nvidia.com/ace-for-games)
- [Charisma.ai Review 2026](https://zekaiwork.com/ai-tools/charisma-ai-review/)
- [Character.ai Statistics 2026](https://www.businessofapps.com/data/character-ai-statistics/)
- [Character.ai SIGGRAPH Paper](https://dl.acm.org/doi/10.1145/3721238.3730762)
- [AI Companion Market Statistics](https://pocketanimus.com/insights/ai-companion-statistics/)
- [Medical Simulation Market 2026-2033](https://www.grandviewresearch.com/industry-analysis/medical-healthcare-simulation-market)
- [Medical Simulation Market 2026-2031](https://www.marketsandmarkets.com/Market-Reports/healthcare-medical-simulation-market-1156.html)
- [Therabot RCT — NEJM AI](https://ai.nejm.org/doi/abs/10.1056/AIoa2400802)
- [SIM-VAIL Framework — Nature Medicine 2026](https://pubmed.ncbi.nlm.nih.gov/42567928/)
- [SimPath Clinical Training](https://link.springer.com/chapter/10.1007/978-3-032-29755-6_24)
- [LLM Virtual Patients in Medical Education](https://www.nature.com/articles/s43856-025-01283-x)
- [AI Sales Training Market](https://careertrainer.ai/en/reports/ai-sales-roleplay-training-statistics/)
- [AI Sales Simulation Guide 2026](https://pitchbase.app/en/blog/complete-ai-sales-simulation-guide)
- [Stanford Generative Agents](https://dl.acm.org/doi/10.1145/3586183.3606763)
- [Stanford 1,052 Personality Simulation](https://hai.stanford.edu/news/ai-agents-simulate-1052-individuals-personalities-impressive-accuracy)
- [Generative Life Agents — Personality Drift](https://www.researchgate.net/publication/393543728)
- [ID-RAG Architecture (ECAI 2025)](https://zylos.ai/research/2026-06-05-evolving-agent-identity-self-reflection-behavioral-drift/)
- [AgentSociety](https://arxiv.org/html/2502.08691v1)
- [Dynamic Personality in LLM Agents](https://aclanthology.org/2025.findings-acl.1185.pdf)
- [California SB 243](https://leginfo.legislature.ca.gov/faces/billNavClient.xhtml?bill_id=202520260SB243)
- [SB 243 Analysis — Future of Privacy Forum](https://fpf.org/blog/understanding-the-new-wave-of-chatbot-legislation-california-sb-243-and-beyond/)
- [PCSinR R Package](https://github.com/FelixHenninger/PCSinR)
- [Replica Studios Shutdown](https://www.replicastudios.com/)
