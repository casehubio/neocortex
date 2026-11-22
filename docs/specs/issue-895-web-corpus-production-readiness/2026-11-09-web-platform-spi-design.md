# WebPlatform SPI Design

**Issue:** casehubio/neocortex#895, connectors#198/#199/#200
**Date:** 2026-11-09

## Overview

Extract web content acquisition from neocortex into a `WebPlatform` connector SPI in casehubio/connectors. Generic web reading (fetch, parse, images, intervention) becomes a platform capability; neocortex retains only the thin corpus bridge.

## Module Structure

```
connectors/
  web-spi/        — WebPlatform SPI + value types
  web-ref/        — in-memory reference implementation
  web/            — real implementation (readability4j, flexmark, Jsoup, PDF/DOCX parsers)
```

## WebPlatform SPI

Package: `io.casehub.connectors.web.spi`

```java
@SimulationEligible(name = "web-platform",
    capabilities = {"fetch", "parse", "imageCapture", "documentParsing", "interventionManagement"})
public interface WebPlatform {

    String id();
    boolean supports(Class<?> capability);

    Fetch fetch();
    Parse parse();
    ImageCapture imageCapture();
    DocumentParsing documentParsing();
    InterventionManagement interventionManagement();

    interface Fetch {
        FetchResult fetch(String url);
        FetchResult fetch(String url, FetchOptions options);
    }

    interface Parse {
        ParseResult parseHtml(String html, String sourceUrl);
    }

    interface ImageCapture {
        ImageProcessingResult extractImages(String html, String sourceUrl);
        String enrichMarkdown(String markdown, ImageProcessingResult result);
    }

    interface DocumentParsing {
        String parse(byte[] content, String mimeType);
        boolean supportsFormat(String mimeType);
    }

    interface InterventionManagement {
        String request(String url, String reason);
        List<InterventionRequest> pending();
        void resolve(String requestId, String html);
    }
}
```

## Value Types

Package: `io.casehub.connectors.web.model`

| Record | Fields |
|--------|--------|
| `FetchResult` | `String html`, `int statusCode`, `String contentType`, `Map<String, List<String>> headers` |
| `FetchOptions` | `Map<String, String> cookies`, `Map<String, String> headers`, `Duration timeout` |
| `ParseResult` | `String markdown`, `String title`, `Map<String, String> metadata` |
| `CapturedImage` | `String src`, `String alt`, `byte[] data`, `String mimeType`, `String description` |
| `ImageProcessingResult` | `List<CapturedImage> images` |
| `InterventionRequest` | `String id`, `String url`, `String reason`, `Instant requestedAt` |

## SPIs (extension points)

| Interface | Package | Purpose |
|-----------|---------|---------|
| `ImageDescriber` | `web.spi` | `@FunctionalInterface`: `describe(byte[], String mimeType) → String`. Implementations live downstream (e.g. `LlmImageDescriber` in neocortex). |

## NoOp Default Bean

`NoOpWebPlatform` — `@DefaultBean @ApplicationScoped`, `supports()` returns false, all capabilities throw `UnsupportedOperationException`. Active when no provider on classpath.

## Reference Implementation (web-ref)

`RefWebPlatform` — in-memory implementation for tests:
- `Fetch`: configurable URL→HTML map, returns 404 for unknown URLs
- `Parse`: delegates to real parser or returns input as-is
- `ImageCapture`: returns placeholder descriptions
- `DocumentParsing`: returns placeholder text
- `InterventionManagement`: in-memory queue with configurable timeout

## Real Implementation (web)

`DefaultWebPlatform` — production implementation:

| Capability | Implementation | Migrated from |
|-----------|----------------|---------------|
| Fetch | `java.net.http.HttpClient` with cookie support | `CorpusApiBean.webCapture()` lines 604-621 |
| Parse | `HtmlDocumentParser` + `PositionalMarkdownBuilder` via readability4j + flexmark | `corpus-management-parsers` |
| ImageCapture | `WebImageProcessor` (Jsoup + `ImageDescriber` SPI) | `corpus-management/impl` |
| DocumentParsing | `PdfDocumentParser`, `ImageDocumentParser`, `DocxDocumentParser` | `corpus-management-parsers` |
| InterventionManagement | `InterventionQueue` (ConcurrentHashMap + expiry) | `corpus-management/impl` |

CDI wiring via `WebPlatformBeans` (`@ApplicationScoped`, classpath-activated):
- `ImageDescriber` via `Instance<ImageDescriber>` — graceful degradation when absent
- Intervention timeout via `@ConfigMapping` (`casehub.web.intervention.timeout-minutes`, default 10)

## Neocortex Changes

### Removed from neocortex

| File | Module | Reason |
|------|--------|--------|
| `HtmlToMarkdownParser` | corpus-management-api | Replaced by `WebPlatform.Parse` |
| `ImageDescriber` | corpus-management-api | Moved to `web-spi` |
| `HtmlDocumentParser`, `PositionalMarkdownBuilder` | corpus-management-parsers | Moved to `web` |
| `HtmlToMarkdownParserAdapter` | corpus-management-parsers | Moved to `web` |
| `DocumentParser`, `PdfDocumentParser`, `ImageDocumentParser`, `DocxDocumentParser` | corpus-management-parsers | Moved to `web` |
| `WebImageProcessor`, `CapturedImage`, `ImageProcessingResult` | corpus-management/impl | Moved to `web` |
| `InterventionQueue`, `InterventionRequest` | corpus-management/impl | Moved to `web` |

### Kept in neocortex

| File | Module | Why |
|------|--------|-----|
| `WebCaptureService` | corpus-management/impl | Corpus bridge: dedup, versioning, TTL against CorpusEntryStore |
| `WebCaptureMetadata` | corpus-management-api | Corpus metadata constants |
| `WebCacheCurationService` | corpus-management/impl | TTL lifecycle |
| `StructuralChunker`, `Layout`, `LayoutPresets` | corpus-management-parsers | Corpus/RAG chunking concern |

### CorpusApiBean simplification

Replace 3 nullable web capture params (`HtmlToMarkdownParser`, `WebImageProcessor`, `InterventionQueue`) with a single `WebPlatform` dependency:
- Constructor: `WebPlatform webPlatform` (nullable for backward compat)
- `webCapture()`: delegates to `webPlatform.fetch()` + `webPlatform.parse()`, stores via `WebCaptureService`
- `requestIntervention()`: delegates to `webPlatform.interventionManagement()`

## Dependencies

```
web-spi  ← connectors-api, platform-simulation-api, platform-api
web-ref  ← web-spi
web      ← web-spi, readability4j, flexmark, jsoup, poi-ooxml, pdfbox, tika-core

neocortex corpus-management ← web-spi (compile), web (runtime)
neocortex corpus-management-parsers — loses DocumentParser/PDF/Image/Docx; keeps StructuralChunker
```

## References

- `connectors/document-spi/DocumentPlatform.java` — SPI pattern template
- `connectors/document-spi/NoOpDocumentPlatform.java` — NoOp pattern
- `neocortex/corpus-management-api/ImageDescriber.java` — SPI to migrate
- `neocortex/corpus-management-parsers/HtmlDocumentParser.java` — parser to migrate
- `neocortex/corpus-management/impl/WebImageProcessor.java` — image capture to migrate
- `neocortex/corpus-management/impl/InterventionQueue.java` — queue to migrate
- casehubio/neocortex#882 — original HtmlDocumentParser
- casehubio/neocortex#887 — image capture
- casehubio/neocortex#888 — intervention inbox