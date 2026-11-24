package io.casehub.neocortex.mindmap.intelligence.consolidation;

import io.casehub.neocortex.mindmap.AttentionSignal;
import io.casehub.neocortex.mindmap.SignalCategory;
import io.casehub.neocortex.mindmap.intelligence.CuriositySignal;
import io.casehub.neocortex.mindmap.intelligence.CuriositySignalGenerator;
import jakarta.annotation.Priority;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

@ApplicationScoped
@Priority(40)
public class CuriosityRefreshPhase implements ConsolidationPhase {

    private final CuriositySignalGenerator generator;
    private final List<AttentionSignal> pendingSignals = new ArrayList<>();

    @Inject
    public CuriosityRefreshPhase(CuriositySignalGenerator generator) {
        this.generator = generator;
    }

    @Override
    public String name() {
        return "curiosity-refresh";
    }

    @Override
    public void beginTick() {
        pendingSignals.clear();
    }

    @Override
    public List<AttentionSignal> signals() {
        var result = List.copyOf(pendingSignals);
        pendingSignals.clear();
        return result;
    }

    @Override
    public void run(String tenantId, List<String> subgraphPriority) {
        List<CuriositySignal> computed = generator.computeSignals(tenantId, Set.of());
        for (CuriositySignal cs : computed) {
            pendingSignals.add(new AttentionSignal(
                    null, tenantId, SignalCategory.DRIVE_SHIFT,
                    cs.targetNodeId(), cs.targetSubgraphType(),
                    cs.score(), cs.question()));
        }
    }
}
