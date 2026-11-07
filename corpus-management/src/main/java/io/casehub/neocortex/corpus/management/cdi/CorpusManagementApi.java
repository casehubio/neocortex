package io.casehub.neocortex.corpus.management.cdi;

import io.casehub.neocortex.corpus.management.CorpusDescriptor;
import io.casehub.neocortex.corpus.management.CorpusEntry;
import io.casehub.neocortex.corpus.management.CorpusSearchResult;
import io.casehub.neocortex.corpus.management.FederatedSearchResult;
import io.casehub.neocortex.corpus.management.HealthReport;
import io.casehub.platform.api.mcp.ContextParam;
import io.casehub.platform.api.mcp.McpDomain;
import io.casehub.platform.api.mcp.McpExclude;
import io.casehub.platform.api.mcp.McpParam;
import io.casehub.platform.api.mcp.McpParamFormat;
import io.casehub.platform.api.mcp.PlatformMutation;
import io.casehub.platform.api.mcp.PlatformQuery;

import java.util.List;
import java.util.Map;

@McpDomain(value = "corpus-management", app = "neocortex",
        summary = "Corpus catalogue, entry CRUD, and federated search — generic SPI surface for LLM agents")
public interface CorpusManagementApi {

    @PlatformQuery("List all corpora for a tenant, filtered by lifecycle status (ACTIVE or ARCHIVED)")
    List<CorpusDescriptor> listCorpora(@ContextParam("tenancyId") String tenancyId, String lifecycle);

    @PlatformQuery("Get a single corpus descriptor by tenant and corpus name")
    CorpusDescriptor getCorpus(@ContextParam("tenancyId") String tenancyId, String corpusName);

    @PlatformQuery("List corpora of a given type for a tenant")
    List<CorpusDescriptor> listByType(String typeName, @ContextParam("tenancyId") String tenancyId);

    @PlatformQuery("Resolve the full search scope for a corpus — includes upstream and peer corpora reachable via federation")
    List<CorpusDescriptor> searchScope(@ContextParam("tenancyId") String tenancyId, String corpusName);

    @PlatformQuery("Run a health reconciliation check for a tenant's corpora")
    HealthReport reconcile(@ContextParam("tenancyId") String tenancyId);

    @PlatformMutation("Add a new entry to a corpus")
    String addEntry(@ContextParam("tenancyId") String tenancyId, String corpusName, String entryId, String body,
                    @McpParam(format = McpParamFormat.MAP) String metadata, String artifactRef);

    @PlatformQuery("Get a single entry from a corpus by ID")
    CorpusEntry getEntry(@ContextParam("tenancyId") String tenancyId, String corpusName, String entryId);

    @McpExclude
    @PlatformMutation("Update an existing entry in a corpus")
    String updateEntry(@ContextParam("tenancyId") String tenancyId, String corpusName, String entryId, String body,
                       @McpParam(format = McpParamFormat.MAP) String metadata, String artifactRef);

    @PlatformMutation("Remove an entry from a corpus and its vector index")
    String removeEntry(@ContextParam("tenancyId") String tenancyId, String corpusName, String entryId);

    @PlatformQuery("Search a single corpus by text query")
    List<CorpusSearchResult> searchCorpus(@ContextParam("tenancyId") String tenancyId, String corpusName,
                                          String query, Integer maxResults);

    @PlatformQuery("Federated search across corpora — walks upstream and peer federation links, "
            + "deduplicates by entry ID, returns results ranked by tier then score")
    FederatedSearchResult federatedSearch(@ContextParam("tenancyId") String tenancyId, String query,
                                          Integer maxResults, Integer federationDepth);
}
