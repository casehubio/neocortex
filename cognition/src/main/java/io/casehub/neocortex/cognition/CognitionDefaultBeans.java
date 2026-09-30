package io.casehub.neocortex.cognition;

import io.casehub.neocortex.cognition.core.CognitiveImpact;
import io.casehub.neocortex.cognition.core.InteractionMapper;
import io.casehub.neocortex.cognition.core.SubjectResolver;
import io.casehub.neocortex.cognition.drive.DriveConfig;
import io.casehub.neocortex.cognition.emergence.NormDetectionConfig;
import io.casehub.neocortex.cognition.emergence.NormFilter;
import io.casehub.neocortex.cognition.goal.CognitiveGoalConfig;
import io.casehub.neocortex.cognition.goal.GoalEscalationConfig;
import io.casehub.neocortex.cognition.goal.GoalProposalConfig;
import io.casehub.neocortex.cognition.innerlife.InnerLifeConfig;
import io.casehub.neocortex.cognition.mentalmodel.MentalModelConfig;
import io.casehub.neocortex.cognition.mood.MoodConfig;
import io.casehub.neocortex.cognition.mood.MoodCongruenceConfig;
import io.casehub.neocortex.cognition.narrative.NarrativeConfig;
import io.casehub.neocortex.cognition.personality.PersonalityEvolutionConfig;
import io.casehub.neocortex.cognition.strategy.StrategyLearningConfig;
import io.casehub.neocortex.cognition.usermodel.UserModelConfig;
import io.quarkus.arc.DefaultBean;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;
import jakarta.inject.Singleton;

import java.util.Set;

@ApplicationScoped
public class CognitionDefaultBeans {

    @Produces @DefaultBean @Singleton
    DriveConfig driveConfig() {
        return DriveConfig.defaults();
    }

    @Produces @DefaultBean @Singleton
    MoodConfig moodConfig() {
        return MoodConfig.defaults();
    }

    @Produces @DefaultBean @Singleton
    PersonalityEvolutionConfig personalityEvolutionConfig() {
        return PersonalityEvolutionConfig.defaults();
    }

    @Produces @DefaultBean @Singleton
    InnerLifeConfig innerLifeConfig() {
        return InnerLifeConfig.defaults();
    }

    @Produces @DefaultBean @Singleton
    MentalModelConfig mentalModelConfig() {
        return MentalModelConfig.defaults();
    }

    @Produces @DefaultBean @Singleton
    UserModelConfig userModelConfig() {
        return UserModelConfig.defaults();
    }

    @Produces @DefaultBean @Singleton
    StrategyLearningConfig strategyLearningConfig() {
        return StrategyLearningConfig.defaults();
    }

    @Produces @DefaultBean @Singleton
    NarrativeConfig narrativeConfig() {
        return NarrativeConfig.defaults();
    }

    @Produces @DefaultBean @Singleton
    GoalProposalConfig goalProposalConfig() {
        return GoalProposalConfig.defaults();
    }

    @Produces @DefaultBean @Singleton
    GoalEscalationConfig goalEscalationConfig() {
        return GoalEscalationConfig.defaults();
    }

    @Produces @DefaultBean @Singleton
    NormDetectionConfig normDetectionConfig() {
        return NormDetectionConfig.defaults();
    }

    @Produces @DefaultBean @Singleton
    MoodCongruenceConfig moodCongruenceConfig() {
        return MoodCongruenceConfig.defaults();
    }

    @Produces @DefaultBean @Singleton
    CognitiveGoalConfig cognitiveGoalConfig() {
        return CognitiveGoalConfig.defaults();
    }

    @Produces @DefaultBean @Singleton
    SubjectResolver subjectResolver() {
        return (agentId, tenantId) -> Set.of();
    }

    @Produces @DefaultBean @Singleton
    InteractionMapper interactionMapper() {
        return (agentId, targetId, interactionType) ->
                CognitiveImpact.fromText(interactionType);
    }

    @Produces @DefaultBean @Singleton
    NormFilter normFilter() {
        return (norms, agentId, tenantId) -> norms;
    }
}
