# Decisions — #888 Intervention Inbox

## D5: Async model — detection + separate MCP tools

**Choice:** webCapture returns challenge status; agent uses separate captchaPending/captchaResolve MCP tools to orchestrate the human handoff
**Alternatives:**
- Callback-based — background watcher polls for resolution and re-triggers capture. Over-engineered for MCP model.
- Blocking with timeout — ties up the MCP call waiting for human. Bad UX.
**Rationale:** Fits the synchronous MCP tool model. Agent is the orchestrator — it decides when to ask the human and when to re-submit.
**Trade-offs:** Agent must orchestrate the multi-step flow. Acceptable — agents are good at multi-step tool use.
**Sources:** CorpusApiBean.webCapture() current synchronous model.
**Exploration:** quick
**Status:** captured

## D6: Detection — agent-driven, no auto-detection

**Choice:** No server-side challenge detection. Agent decides when a page needs human intervention and calls requestIntervention explicitly.
**Alternatives:**
- Keyword heuristics inside webCapture — fragile, false positives, maintenance burden as providers change.
- Both detection + agent submission — belt and suspenders, but adds fragile code.
**Rationale:** The agent (via Playwright) can see the page and judge "this isn't real content" better than keyword matching on HTML. Eliminates an entire class of false positives/negatives. Also generalizes to logins, paywalls, cookie walls — any human-required step.
**Trade-offs:** Agent must recognize challenges itself. Acceptable — agents with Playwright can see the rendered page.
**Sources:** Discussion of CAPTCHA detection reliability and login wall generalization.
**Exploration:** quick
**Status:** captured

## D7: Storage — in-memory ConcurrentHashMap

**Choice:** Pending interventions stored in ConcurrentHashMap keyed by ID, with auto-expiry
**Alternatives:**
- SQLite table — persistent across restarts but interventions are transient (minutes).
- Corpus entries — overloads corpus model for ephemeral state.
**Rationale:** Interventions are short-lived. Lost on restart is acceptable — agent re-fetches. Matches the in-process MCP server model.
**Trade-offs:** Not durable across restarts. Acceptable for transient state.
**Sources:** ConcurrentHashMap pattern used elsewhere in the codebase (sessionFindings in CorpusApiBean).
**Exploration:** quick
**Status:** captured

## D8: Scope — general intervention inbox

**Choice:** General intervention inbox that handles any human-required step (CAPTCHA, login, paywall, cookie wall, age verification)
**Alternatives:**
- CAPTCHA-only — narrower scope but misses the common case (logins).
- CAPTCHA detection + intervention — adds fragile heuristics on top.
**Rationale:** Login walls are more common than CAPTCHAs. Same mechanism handles both. No detection heuristics needed. Simpler and more robust.
**Trade-offs:** Broader scope, but the implementation is actually simpler (no detection code).
**Sources:** User insight: "the same can be said for logins too."
**Depends on:** D6 (no auto-detection)
**Exploration:** quick
**Status:** captured