package io.casehub.neocortex.corpus.management.impl;

import io.casehub.neocortex.corpus.management.CorpusCatalogue;
import io.casehub.neocortex.corpus.management.CorpusDescriptor;
import io.casehub.neocortex.corpus.management.CorpusLifecycle;
import io.casehub.neocortex.corpus.management.CorpusQuery;
import io.casehub.neocortex.corpus.management.CorpusSearch;
import io.casehub.neocortex.corpus.management.CorpusSearchResult;
import io.casehub.neocortex.corpus.management.FederatedCorpusSearch;
import io.casehub.neocortex.corpus.management.FederatedSearchResult;
import io.casehub.neocortex.corpus.management.FederationRef;
import io.casehub.neocortex.corpus.management.SearchReport;
import io.casehub.neocortex.corpus.management.SkippedCorpus;
import io.casehub.neocortex.rag.CorpusRef;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

public class ChainWalkerFederatedSearch implements FederatedCorpusSearch {

    static final int TIER_PEER = Integer.MAX_VALUE;

    private final CorpusCatalogue catalogue;
    private final CorpusSearch corpusSearch;
    private final double relevanceThreshold;

    public ChainWalkerFederatedSearch(CorpusCatalogue catalogue, CorpusSearch corpusSearch,
                                      double relevanceThreshold) {
        this.catalogue = catalogue;
        this.corpusSearch = corpusSearch;
        this.relevanceThreshold = relevanceThreshold;
    }

    @Override
    public FederatedSearchResult search(CorpusQuery query, String tenantId) {
        List<CorpusRef> startingCorpora;
        if (query.corpusRefs() != null && !query.corpusRefs().isEmpty()) {
            startingCorpora = List.copyOf(query.corpusRefs());
        } else {
            startingCorpora = catalogue.list(tenantId, CorpusLifecycle.ACTIVE).stream()
                .map(CorpusDescriptor::corpusRef)
                .toList();
        }

        var all = new ArrayList<TieredResult>();
        var visited = new HashSet<CorpusRef>();
        var searched = new ArrayList<CorpusRef>();
        var skipped = new ArrayList<SkippedCorpus>();

        for (CorpusRef start : startingCorpora) {
            if (!visited.add(start)) continue;

            searchCorpus(start, query, 0, all, searched, skipped);

            var desc = catalogue.get(start);
            if (desc.isEmpty()) continue;

            walkUpstream(desc.get(), query, 1, all, visited, searched, skipped);

            if (!isSufficient(all, query.maxResults())) {
                for (FederationRef peerRef : desc.get().peers()) {
                    if (peerRef.localRef() != null && visited.add(peerRef.localRef())) {
                        searchCorpus(peerRef.localRef(), query, TIER_PEER, all, searched, skipped);
                    }
                }
            }
        }

        var merged = merge(all, query.maxResults());
        var report = new SearchReport(List.copyOf(searched), List.copyOf(skipped), !skipped.isEmpty());
        return new FederatedSearchResult(merged, report);
    }

    private void walkUpstream(CorpusDescriptor current, CorpusQuery query, int tier,
                              List<TieredResult> all, Set<CorpusRef> visited,
                              List<CorpusRef> searched, List<SkippedCorpus> skipped) {
        int maxDepth = query.federationDepth();
        if (maxDepth >= 0 && tier > maxDepth) return;

        boolean sufficient = isSufficient(all, query.maxResults());

        for (FederationRef upRef : current.upstream()) {
            if (upRef.localRef() == null || !visited.add(upRef.localRef())) continue;

            boolean shouldQuery = "always".equals(upRef.searchOrder()) || !sufficient;
            if (!shouldQuery) continue;

            searchCorpus(upRef.localRef(), query, tier, all, searched, skipped);

            if (!sufficient && isSufficient(all, query.maxResults())) {
                sufficient = true;
                break;
            }

            var upDesc = catalogue.get(upRef.localRef());
            if (upDesc.isPresent() && !sufficient) {
                walkUpstream(upDesc.get(), query, tier + 1, all, visited, searched, skipped);
                sufficient = isSufficient(all, query.maxResults());
            }
        }
    }

    private void searchCorpus(CorpusRef corpus, CorpusQuery query, int tier,
                              List<TieredResult> all, List<CorpusRef> searched,
                              List<SkippedCorpus> skipped) {
        try {
            var results = corpusSearch.search(corpus, query);
            for (var r : results) {
                all.add(new TieredResult(
                    new CorpusSearchResult(r.entry(), r.score(), r.sourceCorpus(), tier, r.entryTimestamp()),
                    tier));
            }
            searched.add(corpus);
        } catch (Exception e) {
            skipped.add(new SkippedCorpus(corpus, e.getMessage()));
        }
    }

    private boolean isSufficient(List<TieredResult> results, int limit) {
        long aboveThreshold = results.stream()
            .filter(r -> r.result.score() >= relevanceThreshold)
            .count();
        return aboveThreshold >= limit;
    }

    static List<CorpusSearchResult> merge(List<TieredResult> all, int limit) {
        var seen = new LinkedHashSet<DedupKey>();
        var deduped = new ArrayList<TieredResult>();
        for (var tr : all) {
            if (seen.add(new DedupKey(tr.result.entry().id(), tr.result.sourceCorpus()))) {
                deduped.add(tr);
            }
        }

        return deduped.stream()
            .collect(Collectors.groupingBy(tr -> tr.tier))
            .entrySet().stream()
            .sorted(Map.Entry.comparingByKey())
            .flatMap(entry -> entry.getValue().stream()
                .sorted(Comparator.comparingDouble((TieredResult tr) -> tr.result.score()).reversed())
                .map(tr -> tr.result))
            .limit(limit)
            .toList();
    }

    record TieredResult(CorpusSearchResult result, int tier) {}
    record DedupKey(String entryId, CorpusRef sourceCorpus) {}
}
