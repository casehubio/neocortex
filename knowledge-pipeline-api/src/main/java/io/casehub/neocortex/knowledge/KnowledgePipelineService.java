package io.casehub.neocortex.knowledge;

import java.util.List;

public interface KnowledgePipelineService {

    List<CachedEntity> search(KnowledgeQuery query, String tenantId);

    List<CachedEntity> search(KnowledgeQuery query, String tenantId,
                               String researchSessionId);

    PromotionResult promote(PromotionRequest request);

    void refreshStale(String tenantId);
}
