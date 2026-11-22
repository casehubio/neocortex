# Decisions — #887 Image Capture + LLM Description

## D1: Image source — Java-side mechanical fetch

**Choice:** Java side fetches images mechanically from `<img>` src URLs found in the HTML
**Alternatives:**
- Caller-provided — agent provides pre-captured images via MCP parameter. More complex caller contract, couples to Playwright.
- Hybrid — accept caller-provided when available, fall back to URL fetch. Most flexible but most complex.
**Rationale:** Everything mechanical should be mechanical. The Java side can extract and fetch image URLs without LLM involvement. Only ImageDescriber.describe() needs the LLM.
**Trade-offs:** Images behind authentication or generated via JS may not be fetchable. Acceptable — graceful degradation handles this.
**Sources:** CorpusApiBean.webCapture() already uses HttpClient for page fetch; same pattern for images.
**Exploration:** quick
**Status:** captured

## D2: Markdown format — enrich figure annotation

**Choice:** Keep `#@ figure:` annotation, add `describe="LLM description"` attribute
**Alternatives:**
- Inline paragraph — replace image reference with plain text. Simpler for RAG but loses structural annotation.
- Both annotation + paragraph — most information but verbose.
**Rationale:** Consistent with how ImageDocumentParser already uses `#@ describe:`. Keeps structural annotations uniform across all document parsers.
**Trade-offs:** Description is in an annotation attribute, not free text — RAG chunker needs to extract it. DefaultStructuralChunker already handles `#@` annotations.
**Sources:** HtmlDocumentParser.addAnnotations(), ImageDocumentParser.parse()
**Exploration:** quick
**Status:** captured

## D3: Image storage — separate corpus entries

**Choice:** Each image becomes its own CorpusEntry (domain=web-cache-image) linked to the page entry via `derived-from` relationship
**Alternatives:**
- Directory artifact — store all images in a directory, set artifactRef to directory path. Not individually searchable, conflicts with PDF artifactRef.
- Skip storage — only store LLM descriptions inline. Simplest but loses originals.
**Rationale:** Fits the existing CorpusEntry model. Each image is individually searchable via RAG. Uses the relationship model from #864.
**Trade-offs:** More entries in the store. Acceptable — images are genuinely separate documents.
**Sources:** CorpusEntry record (single artifactRef), store.addRelationship() already used by WebCaptureService for `updates` relationships.
**Exploration:** quick
**Status:** captured

## D4: Architecture — new WebImageProcessor service

**Choice:** Standalone `WebImageProcessor` in corpus-management. Extracts images from HTML, fetches, describes, returns structured results. Called by webCapture after HTML→markdown conversion.
**Alternatives:**
- Extend HtmlDocumentParser — fetch images during parsing. Requires widening HtmlToMarkdownParser SPI or bypassing it.
- Process in CorpusApiBean — inline logic in webCapture(). Couples MCP surface to image processing.
**Rationale:** Keeps parser unchanged (no SPI changes), image processing is opt-in via constructor injection, and the service is independently testable.
**Trade-offs:** One more class. Acceptable — clear single responsibility.
**Sources:** HtmlToMarkdownParser SPI (@FunctionalInterface, returns String only), WebCaptureService pattern.
**Exploration:** quick
**Status:** captured