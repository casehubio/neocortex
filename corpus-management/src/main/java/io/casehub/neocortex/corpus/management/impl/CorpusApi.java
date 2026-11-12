package io.casehub.neocortex.corpus.management.impl;

import io.casehub.neocortex.rag.ProvenanceRecord;
import io.casehub.neocortex.rag.ProvenanceStats;
import io.casehub.platform.api.mcp.ContextParam;
import io.casehub.platform.api.mcp.McpDomain;
import io.casehub.platform.api.mcp.McpParam;
import io.casehub.platform.api.mcp.McpParamFormat;
import io.casehub.platform.api.mcp.PlatformMutation;
import io.casehub.platform.api.mcp.PlatformQuery;

import io.casehub.neocortex.corpus.management.ChangeResult;

import java.time.Instant;
import java.util.List;

// corpusId parameters are accepted for forward-compatibility with multi-corpus routing.
// Current implementations use a single injected CorpusRef; the corpusId value is not dispatched.
@McpDomain(value = "neocortex/corpus", app = "neocortex",
        summary = "Corpus knowledge management — search, lifecycle, provenance, and feedback")
public interface CorpusApi {

    @PlatformQuery("Search a corpus with optional domain, type, and tag filters. "
            + "Supports search profiles and inline stack overrides (e.g. 'quarkus:3.36.1|jdk:26').")
    List<SearchResult> search(@ContextParam("corpusId") String corpusId, String query,
            List<String> domains, String type, @McpParam(format = McpParamFormat.LIST) String tags,
            Integer limit, String profile, String stack);

    @PlatformQuery("Adaptive search with keyword boosting — extends results "
            + "when initial set is too small, trims when too large. "
            + "Stack format: 'artifact:version' pairs (e.g. 'quarkus:3.36.1|jdk:26').")
    AdaptiveResult adaptiveSearch(@ContextParam("corpusId") String corpusId, String query,
            @McpParam(format = McpParamFormat.LIST) String keywords,
            List<String> domains, String type, @McpParam(format = McpParamFormat.LIST) String tags,
            Integer limit, String profile, String stack);

    @PlatformQuery("Fetch a single corpus entry by ID — same shape as search results for consistent agent experience")
    SearchResult entry(@ContextParam("corpusId") String corpusId, String entryId);

    @PlatformQuery("List changes to a corpus since a given timestamp — shows added and modified entries with content previews. "
                   + "Agent provides a timestamp, system handles cursor management internally.")
    ChangeResult changes(@ContextParam("corpusId") String corpusId, Instant since);


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
}
