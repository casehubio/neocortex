package io.casehub.neocortex.cognitive.observability;

import java.time.Instant;

public record GraphMutationRecorded(String tenantId, GraphMutation mutation) {
    public Instant timestamp() {
        return mutation.timestamp();
    }
}
