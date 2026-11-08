package io.casehub.neocortex.corpus.management.cdi;

import io.casehub.neocortex.corpus.management.CorpusDescriptor;
import io.casehub.neocortex.corpus.management.CorpusEntry;
import io.casehub.neocortex.corpus.management.CorpusSearchResult;
import io.casehub.neocortex.corpus.management.FederatedSearchResult;
import io.casehub.neocortex.corpus.management.HealthReport;
import io.casehub.neocortex.corpus.ChangeSet;
import io.casehub.neocortex.corpus.ChangeSource;
import io.casehub.neocortex.corpus.CorpusReader;
import io.casehub.neocortex.corpus.VersionInfo;
import io.casehub.platform.api.mcp.ContextParam;
import io.casehub.platform.api.mcp.McpCursorMap;
import io.casehub.platform.api.mcp.McpCursorType;
import io.casehub.platform.api.mcp.McpDelegate;
import io.casehub.platform.api.mcp.McpDomain;
import io.casehub.platform.api.mcp.McpEnrich;
import io.casehub.platform.api.mcp.McpExclude;
import io.casehub.platform.api.mcp.McpOnly;
import io.casehub.platform.api.mcp.McpParam;
import io.casehub.platform.api.mcp.McpParamFormat;
import io.casehub.platform.api.mcp.McpAutoRoute;
import io.casehub.platform.api.mcp.McpTransform;
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

    @McpAutoRoute(check = "getEntry", alternate = "updateEntry")
    @PlatformMutation("Add or update a corpus entry — auto-detects create vs update")
    String addEntry(@ContextParam("tenancyId") String tenancyId, String corpusName, String entryId, String body,
                    @McpParam(format = McpParamFormat.MAP) String metadata, String artifactRef);

    @McpEnrich(spi = CorpusReader.class, method = "versions", as = "versions")
    @PlatformQuery(value = "Read a document with full context — content, metadata, version history, provenance tracked", track = true)
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

    @PlatformQuery("Entries the agent hasn't read — blind spot detection powered by automatic read tracking")
    List<String> unread(@ContextParam("tenancyId") String tenancyId, String corpusName, Integer limit);

    // --- MCP-only tools (not projected to REST/GraphQL) ---

    @McpOnly("What changed in a corpus since a point in time — pass an ISO-8601 timestamp, get structured changes back")
    @McpDelegate(spi = ChangeSource.class, method = "changesSince")
    default ChangeSet changes(@McpCursorMap(McpCursorType.INSTANT) String cursor) {
        throw new UnsupportedOperationException("MCP-only");
    }

    @McpOnly("Version history of a specific document — all versions with timestamps and source references")
    @McpDelegate(spi = CorpusReader.class, method = "versions")
    default List<VersionInfo> history(String path) {
        throw new UnsupportedOperationException("MCP-only");
    }

    @McpOnly("Read a historical version of a document — returns parsed content with metadata")
    @McpDelegate(spi = CorpusReader.class, method = "readVersion")
    @McpTransform(spi = io.casehub.neocortex.corpus.management.impl.GardenMetadataExtractor.class, method = "extract")
    default byte[] readVersion(String path, int version) {
        throw new UnsupportedOperationException("MCP-only");
    }

    @McpOnly("List all entries in a corpus with title, last modified, and version count — optionally filtered by path prefix")
    @McpDelegate(spi = CorpusReader.class, method = "list")
    @McpTransform(spi = io.casehub.neocortex.corpus.management.impl.EntrySummariser.class, method = "summarise", perElement = true)
    default List<String> listEntries(String prefix) {
        throw new UnsupportedOperationException("MCP-only");
    }
}
