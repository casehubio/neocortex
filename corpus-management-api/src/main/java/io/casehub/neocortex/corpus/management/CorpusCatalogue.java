package io.casehub.neocortex.corpus.management;

import io.casehub.neocortex.rag.CorpusRef;

import java.util.List;
import java.util.Optional;

public interface CorpusCatalogue {

    List<CorpusDescriptor> list(String tenantId, CorpusLifecycle status);

    Optional<CorpusDescriptor> get(CorpusRef corpus);

    List<CorpusDescriptor> forType(CorpusType type, String tenantId);

    List<CorpusDescriptor> searchScope(CorpusRef corpus);

    HealthReport reconcile(String tenantId);
}
