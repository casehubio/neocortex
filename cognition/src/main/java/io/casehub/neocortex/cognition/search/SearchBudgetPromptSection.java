package io.casehub.neocortex.cognition.search;

import io.casehub.neocortex.cognition.prompt.BlockTag;
import io.casehub.neocortex.cognition.prompt.CognitionPromptRenderer;
import io.casehub.neocortex.cognition.prompt.CognitionRenderContext;
import io.casehub.neocortex.mindmap.ContinuumPosition;
import io.casehub.neocortex.mindmap.MindMapQuery;
import io.casehub.neocortex.mindmap.MindMapStore;
import io.casehub.neocortex.mindmap.SubgraphTypes;
import org.jspecify.annotations.Nullable;

import java.util.Comparator;
import java.util.Map;

/**
 * Renders the search budget as a cognitive-state description.
 * <p>
 * Prefers crystallised prose from consolidation-time LLM generation
 * (SearchBudgetDescriptionGenerationPhase). Falls back to programmatic
 * rendering from numeric parameters when no crystallised prose exists.
 * <p>
 * Rendering style grounded in Anthropic "Functional Emotions" (2026):
 * cognitive state descriptions causally influence LLM behavior through
 * internal representations.
 */
public class SearchBudgetPromptSection implements CognitionPromptRenderer {

    static final String GENERATED_TRAIT = "SearchBudgetGenerated";

    private final SearchBudgetTickParticipant participant;
    private final @Nullable MindMapStore mindMapStore;

    public SearchBudgetPromptSection(SearchBudgetTickParticipant participant,
                                     @Nullable MindMapStore mindMapStore) {
        this(participant, mindMapStore, null);
    }

    private final @Nullable Map<String, ContinuumPosition> continuum;

    public SearchBudgetPromptSection(SearchBudgetTickParticipant participant,
                                     @Nullable MindMapStore mindMapStore,
                                     @Nullable Map<String, ContinuumPosition> continuum) {
        this.participant  = participant;
        this.mindMapStore = mindMapStore;
        this.continuum    = continuum;
    }


    @Override
    public BlockTag blockTag() {
        return BlockTag.SEARCH_BUDGET;
    }

    @Override
    public @Nullable String render(CognitionRenderContext context) {
        var budgetOpt = participant.currentBudget(context.agentId(), context.tenantId());
        if (budgetOpt.isEmpty()) {return null;}
        var budget = budgetOpt.get();
        String agentId = context.agentId();
        String tenantId = context.tenantId();

        String base = budget.extremeActivation()
                ? readCrystallisedProse(agentId, tenantId, "extreme-description")
                : readCrystallisedProse(agentId, tenantId, "description");
        if (budget.capacityConstrained()) {
            String constrained = readCrystallisedProse(agentId, tenantId, "constrained-description");
            if (constrained != null && base != null) {
                return modulateByContinuum(constrained + " " + base);
            }
            if (constrained != null) {
                return modulateByContinuum(constrained);
            }
        }
        if (base != null) {
            return modulateByContinuum(base);
        }

        if (budget.extremeActivation()) {
            String extremeFallback = readCrystallisedProse(agentId, tenantId, "description");
            if (extremeFallback != null) {
                return modulateByContinuum("Under pressure: " + extremeFallback);
            }
        }
        return modulateByContinuum(renderFromParameters(budget));
    }

    private @Nullable String readCrystallisedProse(String agentId, String tenantId, String propertyKey) {
        if (mindMapStore == null) return null;
        return mindMapStore.search(MindMapQuery.of(tenantId, 10)
                                               .withType(SubgraphTypes.COGNITIVE))
                           .stream()
                           .filter(n -> n.traits().contains(GENERATED_TRAIT))
                           .filter(n -> agentId.equals(n.property("agent-id").orElse(null)))
                           .findFirst()
                           .flatMap(n -> n.property(propertyKey))
                           .filter(d -> !d.isBlank())
                           .orElse(null);
    }

    private String renderFromParameters(SearchBudget budget) {
        if (budget.convergence() < 0.05 && budget.breadth() < 0.15) {
            return "You keep coming back to this. Other thoughts won't stick.";
        }

        double totalBudget = (budget.breadth() + budget.laterality() + budget.depth()) / 3.0;
        if (totalBudget < 0.2) {
            return "This registers, but doesn't hold your attention.";
        }

        return switch (budget.source()) {
            case CURIOSITY -> renderCuriosity(budget);
            case COMPETENCE -> renderCompetence(budget);
            case AFFILIATION -> renderAffiliation(budget);
            case AUTONOMY -> renderAutonomy(budget);
            case THREAT -> renderThreat(budget);
            case GOAL -> renderGoal(budget);
        };
    }

    private String renderCuriosity(SearchBudget b) {
        if (b.breadth() > 0.7 && b.laterality() > 0.5) {
            return "This pulls at your attention from multiple angles — the obvious answer isn't enough, and connections to adjacent areas keep surfacing.";
        }
        if (b.breadth() > 0.5) {
            return "Several angles present themselves. The first explanation doesn't quite satisfy.";
        }
        return "Something here catches your interest, though it doesn't demand deep investigation.";
    }

    private String renderCompetence(SearchBudget b) {
        if (b.depth() > 0.7) {
            return "You want to trace this to its source. Surface answers aren't satisfying — there's a mechanism underneath.";
        }
        return "This is worth understanding properly, not just noting.";
    }

    private String renderAffiliation(SearchBudget b) {
        return "This matters to someone you're connected to. A quick scan for the socially relevant signal, then move.";
    }

    private String renderAutonomy(SearchBudget b) {
        if (b.laterality() > 0.5 && b.depth() > 0.5) {
            return "You want to find your own path through this — not the obvious route, and not a shallow one.";
        }
        return "You'd rather work this out independently than follow someone else's framing.";
    }

    private String renderThreat(SearchBudget b) {
        return "Your attention narrows. Distant connections aren't useful right now — what matters is the immediate situation.";
    }

    private String renderGoal(SearchBudget b) {
        if (b.convergence() > 0.6) {
            return "The goal is close and the path is clear. Time to execute, not explore.";
        }
        return "There's a specific outcome to reach. Exploration serves the goal, not the other way around.";
    }

    private String modulateByContinuum(String base) {
        if (continuum == null || continuum.isEmpty()) {return base;}
        var worst = continuum.values().stream()
                             .max(Comparator.comparingInt(ContinuumPosition::ordinal))
                             .orElse(ContinuumPosition.HEALTHY);
        return switch (worst) {
            case HEALTHY -> base;
            case FIXATION -> "You keep circling back to this. " + base;
            case OBSESSION -> "The thought is compulsive now — depth without choice. " + base;
            case RUMINATION -> "Everything has collapsed. You can't stop, but nothing moves forward.";
        };
    }

}
