package io.casehub.neocortex.corpus.management.impl;

import io.casehub.neocortex.corpus.management.CorpusCatalogue;
import io.casehub.neocortex.corpus.management.CorpusDescriptor;
import io.casehub.neocortex.corpus.management.CorpusManager;
import io.casehub.neocortex.rag.CorpusRef;

import java.util.List;
import java.util.Map;
import java.util.Optional;

public class TranscriptCorpusInitializer {

    private final CorpusCatalogue catalogue;
    private final CorpusManager manager;

    public TranscriptCorpusInitializer(CorpusCatalogue catalogue, CorpusManager manager) {
        this.catalogue = catalogue;
        this.manager = manager;
    }

    public CorpusRef ensureCorpusExists(String agentId, String tenantId) {
        String corpusName = agentId + "-interactions";
        CorpusRef ref = new CorpusRef(tenantId, corpusName);
        Optional<CorpusDescriptor> existing = catalogue.get(ref);
        if (existing.isPresent()) {
            return ref;
        }
        manager.create(corpusName, BuiltInCorpusTypes.TRANSCRIPT, tenantId,
            List.of(), List.of(), Map.of("agent_id", agentId));
        return ref;
    }

    public static String corpusNameFor(String agentId) {
        return agentId + "-interactions";
    }
}
