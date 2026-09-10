package io.casehub.neocortex.mindmap.intelligence.consolidation;

import io.casehub.neocortex.mindmap.intelligence.CuriositySignalGenerator;
import jakarta.annotation.Priority;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.util.List;
import java.util.Set;

@ApplicationScoped
@Priority(40)
public class CuriosityRefreshPhase implements ConsolidationPhase {

    private final CuriositySignalGenerator generator;

    @Inject
    public CuriosityRefreshPhase(CuriositySignalGenerator generator) {
        this.generator = generator;
    }

    @Override
    public String name() {
        return "curiosity-refresh";
    }

    @Override
    public void run(String tenantId, List<String> subgraphPriority) {
        generator.computeSignals(tenantId, Set.of());
    }
}
