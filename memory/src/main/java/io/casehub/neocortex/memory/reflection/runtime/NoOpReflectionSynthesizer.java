package io.casehub.neocortex.memory.reflection.runtime;

import io.casehub.neocortex.memory.Memory;
import io.casehub.neocortex.memory.reflection.ReflectionEvent;
import io.casehub.neocortex.memory.reflection.ReflectionSynthesizer;
import io.quarkus.arc.DefaultBean;
import jakarta.enterprise.context.ApplicationScoped;
import java.util.List;

@DefaultBean
@ApplicationScoped
public class NoOpReflectionSynthesizer implements ReflectionSynthesizer {
    @Override
    public List<ReflectionEvent> synthesize(String agentId, String tenantId,
            List<Memory> sources, int targetLevel) {
        return List.of();
    }
}
