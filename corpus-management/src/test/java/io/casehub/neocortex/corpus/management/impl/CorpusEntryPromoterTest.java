package io.casehub.neocortex.corpus.management.impl;

import io.casehub.neocortex.corpus.management.CorpusEntry;
import io.casehub.neocortex.corpus.management.CorpusEntryStore;
import io.casehub.neocortex.mindmap.NodeRef;
import io.casehub.neocortex.mindmap.SubgraphInput;
import io.casehub.neocortex.mindmap.inmem.InMemoryMindMapStore;
import io.casehub.neocortex.rag.CorpusRef;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CorpusEntryPromoterTest {

    private static final CorpusRef CORPUS = new CorpusRef("tenant-1", "research-corpus");

    @Test
    void promotesEntryToMindMapNode() {
        var entry = new CorpusEntry("CE-20261102-abc123",
                                    Map.of("title", "Sleep consolidation", "type", "research-paper",
                                           "corpus", "research-corpus", "domain", "neuroscience"),
                                    "Walker (2005) demonstrates two-stage consolidation.", null);

        var store   = new RecordingEntryStore(entry);
        var mindMap = new InMemoryMindMapStore();
        String subgraphId = mindMap.createSubgraph(new SubgraphInput("research-area", "research_area", null), "tenant-1");
        var promoter = new CorpusEntryPromoter(store, mindMap);

        var result = promoter.promote(CORPUS, "CE-20261102-abc123", subgraphId, "tenant-1");

        assertThat(result.nodeId()).isNotBlank();
        assertThat(result.isNew()).isTrue();

        var node = mindMap.getNode(result.nodeId(), "tenant-1");
        assertThat(node).isNotNull();
        assertThat(node.name()).isEqualTo("Sleep consolidation");
        assertThat(node.refs()).contains(
                new NodeRef("corpus", "CE-20261102-abc123", "research-corpus"));
        assertThat(node.property("domain")).hasValue("neuroscience");
        assertThat(node.property("provenance")).hasValue("corpus-promotion");

        var updated = store.lastUpdated;
        assertThat(updated).isNotNull();
        assertThat(updated.frontmatter().get("promoted_to")).isEqualTo(result.nodeId());
    }

    @Test
    void throwsWhenEntryNotFound() {
        var store    = new RecordingEntryStore(null);
        var mindMap  = new InMemoryMindMapStore();
        var promoter = new CorpusEntryPromoter(store, mindMap);

        assertThatThrownBy(() -> promoter.promote(CORPUS, "missing", "CONCEPT", "tenant-1"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("missing");
    }

    static class RecordingEntryStore implements CorpusEntryStore {
        private final CorpusEntry fixedEntry;
        CorpusEntry lastUpdated;

        RecordingEntryStore(CorpusEntry entry)                 {this.fixedEntry = entry;}

        @Override
        public String add(CorpusRef corpus, CorpusEntry entry) {return entry.id();}

        @Override
        public void update(CorpusRef corpus, String entryId, CorpusEntry entry) {
            this.lastUpdated = entry;
        }

        @Override
        public void remove(CorpusRef corpus, String entryId) {}

        @Override
        public Optional<CorpusEntry> get(CorpusRef corpus, String entryId) {
            return Optional.ofNullable(fixedEntry);
        }
    }
}
