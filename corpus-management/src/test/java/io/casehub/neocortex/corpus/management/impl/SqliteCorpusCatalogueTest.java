package io.casehub.neocortex.corpus.management.impl;

import io.casehub.neocortex.corpus.management.CorpusDescriptor;
import io.casehub.neocortex.corpus.management.CorpusLifecycle;
import io.casehub.neocortex.corpus.management.CorpusType;
import io.casehub.neocortex.corpus.management.FederationRef;
import io.casehub.neocortex.rag.CorpusRef;
import io.casehub.neocortex.sqlite.SqliteDataSourceFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SqliteCorpusCatalogueTest {

    private SqliteCorpusCatalogue catalogue;

    @BeforeEach
    void setUp() {
        var ds = SqliteDataSourceFactory.create(":memory:", 1, 3000);
        catalogue = new SqliteCorpusCatalogue(ds);
    }

    @Test
    void registerAndRetrieve() {
        var desc = catalogue.register("test-corpus", CorpusType.RESEARCH, "tenant-1",
                "qdrant-test-corpus", List.of(), List.of(), Map.of());

        assertThat(catalogue.get(new CorpusRef("tenant-1", "test-corpus")))
            .isPresent()
            .hasValueSatisfying(d -> {
                assertThat(d.name()).isEqualTo("test-corpus");
                assertThat(d.type().name()).isEqualTo(CorpusType.RESEARCH.name());
                assertThat(d.lifecycle()).isEqualTo(CorpusLifecycle.ACTIVE);
                assertThat(d.qdrantCollection()).isEqualTo("qdrant-test-corpus");
                assertThat(d.corpusRef()).isEqualTo(new CorpusRef("tenant-1", "test-corpus"));
            });
    }

    @Test
    void duplicateNameSameTenantThrows() {
        catalogue.register("dup", CorpusType.RESEARCH, "tenant-1",
                "qdrant-dup", List.of(), List.of(), Map.of());

        assertThatThrownBy(() -> catalogue.register("dup", CorpusType.RESEARCH, "tenant-1",
                "qdrant-dup-2", List.of(), List.of(), Map.of()))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void sameNameDifferentTenantAllowed() {
        catalogue.register("shared", CorpusType.RESEARCH, "tenant-1",
                "qdrant-shared-1", List.of(), List.of(), Map.of());
        catalogue.register("shared", CorpusType.RESEARCH, "tenant-2",
                "qdrant-shared-2", List.of(), List.of(), Map.of());

        assertThat(catalogue.get(new CorpusRef("tenant-1", "shared"))).isPresent();
        assertThat(catalogue.get(new CorpusRef("tenant-2", "shared"))).isPresent();
    }

    @Test
    void listByLifecycle() {
        catalogue.register("active-one", CorpusType.RESEARCH, "t1",
                "q1", List.of(), List.of(), Map.of());
        catalogue.register("active-two", CorpusType.TRANSCRIPT, "t1",
                "q2", List.of(), List.of(), Map.of());
        catalogue.register("to-archive", CorpusType.KNOWLEDGE_GARDEN, "t1",
                "q3", List.of(), List.of(), Map.of());
        catalogue.updateLifecycle(new CorpusRef("t1", "to-archive"), CorpusLifecycle.ARCHIVED);

        assertThat(catalogue.list("t1", CorpusLifecycle.ACTIVE)).hasSize(2);
        assertThat(catalogue.list("t1", CorpusLifecycle.ARCHIVED)).hasSize(1);
        assertThat(catalogue.list("t1", CorpusLifecycle.ARCHIVED).getFirst().name())
            .isEqualTo("to-archive");
    }

    @Test
    void forTypeFilters() {
        catalogue.register("research-1", CorpusType.RESEARCH, "t1",
                "q1", List.of(), List.of(), Map.of());
        catalogue.register("transcript-1", CorpusType.TRANSCRIPT, "t1",
                "q2", List.of(), List.of(), Map.of());
        catalogue.register("research-2", CorpusType.RESEARCH, "t1",
                "q3", List.of(), List.of(), Map.of());

        assertThat(catalogue.forType(CorpusType.RESEARCH, "t1")).hasSize(2);
        assertThat(catalogue.forType(CorpusType.TRANSCRIPT, "t1")).hasSize(1);
    }

    @Test
    void deleteRemovesEntry() {
        catalogue.register("doomed", CorpusType.RESEARCH, "t1",
                "q1", List.of(), List.of(), Map.of());
        assertThat(catalogue.get(new CorpusRef("t1", "doomed"))).isPresent();

        catalogue.deregister(new CorpusRef("t1", "doomed"));
        assertThat(catalogue.get(new CorpusRef("t1", "doomed"))).isEmpty();
    }

    @Test
    void searchScopeIncludesUpstream() {
        catalogue.register("parent", CorpusType.KNOWLEDGE_GARDEN, "t1",
                "q-parent", List.of(), List.of(), Map.of());

        var parentRef = new CorpusRef("t1", "parent");
        catalogue.register("child", CorpusType.RESEARCH, "t1",
                "q-child",
                List.of(new FederationRef(parentRef, null, null, "default")),
                List.of(),
                Map.of());

        var scope = catalogue.searchScope(new CorpusRef("t1", "child"));
        assertThat(scope).extracting(CorpusDescriptor::name)
            .containsExactlyInAnyOrder("child", "parent");
    }

    @Test
    void searchScopeIncludesPeers() {
        catalogue.register("main", CorpusType.RESEARCH, "t1",
                "q-main", List.of(), List.of(), Map.of());
        catalogue.register("peer-a", CorpusType.RESEARCH, "t1",
                "q-peer-a", List.of(), List.of(), Map.of());

        var peerRef = new CorpusRef("t1", "peer-a");
        catalogue.register("searcher", CorpusType.RESEARCH, "t1",
                "q-searcher",
                List.of(),
                List.of(new FederationRef(peerRef, null, null, null)),
                Map.of());

        var scope = catalogue.searchScope(new CorpusRef("t1", "searcher"));
        assertThat(scope).extracting(CorpusDescriptor::name)
            .containsExactlyInAnyOrder("searcher", "peer-a");
    }

    @Test
    void searchScopeDetectsCycles() {
        catalogue.register("a", CorpusType.RESEARCH, "t1",
                "qa", List.of(), List.of(), Map.of());
        catalogue.register("b", CorpusType.RESEARCH, "t1",
                "qb",
                List.of(new FederationRef(new CorpusRef("t1", "a"), null, null, "default")),
                List.of(),
                Map.of());

        // Update a to point upstream to b — creating a cycle a→b→a
        catalogue.updateFederation(new CorpusRef("t1", "a"),
                List.of(new FederationRef(new CorpusRef("t1", "b"), null, null, "default")),
                List.of());

        var scope = catalogue.searchScope(new CorpusRef("t1", "a"));
        assertThat(scope).extracting(CorpusDescriptor::name)
            .containsExactlyInAnyOrder("a", "b");
    }

    @Test
    void getNonExistentReturnsEmpty() {
        assertThat(catalogue.get(new CorpusRef("t1", "nope"))).isEmpty();
    }

    @Test
    void updateLifecycleToArchivedAndBack() {
        catalogue.register("flip", CorpusType.RESEARCH, "t1",
                "q1", List.of(), List.of(), Map.of());

        catalogue.updateLifecycle(new CorpusRef("t1", "flip"), CorpusLifecycle.ARCHIVED);
        assertThat(catalogue.get(new CorpusRef("t1", "flip")).orElseThrow().lifecycle())
            .isEqualTo(CorpusLifecycle.ARCHIVED);

        catalogue.updateLifecycle(new CorpusRef("t1", "flip"), CorpusLifecycle.ACTIVE);
        assertThat(catalogue.get(new CorpusRef("t1", "flip")).orElseThrow().lifecycle())
            .isEqualTo(CorpusLifecycle.ACTIVE);
    }
}
