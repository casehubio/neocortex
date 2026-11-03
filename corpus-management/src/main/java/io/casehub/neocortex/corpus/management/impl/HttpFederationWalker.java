package io.casehub.neocortex.corpus.management.impl;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;

public class HttpFederationWalker implements SearchResource.FederationWalker {

    private static final Logger LOG = Logger.getLogger(HttpFederationWalker.class.getName());

    static final int TIER_OWN = 0;
    static final int TIER_PEER = Integer.MAX_VALUE;

    private final FederationConfig config;
    private final ExecutorService executor;
    private final Map<String, RemoteCorpusClient> upstreamClients = new LinkedHashMap<>();
    private final Map<String, RemoteCorpusClient> peerClients = new LinkedHashMap<>();

    public HttpFederationWalker(FederationConfig config, ExecutorService executor,
                                 Function<String, RemoteCorpusClient> clientFactory) {
        this.config = config;
        this.executor = executor;
        for (var ref : config.upstream()) {
            upstreamClients.put(ref.url(), clientFactory.apply(ref.url()));
        }
        for (var ref : config.peers()) {
            peerClients.put(ref.url(), clientFactory.apply(ref.url()));
        }
    }

    @Override
    public List<SearchResult> walk(String query, List<String> domains, String type, String tags,
                                    int maxResults, List<SearchResult> ownResults, Set<String> visited) {
        if (!config.hasFederation()) {
            return ownResults;
        }

        List<TieredResult> all = new ArrayList<>();
        ownResults.forEach(r -> all.add(new TieredResult(r, TIER_OWN)));

        boolean sufficient = isSufficient(ownResults, maxResults);

        if (!sufficient || hasAlwaysUpstream()) {
            String visitedHeader = String.join(",", visited);
            int upstreamIndex = 0;
            for (var ref : config.upstream()) {
                boolean shouldQuery = "always".equals(ref.searchOrder()) || !sufficient;
                if (!shouldQuery) {
                    upstreamIndex++;
                    continue;
                }

                int tier = upstreamIndex + 1;
                try {
                    RemoteCorpusClient client = upstreamClients.get(ref.url());
                    List<SearchResult> results = client.search(query, domains, type, tags, maxResults, visitedHeader);
                    results.forEach(r -> all.add(new TieredResult(r, tier)));

                    if (!sufficient && isSufficientCombined(all, maxResults)) {
                        sufficient = true;
                        break;
                    }
                } catch (Exception e) {
                    LOG.log(Level.WARNING, "Federation upstream {0} failed: {1}",
                            new Object[]{ref.url(), e.getMessage()});
                }
                upstreamIndex++;
            }
        }

        if (!isSufficientCombined(all, maxResults) && config.hasPeers()) {
            String visitedHeader = String.join(",", visited);
            List<Callable<List<SearchResult>>> peerCalls = config.peers().stream()
                    .map(ref -> (Callable<List<SearchResult>>) () -> {
                        RemoteCorpusClient client = peerClients.get(ref.url());
                        return client.search(query, domains, type, tags, maxResults, visitedHeader);
                    })
                    .toList();

            try {
                List<Future<List<SearchResult>>> futures = executor.invokeAll(
                        peerCalls, config.federationTimeoutSeconds(), TimeUnit.SECONDS);
                for (var future : futures) {
                    try {
                        if (future.isDone() && !future.isCancelled()) {
                            future.get().forEach(r -> all.add(new TieredResult(r, TIER_PEER)));
                        }
                    } catch (ExecutionException e) {
                        LOG.log(Level.WARNING, "Federation peer failed: {0}",
                                e.getCause().getMessage());
                    }
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                LOG.warning("Federation peer fan-out interrupted");
            }
        }

        return merge(all, maxResults);
    }

    private boolean isSufficient(List<SearchResult> results, int limit) {
        long aboveThreshold = results.stream()
                .filter(r -> r.relevance() >= config.relevanceThreshold())
                .count();
        return aboveThreshold >= limit;
    }

    private boolean isSufficientCombined(List<TieredResult> results, int limit) {
        long aboveThreshold = results.stream()
                .filter(r -> r.result.relevance() >= config.relevanceThreshold())
                .count();
        return aboveThreshold >= limit;
    }

    private boolean hasAlwaysUpstream() {
        return config.upstream().stream().anyMatch(ref -> "always".equals(ref.searchOrder()));
    }

    static List<SearchResult> merge(List<TieredResult> all, int limit) {
        Set<DedupKey> seen = new LinkedHashSet<>();
        List<TieredResult> deduped = new ArrayList<>();
        for (var tr : all) {
            if (seen.add(new DedupKey(tr.result.id(), tr.result.source()))) {
                deduped.add(tr);
            }
        }

        return deduped.stream()
                .collect(Collectors.groupingBy(tr -> tr.tier))
                .entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .flatMap(entry -> entry.getValue().stream()
                        .sorted(Comparator.comparingDouble((TieredResult tr) -> tr.result.relevance()).reversed())
                        .map(tr -> tr.result))
                .limit(limit)
                .toList();
    }

    record TieredResult(SearchResult result, int tier) {}
    record DedupKey(String id, String source) {}
}
