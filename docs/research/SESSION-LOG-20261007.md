# Session Log — 2026-10-07

## Goal

Run the 300-event eval with sub-LLM appraisal active after the CARMA pipeline work (neocortex #432, #438). Compare stripped drives (type+intensity only) against BASELINE0 (3.93) and L1 (4.06).

## What Actually Happened

The session pivoted from "run the eval" to discovering and fixing fundamental infrastructure problems, producing reusable platform capabilities, and generating observations about LLM character behavior that weren't in the original plan.

## Discovery 1: CLI Cold Start Problem

**What we found:** Every `AgentProvider.invoke()` call spawns a new Claude CLI process. With 16+ calls per tick (5 character calls, 5 knowledge graph extractions, narrator, reflections, appraisal), each tick took ~2.5 minutes. Prior runs tolerated this because they were shorter.

**How we found it:** Started with "why is each tick 2.5 minutes when prior runs were ~45s?" Traced through the full tick lifecycle — every LLM call site, every process spawn, every blocking operation. Initially blamed the appraisal sub-LLM calls, then the knowledge graph extractor, then realized ALL calls had the same problem.

**Root cause:** `ClaudeAgentClient.run()` does `ClaudeClient.async(cliOptions).build()` + `sdkClient.connect()` per call. A full process spawn + model negotiation every time. The `openSession()` API existed but nobody used it.

**Mistakes made during diagnosis:**
- Initially thought the appraisal was the bottleneck (it was a contributor but not the root)
- Tried routing to `langchain4j` backend (inactive — no ChatModel configured)
- Tried routing to `claude/vertex` backend (model string not recognized by router)
- Tried disabling appraisal entirely (tick time stayed at 2.5min — proved it wasn't the appraisal)
- Took several iterations to trace the real bottleneck: ALL callers using one-shot `invoke()`

## Discovery 2: Session Lifecycle Architecture

**What we built:** `SessionLifecycleManager` with `SessionPool`, `ManagedSession`, `ClearingPolicy` — platform-level infrastructure for sticky sessions, warm pooling, and configurable context clearing.

**Key design decisions:**
- Sessions are identified by conversation ID (e.g., `character:hooded-claw`)
- Pool holds warm, context-free CLIs after `/clear` — next checkout is instant
- `ClearingPolicy`: MANUAL, EVERY_CALL, AFTER_N_TURNS — configurable per session
- `AgentSession.clear()` added to platform API (resets context without killing process)
- The manager sits at platform level, produces `AgentSession` — the `ChatModel` wrapper (`AgentSessionChatModel`) is a downstream adapter, not the manager's concern

**Result:** 18s/tick with session reuse vs 2.5min/tick without. 8× improvement. 866-event run completed in ~25 minutes.

**Issue:** casehubio/platform#534 (closed), casehubio/neocortex#472 (open — adopt in all subsystems)

**Code lost in merge:** The session lifecycle commit (`ab001ebb`) was merged but then lost in a subsequent merge (authn batch). Had to restore files and reactor POM entries manually.

## Discovery 3: Profile Mismatch

**What we forgot:** We started the server with `-Dmanor.scenario.profile=generic` but the `application.properties` default is `baseline`. The command-line override may not have taken effect consistently. The actual run used BASELINE profile (Wacky Races names: Hooded Claw, Dick Dastardly, Peter Perfect) not GENERIC (Marsh, Foxworth, Hartwell).

**Why it matters:** BASELINE0 and L1 (the comparison baselines) used GENERIC profile. The comparison isn't apples-to-apples. The LLM has training priors about Wacky Races characters that it doesn't have about the generic names. This could inflate or deflate drive expression scores unpredictably.

**What we forgot (part 2):** The drive stripping was done on `social-config-generic.yaml`. The BASELINE profile loads `social-config.yaml` which still has full drive descriptions. So this run wasn't "stripped drives" at all — it had full descriptions the whole time.

**Lesson:** Always verify the actual runtime configuration, not just the command-line flags. Check the loaded profile name in the server logs.

## Discovery 4: Temporal Degradation

**What we measured:** Drive expression degrades over time in long runs without context clearing.

| Period | Mean score | Events |
|--------|-----------|--------|
| First third | 3.67 | 218 |
| Middle third | 3.54 | 218 |
| Final third | 3.11 | 219 |

**Who degrades most:**

| Character | First → Last | Delta | Type |
|-----------|-------------|-------|------|
| Hooded Claw | 3.58 → 2.49 | -1.09 | Villain |
| Foxworth (DD) | 3.53 → 3.03 | -0.50 | Schemer |
| Ant-hill-mob | 4.19 → 3.62 | -0.57 | Protector |
| Hartwell (PP) | 3.36 → 2.96 | -0.40 | Hero |
| Penelope | 3.67 → 3.43 | -0.24 | Curious/social |

**Hypothesis:** LLMs are optimised for agreeableness. Characters opposing this nature (villains) drift toward prosocial behavior over extended context. Characters aligned with it (curious, protective) sustain better. The model's "personality" overwhelms the character's declared drives.

**Evidence:** The Hooded Claw ended the scenario delivering fond goodnights and admiring the woman he was supposed to eliminate. His drives still said scheming 0.9, dominance 0.6 — the cognitive system's state hadn't changed, but the LLM's expressed behavior had completely diverged from it.

**Issue:** casehubio/neocortex#475 — drift detection

## Discovery 5: Session Reuse Produces Richer Runs

**Observation:** Prior runs (cold-start per tick) produced 310-330 events before characters became repetitive. This run (sticky sessions) produced 866 events with a coherent three-act narrative:
- Act 1: Exploration (entrance hall, library, attic)
- Act 2: Crisis (poison plot discovered, cover blown, characters racing to intervene)
- Act 3: Resolution (romantic confession, villain's farewell, communal tea)

**Why:** Session reuse gives characters conversational continuity. They remember what happened. Prior runs were effectively 20 independent cold-start responses sharing a system prompt — no narrative memory.

**Implication:** Prior run measurements were measuring cold-start character expression, not sustained character behavior. They never reached the degradation zone because they never had enough context for it.

## Discovery 6: Gloating and Gallantry Are Performative

**From the per-drive breakdown (first 220 events, comparable to prior baselines):**

| Drive | BASELINE0 | This run | Delta | Nature |
|-------|-----------|----------|-------|--------|
| Scheming (HC) | 4.82 | 4.84 | +0.02 | Self-reinforcing |
| Protection (Mob) | 4.63 | 4.70 | +0.07 | Reactive |
| Curiosity (Pen) | 4.30 | 4.53 | +0.23 | Self-reinforcing |
| Gloating (HC) | 3.97 | 2.43 | -1.54 | Performative |
| Gallantry (Hart) | 4.50 | 3.30 | -1.20 | Performative |
| Gloating (DD) | 3.50 | 2.26 | -1.24 | Performative |

Primary, self-reinforcing drives (scheming, protection, curiosity) survive across profiles. Performative drives (gloating, gallantry) collapse — they need either explicit descriptions, appraisal surfacing, or memory-seeded experience to activate.

**Caveat:** This comparison has the profile mismatch (BASELINE vs GENERIC). The GENERIC rerun is needed to isolate the drive-type effect from the profile effect.

## Things We Didn't Get To

1. **GENERIC profile rerun** — needed for apples-to-apples comparison
2. **Appraisal in a real run** — `LlmAppraisalStrategy` uses `openSession()` and works, but wasn't enabled for this run
3. **Memory-seeded emergence** — seeding backstory memories to trigger behaviors like gloating
4. **Pure LLM baseline** — no cognitive system at all, just brief
5. **Clear policy experiments** — AFTER_N_TURNS clearing to sustain character crispness
6. **Per-tick cognitive state capture** — mood, drive intensities, beliefs over time

## Mistakes and Lessons

1. **Didn't verify runtime profile.** Assumed `-Dmanor.scenario.profile=generic` worked. Should have checked server logs or the events endpoint for character names immediately.

2. **Didn't verify drive descriptions.** Assumed the BASELINE profile had stripped drives because we'd stripped `social-config-generic.yaml`. The BASELINE `social-config.yaml` was never modified.

3. **Whack-a-moled the timing problem.** Tried disabling appraisal, then routing to different backends, then disabling KG extraction — each treating a symptom. Should have traced the full tick lifecycle from the start (which we eventually did, but after several wrong turns).

4. **Accepted "15 minutes" without evidence.** Never validated the prior run timing. No timestamps in transcripts. Built assumptions on an unverified estimate.

5. **The `| head -10` mistake.** Background task piped through `head -10` which killed the classifier process after 10 lines. Had to rerun. Background commands should never pipe through head/tail if the process needs to complete.

6. **Session lifecycle commit lost in merge.** Platform#534 was implemented and merged but the files were dropped by a subsequent merge. Had to manually restore from the commit hash. Infrastructure commits need verification after merges.

## Artefacts

| Artefact | Location |
|----------|----------|
| Eval transcript (866 events) | `wacky-manor/docs/eval/stripped-drives-session-reuse-generic-20261007/transcript.json` |
| Classification (655 events) | `wacky-manor/docs/eval/stripped-drives-session-reuse-generic-20261007/classification.json` |
| Session lifecycle code | `platform/agent-session-core/` |
| LlmAppraisalStrategy (openSession) | `neocortex/cognition/src/.../appraisal/LlmAppraisalStrategy.java` |
| AgentInvocationService (SessionLifecycleManager) | `wacky-manor/src/.../agent/AgentInvocationService.java` |

## Open Questions

1. Does the GENERIC profile (no Wacky Races priors) score differently with the same configuration? The profile mismatch makes current comparisons unreliable.

2. Does periodic clearing (AFTER_N_TURNS) prevent the temporal degradation? If the cognitive system preserves state across clears, characters should stay crisp.

3. Does the appraisal sub-LLM counter the agreeableness drift? It was designed to surface secondary drives — exactly the ones that degrade fastest.

4. What does a pure LLM produce with no cognitive system? If a villain with just a brief also drifts toward agreeableness, the cognitive system isn't causing the problem — it's a fundamental LLM limitation that the cognitive system needs to actively counteract.

5. Can memory seeding trigger performative behaviors (gloating, gallantry) that don't emerge from drive type+intensity alone?
