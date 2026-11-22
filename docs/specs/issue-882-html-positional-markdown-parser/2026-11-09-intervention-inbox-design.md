# Intervention Inbox — Human-in-the-Loop Resolution

**Issue:** casehubio/neocortex#888
**Parent:** casehubio/neocortex#875 (Web knowledge cache epic)
**Date:** 2026-11-09

## Problem

Some web pages require human action before content is accessible — CAPTCHAs, login walls, paywalls, cookie consent, age verification. The `webCapture` pipeline cannot proceed without human intervention. Auto-detecting these via keyword heuristics is fragile and narrow; the agent (via Playwright) is in a better position to judge when a page needs help.

## Design

### General intervention inbox — agent-driven

No server-side detection. The agent decides when a page needs human intervention and explicitly requests it. This handles CAPTCHAs, logins, paywalls, and any other human-required step through a single mechanism.

### InterventionQueue

In-memory queue in `corpus-management` for pending interventions. Short-lived state — interventions are resolved in minutes. Lost on restart is acceptable (agent re-fetches).

```java
public class InterventionQueue {

    private final ConcurrentHashMap<String, InterventionRequest> pending;
    private final Duration timeout;  // default 10 minutes

    public String submit(String url, String reason, String screenshotRef) { ... }
    public List<InterventionRequest> pending() { ... }
    public Optional<InterventionRequest> resolve(String id) { ... }
}
```

```java
public record InterventionRequest(
    String id,
    String url,
    String reason,        // "CAPTCHA", "login required", "paywall", free text
    String screenshotRef, // nullable — path to screenshot image
    Instant submittedAt
) {}
```

**Auto-expiry:** `pending()` and `resolve()` evict entries older than `timeout`. Expired interventions return empty on resolve — the agent re-fetches.

**ID generation:** `"intv-" + url.hashCode() + "-" + epochMillis` — deterministic prefix for debugging.

### MCP tools on CorpusApi

Three tools — the agent orchestrates the flow:

**`requestIntervention(corpusId, url, reason, screenshotRef)`**
- Queues the intervention
- Returns `InterventionResult(id, url, reason, submittedAt)`
- The agent shows this to the human: "This page needs you to [reason]. URL: [url]"

**`pendingInterventions(corpusId)`**
- Returns list of pending interventions (id, url, reason, age in seconds)
- Enables polling or dashboard views

**`resolveIntervention(corpusId, interventionId, resolvedHtml, ttlDays, artifactRef)`**
- Removes the intervention from the queue
- Processes the resolved HTML through the normal webCapture pipeline (parse, image capture, store)
- Returns `WebCaptureResult` — same as webCapture
- If intervention expired or not found, returns an error result

### WebCaptureResult extension

Add two nullable fields:

```java
public record WebCaptureResult(
    String entryId,
    String url,
    String title,
    String body,
    String fetchedAt,
    boolean cached,
    int imageCount,
    String interventionId,  // NEW — non-null when this capture resolved an intervention
    String error            // NEW — non-null on failure (expired intervention, not found)
) {}
```

### Agent workflow

```
1. Agent calls webCapture("https://example.com/article", html)
   → Agent sees: page is a login wall (agent inspects rendered page via Playwright)

2. Agent calls requestIntervention(corpusId, url, "Login required — site asks for credentials")
   → Returns: InterventionResult(id="intv-...", ...)

3. Agent tells human: "https://example.com needs you to log in. Please log in and let me know."
   (Optionally with screenshot)

4. Human logs in via real browser, tells agent "done"

5. Agent re-fetches via Playwright (now authenticated via cookie handoff — see #893)
   Agent calls resolveIntervention(corpusId, interventionId, authenticatedHtml)
   → Returns: WebCaptureResult with the parsed content

6. Normal corpus entry created, future agents find it via cache
```

### What stays unchanged

- `webCapture` — unchanged, not aware of interventions
- `WebCaptureService` — dedup/TTL unchanged
- `WebImageProcessor` — image processing runs on resolved HTML via resolveIntervention
- `HtmlToMarkdownParser` — unchanged

### Module placement

All new code in `corpus-management`:
- `InterventionQueue` — new class
- `InterventionRequest` — new record
- `InterventionResult` — new record (MCP return type)
- `CorpusApi` — three new MCP tool methods
- `CorpusApiBean` — implementation of new methods
- `WebCaptureResult` — extended with `interventionId` and `error`

### Testing

- `InterventionQueueTest` — unit tests:
  - Submit creates entry with ID
  - Pending returns submitted entries
  - Resolve removes and returns entry
  - Expired entries are evicted from pending
  - Resolve of expired entry returns empty
  - Resolve of unknown ID returns empty
  - Concurrent submit/resolve safety
- `CorpusApiBean` integration:
  - requestIntervention → pendingInterventions shows it → resolveIntervention processes HTML
  - resolveIntervention runs through webCapture pipeline (parsed, stored, images processed)
  - Expired intervention returns error

## References

- `corpus-management/...CorpusApiBean.java` — MCP tool surface
- `corpus-management/...WebCaptureResult.java` — return type to extend
- `corpus-management/...CorpusApi.java` — MCP tool interface
- casehubio/neocortex#875 — parent epic
- casehubio/neocortex#893 — follow-up: Playwright session transfer for authenticated capture