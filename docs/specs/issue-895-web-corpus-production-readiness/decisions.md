# Decisions — #895 Web Corpus Production Readiness

## D1: Architectural home for web content acquisition

**Choice:** New `WebPlatform` SPI in connectors repo (casehubio/connectors)
**Alternatives:**
- CDI wiring inside neocortex corpus-management — mixes generic web reading with corpus storage
- New neocortex module — wrong repo; web reading is a connector concern
**Rationale:** Web content acquisition (fetch, parse HTML, extract images, handle auth) is a generic platform capability with zero corpus-specific dependencies. The connectors repo has the established `<domain>-spi` + `<domain>-ref` + `<domain>-<provider>` pattern with 11 existing platforms.
**Trade-offs:** Cross-repo dependency; neocortex corpus-management must depend on connectors web-spi. But this is the same pattern as knowledge-pipeline depending on location-spi.
**Sources:** connectors `document-spi/DocumentPlatform.java` — template pattern
**Exploration:** quick
**Status:** captured

## D2: Capability structure

**Choice:** Five capability sub-interfaces: Fetch, Parse, ImageCapture, DocumentParsing, InterventionManagement
**Alternatives:**
- Six capabilities (separate SessionTransfer) — over-segmented; session/auth is a Fetch concern
- Fewer capabilities (merge Parse + ImageCapture) — loses independent activation
**Rationale:** Each capability maps to a distinct concern that can be independently supported or absent. Session transfer (Playwright cookies) is modelled as `FetchOptions` on `Fetch` rather than a separate capability — it's auth context for fetching, not a standalone operation.
**Trade-offs:** No SessionTransfer capability means the API is slightly less discoverable for the Playwright cookie handoff use case. But `Fetch.fetch(url, FetchOptions.withCookies(...))` is clearer than a separate capability.
**Sources:** Existing connector SPIs (DocumentPlatform, LocationPlatform) — all use capability sub-interfaces
**Exploration:** quick
**Status:** captured

## D3: Document parser migration

**Choice:** Move `DocumentParser` interface + PDF/Image/Docx parsers from neocortex `corpus-management-parsers` to connectors `web` module as `DocumentParsing` capability
**Alternatives:**
- Keep parsers in neocortex — forces connectors web module to depend on neocortex for document parsing
- Separate `parser-spi` module — over-segmented for 3 parsers
**Rationale:** Document parsing is generic content extraction — no corpus-specific logic. The web module needs PDF parsing (visual archive) and image description anyway. Moving them avoids a circular dependency.
**Trade-offs:** `corpus-management-parsers` loses most of its content (only StructuralChunker + layout classes remain). May warrant renaming to `corpus-chunking` later.
**Sources:** neocortex `corpus-management-parsers/` source files
**Exploration:** quick
**Depends on:** D1
**Status:** captured