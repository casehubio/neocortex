package io.casehub.neocortex.mindmap.intelligence;

import java.util.List;
import java.util.Set;

public interface CuriositySignalProvider {
    List<CuriositySignal> computeSignals(String tenantId, Set<String> recentEntityIds);
}
