package io.casehub.neocortex.corpus.management;

import io.casehub.neocortex.rag.CorpusRef;

import java.util.List;
import java.util.Map;

public interface CorpusManager {

    CorpusDescriptor create(String name, CorpusType type, String tenantId,
                            List<FederationRef> upstream,
                            List<FederationRef> peers,
                            Map<String, String> metadata);

    void archive(CorpusRef corpus);

    void revive(CorpusRef corpus);

    void delete(CorpusRef corpus);
}
