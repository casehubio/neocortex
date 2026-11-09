---
layout: post
title: "The Corpus That Grows Itself"
date: 2026-11-09
entry_type: article
subtype: diary
projects: [casehubio/neocortex]
tags: [architecture, corpus, llm-agents, knowledge-management, web-capture, library-reuse, image-processing, human-in-the-loop]
series: issue-763-cognitive-knowledge-management
---

# The Corpus That Grows Itself

Every research session involves web searches. An agent fetches a documentation page, reads an API reference, follows a link to a paper. That knowledge informs the session's work and then evaporates. The next agent investigating the same topic re-fetches the same pages, re-processes the same content, re-discovers the same things.

The waste compounds in a multi-agent system. Five agents researching overlapping topics each make independent HTTP requests to the same URLs, each parse the same HTML, each produce ephemeral results that die with the session. The knowledge exists — it just never persists.

## Corpus-first fetch

The fix is a single tool: `webCapture(url)`. An agent calls it instead of raw web fetch. The tool checks the corpus first — if a previous agent already captured this URL and the content is still fresh, return the cached version. No network, no parsing, no compute. Cache miss triggers the full pipeline: fetch, parse, store, index.

The key insight is where the cache lives. A browser cache is per-user, per-machine. A corpus cache is shared across every agent, every session, every runtime. A page that one agent fetched last Tuesday is instantly available to a different agent running today. The corpus is the cache.

![Web capture pipeline — library reuse architecture](svg/web-knowledge-cache-flow.svg)

URL dedup is the gatekeeper. Every web-cache entry carries a `fetched_at` timestamp and a `ttl_days` value. Before storing anything, the service queries the corpus by URL using SQLite's `json_extract` on the metadata blob. If a fresh entry exists, the caller gets back the cached content immediately. If the entry exists but has expired, a new version is created and linked to the old one via an `updates` relationship — the versioned chain preserves history without polluting search results.

## The parser that didn't need writing

The first implementation of the HTML parser was hand-rolled. Jsoup for DOM parsing, a content-density scoring algorithm inspired by Mozilla's Readability.js for article extraction, and a tree walker that converted HTML elements to positional markdown one tag at a time. Headings, lists, tables, blockquotes, code blocks, images, links, inline formatting — all implemented from scratch. It worked. It was 314 lines.

It was also reinventing two well-solved problems.

Readability4J is a JVM port of Mozilla's Readability.js — the same algorithm that powers Firefox Reader View. It handles the messy reality of web pages: navigation bars, ad blocks, cookie banners, sidebars, footers. It has been tested against millions of web pages over years. My content-density scorer was a naive approximation of what that project refined through sustained, real-world iteration.

Flexmark's HTML-to-markdown converter handles the structural conversion: headings, nested lists, table alignment, inline formatting, code blocks. It handles edge cases I hadn't thought about yet and wouldn't have found until production traffic hit them.

The rewrite replaced 314 lines with 131. But the line count isn't the point. The custom code that remained is the integration layer — the part that is genuinely specific to this system. Setext-to-ATX heading conversion (flexmark defaults to `===` underlines; the structural chunker needs `#` prefixes). Positional markdown annotations (`#@ page:`, `#@ table:`, `#@ figure:`) injected by scanning the converted output. Metadata extraction combining Readability4J's article metadata with Jsoup fallbacks for meta tags. Boilerplate cleanup as a safety net after Readability4J's extraction.

That's the correct distribution of effort. Established libraries handle the general problem. Custom code handles the system-specific integration. The ratio should be heavily weighted toward the former.

## SPI decoupling

The parser module carries heavy dependencies — Tika, PDFBox, POI for PDF and DOCX parsing, plus Readability4J, Flexmark, and Jsoup for HTML. The corpus management module — where the MCP tool lives — should not transitively depend on any of them.

The solution is a one-method SPI:

```java
@FunctionalInterface
public interface HtmlToMarkdownParser {
    String parse(String html, String sourceUrl);
}
```

The SPI lives in `corpus-management-api`. The implementation lives in `corpus-management-parsers`. When the parsers module is on the classpath, it provides the implementation. When it isn't, the MCP tool stores HTML as-is. No compile-time coupling, no transitive dependency chains, no classpath pollution.

This is the same CDI `Instance<T>` pattern used throughout the platform — optional capabilities that activate by classpath presence rather than configuration. The corpus management module never imports a class from the parsers module. It asks the CDI container for an `HtmlToMarkdownParser` and gracefully handles absence.

## Access as curation

A web cache that captures every page an agent visits accumulates noise. Most fetched pages are glanced at once and never consulted again. Without curation, the cache degrades search quality — more entries means more irrelevant results competing for attention.

The curation signal is already being collected. The retrieval tracker — built for RAG quality measurement — records every time any agent retrieves any document from the corpus. That access data is exactly the curation signal a cache needs.

![TTL curation — access renewal](svg/web-cache-ttl-curation.svg)

Every web-cache entry has a TTL — thirty days by default. The curation service scans web-cache entries and checks whether any agent retrieved them within their TTL window. If so, the entry is renewed — the access proves its continued usefulness. If no agent has touched it since capture, and the TTL has passed, the entry is evicted.

The multi-agent property is what makes this work. A page that five independent agents in separate sessions all find useful is clearly worth keeping. One agent's search patterns might be idiosyncratic; five agents' overlapping retrievals are a strong quality signal. Archived entries are exempt — explicit curation decisions override automatic eviction.

No scheduled curation review. No LLM judgment calls. The cache works like a browser cache scaled across agents — content that earns its place stays; content that doesn't, doesn't.

## Images are documents too

Text extraction captures the article. It does not capture the diagram that explains the architecture, the chart that shows the performance data, or the screenshot that documents the UI state. For many technical pages, the images carry as much information as the prose.

The image capture pipeline is mechanical. Jsoup parses the raw HTML and extracts every `<img>` element. Each `src` attribute is resolved to an absolute URL — relative paths, protocol-relative URLs, all normalised against the page's base URL. A shared `HttpClient` fetches the image bytes. No Playwright needed, no browser rendering — the images are fetched directly from their source URLs.

![Image capture pipeline — mechanical extraction + LLM description](svg/web-image-capture-pipeline.svg)

The LLM enters only where mechanical processing cannot: describing what the image contains. An `ImageDescriber` SPI accepts image bytes and a MIME type, returns a text description. The description is injected into the positional markdown as an attribute on the `#@ figure:` annotation — the same structural annotation the HTML parser already places for images. A chunker that understands `#@` annotations gets the description for free.

Each captured image becomes its own corpus entry, linked to the parent page via a `derived-from` relationship. The image entry's body is the LLM description; the original bytes are stored as an artifact reference. This means images are independently searchable. An agent searching for "architecture diagram showing three tiers" can find the image entry directly, without needing to retrieve the parent page first.

Graceful degradation is the design principle. No `ImageDescriber` on the classpath? Images are stored without descriptions. Image fetch fails? That image is skipped, the rest proceed. The `ImageDescriber` throws? Description is null for that image, processing continues. Every failure mode falls through to the next step rather than blocking the pipeline. Text capture never depends on image capture succeeding.

## The page behind the wall

Not every URL returns content. Some return a login page. Some return a CAPTCHA. Some return a paywall notice. Some return a cookie consent modal that blocks the article. The failure mode is always the same: the agent fetched HTML, but the HTML isn't the content it wanted.

The first instinct is detection heuristics. Scan the HTML for known CAPTCHA signatures — `g-recaptcha`, `cf-challenge`, "please verify you are a human." Build a library of patterns for each provider and maintain it as providers change their markup.

This is the wrong approach. Detection heuristics are brittle (new providers, obfuscated markup, custom implementations), narrow (each pattern covers one provider), and prone to both false positives (a page that mentions CAPTCHAs in its text) and false negatives (a challenge the heuristics don't recognise). And the CAPTCHA framing misses the broader problem — login walls are more common than CAPTCHAs, and paywalls more common still.

The agent is in a better position to judge. If it rendered the page through Playwright, it can see what a human would see. It doesn't need keyword matching to recognise a login form — it can read the page.

![Intervention inbox — agent-driven human resolution](svg/web-intervention-inbox.svg)

The intervention inbox is a general-purpose queue. An agent calls `requestIntervention(url, reason)` when it encounters any page that requires human action. The reason is free text: "Login required," "Cloudflare CAPTCHA," "Paywall — subscription needed," "Cookie consent blocking content." The queue doesn't parse the reason or try to categorise it. It stores the request and waits.

A human resolves the barrier — logs in, solves the challenge, accepts cookies, whatever the page requires. The agent re-fetches through Playwright (now authenticated via session transfer), and calls `resolveIntervention(id, resolvedHtml)`. The resolved HTML flows through the normal web capture pipeline: parse, image extraction, store, index. The intervention is removed from the queue.

The queue is in-memory — a `ConcurrentHashMap` with auto-expiry. Interventions are transient state, resolved in minutes. If the server restarts, pending interventions are lost, and the agent re-fetches. This is the correct durability level for ephemeral requests. Expired entries are evicted on access — no background thread, no scheduled cleanup, no database table for state that lives less than ten minutes.

The key architectural insight: no detection, no classification, no heuristic maintenance. The agent decides when it needs help. The queue doesn't care why. This means the same three MCP tools — `requestIntervention`, `pendingInterventions`, `resolveIntervention` — handle every barrier type that exists today and every type that will exist tomorrow. The extensibility is in the agent's judgment, not in the platform's pattern library.

## What grows from this

A knowledge system that accumulates from use changes what's possible for multi-agent research. Each session's web research becomes available to every future session. Topics that multiple agents investigate build rich, deduplicated coverage over time. The corpus doesn't just store what someone put in it — it grows from the collective activity of every agent that uses it.

The practical architecture is seven components: an HTML parser built on established extraction libraries, a store with URL dedup and TTL-aware versioning, an MCP tool that makes the cache invisible to the agent, optional PDF archiving for visual fidelity, access-renewal curation that keeps the useful and evicts the noise, image extraction that captures the visual content alongside the text, and an intervention inbox that lets humans unblock what agents cannot access alone.

The system works because each component degrades gracefully. No image describer? Text capture works. No intervention queue? Direct fetch works. No parser module? Raw HTML is stored. Each capability enriches the pipeline when present and disappears without trace when absent. The SPI boundaries are not abstraction for its own sake — they are the mechanism that lets the pipeline run in configurations from minimal (fetch and store) to full (parse, extract images, describe, archive PDF, track access, curate by TTL, queue human interventions).

An agent that calls `webCapture(url)` doesn't know or care whether the content comes from the network or the corpus. That's the point. The cache is an implementation detail. The knowledge persistence is the feature.
