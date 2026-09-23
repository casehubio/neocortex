package io.casehub.neocortex.mindmap.intelligence.consolidation;

import io.casehub.neocortex.mindmap.CognitiveGoalRecognizer;
import io.casehub.neocortex.mindmap.MindMapNode;
import io.casehub.neocortex.mindmap.MindMapStore;
import io.casehub.neocortex.mindmap.MindMapSubgraph;
import io.casehub.neocortex.mindmap.NodeInput;
import io.casehub.neocortex.mindmap.RecognizedGoal;
import io.casehub.neocortex.mindmap.SubgraphTypes;
import io.casehub.neocortex.memory.CaseMemoryStore;
import io.casehub.neocortex.memory.Memory;
import io.casehub.neocortex.memory.MemoryScanRequest;
import jakarta.annotation.Priority;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

@ApplicationScoped
@Priority(45)
public class GoalRecognitionPhase implements ConsolidationPhase {

    private static final Logger LOG = Logger.getLogger(GoalRecognitionPhase.class.getName());

    private final MindMapStore mindMapStore;
    private final CaseMemoryStore memoryStore;
    private final CognitiveGoalRecognizer recognizer;

    @Inject
    public GoalRecognitionPhase(Instance<MindMapStore> mindMapStore,
                                 Instance<CaseMemoryStore> memoryStore,
                                 Instance<CognitiveGoalRecognizer> recognizer) {
        this.mindMapStore = mindMapStore.isResolvable() ? mindMapStore.get() : null;
        this.memoryStore = memoryStore.isResolvable() ? memoryStore.get() : null;
        this.recognizer = recognizer.isResolvable() ? recognizer.get() : null;
    }

    public GoalRecognitionPhase(MindMapStore mindMapStore,
                          CaseMemoryStore memoryStore,
                          CognitiveGoalRecognizer recognizer) {
        this.mindMapStore = mindMapStore;
        this.memoryStore = memoryStore;
        this.recognizer = recognizer;
    }

    @Override
    public String name() {
        return "goal-recognition";
    }

    @Override
    public void run(String tenantId, List<String> subgraphPriority) {
        if (mindMapStore == null || memoryStore == null || recognizer == null) return;

        String goalSgId = findGoalSubgraph(tenantId);
        if (goalSgId == null) return;

        List<Memory> experiences = memoryStore.scan(
                new MemoryScanRequest(tenantId, "experience", null, null, 100, null));
        if (experiences.isEmpty()) return;

        List<MindMapNode> existingGoals = mindMapStore.nodesIn(goalSgId, tenantId);

        StringBuilder combinedText = new StringBuilder();
        for (Memory mem : experiences) {
            if (mem.text() != null && !mem.text().isBlank()) {
                combinedText.append(mem.text()).append("\n");
            }
        }
        if (combinedText.isEmpty()) return;

        List<RecognizedGoal> recognized = recognizer.recognize(
                combinedText.toString(), existingGoals, tenantId);

        for (RecognizedGoal goal : recognized) {
            boolean duplicate = existingGoals.stream()
                    .anyMatch(n -> n.name().equalsIgnoreCase(goal.description()));
            if (duplicate) {
                LOG.fine("Skipping duplicate goal: " + goal.description());
                continue;
            }

            Map<String, String> props = new HashMap<>();
            props.put("description", goal.description());
            props.put("status", "active");
            if (goal.origin() != null) props.put("origin", goal.origin());
            if (goal.suggestedHorizon() != null) props.put("horizon", goal.suggestedHorizon());

            mindMapStore.addNode(NodeInput.of(goal.description(), goalSgId)
                    .withProperties(props), tenantId);
            LOG.fine("Created recognized goal: " + goal.description());
        }
    }

    private String findGoalSubgraph(String tenantId) {
        for (MindMapSubgraph sg : mindMapStore.listSubgraphs(tenantId)) {
            if (SubgraphTypes.GOAL.equals(sg.type())) {
                return sg.id();
            }
        }
        return null;
    }
}
