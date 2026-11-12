package io.casehub.neocortex.corpus.management.impl;

import io.casehub.neocortex.corpus.VersionInfo;
import io.casehub.neocortex.corpus.management.ChangeResult;
import io.casehub.neocortex.corpus.management.CorpusEntry;
import io.casehub.neocortex.rag.ProvenanceRecord;
import io.casehub.neocortex.rag.ProvenanceStats;
import io.casehub.platform.api.mcp.ContextParam;
import io.casehub.platform.api.mcp.McpDomain;
import io.casehub.platform.api.mcp.McpParam;
import io.casehub.platform.api.mcp.McpParamFormat;
import io.casehub.platform.api.mcp.PlatformMutation;
import io.casehub.platform.api.mcp.PlatformQuery;

import java.time.Instant;
import java.util.List;

// corpusId parameters are accepted for forward-compatibility with multi-corpus routing.
// Current implementations use a single injected CorpusRef; the corpusId value is not dispatched.
@McpDomain(value = "neocortex/corpus", app = "neocortex",
        summary = "Corpus knowledge management — search, lifecycle, provenance, and feedback")
public interface CorpusApi {

    @PlatformQuery("Search a corpus with optional domain, type, and tag filters. "
                   + "Supports search profiles and inline stack overrides (e.g. 'quarkus:3.36.1|jdk:26'). "
                   + "Results include document body; set previewLength to truncate long bodies. "
                   + "includeStatus controls lifecycle filtering: null=published only (default), 'draft'=include drafts, "
                   + "'archived'=include archived, 'all'=no filtering.")
    List<SearchResult> search(@ContextParam("corpusId") String corpusId, String query,
                              List<String> domains, String type, @McpParam(format = McpParamFormat.LIST) String tags,
                              Integer limit, String profile, String stack, Integer previewLength, String includeStatus);

    @PlatformQuery("Adaptive search with keyword boosting — extends results "
                   + "when initial set is too small, trims when too large. "
                   + "Stack format: 'artifact:version' pairs (e.g. 'quarkus:3.36.1|jdk:26'). "
                   + "Results include document body; set previewLength to truncate long bodies. "
                   + "includeStatus controls lifecycle filtering: null=published only (default), 'all'=no filtering.")
    AdaptiveResult adaptiveSearch(@ContextParam("corpusId") String corpusId, String query,
                                  @McpParam(format = McpParamFormat.LIST) String keywords,
                                  List<String> domains, String type, @McpParam(format = McpParamFormat.LIST) String tags,
                                  Integer limit, String profile, String stack, Integer previewLength, String includeStatus);

    @PlatformQuery("Fetch a single corpus entry by ID — same shape as search results for consistent agent experience")
    SearchResult entry(@ContextParam("corpusId") String corpusId, String entryId);

    @PlatformQuery("List changes to a corpus since a given timestamp — shows added and modified entries with content previews. "
                   + "Agent provides a timestamp, system handles cursor management internally.")
    ChangeResult changes(@ContextParam("corpusId") String corpusId, Instant since);

    @PlatformQuery("List all versions of a document — shows version number, timestamp, source, and author for each revision")
    List<VersionInfo> versions(@ContextParam("corpusId") String corpusId, String entryId);

    @PlatformQuery("Fetch a specific historical version of a document as structured content — returns metadata and body for the requested version")
    CorpusEntry version(@ContextParam("corpusId") String corpusId, String entryId, int version);


    @PlatformQuery("Index status: entry count, corpus path, collection state")
    CorpusStatus status(@ContextParam("corpusId") String corpusId);

    @PlatformQuery("List entries that have never been retrieved or are stale")
    UnretrievedReport unretrieved(@ContextParam("corpusId") String corpusId, Integer minDays, Integer staleDays);

    @PlatformMutation("Trigger full re-index: delete collection and rebuild from source")
    GardenReindexService.ReindexResult reindex(@ContextParam("corpusId") String corpusId);

    @PlatformMutation("Re-index a single entry: delete vectors then re-extract and re-ingest")
    String reindexEntry(@ContextParam("corpusId") String corpusId, String entryId);

    @PlatformMutation("Delete a single entry's vectors from the index")
    String deleteEntry(@ContextParam("corpusId") String corpusId, String entryId);

    @PlatformQuery("Get a search profile's BOM stack by name")
    String profile(String name);

    @PlatformQuery("List all search profile names")
    List<String> profiles();

    @PlatformMutation("Create or update a search profile with a BOM stack definition")
    void saveProfile(String name, String stack);

    @PlatformMutation("Delete a search profile by name")
    boolean deleteProfile(String name);

    @PlatformQuery("Forward lineage: which corpus entries were used for a given issue")
    List<ProvenanceRecord> forwardLineage(String issueRepo, Integer issueNumber);

    @PlatformQuery("Reverse lineage: which issues consumed a given corpus entry")
    List<ProvenanceRecord> reverseLineage(String entryId);

    @PlatformQuery("Provenance statistics: total links, active entries, coverage")
    ProvenanceStats provenanceStats();

    @PlatformMutation("Record provenance links between corpus entries and an issue/spec")
    int recordProvenance(String issueRepo, Integer issueNumber,
            String specName, @McpParam(format = McpParamFormat.LIST) String entryIds, String recordedBy);

    @PlatformQuery("Outcome entries: all entries with recorded outcomes, sorted by trust score (lowest first)")
    List<OutcomeEntry> outcomeEntries();

    @PlatformMutation("Record an outcome for a corpus entry (success/failure with context)")
    String recordOutcome(String entryId, String issueRepo, Integer issueNumber,
            String workContext, Double successRate, String detail);

    @PlatformMutation("Record retrieval feedback for entries — updates staleness tracking and provenance context. "
            + "outcome must be one of: RELEVANT, HIGHLY_RELEVANT, PARTIALLY_RELEVANT, NOT_RELEVANT, OUTDATED. "
            + "OUTDATED requires stack (e.g. 'quarkus:3.36.1|jdk:26') — marks the entry for revision")
    String recordFeedback(@McpParam(format = McpParamFormat.LIST) String entryIds, String outcome,
            String stack, String issueRepo, Integer issueNumber);

    @PlatformMutation("Record a research finding — stores as a corpus entry with metadata and provenance in one call. "
                      + "Returns the generated entry ID. If sourceIssue is provided (e.g. 'casehubio/neocortex#856'), "
                      + "provenance is recorded automatically. "
                      + "status defaults to 'published'; set to 'draft' for provisional findings. "
                      + "sessionId links the finding to a research session for cross-session continuity.")
    String recordFinding(@ContextParam("corpusId") String corpusId, String topic, String insight,
                         String sourceIssue, @McpParam(format = McpParamFormat.LIST) String tags,
                         String domain, String type, String status, String sessionId);

    @PlatformMutation("Record multiple findings in one call — each finding becomes a corpus entry with provenance. "
                      + "Returns entry IDs for successes; partial failures don't block successful entries.")
    BatchFindingResult recordFindings(@ContextParam("corpusId") String corpusId, List<FindingInput> findings);

    @PlatformMutation("Promote a draft entry to published — makes it visible in default search results")
    String publishEntry(@ContextParam("corpusId") String corpusId, String entryId);

    @PlatformMutation("Archive an entry — removes it from default search results without deleting it")
    String archiveEntry(@ContextParam("corpusId") String corpusId, String entryId);

    @PlatformMutation("Start a research session — creates a named context for cross-session continuity. "
                      + "Returns the session ID. The topic describes what this research investigates.")
    String startSession(@ContextParam("corpusId") String corpusId, String name, String topic);

    @PlatformQuery("Resume a research session — loads context including findings recorded during the session")
    ResearchSessionView resumeSession(@ContextParam("corpusId") String corpusId, String sessionId);

    @PlatformMutation("Pause a research session — saves state for later resumption")
    String pauseSession(@ContextParam("corpusId") String corpusId, String sessionId);

    @PlatformMutation("Complete a research session — marks as done, preserves all findings")
    String completeSession(@ContextParam("corpusId") String corpusId, String sessionId, String summary);

    @PlatformQuery("List active and paused research sessions")
    List<ResearchSessionView> sessions(@ContextParam("corpusId") String corpusId);

    @PlatformQuery("List entries created or modified in this corpus — scoped by time range and optional session. "
                   + "Shows what was contributed for session handover.")
    List<ContributionRecord> contributions(@ContextParam("corpusId") String corpusId,
                                           Instant since, String sessionId);

    @PlatformMutation("Add an annotation to a corpus entry — visible to future sessions alongside the entry content. "
                      + "annotationType must be one of: correction, caveat, update, question. "
                      + "Annotations accumulate — multiple agents can annotate the same entry.")
    String annotate(@ContextParam("corpusId") String corpusId, String entryId,
                    String note, String annotationType);

    @PlatformQuery("List all annotations on a corpus entry — returns typed notes with author and timestamp")
    List<io.casehub.neocortex.corpus.management.Annotation> annotations(
            @ContextParam("corpusId") String corpusId, String entryId);

    @PlatformMutation("Link two entries with a typed relationship. "
                      + "relationship must be one of: supersedes, updates, contradicts, see-also, derived-from. "
                      + "'supersedes' also archives the target entry.")
    String relateEntries(@ContextParam("corpusId") String corpusId,
                         String fromEntryId, String toEntryId, String relationship);

    @PlatformQuery("Get entries related to a given entry — returns bidirectional relationships "
                   + "(e.g. A supersedes B → B shows as 'superseded-by A')")
    List<RelatedEntry> relatedEntries(@ContextParam("corpusId") String corpusId, String entryId);

    @PlatformMutation("Fetch a web page into the corpus cache — corpus-first with URL dedup. "
                      + "Returns cached content on hit (no network). On miss, parses the provided html "
                      + "(or fetches the URL if html is null) and stores as a web-cache corpus entry. "
                      + "Content is returned as positional markdown. ttlDays defaults to 30. "
                      + "artifactRef is an optional reference to a PDF visual archive of the page "
                      + "(e.g. generated by Playwright page.pdf()).")
    WebCaptureResult webCapture(@ContextParam("corpusId") String corpusId, String url,
                                String html, Integer ttlDays, String artifactRef);
}
