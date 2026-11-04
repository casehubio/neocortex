package io.casehub.neocortex.corpus.management.impl;

import io.casehub.neocortex.cognitive.Confidence;
import io.casehub.neocortex.corpus.management.CorpusEntry;
import io.casehub.neocortex.corpus.management.CorpusEntryStore;
import io.casehub.neocortex.mindmap.MindMapStore;
import io.casehub.neocortex.mindmap.NodeInput;
import io.casehub.neocortex.mindmap.NodeRef;
import io.casehub.neocortex.rag.CorpusRef;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

public class CorpusEntryPromoter {

    private final CorpusEntryStore entryStore;
    private final MindMapStore mindMapStore;

    public CorpusEntryPromoter(CorpusEntryStore entryStore, MindMapStore mindMapStore) {
        this.entryStore = entryStore;
        this.mindMapStore = mindMapStore;
    }

    public PromotionResult promote(CorpusRef corpus, String entryId,
                                    String targetSubgraph, String tenantId) {
        CorpusEntry entry = entryStore.get(corpus, entryId)
            .orElseThrow(() -> new IllegalArgumentException(
                "Entry '" + entryId + "' not found in corpus '" + corpus.corpusName() + "'"));

        String title = entry.stringField("title");
        if (title == null || title.isBlank()) {
            title = entryId;
        }

        var properties = new LinkedHashMap<String, String>();
        properties.put("provenance", "corpus-promotion");
        properties.put("source_corpus", corpus.corpusName());
        properties.put("source_entry_id", entryId);
        String domain = entry.stringField("domain");
        if (domain != null) {
            properties.put("domain", domain);
        }

        var corpusRef = new NodeRef("corpus", entryId, corpus.corpusName());

        var nodeInput = new NodeInput(
            title,
            targetSubgraph,
            Confidence.unknown(0.8),
            "corpus-promotion",
            Set.of(),
            Set.of(corpusRef),
            null, null, null, null, null,
            Map.copyOf(properties)
        );

        String nodeId = mindMapStore.addNode(nodeInput, tenantId);

        var updatedFrontmatter = new LinkedHashMap<>(entry.metadata());
        updatedFrontmatter.put("promoted_to", nodeId);
        var updatedEntry = new CorpusEntry(entry.id(), Map.copyOf(updatedFrontmatter),
            entry.body(), entry.artifactRef());
        entryStore.update(corpus, entryId, updatedEntry);

        return new PromotionResult(nodeId, true);
    }

    public record PromotionResult(String nodeId, boolean isNew) {}
}
