package io.casehub.neocortex.corpus.management.impl;

import io.casehub.neocortex.rag.AdaptiveFilter;
import io.casehub.neocortex.rag.AdaptiveFilterOptions;
import io.casehub.neocortex.rag.AdaptiveSearchConfig;
import io.casehub.neocortex.rag.CaseRetriever;
import io.casehub.neocortex.rag.CorpusRef;
import io.casehub.neocortex.rag.PayloadFilter;
import io.casehub.neocortex.rag.RetrievalQuery;
import io.casehub.neocortex.rag.RetrievedChunk;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

public class SearchResource {

    public static final int DEFAULT_LIMIT                 = 16;
    public static final int MAX_LIMIT                     = 50;
    static final        int OVERFETCH_MULTIPLIER          = 2;
    static final        int OVERFETCH_MULTIPLIER_KEYWORDS = 3;
    private static final Logger LOG = Logger.getLogger(SearchResource.class.getName());
    private static final double                           LN2               = 0.693147;
    private static final java.time.Duration               DEFAULT_HALF_LIFE = java.time.Duration.ofDays(365);
    private static final Map<Integer, java.time.Duration> TIER_HALF_LIVES   = Map.of(
            0, java.time.Duration.ofDays(730),
            1, java.time.Duration.ofDays(365),
            2, java.time.Duration.ofDays(180),
            3, java.time.Duration.ofDays(90)
                                                                                    );
    private final CaseRetriever       caseRetriever;
    private final CorpusRef           corpusRef;
    private final FederationConfig    federationConfig;
    private final FederationWalker    federationWalker;
    private final SearchConfig        searchConfig;
    private final SearchScoringConfig scoringConfig;
    private final SearchProfileStore  profileStore;

    public SearchResource(CaseRetriever caseRetriever, CorpusRef corpusRef,
                          FederationConfig federationConfig, FederationWalker federationWalker,
                          SearchConfig searchConfig, SearchScoringConfig scoringConfig,
                          SearchProfileStore profileStore) {
        this.caseRetriever    = caseRetriever;
        this.corpusRef        = corpusRef;
        this.federationConfig = federationConfig;
        this.federationWalker = federationWalker;
        this.searchConfig     = searchConfig;
        this.scoringConfig    = scoringConfig;
        this.profileStore     = profileStore;
    }

    static double temporalDecayScore(String submitted, String decayTier) {
        if (submitted == null || submitted.isBlank()) {return 1.0;}
        try {
            var  date      = java.time.LocalDate.parse(submitted.length() > 10 ? submitted.substring(0, 10) : submitted);
            long daysSince = java.time.temporal.ChronoUnit.DAYS.between(date, java.time.LocalDate.now());
            if (daysSince <= 0) {return 1.0;}
            int tier = 1;
            if (decayTier != null && !decayTier.isBlank()) {
                try {tier = Integer.parseInt(decayTier);} catch (NumberFormatException ignored) {}
            }
            java.time.Duration halfLife = TIER_HALF_LIVES.getOrDefault(tier, DEFAULT_HALF_LIFE);
            return Math.exp(-LN2 * daysSince / halfLife.toDays());
        } catch (java.time.format.DateTimeParseException e) {
            return 1.0;
        }
    }

    static double versionScore(String verifiedOn, Map<String, String> bom,
                               double decayFactor, double floor) {
        if (verifiedOn == null || verifiedOn.isBlank() || bom.isEmpty()) {return 1.0;}
        String[] parts = verifiedOn.split(":", 2);
        if (parts.length < 2) {return 1.0;}
        String stack          = parts[0];
        String entryVersion   = parts[1];
        String currentVersion = bom.get(stack);
        if (currentVersion == null) {return 1.0;}
        int distance = versionDistance(entryVersion, currentVersion);
        if (distance <= 0) {return 1.0;}
        return Math.max(floor, Math.pow(decayFactor, distance));
    }

    private static int versionDistance(String a, String b) {
        int[] av        = parseVersion(a), bv = parseVersion(b);
        int   majorDiff = Math.abs(bv[0] - av[0]);
        int   minorDiff = Math.abs(bv[1] - av[1]);
        return majorDiff * 10 + minorDiff;
    }

    private static int[] parseVersion(String v) {
        String[] parts = v.split("[.]");
        try {
            return new int[]{
                    Integer.parseInt(parts[0]),
                    parts.length > 1 ? Integer.parseInt(parts[1]) : 0
            };
        } catch (NumberFormatException e) {return new int[]{0, 0};}
    }

    private static int resolveLimit(Integer limit) {
        if (limit == null || limit <= 0) {return DEFAULT_LIMIT;}
        return Math.min(limit, MAX_LIMIT);
    }

    public static List<SearchResult> expandWithAdjacent(
            List<SearchResult> results,
            java.util.function.Function<List<String>, List<SearchResult>> adjacentResolver) {

        Set<String> presentIds = new java.util.HashSet<>();
        for (SearchResult r : results) {
            String id = r.id();
            if (id.contains("/")) {id = id.substring(id.lastIndexOf('/') + 1);}
            if (id.endsWith(".md")) {id = id.substring(0, id.length() - 3);}
            presentIds.add(id);
        }

        Set<String> missingIds = new java.util.LinkedHashSet<>();
        for (SearchResult r : results) {
            for (String seeAlsoId : r.seeAlsoIds()) {
                if (!presentIds.contains(seeAlsoId)) {
                    missingIds.add(seeAlsoId);
                }
            }
        }

        if (missingIds.isEmpty()) {
            return results;
        }

        List<SearchResult> adjacentResults = adjacentResolver.apply(List.copyOf(missingIds));
        if (adjacentResults.isEmpty()) {
            return results;
        }

        List<SearchResult> combined = new ArrayList<>(results);
        combined.addAll(adjacentResults);
        return combined;
    }

    public static AdaptiveResult adaptiveFilter(List<SearchResult> candidates,
                                                int requestedLimit,
                                                double scoreFloor,
                                                double gapThreshold,
                                                int minResults, double boostWeight) {
        if (candidates.isEmpty()) {
            return new AdaptiveResult(List.of(), requestedLimit, 0, false, false, 0);
        }

        int floorFiltered = (int) candidates.stream()
                                            .filter(r -> boostedScore(r, boostWeight) < scoreFloor)
                                            .count();

        var options = AdaptiveFilterOptions.<SearchResult>of(
                                                   new AdaptiveSearchConfig(scoreFloor, Math.min(gapThreshold, 1.0), minResults, 1.0),
                                                   r -> boostedScore(r, boostWeight))
                                           .withCeBoundary(r -> r.crossEncoderScore() != null)
                                           .withClusterExtension(0.05);

        List<SearchResult> filtered = AdaptiveFilter.filter(candidates, requestedLimit, options);

        int     availableAboveFloor = candidates.size() - floorFiltered;
        boolean extended            = filtered.size() > requestedLimit;
        boolean trimmed             = filtered.size() < requestedLimit && (floorFiltered > 0 || filtered.size() < availableAboveFloor);

        return new AdaptiveResult(filtered, requestedLimit, availableAboveFloor, extended, trimmed, floorFiltered);
    }

    private static double primaryScore(SearchResult r) {
        return r.crossEncoderScore() != null ? r.crossEncoderScore() : r.relevance();
    }

    static double boostedScore(SearchResult r, double boostWeight) {
        return primaryScore(r) + (r.score() * boostWeight);
    }

    public static PayloadFilter buildFilter(List<String> domains, String type, String tags) {
        List<PayloadFilter> filters = new ArrayList<>();

        if (domains != null && !domains.isEmpty()) {
            List<String> nonBlank = domains.stream().filter(d -> d != null && !d.isBlank()).toList();
            if (!nonBlank.isEmpty()) {
                filters.add(nonBlank.size() == 1
                            ? PayloadFilter.eq("domain", nonBlank.getFirst())
                            : PayloadFilter.in("domain", nonBlank));
            }
        }
        if (type != null && !type.isBlank()) {
            filters.add(PayloadFilter.eq("type", type));
        }
        if (tags != null && !tags.isBlank()) {
            List<String> tagList = Arrays.stream(tags.split(","))
                                         .map(String::trim).filter(s -> !s.isEmpty()).toList();
            if (!tagList.isEmpty()) {
                filters.add(PayloadFilter.in("tags", tagList));
            }
        }

        if (filters.isEmpty()) {return null;}
        if (filters.size() == 1) {return filters.getFirst();}
        return new PayloadFilter.And(filters);
    }

    public static PayloadFilter buildDomainFilter(List<String> domains) {
        return buildFilter(domains, null, null);
    }

    public static PayloadFilter buildFilter(List<String> domains, String type, String tags, String includeStatus) {
        PayloadFilter base         = buildFilter(domains, type, tags);
        PayloadFilter statusFilter = buildStatusFilter(includeStatus);
        if (base == null && statusFilter == null) {return null;}
        if (base == null) {return statusFilter;}
        if (statusFilter == null) {return base;}
        return new PayloadFilter.And(List.of(base, statusFilter));
    }

    static PayloadFilter buildStatusFilter(String includeStatus) {
        if ("all".equalsIgnoreCase(includeStatus)) {return null;}
        java.util.Set<String> excluded = new java.util.LinkedHashSet<>(List.of("draft", "archived"));
        if (includeStatus != null && !includeStatus.isBlank()) {
            Arrays.stream(includeStatus.split("[|,]"))
                  .map(String::trim).filter(s -> !s.isEmpty())
                  .forEach(excluded::remove);
        }
        if (excluded.isEmpty()) {return null;}
        return PayloadFilter.not(
                excluded.size() == 1
                ? PayloadFilter.eq("status", excluded.iterator().next())
                : PayloadFilter.in("status", List.copyOf(excluded)));
    }

    private static Set<String> parseVisited(String header) {
        if (header == null || header.isBlank()) {
            return new LinkedHashSet<>();
        }
        return new LinkedHashSet<>(
                Arrays.stream(header.split(",")).map(String::trim).toList());
    }

    private static int parseScore(String s) {
        try {
            return s != null && !s.isEmpty() ? Integer.parseInt(s) : 0;
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private static Double parseDouble(String s) {
        if (s == null || s.isEmpty()) {return null;}
        try {
            return Double.parseDouble(s);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    public List<SearchResult> search(String query, List<String> domains, String type,
                                     String tags, Integer limit, String visited) {
        return search(query, domains, type, tags, limit, visited, null);
    }

    public List<SearchResult> search(String query, List<String> domains, String type,
                                     String tags, Integer limit, String visited, String includeStatus) {
        if (query == null || query.isBlank()) {
            throw new IllegalArgumentException("query is required");
        }
        int maxResults = resolveLimit(limit);
        return doSearch(query, null, domains, type, tags, maxResults, visited, includeStatus);
    }

    public List<SearchResult> searchFor(String query, List<String> domains, String type, String tags, Integer limit) {
        int maxResults = resolveLimit(limit);
        return doSearch(query, null, domains, type, tags, maxResults, null);
    }

    public List<SearchResult> fetchByDocumentIds(String query, List<String> documentIds) {
        if (documentIds.isEmpty()) {return List.of();}

        PayloadFilter filter = PayloadFilter.in("sourceDocumentId", documentIds);

        List<RetrievedChunk> chunks = caseRetriever.retrieve(
                RetrievalQuery.of(query), corpusRef, documentIds.size(), filter);

        List<SearchResult> results = new ArrayList<>(chunks.size());
        for (RetrievedChunk chunk : chunks) {
            String seeAlsoRaw = chunk.metadata().getOrDefault("see_also_ids", "");
            List<String> seeAlsoIds = seeAlsoRaw.isEmpty() ? List.of()
                                                           : List.of(seeAlsoRaw.split("[|]"));
            results.add(new SearchResult(
                    chunk.sourceDocumentId(),
                    chunk.metadata().getOrDefault("title", ""),
                    chunk.metadata().getOrDefault("domain", ""),
                    chunk.metadata().getOrDefault("type", ""),
                    parseScore(chunk.metadata().get("score")),
                    chunk.content(),
                    chunk.relevanceScore(),
                    parseDouble(chunk.metadata().get("_crossEncoderScore")),
                    federationConfig.corpusId(),
                    federationConfig.idPrefix(),
                    seeAlsoIds,
                    chunk.metadata()));
        }
        return results;
    }

    public AdaptiveResult searchAdaptive(String query, String keywords, List<String> domains, String type, String tags, Integer limit) {
        return searchAdaptive(query, keywords, domains, type, tags, limit, null, null);
    }

    public AdaptiveResult searchAdaptive(String query, String keywords, List<String> domains, String type, String tags, Integer limit, String profile, String stack) {
        return searchAdaptive(query, keywords, domains, type, tags, limit, profile, stack, null);
    }

    public AdaptiveResult searchAdaptive(String query, String keywords, List<String> domains, String type, String tags, Integer limit, String profile, String stack, String includeStatus) {
        int                requestedLimit = resolveLimit(limit);
        boolean            hasKeywords    = keywords != null && !keywords.isBlank();
        int                multiplier     = hasKeywords ? OVERFETCH_MULTIPLIER_KEYWORDS : OVERFETCH_MULTIPLIER;
        int                fetchLimit     = Math.min(requestedLimit * multiplier, MAX_LIMIT);
        List<SearchResult> candidates     = doSearch(query, keywords, domains, type, tags, fetchLimit, null, includeStatus);
        double             boostWeight    = searchConfig.scoreBoostWeight();

        Map<String, String> bom       = resolveBom(profile, stack);
        String              queryText = keywords != null ? query + " " + keywords : query;
        candidates = applyScoring(candidates, queryText, bom);

        List<SearchResult> sorted = candidates.stream()
                                              .sorted(Comparator.comparing(
                                                                        (SearchResult r) -> r.crossEncoderScore() != null ? 0 : 1)
                                                                .thenComparing(r -> -boostedScore(r, boostWeight)))
                                              .toList();

        double gapThreshold = hasKeywords ? Double.MAX_VALUE : searchConfig.gapThreshold();
        double scoreFloor   = hasKeywords ? Double.NEGATIVE_INFINITY : searchConfig.scoreFloor();
        return adaptiveFilter(sorted, requestedLimit,
                              scoreFloor, gapThreshold,
                              searchConfig.minResults(), boostWeight);
    }

    public AdaptiveResult adaptiveSearchEndpoint(String query, String keywords,
                                                 List<String> domains, String type,
                                                 String tags, Integer limit) {
        if (query == null || query.isBlank()) {
            throw new IllegalArgumentException("query is required");
        }
        String expandedKeywords = keywords != null && !keywords.isBlank()
                                  ? keywords.replace("|", " ") : null;
        try {
            return searchAdaptive(query, expandedKeywords, domains, type, tags, limit);
        } catch (Exception e) {
            LOG.log(Level.WARNING, "Adaptive search failed", e);
            return new AdaptiveResult(List.of(), resolveLimit(limit), 0, false, false, 0, false);
        }
    }

    private Map<String, String> resolveBom(String profile, String stack) {
        if (stack != null && !stack.isBlank()) {
            return SearchProfileStore.parseStack(stack);
        }
        if (profile != null && !profile.isBlank()) {
            return profileStore.get(profile).orElse(Map.of());
        }
        return Map.of();
    }

    List<SearchResult> applyScoring(List<SearchResult> results, String queryText, Map<String, String> bom) {
        boolean applyTemporal = scoringConfig.temporalDecayEnabled();
        boolean applyVersion  = scoringConfig.versionScoringEnabled() && !bom.isEmpty();

        if (!applyTemporal && !applyVersion) {return results;}

        return results.stream().map(r -> {
            if (r.metadata() == null) {return r;}
            double multiplier = 1.0;

            if (applyTemporal) {
                multiplier *= temporalDecayScore(
                        r.metadata().get("submitted"),
                        r.metadata().get("decay_tier"));
            }

            if (applyVersion) {
                multiplier *= versionScore(
                        r.metadata().get("verified_on"),
                        bom, scoringConfig.versionDecayFactor(),
                        scoringConfig.versionDecayFloor());
            }

            if (multiplier >= 0.999) {return r;}

            Double adjustedCe = r.crossEncoderScore() != null ? r.crossEncoderScore() * multiplier : null;
            return r.withAdjustedScores(r.relevance() * multiplier, adjustedCe);
        }).toList();
    }

    List<SearchResult> doSearch(String query, String keywords, List<String> domains, String type, String tags, int maxResults, String visitedHeader) {
        return doSearch(query, keywords, domains, type, tags, maxResults, visitedHeader, null);
    }

    List<SearchResult> doSearch(String query, String keywords, List<String> domains, String type, String tags, int maxResults, String visitedHeader, String includeStatus) {
        Set<String> visited = parseVisited(visitedHeader);
        if (visited.contains(federationConfig.corpusId())) {
            return List.of();
        }
        visited.add(federationConfig.corpusId());
        boolean            depthExceeded = visited.size() > federationConfig.maxDepth();
        List<SearchResult> ownResults    = searchLocal(query, keywords, domains, type, tags, maxResults, includeStatus);
        if (depthExceeded) {
            return ownResults;
        }
        String federationQuery = keywords != null ? query + " " + keywords : query;
        return federationWalker.walk(federationQuery, domains, type, tags, maxResults, ownResults, visited);
    }

    private List<SearchResult> searchLocal(String query, String keywords, List<String> domains, String type, String tags, int maxResults, String includeStatus) {

        PayloadFilter filter = buildFilter(domains, type, tags, includeStatus);

        RetrievalQuery retrievalQuery;
        if (keywords != null && !keywords.isBlank()) {
            retrievalQuery = new RetrievalQuery(keywords, query + " " + keywords, java.util.Map.of());
        } else {
            retrievalQuery = RetrievalQuery.of(query);
        }

        List<RetrievedChunk> chunks = caseRetriever.retrieve(retrievalQuery, corpusRef, maxResults, filter);

        List<SearchResult> results = new ArrayList<>(chunks.size());
        for (RetrievedChunk chunk : chunks) {
            String seeAlsoRaw = chunk.metadata().getOrDefault("see_also_ids", "");
            List<String> seeAlsoIds = seeAlsoRaw.isEmpty() ? List.of()
                                                           : List.of(seeAlsoRaw.split("[|]"));
            results.add(new SearchResult(
                    chunk.sourceDocumentId(),
                    chunk.metadata().getOrDefault("title", ""),
                    chunk.metadata().getOrDefault("domain", ""),
                    chunk.metadata().getOrDefault("type", ""),
                    parseScore(chunk.metadata().get("score")),
                    chunk.content(),
                    chunk.relevanceScore(),
                    parseDouble(chunk.metadata().get("_crossEncoderScore")),
                    federationConfig.corpusId(),
                    federationConfig.idPrefix(),
                    seeAlsoIds,
                    chunk.metadata()));
        }
        return results;
    }

    @FunctionalInterface
    public interface FederationWalker {
        List<SearchResult> walk(String query, List<String> domains, String type, String tags,
                                int maxResults, List<SearchResult> ownResults, Set<String> visited);
    }

    public record SearchConfig(double scoreFloor, double gapThreshold, int minResults, double scoreBoostWeight) {
        public static final SearchConfig DEFAULT = new SearchConfig(0.0, 0.15, 3, 0.01);
    }
}
