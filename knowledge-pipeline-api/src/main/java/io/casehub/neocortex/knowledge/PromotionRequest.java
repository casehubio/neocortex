package io.casehub.neocortex.knowledge;

import java.util.Objects;

public record PromotionRequest(String cacheEntityId, String tenantId,
                                String researchSessionId) {
    public PromotionRequest {
        Objects.requireNonNull(cacheEntityId);
        Objects.requireNonNull(tenantId);
    }
}
