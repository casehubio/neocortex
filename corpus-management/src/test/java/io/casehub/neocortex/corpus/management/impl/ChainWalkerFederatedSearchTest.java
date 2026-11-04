package io.casehub.neocortex.corpus.management.impl;

import io.casehub.neocortex.corpus.management.CorpusCatalogue;
import io.casehub.neocortex.corpus.management.CorpusDescriptor;
import io.casehub.neocortex.corpus.management.CorpusEntry;
import io.casehub.neocortex.corpus.management.CorpusLifecycle;
import io.casehub.neocortex.corpus.management.CorpusQuery;
import io.casehub.neocortex.corpus.management.CorpusSearch;
import io.casehub.neocortex.corpus.management.CorpusSearchResult;
import io.casehub.neocortex.corpus.management.CorpusType;
import io.casehub.neocortex.corpus.management.FederationRef;
import io.casehub.neocortex.corpus.management.HealthReport;
import io.casehub.neocortex.rag.CorpusRef;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class ChainWalkerFederatedSearchTest {

    private static final double THRESHOLD = 0.7;
    private static final double HIGH = 0.9;
    private static final double LOW = 0.3;
    private static final int LIMIT = 3;
    private static final String TENANT = "tenant-1";

    private StubCatalogue catalogue;
    private StubSearch search;
    private ChainWalkerFederatedSearch walker;

    @BeforeEach
    void setUp() {
        catalogue = new StubCatalogue();
        search = new StubSearch();
        walker = new ChainWalkerFederatedSearch(catalogue, search, THRESHOLD);
    }

    @Test
    void searchesSingleCorpusWithNoFederation() {
        var corpus = registerCorpus("my-corpus", List.of(), List.of());
        search.put(corpus, List.of(result("e1", HIGH, corpus)));

        var result = walker.search(query("test", LIMIT, corpus), TENANT);

        assertThat(result.results()).hasSize(1);
        assertThat(result.results().get(0).entry().id()).isEqualTo("e1");
        assertThat(result.results().get(0).tier()).isEqualTo(0);
        assertThat(result.report().searched()).containsExactly(corpus);
        assertThat(result.report().skipped()).isEmpty();
    }

    @Test
    void walksUpstreamWhenInsufficient() {
        var parent = registerCorpus("parent", List.of(), List.of());
        var child = registerCorpus("child",
            List.of(new FederationRef(parent, null, null, "fallback")), List.of());
        search.put(child, List.of(result("e1", LOW, child)));
        search.put(parent, List.of(result("u1", HIGH, parent), result("u2", HIGH, parent)));

        var result = walker.search(query("test", LIMIT, child), TENANT);

        assertThat(result.results()).hasSize(3);
        assertThat(result.results().get(0).sourceCorpus()).isEqualTo(child);
        assertThat(result.results().get(0).tier()).isEqualTo(0);
        assertThat(result.results().get(1).sourceCorpus()).isEqualTo(parent);
        assertThat(result.results().get(1).tier()).isEqualTo(1);
    }

    @Test
    void skipsUpstreamWhenSufficient() {
        var parent = registerCorpus("parent", List.of(), List.of());
        var child = registerCorpus("child",
            List.of(new FederationRef(parent, null, null, "fallback")), List.of());
        search.put(child, List.of(
            result("e1", HIGH, child), result("e2", HIGH, child), result("e3", HIGH, child)));

        walker.search(query("test", LIMIT, child), TENANT);

        assertThat(search.searchedCorpora()).doesNotContain(parent);
    }

    @Test
    void shortCircuitsUpstreamChainOnSufficientResults() {
        var grandparent = registerCorpus("grandparent", List.of(), List.of());
        var parent = registerCorpus("parent",
            List.of(new FederationRef(grandparent, null, null, "fallback")), List.of());
        var child = registerCorpus("child",
            List.of(new FederationRef(parent, null, null, "fallback")), List.of());
        search.put(child, List.of(result("e1", LOW, child)));
        search.put(parent, List.of(
            result("u1", HIGH, parent), result("u2", HIGH, parent), result("u3", HIGH, parent)));
        search.put(grandparent, List.of(result("g1", HIGH, grandparent)));

        walker.search(query("test", LIMIT, child), TENANT);

        assertThat(search.searchedCorpora()).contains(parent);
        assertThat(search.searchedCorpora()).doesNotContain(grandparent);
    }

    @Test
    void fansOutToPeersWhenInsufficient() {
        var peer = registerCorpus("peer", List.of(), List.of());
        var corpus = registerCorpus("my-corpus",
            List.of(), List.of(new FederationRef(peer, null, null, null)));
        search.put(corpus, List.of(result("e1", LOW, corpus)));
        search.put(peer, List.of(result("p1", HIGH, peer)));

        var result = walker.search(query("test", LIMIT, corpus), TENANT);

        assertThat(result.results()).anyMatch(r -> r.sourceCorpus().equals(peer));
    }

    @Test
    void peerTierAfterUpstreamTier() {
        var peer = registerCorpus("peer", List.of(), List.of());
        var parent = registerCorpus("parent", List.of(), List.of());
        var corpus = registerCorpus("my-corpus",
            List.of(new FederationRef(parent, null, null, "fallback")),
            List.of(new FederationRef(peer, null, null, null)));
        search.put(corpus, List.of(result("e1", LOW, corpus)));
        search.put(parent, List.of(result("u1", 0.8, parent)));
        search.put(peer, List.of(result("p1", 0.95, peer)));

        var result = walker.search(query("test", 10, corpus), TENANT);

        assertThat(result.results().get(0).sourceCorpus()).isEqualTo(corpus);
        assertThat(result.results().get(1).sourceCorpus()).isEqualTo(parent);
        assertThat(result.results().get(2).sourceCorpus()).isEqualTo(peer);
    }

    @Test
    void detectsCyclesInFederationGraph() {
        var a = new CorpusRef(TENANT, "a");
        var b = new CorpusRef(TENANT, "b");
        catalogue.register(descriptor("a",
            List.of(new FederationRef(b, null, null, "fallback")), List.of()));
        catalogue.register(descriptor("b",
            List.of(new FederationRef(a, null, null, "fallback")), List.of()));
        search.put(a, List.of(result("ea", LOW, a)));
        search.put(b, List.of(result("eb", LOW, b)));

        var result = walker.search(query("test", LIMIT, a), TENANT);
        assertThat(result.results()).isNotEmpty();
    }

    @Test
    void reportsSkippedCorporaOnSearchFailure() {
        var parent = registerCorpus("parent", List.of(), List.of());
        var child = registerCorpus("child",
            List.of(new FederationRef(parent, null, null, "fallback")), List.of());
        search.put(child, List.of(result("e1", LOW, child)));
        search.setFailing(parent);

        var result = walker.search(query("test", LIMIT, child), TENANT);

        assertThat(result.report().skipped()).hasSize(1);
        assertThat(result.report().skipped().get(0).corpus()).isEqualTo(parent);
    }

    @Test
    void dedupsByEntryIdAndSourceCorpus() {
        var parent = registerCorpus("parent", List.of(), List.of());
        var child = registerCorpus("child",
            List.of(new FederationRef(parent, null, null, "fallback")), List.of());
        search.put(child, List.of(result("e1", LOW, child)));
        search.put(parent, List.of(result("dup", HIGH, parent), result("dup", 0.85, parent)));

        var result = walker.search(query("test", 10, child), TENANT);

        long dupCount = result.results().stream()
            .filter(r -> r.entry().id().equals("dup") && r.sourceCorpus().equals(parent))
            .count();
        assertThat(dupCount).isEqualTo(1);
    }

    @Test
    void sameEntryIdDifferentCorpusNotDeduped() {
        var parent = registerCorpus("parent", List.of(), List.of());
        var child = registerCorpus("child",
            List.of(new FederationRef(parent, null, null, "fallback")), List.of());
        search.put(child, List.of(result("shared-id", LOW, child)));
        search.put(parent, List.of(result("shared-id", HIGH, parent)));

        var result = walker.search(query("test", 10, child), TENANT);

        assertThat(result.results()).hasSize(2);
    }

    @Test
    void truncatesToMaxResults() {
        var parent = registerCorpus("parent", List.of(), List.of());
        var child = registerCorpus("child",
            List.of(new FederationRef(parent, null, null, "fallback")), List.of());
        search.put(child, List.of(result("e1", HIGH, child), result("e2", HIGH, child)));
        search.put(parent, List.of(result("u1", HIGH, parent), result("u2", HIGH, parent)));

        var result = walker.search(query("test", 3, child), TENANT);

        assertThat(result.results()).hasSize(3);
    }

    @Test
    void alwaysModeQueriesUpstreamEvenWhenSufficient() {
        var parent = registerCorpus("parent", List.of(), List.of());
        var child = registerCorpus("child",
            List.of(new FederationRef(parent, null, null, "always")), List.of());
        search.put(child, List.of(
            result("e1", HIGH, child), result("e2", HIGH, child), result("e3", HIGH, child)));
        search.put(parent, List.of(result("u1", HIGH, parent)));

        walker.search(query("test", LIMIT, child), TENANT);

        assertThat(search.searchedCorpora()).contains(parent);
    }

    @Test
    void searchesAllActiveCorporaWhenNoneSpecified() {
        var c1 = registerCorpus("corpus-1", List.of(), List.of());
        var c2 = registerCorpus("corpus-2", List.of(), List.of());
        search.put(c1, List.of(result("e1", HIGH, c1)));
        search.put(c2, List.of(result("e2", HIGH, c2)));

        var result = walker.search(CorpusQuery.of("test", 10), TENANT);

        assertThat(result.results()).hasSize(2);
        assertThat(search.searchedCorpora()).containsExactlyInAnyOrder(c1, c2);
    }

    @Test
    void tierGroupedRelevanceSortedWithinTier() {
        var parent = registerCorpus("parent", List.of(), List.of());
        var child = registerCorpus("child",
            List.of(new FederationRef(parent, null, null, "fallback")), List.of());
        search.put(child, List.of(result("e1", 0.4, child), result("e2", 0.6, child)));
        search.put(parent, List.of(result("u1", 0.95, parent), result("u2", 0.80, parent)));

        var result = walker.search(query("test", 10, child), TENANT);

        assertThat(result.results().get(0).entry().id()).isEqualTo("e2");
        assertThat(result.results().get(1).entry().id()).isEqualTo("e1");
        assertThat(result.results().get(2).entry().id()).isEqualTo("u1");
        assertThat(result.results().get(3).entry().id()).isEqualTo("u2");
    }

    // --- Helpers ---

    private CorpusRef registerCorpus(String name, List<FederationRef> upstream, List<FederationRef> peers) {
        var ref = new CorpusRef(TENANT, name);
        catalogue.register(descriptor(name, upstream, peers));
        return ref;
    }

    private CorpusDescriptor descriptor(String name, List<FederationRef> upstream, List<FederationRef> peers) {
        return new CorpusDescriptor(
            UUID.randomUUID().toString(), name, BuiltInCorpusTypes.RESEARCH, CorpusLifecycle.ACTIVE,
            new CorpusRef(TENANT, name), name + "-collection",
            upstream, peers, Map.of(), Instant.now(), Instant.now());
    }

    private static CorpusSearchResult result(String id, double score, CorpusRef source) {
        var entry = new CorpusEntry(id, Map.of("title", "Title " + id), "Body " + id, null);
        return new CorpusSearchResult(entry, score, source, 0, Instant.now());
    }

    private static CorpusQuery query(String text, int maxResults, CorpusRef... corpora) {
        return new CorpusQuery(text, null, null, Set.of(corpora), maxResults, -1, false, Map.of());
    }

    // --- Stubs ---

    static class StubCatalogue implements CorpusCatalogue {
        private final Map<CorpusRef, CorpusDescriptor> corpora = new LinkedHashMap<>();

        void register(CorpusDescriptor desc) {
            corpora.put(desc.corpusRef(), desc);
        }

        @Override
        public List<CorpusDescriptor> list(String tenantId, CorpusLifecycle status) {
            return corpora.values().stream()
                .filter(d -> d.corpusRef().tenantId().equals(tenantId) && d.lifecycle() == status)
                .toList();
        }

        @Override
        public Optional<CorpusDescriptor> get(CorpusRef corpus) {
            return Optional.ofNullable(corpora.get(corpus));
        }

        @Override
        public List<CorpusDescriptor> forType(CorpusType type, String tenantId) {
            return corpora.values().stream()
                .filter(d -> d.type().equals(type) && d.corpusRef().tenantId().equals(tenantId))
                .toList();
        }

        @Override
        public List<CorpusDescriptor> searchScope(CorpusRef corpus) {
            return corpora.get(corpus) != null ? List.of(corpora.get(corpus)) : List.of();
        }

        @Override
        public HealthReport reconcile(String tenantId) {
            return HealthReport.HEALTHY;
        }
    }

    static class StubSearch implements CorpusSearch {
        private final Map<CorpusRef, List<CorpusSearchResult>> results = new LinkedHashMap<>();
        private final Set<CorpusRef> failing = new HashSet<>();
        private final List<CorpusRef> searched = new ArrayList<>();

        void put(CorpusRef corpus, List<CorpusSearchResult> searchResults) {
            results.put(corpus, searchResults);
        }

        void setFailing(CorpusRef corpus) {
            failing.add(corpus);
        }

        List<CorpusRef> searchedCorpora() {
            return List.copyOf(searched);
        }

        @Override
        public List<CorpusSearchResult> search(CorpusRef corpus, CorpusQuery query) {
            searched.add(corpus);
            if (failing.contains(corpus)) {
                throw new RuntimeException("Search failed for " + corpus);
            }
            return results.getOrDefault(corpus, List.of());
        }
    }
}
