package io.casehub.neocortex.cognition.core;

import io.casehub.neocortex.cognition.appraisal.AppraisalStrategy;
import io.casehub.neocortex.cognition.appraisal.SalienceStrategy;
import io.casehub.neocortex.cognition.drive.DriveOrchestrator;
import io.casehub.neocortex.cognition.drive.NeedTierMappingProvider;
import io.casehub.neocortex.cognition.goal.GoalProposalOrchestrator;
import io.casehub.neocortex.cognition.innerlife.InnerLifeOrchestrator;
import io.casehub.neocortex.cognition.memory.MemoryHygieneOrchestrator;
import io.casehub.neocortex.cognition.mentalmodel.MentalModelOrchestrator;
import io.casehub.neocortex.cognition.mood.MoodOrchestrator;
import io.casehub.neocortex.cognition.narrative.NarrativeOrchestrator;
import io.casehub.neocortex.cognition.strategy.StrategyLearningOrchestrator;
import io.casehub.neocortex.cognition.temporal.ReflectionRetrievalOrchestrator;
import io.casehub.neocortex.cognition.temporal.TemporalFocusOrchestrator;
import io.casehub.neocortex.cognition.usermodel.UserModelOrchestrator;
import io.casehub.neocortex.cognitive.index.CognitiveDefaultsRegistry;
import io.casehub.neocortex.memory.CaseMemoryStore;
import io.casehub.neocortex.memory.engagement.EngagementEvent;
import io.casehub.neocortex.mindmap.MindMapStore;
import io.casehub.platform.agent.AgentProvider;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;

import java.util.function.Consumer;

/**
 * Produces fully-wired CognitionCore instances with zero application boilerplate.
 *
 * Applications inject this factory and call {@link #create(CognitionConfig)} —
 * the factory auto-discovers all available cognitive SPIs via CDI and wires
 * appraisal, gut feeling, and sub-thought pipelines automatically.
 *
 * Replaces the imperative configure*() pattern where every application had
 * to manually call configureAppraisal(), configureGutFeeling(), etc.
 */
@ApplicationScoped
public class CognitionCoreFactory {

    @Inject Instance<MoodOrchestrator> mood;
    @Inject Instance<DriveOrchestrator> drives;
    @Inject Instance<UserModelOrchestrator> userModel;
    @Inject Instance<MentalModelOrchestrator> mentalModel;
    @Inject Instance<StrategyLearningOrchestrator> strategy;
    @Inject Instance<NarrativeOrchestrator> narrative;
    @Inject Instance<GoalProposalOrchestrator> goals;
    @Inject Instance<MemoryHygieneOrchestrator> memoryHygiene;
    @Inject Instance<InnerLifeOrchestrator> innerLife;
    @Inject Instance<AgentProvider> agentProvider;
    @Inject Instance<MindMapStore> mindMapStore;
    @Inject Instance<NeedTierMappingProvider> needTierMapping;
    @Inject Instance<CognitiveAttentionMediator> attentionMediator;
    @Inject Instance<TemporalFocusOrchestrator> temporalFocus;
    @Inject Instance<ReflectionRetrievalOrchestrator> reflectionOrchestrator;
    @Inject Instance<ConsolidationMediator> consolidationMediator;
    @Inject Instance<CognitiveDefaultsRegistry> defaultsRegistry;

    @Inject Instance<AppraisalStrategy> appraisalStrategy;
    @Inject Instance<SalienceStrategy> salienceStrategy;
    @Inject Instance<CaseMemoryStore> memoryStore;
    @Inject Instance<io.casehub.neocortex.cognition.subthought.SubThoughtTickParticipant> subThoughtParticipant;
    @Inject Instance<io.casehub.neocortex.knowledge.TermNormalizer> termNormalizer;
    @Inject Instance<Consumer<EngagementEvent>> engagementPersister;

    public CognitionCore create(CognitionConfig config) {
        var core = new CognitionCore(
                resolve(mood), resolve(drives),
                resolve(userModel), resolve(mentalModel),
                resolve(strategy), resolve(narrative),
                resolve(goals), resolve(memoryHygiene),
                resolve(innerLife), resolve(agentProvider),
                config,
                resolve(mindMapStore), resolve(needTierMapping),
                resolve(attentionMediator), resolve(engagementPersister),
                resolve(temporalFocus), resolve(reflectionOrchestrator),
                resolve(consolidationMediator));

        if (defaultsRegistry.isResolvable()) {
            core.setDefaultsRegistry(defaultsRegistry.get());
        }

        if (appraisalStrategy.isResolvable() && salienceStrategy.isResolvable()) {
            core.configureAppraisal(appraisalStrategy.get(), salienceStrategy.get());
        }

        if (memoryStore.isResolvable() && mood.isResolvable()) {
            core.configureGutFeeling(memoryStore.get(), mood.get());
        }

        if (subThoughtParticipant.isResolvable()) {
            core.configureSubThoughts(subThoughtParticipant.get());
        }

        if (mindMapStore.isResolvable() && termNormalizer.isResolvable()) {
            core.configureDriveGoalBridge(mindMapStore.get(), termNormalizer.get());
        }

        return core;
    }

    private static <T> T resolve(Instance<T> instance) {
        return instance.isResolvable() ? instance.get() : null;
    }
}
