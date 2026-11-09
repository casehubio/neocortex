package io.casehub.neocortex.cognition.core;

import io.casehub.neocortex.cognitive.index.CognitiveDefaultsRegistry;
import io.casehub.neocortex.memory.CaseMemoryStore;
import io.casehub.neocortex.mindmap.ContinuumPosition;
import io.casehub.neocortex.mindmap.MindMapStore;
import io.casehub.neocortex.mindmap.intelligence.consolidation.ConsolidationCompleted;
import io.casehub.neocortex.mindmap.intelligence.consolidation.RecurringThoughtPhase;

import java.util.Map;

public class CognitionTestAccess {

    public static void pushConsolidationArtifacts(
            ConsolidationMediator mediator, ConsolidationCompleted event) {
        mediator.onConsolidation(event);
    }

    @SuppressWarnings("unchecked")
    public static Map<String, ContinuumPosition> lastContinuumMap(CognitionCore core) {
        try {
            var field = CognitionCore.class.getDeclaredField("lastContinuumMap");
            field.setAccessible(true);
            var map = (Map<String, ContinuumPosition>) field.get(core);
            return map != null ? map : Map.of();
        } catch (Exception e) {
            throw new RuntimeException("Cannot access lastContinuumMap", e);
        }
    }

    public static RecurringThoughtPhase createRecurringThoughtPhase(
            CaseMemoryStore memStore, MindMapStore mindMapStore, CognitiveDefaultsRegistry registry) {
        try {
            var ctor = RecurringThoughtPhase.class.getDeclaredConstructor(
                CaseMemoryStore.class, MindMapStore.class, CognitiveDefaultsRegistry.class);
            ctor.setAccessible(true);
            return ctor.newInstance(memStore, mindMapStore, registry);
        } catch (Exception e) {
            throw new RuntimeException("Cannot create RecurringThoughtPhase", e);
        }
    }
}
