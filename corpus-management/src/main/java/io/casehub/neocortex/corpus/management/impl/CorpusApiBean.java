package io.casehub.neocortex.corpus.management.impl;

import io.casehub.connectors.web.model.CapturedImage;
import io.casehub.connectors.web.model.FetchResult;
import io.casehub.connectors.web.model.ImageProcessingResult;
import io.casehub.connectors.web.model.InterventionRequest;
import io.casehub.connectors.web.spi.WebPlatform;
import io.casehub.neocortex.corpus.VersionInfo;
import io.casehub.neocortex.corpus.management.ChangeResult;
import io.casehub.neocortex.corpus.management.CorpusEntry;
import io.casehub.neocortex.corpus.management.CorpusEntryStore;
import io.casehub.neocortex.corpus.management.WebCaptureConfig;
import io.casehub.neocortex.corpus.management.WebCaptureMetadata;
import io.casehub.neocortex.rag.CorpusRef;
import io.casehub.neocortex.rag.ProvenanceRecord;
import io.casehub.neocortex.rag.ProvenanceStats;

import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;

public class CorpusApiBean implements CorpusApi {

    private final SearchResource searchResource;
    private final CorpusIndexService   indexService;
    private final CorpusReindexService reindexService;
    private final SearchProfileStore   profileStore;
    private final ProvenanceStore provenanceStore;
    private final CorpusOutcomeService outcomeService;
    private final CorpusRef corpusRef;
    private final Path      corpusRoot;
    private final CorpusEntryStore entryStore;
    private final io.casehub.neocortex.knowledge.ResearchSessionService sessionService;
    private final WebCaptureService webCaptureService;
    private final WebPlatform webPlatform;
    private final WebCaptureConfig webCaptureConfig;
    // shortcut: screenshot refs live with this facade until WebPlatform accepts them in its queue.
    private final java.util.Map<String, String> interventionScreenshots = new java.util.concurrent.ConcurrentHashMap<>();
    private final java.util.concurrent.ConcurrentHashMap<String, java.util.List<ResearchSessionView.FindingSummary>> sessionFindings = new java.util.concurrent.ConcurrentHashMap<>();
    private final java.util.List<ContributionRecord>                                                                 contributionLog = new java.util.concurrent.CopyOnWriteArrayList<>();


    public CorpusApiBean(SearchResource searchResource,
                         CorpusIndexService indexService,
                         CorpusReindexService reindexService,
                         SearchProfileStore profileStore,
                         ProvenanceStore provenanceStore,
                         CorpusOutcomeService outcomeService,
                         CorpusRef corpusRef,
                         Path corpusRoot,
                         CorpusEntryStore entryStore,
                         io.casehub.neocortex.knowledge.ResearchSessionService sessionService) {
        this(searchResource, indexService, reindexService, profileStore, provenanceStore,
             outcomeService, corpusRef, corpusRoot, entryStore, sessionService, null);
    }

    public CorpusApiBean(SearchResource searchResource,
                         CorpusIndexService indexService,
                         CorpusReindexService reindexService,
                         SearchProfileStore profileStore,
                         ProvenanceStore provenanceStore,
                         CorpusOutcomeService outcomeService,
                         CorpusRef corpusRef,
                         Path corpusRoot,
                         CorpusEntryStore entryStore,
                         io.casehub.neocortex.knowledge.ResearchSessionService sessionService,
                         WebPlatform webPlatform) {
        this(searchResource, indexService, reindexService, profileStore, provenanceStore,
             outcomeService, corpusRef, corpusRoot, entryStore, sessionService, webPlatform, null);
    }

    public CorpusApiBean(SearchResource searchResource,
                         CorpusIndexService indexService,
                         CorpusReindexService reindexService,
                         SearchProfileStore profileStore,
                         ProvenanceStore provenanceStore,
                         CorpusOutcomeService outcomeService,
                         CorpusRef corpusRef,
                         Path corpusRoot,
                         CorpusEntryStore entryStore,
                         io.casehub.neocortex.knowledge.ResearchSessionService sessionService,
                         WebPlatform webPlatform,
                         WebCaptureConfig webCaptureConfig) {
        this.searchResource    = searchResource;
        this.indexService      = indexService;
        this.reindexService    = reindexService;
        this.profileStore      = profileStore;
        this.provenanceStore   = provenanceStore;
        this.outcomeService    = outcomeService;
        this.corpusRef         = corpusRef;
        this.corpusRoot        = corpusRoot;
        this.entryStore        = entryStore;
        this.sessionService    = sessionService;
        this.webCaptureService = new WebCaptureService(entryStore);
        this.webPlatform       = webPlatform;
        this.webCaptureConfig  = webCaptureConfig != null ? webCaptureConfig : WebCaptureConfig.DEFAULT;
    }

    CorpusApiBean(CorpusEntryStore entryStore, CorpusRef corpusRef, WebPlatform webPlatform) {
        this.searchResource     = null;
        this.indexService       = null;
        this.reindexService     = null;
        this.profileStore       = null;
        this.provenanceStore    = null;
        this.outcomeService     = null;
        this.corpusRef          = corpusRef;
        this.corpusRoot         = null;
        this.entryStore         = entryStore;
        this.sessionService     = null;
        this.webCaptureService  = new WebCaptureService(entryStore);
        this.webPlatform        = webPlatform;
        this.webCaptureConfig   = WebCaptureConfig.DEFAULT;
    }


    @Override
    public List<SearchResult> search(String corpusId, String query,
                                     List<String> domains, String type, String tags,
                                     Integer limit, String profile, String stack, Integer previewLength, String includeStatus) {
        List<SearchResult> results;
        if (profile != null || stack != null) {
            results = searchResource.searchAdaptive(query, null, domains, type, tags,
                                                    limit, profile, stack, includeStatus).results();
        } else {
            results = searchResource.search(query, domains, type, tags, limit, null, includeStatus);
        }
        return enrichWithRelationshipCounts(enrichWithAnnotationCounts(applyPreview(results, previewLength)));
    }

    @Override
    public AdaptiveResult adaptiveSearch(String corpusId, String query,
                                         String keywords, List<String> domains, String type,
                                         String tags, Integer limit, String profile, String stack, Integer previewLength, String includeStatus) {
        String expandedKeywords = keywords != null && !keywords.isBlank()
                                  ? keywords.replace("|", " ") : null;
        AdaptiveResult result = searchResource.searchAdaptive(query, expandedKeywords,
                                                              domains, type, tags, limit, profile, stack, includeStatus);
        var enriched = enrichWithRelationshipCounts(enrichWithAnnotationCounts(
                applyPreview(result.results(), previewLength != null ? previewLength : null)));
        return result.withResults(enriched);
    }

    private static List<SearchResult> applyPreview(List<SearchResult> results, Integer previewLength) {
        if (previewLength == null || results.isEmpty()) {return results;}
        return results.stream().map(r -> r.withPreview(previewLength)).toList();
    }

    private List<SearchResult> enrichWithAnnotationCounts(List<SearchResult> results) {
        if (results.isEmpty()) {return results;}
        var ids    = results.stream().map(SearchResult::id).toList();
        var counts = entryStore.annotationCounts(corpusRef, ids);
        if (counts.isEmpty()) {return results;}
        return results.stream()
                      .map(r -> {
                          Integer count = counts.get(r.id());
                          return count != null && count > 0 ? r.withAnnotationData(count, null) : r;
                      })
                      .toList();
    }

    private List<SearchResult> enrichWithRelationshipCounts(List<SearchResult> results) {
        if (results.isEmpty()) {return results;}
        var ids    = results.stream().map(SearchResult::id).toList();
        var counts = entryStore.relationshipCounts(corpusRef, ids);
        if (counts.isEmpty()) {return results;}
        return results.stream()
                      .map(r -> {
                          Integer count = counts.get(r.id());
                          return count != null && count > 0 ? r.withExtraMetadata("relationship_count", String.valueOf(count)) : r;
                      })
                      .toList();
    }


    @Override
    public SearchResult entry(String corpusId, String entryId) {
        if (corpusRoot == null) {return null;}
        SearchResult result = indexService.entry(corpusRoot, entryId);
        if (result == null) {return null;}
        var annotationList = entryStore.annotations(corpusRef, entryId);
        result = result.withAnnotationData(annotationList.size(), annotationList);
        result = enrichWithAuditInfo(result, entryId);
        return result;
    }

    private SearchResult enrichWithAuditInfo(SearchResult result, String entryId) {
        var audit = entryStore.auditInfo(corpusRef, entryId);
        if (audit.isEmpty()) {return result;}
        var info = audit.get();
        if (info.createdBy() != null) {result = result.withExtraMetadata("created_by", info.createdBy());}
        if (info.createdAt() != null) {result = result.withExtraMetadata("created_at", info.createdAt().toString());}
        if (info.updatedBy() != null) {result = result.withExtraMetadata("updated_by", info.updatedBy());}
        if (info.updatedAt() != null) {result = result.withExtraMetadata("updated_at", info.updatedAt().toString());}
        return result;
    }

    @Override
    public ChangeResult changes(String corpusId, java.time.Instant since) {
        var records = entryStore.recentChanges(corpusRef, since);
        return new ChangeResult(records);
    }

    @Override
    public List<VersionInfo> versions(String corpusId, String entryId) {
        return entryStore.versions(corpusRef, entryId);
    }

    @Override
    public CorpusEntry version(String corpusId, String entryId, int version) {
        return entryStore.getVersion(corpusRef, entryId, version).orElse(null);
    }


    @Override
    public CorpusStatus status(String corpusId) {
        return indexService.status(corpusRef, corpusRoot != null ? corpusRoot.toString() : "");
    }

    @Override
    public UnretrievedReport unretrieved(String corpusId, Integer minDays, Integer staleDays) {
        return indexService.unretrieved(corpusRef,
                minDays != null ? minDays : 30,
                staleDays != null ? staleDays : 90);
    }

    @Override
    public CorpusReindexService.ReindexResult reindex(String corpusId) {
        return reindexService.reindex();
    }

    @Override
    public String reindexEntry(String corpusId, String entryId) {
        if (corpusRoot == null) return "No corpus root configured — cannot reindex.";
        return indexService.reindexEntry(corpusRef, entryId, corpusRoot);
    }

    @Override
    public String deleteEntry(String corpusId, String entryId) {
        return indexService.deleteEntry(corpusRef, entryId);
    }

    @Override
    public String profile(String name) {
        return profileStore.getRawStack(name);
    }

    @Override
    public List<String> profiles() {
        return profileStore.list();
    }

    @Override
    public void saveProfile(String name, String stack) {
        profileStore.put(name, stack);
    }

    @Override
    public boolean deleteProfile(String name) {
        return profileStore.delete(name);
    }

    @Override
    public List<ProvenanceRecord> forwardLineage(String issueRepo, Integer issueNumber) {
        return provenanceStore.forwardLineage(issueRepo, issueNumber != null ? issueNumber : 0);
    }

    @Override
    public List<ProvenanceRecord> reverseLineage(String entryId) {
        return provenanceStore.reverseLineage(entryId);
    }

    @Override
    public ProvenanceStats provenanceStats() {
        return provenanceStore.stats();
    }

    @Override
    public int recordProvenance(String issueRepo, Integer issueNumber,
            String specName, String entryIds, String recordedBy) {
        if (entryIds == null || entryIds.isBlank()) return 0;
        List<String> ids = Arrays.stream(entryIds.split("\\|"))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .toList();
        return provenanceStore.record(issueRepo, issueNumber != null ? issueNumber : 0,
                specName != null ? specName : "", ids, recordedBy);
    }

    @Override
    public List<OutcomeEntry> outcomeEntries() {
        return outcomeService.outcomeEntries();
    }

    @Override
    public String recordOutcome(String entryId, String issueRepo, Integer issueNumber,
            String workContext, Double successRate, String detail) {
        return outcomeService.recordOutcome(entryId, issueRepo,
                issueNumber != null ? issueNumber : 0,
                workContext, successRate != null ? successRate : 0.0, detail);
    }

    @Override
    public String recordFeedback(String entryIds, String outcome,
            String stack, String issueRepo, Integer issueNumber) {
        if (entryIds == null || entryIds.isBlank()) return "Error: no entry IDs provided.";
        List<String> ids = Arrays.stream(entryIds.split("\\|"))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .toList();
        return indexService.recordFeedback(corpusRef, ids, outcome, stack, issueRepo, issueNumber);
    }

    @Override
    public String recordFinding(String corpusId, String topic, String insight,
                                String sourceIssue, String tags, String domain, String type,
                                String status, String sessionId) {
        if (topic == null || topic.isBlank()) {return "Error: topic is required.";}
        if (insight == null || insight.isBlank()) {return "Error: insight is required.";}

        List<String> parsedTags = tags != null && !tags.isBlank()
                                  ? Arrays.stream(tags.split("[|,]")).map(String::trim).filter(s -> !s.isEmpty()).toList()
                                  : List.of();

        String resolvedStatus = (status != null && !status.isBlank()) ? status.toLowerCase() : "published";
        return storeFinding(topic, insight, sourceIssue, parsedTags, domain, type, resolvedStatus, sessionId);
    }

    @Override
    public BatchFindingResult recordFindings(String corpusId, List<FindingInput> findings) {
        if (findings == null || findings.isEmpty()) {
            return new BatchFindingResult(List.of(), 0, 0, List.of("Error: findings list is empty."));
        }

        var entryIds = new java.util.ArrayList<String>();
        var errors   = new java.util.ArrayList<String>();

        for (int i = 0; i < findings.size(); i++) {
            FindingInput f = findings.get(i);
            if (f.topic() == null || f.topic().isBlank()) {
                errors.add("Finding " + (i + 1) + ": topic is required.");
                continue;
            }
            if (f.insight() == null || f.insight().isBlank()) {
                errors.add("Finding " + (i + 1) + ": insight is required.");
                continue;
            }
            try {
                List<String> tags           = f.tags() != null ? f.tags() : List.of();
                String       resolvedStatus = (f.status() != null && !f.status().isBlank()) ? f.status().toLowerCase() : "published";
                String       id             = storeFinding(f.topic(), f.insight(), f.sourceIssue(), tags, f.domain(), f.type(), resolvedStatus, f.sessionId());
                entryIds.add(id);
            } catch (Exception e) {
                errors.add("Finding " + (i + 1) + " (" + f.topic() + "): " + e.getMessage());
            }
        }

        return new BatchFindingResult(List.copyOf(entryIds), findings.size(), entryIds.size(), List.copyOf(errors));
    }

    @Override
    public String publishEntry(String corpusId, String entryId) {
        return transitionStatus(entryId, "published");
    }

    @Override
    public String archiveEntry(String corpusId, String entryId) {
        return transitionStatus(entryId, "archived");
    }

    private String transitionStatus(String entryId, String newStatus) {
        if (entryId == null || entryId.isBlank()) {return "Error: entryId is required.";}
        var existing = entryStore.get(corpusRef, entryId);
        if (existing.isEmpty()) {return "Error: entry not found: " + entryId;}

        var    metadata  = new java.util.LinkedHashMap<>(existing.get().metadata());
        String oldStatus = (String) metadata.getOrDefault("status", "published");
        metadata.put("status", newStatus);

        var updated = new CorpusEntry(entryId, java.util.Map.copyOf(metadata),
                                      existing.get().body(), existing.get().artifactRef());
        entryStore.update(corpusRef, entryId, updated, "agent");

        String title     = (String) metadata.getOrDefault("title", entryId);
        String sessionId = (String) metadata.get("sessionId");
        contributionLog.add(new ContributionRecord(entryId, title, "modified", java.time.Instant.now(), sessionId));

        return oldStatus + " → " + newStatus;
    }

    @Override
    public String startSession(String corpusId, String name, String topic) {
        if (sessionService == null) {return "Error: research sessions not available.";}
        if (name == null || name.isBlank()) {return "Error: session name is required.";}
        var session = sessionService.create(name, topic, corpusRef.tenantId());
        return session.id();
    }

    @Override
    public ResearchSessionView resumeSession(String corpusId, String sessionId) {
        if (sessionService == null) {return null;}
        if (sessionId == null || sessionId.isBlank()) {return null;}
        var session = sessionService.get(sessionId);
        if (session == null) {return null;}
        if (session.state() == io.casehub.neocortex.knowledge.ResearchState.PAUSED) {
            sessionService.resume(sessionId);
            session = sessionService.get(sessionId);
        }
        return toView(session);
    }

    @Override
    public String pauseSession(String corpusId, String sessionId) {
        if (sessionService == null) {return "Error: research sessions not available.";}
        if (sessionId == null || sessionId.isBlank()) {return "Error: sessionId is required.";}
        sessionService.pause(sessionId);
        return "Session paused.";
    }

    @Override
    public String completeSession(String corpusId, String sessionId, String summary) {
        if (sessionService == null) {return "Error: research sessions not available.";}
        if (sessionId == null || sessionId.isBlank()) {return "Error: sessionId is required.";}
        sessionService.complete(sessionId);
        return "Session completed.";
    }

    @Override
    public List<ResearchSessionView> sessions(String corpusId) {
        if (sessionService == null) {return List.of();}
        return sessionService.list(corpusRef.tenantId()).stream()
                             .map(this::toView)
                             .toList();
    }

    @Override
    public List<ContributionRecord> contributions(String corpusId, java.time.Instant since, String sessionId) {
        return contributionLog.stream()
                              .filter(c -> since == null || c.timestamp().isAfter(since))
                              .filter(c -> sessionId == null || sessionId.isBlank() || sessionId.equals(c.sessionId()))
                              .toList();
    }

    private static final java.util.Set<String> VALID_ANNOTATION_TYPES =
            java.util.Set.of("correction", "caveat", "update", "question");

    @Override
    public String annotate(String corpusId, String entryId, String note, String annotationType) {
        if (entryId == null || entryId.isBlank()) {return "Error: entryId is required.";}
        if (note == null || note.isBlank()) {return "Error: note is required.";}
        if (annotationType == null || annotationType.isBlank()) {return "Error: annotationType is required.";}
        String normalised = annotationType.toLowerCase();
        if (!VALID_ANNOTATION_TYPES.contains(normalised)) {
            return "Error: annotationType must be one of: correction, caveat, update, question.";
        }
        return entryStore.addAnnotation(corpusRef, entryId, note, normalised, "agent");
    }

    @Override
    public java.util.List<io.casehub.neocortex.corpus.management.Annotation> annotations(String corpusId, String entryId) {
        if (entryId == null || entryId.isBlank()) {return java.util.List.of();}
        return entryStore.annotations(corpusRef, entryId);
    }

    private static final java.util.Set<String> VALID_RELATIONSHIP_TYPES =
            java.util.Set.of("supersedes", "updates", "contradicts", "see-also", "derived-from");

    @Override
    public String relateEntries(String corpusId, String fromEntryId, String toEntryId, String relationship) {
        if (fromEntryId == null || fromEntryId.isBlank()) {return "Error: fromEntryId is required.";}
        if (toEntryId == null || toEntryId.isBlank()) {return "Error: toEntryId is required.";}
        if (relationship == null || relationship.isBlank()) {return "Error: relationship is required.";}
        String normalised = relationship.toLowerCase();
        if (!VALID_RELATIONSHIP_TYPES.contains(normalised)) {
            return "Error: relationship must be one of: supersedes, updates, contradicts, see-also, derived-from.";
        }
        String id = entryStore.addRelationship(corpusRef, fromEntryId, toEntryId, normalised, "agent");

        if ("supersedes".equals(normalised)) {
            transitionStatus(toEntryId, "archived");
        }

        return id;
    }

    @Override
    public java.util.List<RelatedEntry> relatedEntries(String corpusId, String entryId) {
        if (entryId == null || entryId.isBlank()) {return java.util.List.of();}
        var relationships = entryStore.relationships(corpusRef, entryId);
        return relationships.stream()
                            .map(r -> {
                                if (r.fromEntryId().equals(entryId)) {
                                    return RelatedEntry.forward(r.toEntryId(), r.relationshipType(), r.createdAt());
                                } else {
                                    return RelatedEntry.inverse(r.fromEntryId(), r.relationshipType(), r.createdAt());
                                }
                            })
                            .toList();
    }


    private ResearchSessionView toView(io.casehub.neocortex.knowledge.ResearchSession session) {
        List<ResearchSessionView.FindingSummary> findings =
                sessionFindings.getOrDefault(session.id(), List.of());
        return new ResearchSessionView(
                session.id(), session.name(), session.criteria(),
                session.state().name().toLowerCase(), session.mindMapSubgraphId(),
                session.createdAt(), session.lastActive(), List.copyOf(findings));
    }


    private String storeFinding(String topic, String insight, String sourceIssue,
                                List<String> tags, String domain, String type,
                                String status, String sessionId) {
        String entryId = generateEntryId();

        var metadata = new java.util.LinkedHashMap<String, Object>();
        metadata.put("title", topic);
        if (domain != null && !domain.isBlank()) {metadata.put("domain", domain);}
        if (type != null && !type.isBlank()) {metadata.put("type", type);}
        if (tags != null && !tags.isEmpty()) {metadata.put("tags", tags);}
        metadata.put("status", status != null ? status : "published");
        if (sessionId != null && !sessionId.isBlank()) {metadata.put("sessionId", sessionId);}

        var entry = new CorpusEntry(entryId, java.util.Map.copyOf(metadata), insight, null);
        entryStore.add(corpusRef, entry, "agent");

        if (sourceIssue != null && !sourceIssue.isBlank()) {
            var parts = parseIssueRef(sourceIssue);
            if (parts != null) {
                provenanceStore.record(parts[0], Integer.parseInt(parts[1]),
                                       "", List.of(entryId), "recordFinding");
            }
        }

        if (sessionId != null && !sessionId.isBlank()) {
            sessionFindings.computeIfAbsent(sessionId, k -> java.util.Collections.synchronizedList(new java.util.ArrayList<>()))
                           .add(new ResearchSessionView.FindingSummary(entryId, topic));
        }

        contributionLog.add(new ContributionRecord(entryId, topic, "created", java.time.Instant.now(), sessionId));

        return entryId;
    }

    private static String generateEntryId() {
        String date = java.time.LocalDate.now().format(java.time.format.DateTimeFormatter.BASIC_ISO_DATE);
        byte[] bytes = new byte[3];
        java.util.concurrent.ThreadLocalRandom.current().nextBytes(bytes);
        String hex = String.format("%02x%02x%02x", bytes[0] & 0xFF, bytes[1] & 0xFF, bytes[2] & 0xFF);
        return "GE-" + date + "-" + hex;
    }

    @Override
    public WebCaptureResult webCapture(String corpusId, String url, String html,
                                       Integer ttlDays, String artifactRef) {
        if (url == null || url.isBlank()) {
            throw new IllegalArgumentException("url is required");
        }

        int ttl = ttlDays != null ? ttlDays : webCaptureConfig.defaultTtlDays();

        java.util.Optional<io.casehub.neocortex.corpus.management.CorpusEntry> cached =
                webCaptureService.findFreshByUrl(corpusRef, url, ttl);

        if (cached.isPresent()) {
            var entry = cached.get();
            return new WebCaptureResult(
                    entry.id(), url,
                    entry.stringField("title"),
                    entry.body(),
                    entry.stringField(WebCaptureMetadata.FETCHED_AT),
                    true, 0, null, null);
        }

        String content;
        String contentType;
        int    httpStatus;

        if (html != null && !html.isBlank()) {
            content     = html;
            contentType = "text/html";
            httpStatus  = 200;
        } else if (webPlatform != null && webPlatform.supports(WebPlatform.Fetch.class)) {
            FetchResult fetchResult;
            try {
                fetchResult = webPlatform.fetch().fetch(url);
            } catch (Exception e) {
                return new WebCaptureResult(null, url, null, null, null, false, 0, null,
                        "Fetch failed: " + e.getMessage());
            }
            content     = fetchResult.html() != null ? fetchResult.html() : "";
            contentType = fetchResult.contentType();
            httpStatus  = fetchResult.statusCode();
            if (httpStatus == 0 || httpStatus >= 400) {
                return new WebCaptureResult(null, url, null, null, null, false, 0, null,
                        "Fetch failed: HTTP " + httpStatus);
            }
        } else {
            throw new UnsupportedOperationException(
                    "WebPlatform Fetch capability required — no html provided and no fetch provider configured");
        }

        String markdown;
        if (webPlatform != null && webPlatform.supports(WebPlatform.Parse.class)) {
            markdown = webPlatform.parse().parseHtml(content, url).positionalMarkdown();
        } else {
            markdown = content;
        }

        int                   imageCount  = 0;
        ImageProcessingResult imageResult = ImageProcessingResult.EMPTY;
        if (webPlatform != null && webPlatform.supports(WebPlatform.ImageCapture.class)) {
            try {
                imageResult = webPlatform.imageCapture().extractImages(content, url);
                if (!imageResult.images().isEmpty()) {
                    markdown = webPlatform.imageCapture().enrichMarkdown(markdown, imageResult);
                }
            } catch (Exception e) {
                java.util.logging.Logger.getLogger(CorpusApiBean.class.getName())
                                        .log(java.util.logging.Level.WARNING, "Image capture failed for " + url, e);
            }
        }

        String title    = null;
        int    titleEnd = markdown.indexOf('\n');
        if (titleEnd > 0) {
            String firstLine = markdown.substring(0, titleEnd).strip();
            if (firstLine.startsWith("# ")) {
                title = firstLine.substring(2).strip();
            }
        }

        var result = webCaptureService.capture(corpusRef, url, markdown,
                                               httpStatus, contentType, "mcp-tool", ttl, artifactRef, "corpus-api");

        for (int i = 0; i < imageResult.images().size(); i++) {
            CapturedImage img          = imageResult.images().get(i);
            String        imageEntryId = "wci-" + result.entryId() + "-" + i;

            java.util.Map<String, Object> imgMeta = new java.util.LinkedHashMap<>();
            imgMeta.put("domain", "web-cache-image");
            imgMeta.put("source_url", img.sourceUrl());
            imgMeta.put("parent_url", url);
            imgMeta.put("parent_entry_id", result.entryId());
            imgMeta.put("alt_text", img.altText());
            imgMeta.put("mime_type", img.mimeType());
            imgMeta.put("fetched_at", java.time.Instant.now().toString());

            String body       = img.description() != null ? img.description() : img.altText();
            var    imageEntry = new CorpusEntry(imageEntryId, imgMeta, body, null);
            entryStore.add(corpusRef, imageEntry, "corpus-api");

            entryStore.addRelationship(corpusRef, imageEntryId,
                                       result.entryId(), "derived-from", "corpus-api");
            imageCount++;
        }

        return new WebCaptureResult(result.entryId(), url, title, markdown,
                                    java.time.Instant.now().toString(), false, imageCount, null, null);
    }

    @Override
    public synchronized InterventionRequest requestIntervention(String corpusId, String url,
                                                   String reason, String screenshotRef) {
        if (url == null || url.isBlank()) {
            throw new IllegalArgumentException("url is required");
        }
        if (webPlatform == null || !webPlatform.supports(WebPlatform.InterventionManagement.class)) {
            throw new UnsupportedOperationException("Intervention management not configured");
        }
        if (!interventionScreenshots.isEmpty()) { pendingInterventions(corpusId); }
        String id = webPlatform.interventionManagement().request(url, reason);
        if (screenshotRef != null) { interventionScreenshots.put(id, screenshotRef); }
        return new InterventionRequest(id, url, reason, screenshotRef, java.time.Instant.now());
    }

    @Override
    public synchronized java.util.List<InterventionRequest> pendingInterventions(String corpusId) {
        if (webPlatform == null || !webPlatform.supports(WebPlatform.InterventionManagement.class)) {
            return java.util.List.of();
        }
        var pending = webPlatform.interventionManagement().pending();
        interventionScreenshots.keySet().retainAll(pending.stream()
                .map(InterventionRequest::id).collect(java.util.stream.Collectors.toSet()));
        return pending.stream().map(request -> {
            String screenshot = interventionScreenshots.get(request.id());
            return screenshot == null ? request : new InterventionRequest(request.id(), request.url(),
                    request.reason(), screenshot, request.submittedAt());
        }).toList();
    }

    @Override
    public synchronized WebCaptureResult resolveIntervention(String corpusId, String interventionId,
                                                String resolvedHtml, Integer ttlDays,
                                                String artifactRef) {
        if (webPlatform == null || !webPlatform.supports(WebPlatform.InterventionManagement.class)) {
            return new WebCaptureResult(null, null, null, null, null, false, 0,
                                        interventionId, "Intervention management not configured");
        }
        var resolved = pendingInterventions(corpusId).stream()
                .filter(request -> request.id().equals(interventionId)).findFirst();
        if (resolved.isEmpty()) {
            return new WebCaptureResult(null, null, null, null, null, false, 0,
                                        interventionId, "Intervention not found or expired");
        }
        var request       = resolved.get();
        if (resolvedHtml == null || resolvedHtml.isBlank()) {
            return new WebCaptureResult(null, request.url(), null, null, null, false, 0,
                    interventionId, "Resolved HTML is required");
        }
        var captureResult = webCapture(corpusId, request.url(), resolvedHtml, ttlDays, artifactRef);
        if (captureResult.error() == null) {
            webPlatform.interventionManagement().resolve(interventionId, resolvedHtml);
            interventionScreenshots.remove(interventionId);
        }
        return new WebCaptureResult(captureResult.entryId(), captureResult.url(),
                                    captureResult.title(), captureResult.body(), captureResult.fetchedAt(),
                                    captureResult.cached(), captureResult.imageCount(),
                                    interventionId, captureResult.error());
    }

    @Override
    public WebCacheCurationService.PurgeResult purgeExpiredWebCache(String corpusId) {
        var curationService = new WebCacheCurationService(entryStore, null);
        return curationService.purgeExpired(corpusRef);
    }

    @Override
    public WebCacheStatusResult webCacheStatus(String corpusId) {
        java.util.List<io.casehub.neocortex.corpus.management.CorpusEntry> entries =
                entryStore.findByMetadataField(corpusRef, "domain", io.casehub.neocortex.corpus.management.WebCaptureMetadata.DOMAIN);

        if (entries.isEmpty()) {
            return new WebCacheStatusResult(0, null, null, 0);
        }

        java.time.Instant now          = java.time.Instant.now();
        String            oldest       = null;
        String            newest       = null;
        java.time.Instant oldestTime   = null;
        java.time.Instant newestTime   = null;
        int               expiredCount = 0;

        for (var entry : entries) {
            String fetchedAt = entry.stringField(io.casehub.neocortex.corpus.management.WebCaptureMetadata.FETCHED_AT);
            if (fetchedAt == null) {
                expiredCount++;
                continue;
            }
            java.time.Instant fetchTime = java.time.Instant.parse(fetchedAt);
            if (oldestTime == null || fetchTime.isBefore(oldestTime)) {
                oldestTime = fetchTime;
                oldest     = fetchedAt;
            }
            if (newestTime == null || fetchTime.isAfter(newestTime)) {
                newestTime = fetchTime;
                newest     = fetchedAt;
            }
            Object ttlObj = entry.metadata().get(io.casehub.neocortex.corpus.management.WebCaptureMetadata.TTL_DAYS);
            int    ttl    = ttlObj instanceof Number n ? n.intValue() : webCaptureConfig.defaultTtlDays();
            if (fetchTime.isBefore(now.minus(java.time.Duration.ofDays(ttl)))) {
                expiredCount++;
            }
        }

        return new WebCacheStatusResult(entries.size(), oldest, newest, expiredCount);
    }


    private static String[] parseIssueRef(String issueRef) {
        int hashIndex = issueRef.lastIndexOf('#');
        if (hashIndex <= 0) {return null;}
        String repo = issueRef.substring(0, hashIndex);
        String number = issueRef.substring(hashIndex + 1);
        try {
            Integer.parseInt(number);
            return new String[]{repo, number};
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
