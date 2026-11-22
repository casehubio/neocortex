# Image Capture + LLM Description on Web Capture

**Issue:** casehubio/neocortex#887
**Parent:** casehubio/neocortex#875 (Web knowledge cache epic)
**Date:** 2026-11-09

## Problem

Web pages contain images that carry information (diagrams, charts, screenshots). The current `webCapture` pipeline converts `<img>` tags to `#@ figure:` annotations with only alt text — image bytes are never fetched, no LLM descriptions are generated, and originals are not preserved.

## Design

### New: WebImageProcessor

A standalone service in `corpus-management` that mechanically extracts, fetches, and describes images from HTML.

```java
public class WebImageProcessor {

    private final ImageDescriber imageDescriber;  // nullable — graceful degradation

    public WebImageProcessor(ImageDescriber imageDescriber) { ... }

    public ImageProcessingResult process(String html, String sourceUrl) { ... }
}
```

**Pipeline:**
1. Parse HTML with Jsoup, select all `<img>` elements
2. Resolve each `src` attribute against `sourceUrl` to get absolute URLs
3. Fetch each image via `HttpClient` (10s connect, 30s read timeout — same as page fetch)
4. Detect MIME type from Content-Type header, fall back to file extension
5. If `ImageDescriber` is available, call `describe(bytes, mimeType)` for each image
6. Return `ImageProcessingResult` containing a `List<CapturedImage>`

```java
public record CapturedImage(
    String sourceUrl,
    String altText,
    byte[] imageBytes,
    String mimeType,
    String description   // nullable — null when no ImageDescriber
) {}

public record ImageProcessingResult(List<CapturedImage> images) {
    public static final ImageProcessingResult EMPTY =
        new ImageProcessingResult(List.of());
}
```

**Graceful degradation:**
- Individual image fetch failure → logged, skipped, other images still processed
- No `ImageDescriber` injected → images captured without descriptions
- `ImageDescriber.describe()` throws → description is null for that image, processing continues
- No `<img>` elements in HTML → returns `EMPTY`
- Invalid/relative URLs that can't resolve → skipped

### Markdown enrichment

After `HtmlToMarkdownParser.parse()` produces markdown, `WebImageProcessor` enriches the `#@ figure:` annotations that `HtmlDocumentParser` already places.

Current output:
```
#@ figure: caption="Architecture diagram"
[Architecture diagram]
```

Enriched output:
```
#@ figure: caption="Architecture diagram" describe="A layered architecture showing three tiers: presentation, business logic, and data access..."
[Architecture diagram]
```

Implementation: match `#@ figure:` lines to `CapturedImage` entries by position order (first figure annotation matches first image in HTML document order). Append `describe="..."` attribute to matched annotations. Unmatched figures (more figures than images, or fetch failed) are left unchanged.

### Image storage as corpus entries

Each successfully captured image is stored as a separate `CorpusEntry`:

- **Domain:** `web-cache-image`
- **Entry ID:** `wci-<parentEntryId>-<index>` (deterministic, enables dedup on re-capture)
- **Body:** The LLM description (or alt text if no description available)
- **artifactRef:** File path where image bytes are written (under `casehub.corpus.images-dir`, defaults to `{corpus-root}/images/`)
- **Metadata:** `source_url` (image URL), `parent_url` (page URL), `parent_entry_id`, `alt_text`, `mime_type`, `fetched_at`
- **Relationship:** `derived-from` linking image entry to parent page entry (via `store.addRelationship()`)

### Pipeline integration in CorpusApiBean.webCapture()

Updated flow:

1. Cache check (unchanged)
2. Content acquisition — fetch HTML or use provided (unchanged)
3. HTML → markdown via `HtmlToMarkdownParser` (unchanged)
4. **NEW:** `WebImageProcessor.process(html, sourceUrl)` → `ImageProcessingResult`
5. **NEW:** Enrich markdown with image descriptions from step 4
6. Title extraction (unchanged)
7. Store page entry via `WebCaptureService` (unchanged)
8. **NEW:** For each `CapturedImage`: write bytes to disk, store as corpus entry, add `derived-from` relationship
9. Return `WebCaptureResult` (extended with `imageCount`)

Constructor change: add optional `WebImageProcessor` parameter (nullable, backward-compatible constructor preserved).

### WebCaptureResult extension

```java
public record WebCaptureResult(
    String entryId,
    String url,
    String title,
    String body,
    String fetchedAt,
    boolean cached,
    int imageCount     // NEW — 0 when cached or no images
) {}
```

### What stays unchanged

- `HtmlToMarkdownParser` SPI — still `@FunctionalInterface` returning `String`
- `HtmlDocumentParser` — still produces `#@ figure:` annotations as today
- `ImageDescriber` SPI — still `describe(byte[], String)` (no two-tier from #869)
- `WebCaptureService` — dedup/TTL logic unchanged
- `WebCaptureMetadata` — constants unchanged (new constants for image domain added separately)

### Module placement

All new code in `corpus-management`:
- `WebImageProcessor` — new class
- `CapturedImage` — new record (inner or standalone)
- `ImageProcessingResult` — new record
- `WebCaptureResult` — extended with `imageCount`
- `CorpusApiBean` — updated constructor + webCapture() flow

`ImageDescriber` SPI stays in `corpus-management-api` (no change).

### Testing

- `WebImageProcessorTest` — unit tests with mock HTTP responses:
  - Extracts image URLs from HTML
  - Resolves relative URLs against source
  - Handles fetch failures gracefully (individual and total)
  - Calls ImageDescriber when available
  - Handles null ImageDescriber
  - Handles ImageDescriber exceptions
  - Returns EMPTY for imageless HTML
- `CorpusApiBean.webCapture()` integration:
  - Images stored as separate corpus entries
  - `derived-from` relationships created
  - imageCount in result
  - Cached response returns imageCount=0

## References

- `corpus-management/...WebCaptureService.java` — dedup/TTL/relationship model
- `corpus-management/...CorpusApiBean.java:511-579` — current webCapture flow
- `corpus-management-api/...ImageDescriber.java` — LLM description SPI
- `corpus-management-parsers/...HtmlDocumentParser.java:105-117` — current image→figure annotation
- `corpus-management-parsers/...ImageDocumentParser.java` — existing image parsing pattern
- `corpus-management-parsers/...ImageRegion.java` — existing image data record
- casehubio/neocortex#875 — parent epic
- casehubio/neocortex#869 — LLM image companion files (future, not in scope)
- casehubio/neocortex#864 — entry relationships (`derived-from` type)