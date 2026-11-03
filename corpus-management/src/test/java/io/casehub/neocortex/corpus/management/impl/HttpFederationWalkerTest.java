package io.casehub.neocortex.corpus.management.impl;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.AbstractExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

import static org.assertj.core.api.Assertions.assertThat;

class HttpFederationWalkerTest {

    private static final double HIGH = 0.9;
    private static final double LOW = 0.3;
    private static final double THRESHOLD = 0.7;
    private static final int LIMIT = 3;

    private RecordingClient upstreamClient;
    private RecordingClient peerClient;

    @BeforeEach
    void setUp() {
        upstreamClient = new RecordingClient();
        peerClient = new RecordingClient();
    }

    @Test
    void noFederationReturnsOwnResults() {
        var walker = canonicalWalker();
        var own = List.of(result("e1", HIGH, "my-garden", "MG"));

        var results = walker.walk("query", null, null, null, LIMIT, own, visited());
        assertThat(results).isEqualTo(own);
    }

    @Test
    void sufficientOwnResultsSkipsUpstream() {
        var walker = childWalker();
        var own = List.of(
                result("e1", HIGH, "my-garden", "MG"),
                result("e2", HIGH, "my-garden", "MG"),
                result("e3", HIGH, "my-garden", "MG"));

        walker.walk("query", null, null, null, LIMIT, own, visited());
        assertThat(upstreamClient.callCount).isZero();
    }

    @Test
    void insufficientOwnResultsTriggersUpstream() {
        var walker = childWalker();
        var own = List.of(result("e1", LOW, "my-garden", "MG"));
        upstreamClient.response = List.of(
                result("u1", HIGH, "parent-garden", "PG"),
                result("u2", HIGH, "parent-garden", "PG"));

        var results = walker.walk("query", null, null, null, LIMIT, own, visited());
        assertThat(results).hasSize(3);
        assertThat(upstreamClient.callCount).isEqualTo(1);
        assertThat(results.get(0).source()).isEqualTo("my-garden");
        assertThat(results.get(1).source()).isEqualTo("parent-garden");
    }

    @Test
    void upstreamShortCircuitsOnSufficientResults() {
        var upstream1 = new RecordingClient();
        var upstream2 = new RecordingClient();
        upstream1.response = List.of(
                result("u1", HIGH, "parent", "PG"),
                result("u2", HIGH, "parent", "PG"),
                result("u3", HIGH, "parent", "PG"));

        var config = new FederationConfig("my-garden", "MG", "child", THRESHOLD, 5, 5,
                List.of(
                        new FederationConfig.UpstreamRef("http://parent", "PG", "fallback"),
                        new FederationConfig.UpstreamRef("http://grandparent", "GP", "fallback")),
                List.of());
        Function<String, RemoteCorpusClient> factory = url ->
                url.equals("http://parent") ? upstream1 : upstream2;
        var walker = new HttpFederationWalker(config, new DirectExecutor(), factory);

        var own = List.of(result("e1", LOW, "my-garden", "MG"));
        walker.walk("query", null, null, null, LIMIT, own, visited());

        assertThat(upstream1.callCount).isEqualTo(1);
        assertThat(upstream2.callCount).isZero();
    }

    @Test
    void peerFanOutOnlyWhenInsufficient() {
        var walker = childWithPeerWalker();
        var own = List.of(
                result("e1", HIGH, "my-garden", "MG"),
                result("e2", HIGH, "my-garden", "MG"),
                result("e3", HIGH, "my-garden", "MG"));

        walker.walk("query", null, null, null, LIMIT, own, visited());
        assertThat(peerClient.callCount).isZero();
    }

    @Test
    void peerFanOutTriggersWhenInsufficient() {
        var config = new FederationConfig("my-garden", "MG", "child", THRESHOLD, 5, 5,
                List.of(), List.of(new FederationConfig.PeerRef("http://peer", "PR")));
        Function<String, RemoteCorpusClient> factory = url -> peerClient;
        var walker = new HttpFederationWalker(config, new DirectExecutor(), factory);

        peerClient.response = List.of(result("p1", HIGH, "peer-garden", "PR"));
        var own = List.of(result("e1", LOW, "my-garden", "MG"));

        var results = walker.walk("query", null, null, null, LIMIT, own, visited());
        assertThat(peerClient.callCount).isEqualTo(1);
        assertThat(results).anyMatch(r -> r.source().equals("peer-garden"));
    }

    @Test
    void tierGroupedRelevanceSortedOrdering() {
        var walker = childWalker();
        upstreamClient.response = List.of(
                result("u1", 0.95, "parent", "PG"),
                result("u2", 0.80, "parent", "PG"));

        var own = List.of(
                result("e1", 0.40, "my-garden", "MG"),
                result("e2", 0.60, "my-garden", "MG"));

        var results = walker.walk("query", null, null, null, 10, own, visited());

        assertThat(results.get(0).id()).isEqualTo("e2");
        assertThat(results.get(1).id()).isEqualTo("e1");
        assertThat(results.get(2).id()).isEqualTo("u1");
        assertThat(results.get(3).id()).isEqualTo("u2");
    }

    @Test
    void dedupByIdAndSource() {
        var walker = childWalker();
        upstreamClient.response = List.of(
                result("e1", HIGH, "parent", "PG"),
                result("e1", 0.85, "parent", "PG"));

        var own = List.of(result("e1", LOW, "my-garden", "MG"));

        var results = walker.walk("query", null, null, null, 10, own, visited());
        assertThat(results).hasSize(2);
    }

    @Test
    void upstreamTimeoutContinuesGracefully() {
        var walker = childWalker();
        upstreamClient.shouldThrow = true;

        var own = List.of(result("e1", LOW, "my-garden", "MG"));
        var results = walker.walk("query", null, null, null, LIMIT, own, visited());

        assertThat(results).hasSize(1);
        assertThat(results.get(0).source()).isEqualTo("my-garden");
    }

    @Test
    void truncatesToLimit() {
        var walker = childWalker();
        upstreamClient.response = List.of(
                result("u1", HIGH, "parent", "PG"),
                result("u2", HIGH, "parent", "PG"),
                result("u3", HIGH, "parent", "PG"));

        var own = List.of(
                result("e1", HIGH, "my-garden", "MG"),
                result("e2", HIGH, "my-garden", "MG"));

        var results = walker.walk("query", null, null, null, 3, own, visited());
        assertThat(results).hasSize(3);
    }

    @Test
    void alwaysModeQueriesUpstreamEvenWhenSufficient() {
        var alwaysClient = new RecordingClient();
        alwaysClient.response = List.of(result("a1", HIGH, "always-garden", "AG"));

        var config = new FederationConfig("my-garden", "MG", "child", THRESHOLD, 5, 5,
                List.of(new FederationConfig.UpstreamRef("http://always-upstream", "AG", "always")),
                List.of());
        Function<String, RemoteCorpusClient> factory = url -> alwaysClient;
        var walker = new HttpFederationWalker(config, new DirectExecutor(), factory);

        var own = List.of(
                result("e1", HIGH, "my-garden", "MG"),
                result("e2", HIGH, "my-garden", "MG"),
                result("e3", HIGH, "my-garden", "MG"));

        var results = walker.walk("query", null, null, null, 10, own, visited());
        assertThat(alwaysClient.callCount).isEqualTo(1);
        assertThat(results).anyMatch(r -> r.source().equals("always-garden"));
    }

    @Test
    void visitedHeaderPassedToUpstream() {
        var walker = childWalker();
        upstreamClient.response = List.of();

        var v = new LinkedHashSet<>(Set.of("my-garden"));
        walker.walk("query", null, null, null, LIMIT, List.of(result("e1", LOW, "my-garden", "MG")), v);

        assertThat(upstreamClient.lastVisited).contains("my-garden");
    }

    @Test
    void typeAndTagsPassedToUpstream() {
        var walker = childWalker();
        upstreamClient.response = List.of();

        var v = new LinkedHashSet<>(Set.of("my-garden"));
        walker.walk("query", null, "gotcha", "qdrant,cdi", LIMIT,
                List.of(result("e1", LOW, "my-garden", "MG")), v);

        assertThat(upstreamClient.lastType).isEqualTo("gotcha");
        assertThat(upstreamClient.lastTags).isEqualTo("qdrant,cdi");
    }

    // --- Helpers ---

    private HttpFederationWalker canonicalWalker() {
        var config = new FederationConfig("my-garden", "MG", "canonical", THRESHOLD, 5, 5,
                List.of(), List.of());
        return new HttpFederationWalker(config, new DirectExecutor(), url -> upstreamClient);
    }

    private HttpFederationWalker childWalker() {
        var config = new FederationConfig("my-garden", "MG", "child", THRESHOLD, 5, 5,
                List.of(new FederationConfig.UpstreamRef("http://parent", "PG", "fallback")),
                List.of());
        return new HttpFederationWalker(config, new DirectExecutor(), url -> upstreamClient);
    }

    private HttpFederationWalker childWithPeerWalker() {
        var config = new FederationConfig("my-garden", "MG", "child", THRESHOLD, 5, 5,
                List.of(new FederationConfig.UpstreamRef("http://parent", "PG", "fallback")),
                List.of(new FederationConfig.PeerRef("http://peer", "PR")));
        Function<String, RemoteCorpusClient> factory = url ->
                url.contains("peer") ? peerClient : upstreamClient;
        return new HttpFederationWalker(config, new DirectExecutor(), factory);
    }

    private static SearchResult result(String id, double relevance, String source, String prefix) {
        return new SearchResult(id, "Title " + id, "jvm", "gotcha", 8, "Body", relevance, null, source, prefix);
    }

    private static Set<String> visited() {
        return new LinkedHashSet<>(Set.of("my-garden"));
    }

    static class RecordingClient implements RemoteCorpusClient {
        List<SearchResult> response = List.of();
        boolean shouldThrow = false;
        int callCount = 0;
        String lastVisited;
        String lastType;
        String lastTags;

        @Override
        public List<SearchResult> search(String query, List<String> domains, String type, String tags,
                                         int limit, String visited) {
            callCount++;
            lastVisited = visited;
            lastType = type;
            lastTags = tags;
            if (shouldThrow) {
                throw new RuntimeException("Connection timeout");
            }
            return response;
        }
    }

    static class DirectExecutor extends AbstractExecutorService {
        private volatile boolean shutdown = false;

        @Override public void shutdown() { shutdown = true; }
        @Override public List<Runnable> shutdownNow() { shutdown = true; return List.of(); }
        @Override public boolean isShutdown() { return shutdown; }
        @Override public boolean isTerminated() { return shutdown; }
        @Override public boolean awaitTermination(long timeout, TimeUnit unit) { return true; }
        @Override public void execute(Runnable command) { command.run(); }
    }
}
