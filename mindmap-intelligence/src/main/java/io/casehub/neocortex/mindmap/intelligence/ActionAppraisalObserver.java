package io.casehub.neocortex.mindmap.intelligence;

import io.casehub.neocortex.cognitive.PadProjection;
import io.casehub.neocortex.mindmap.*;
import io.casehub.neocortex.cognitive.index.CognitiveDefaultsRegistry;
import io.casehub.neocortex.memory.experience.ExperienceAttributeKeys;
import io.casehub.neocortex.memory.experience.ExperienceRecorded;
import io.casehub.neocortex.memory.experience.Outcome;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;

import java.util.logging.Level;
import java.util.logging.Logger;

@ApplicationScoped
public class ActionAppraisalObserver {

    private static final Logger LOG = Logger.getLogger(ActionAppraisalObserver.class.getName());

    private final MindMapStore store;
    private final ActionAppraisal appraisal;
    private final CognitiveDefaultsRegistry registry;

    @Inject
    public ActionAppraisalObserver(Instance<MindMapStore> store,
                                    Instance<ActionAppraisal> appraisal,
                                    Instance<CognitiveDefaultsRegistry> registry) {
        this.store     = store.isResolvable() ? store.get() : null;
        this.appraisal = appraisal.isResolvable() ? appraisal.get() : null;
        this.registry  = registry.isResolvable() ? registry.get() : null;
    }

    ActionAppraisalObserver(MindMapStore store, ActionAppraisal appraisal,
                             CognitiveDefaultsRegistry registry) {
        this.store     = store;
        this.appraisal = appraisal;
        this.registry  = registry;
    }

    public void onExperienceRecorded(@Observes ExperienceRecorded event) {
        if (store == null || appraisal == null) return;
        if (!(event.event() instanceof Outcome outcome)) return;

        try {
            String agentId = outcome.agentId();
            String tenantId = outcome.tenantId();

            ActionOutcome actionOutcome = mapOutcome(outcome);
            double goalRelevance = computeGoalRelevance(outcome, tenantId, actionOutcome);
            AppraisalWeights weights = lookupWeights(agentId);

            var selfContext = new ActionContext(
                    agentId, agentId, tenantId, outcome.turnId(),
                    outcome.description(), outcome.capability(), actionOutcome,
                    goalRelevance, PadProjection.NEUTRAL, weights, outcome.timestamp());
            appraisal.appraise(selfContext);

            String targetAgent = outcome.metadata().get(ExperienceAttributeKeys.TARGET_AGENT);
            if (targetAgent != null && !targetAgent.equals(agentId)) {
                var otherContext = new ActionContext(
                        targetAgent, agentId, tenantId, outcome.turnId(),
                        outcome.description(), outcome.capability(), actionOutcome,
                        goalRelevance, PadProjection.NEUTRAL, weights, outcome.timestamp());
                appraisal.appraise(otherContext);
            }
        } catch (Exception e) {
            LOG.log(Level.WARNING, "Action appraisal failed for event " + event.memoryId(), e);
        }
    }

    ActionOutcome mapOutcome(Outcome outcome) {
        String explicit = outcome.metadata().get(ExperienceAttributeKeys.OUTCOME_STATUS);
        if (explicit != null) {
            return switch (explicit.toLowerCase()) {
                case "success" -> ActionOutcome.SUCCESS;
                case "failure" -> ActionOutcome.FAILURE;
                default -> ActionOutcome.NEUTRAL;
            };
        }
        Double confidence = outcome.confidence();
        if (confidence != null) {
            if (confidence >= 0.7) return ActionOutcome.SUCCESS;
            if (confidence <= 0.3) return ActionOutcome.FAILURE;
        }
        return ActionOutcome.NEUTRAL;
    }

    double computeGoalRelevance(Outcome outcome, String tenantId, ActionOutcome actionOutcome) {
        String goalSgId = findGoalSubgraph(tenantId);
        if (goalSgId == null) return 0.0;

        double maxRelevance = 0.0;
        String capability = outcome.capability();

        for (MindMapNode node : store.nodesIn(goalSgId, tenantId)) {
            String status = node.property("status").orElse("active");
            if (!"active".equals(status)) continue;

            boolean matches = false;
            if (capability != null) {
                String goalName = node.name().toLowerCase();
                String capLower = capability.toLowerCase();
                if (goalName.contains(capLower) || capLower.contains(goalName)) {
                    matches = true;
                }
                String goalCapability = node.property("capability").orElse("");
                if (!goalCapability.isEmpty() && goalCapability.equalsIgnoreCase(capability)) {
                    matches = true;
                }
            }

            if (matches) {
                double priority = node.property("priority")
                        .map(v -> {
                            try { return Double.parseDouble(v); }
                            catch (NumberFormatException e) { return 0.5; }
                        })
                        .orElse(0.5);
                double relevance = priority;
                if (actionOutcome == ActionOutcome.FAILURE) relevance = -relevance;
                if (Math.abs(relevance) > Math.abs(maxRelevance)) {
                    maxRelevance = relevance;
                }
            }
        }
        return maxRelevance;
    }

    private String findGoalSubgraph(String tenantId) {
        for (MindMapSubgraph sg : store.listSubgraphs(tenantId)) {
            if (SubgraphTypes.GOAL.equals(sg.type())) {
                return sg.id();
            }
        }
        return null;
    }

    private AppraisalWeights lookupWeights(String agentId) {
        if (registry == null) return AppraisalWeights.NEUTRAL;
        var defaults = registry.forAgentOrDefaults(agentId);
        var w = defaults.appraisalWeights();
        return w != null ? w : AppraisalWeights.NEUTRAL;
    }
}
