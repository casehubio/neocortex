package io.casehub.neocortex.caps.llm.eval;

import io.casehub.neocortex.cognition.core.CognitionConfig;
import io.casehub.neocortex.cognition.core.CognitionCore;
import io.casehub.neocortex.cognition.drive.AffiliationDrive;
import io.casehub.neocortex.cognition.drive.AutonomyDrive;
import io.casehub.neocortex.cognition.drive.CompetenceDrive;
import io.casehub.neocortex.cognition.drive.CuriosityDrive;
import io.casehub.neocortex.cognition.drive.DriveComposer;
import io.casehub.neocortex.cognition.drive.DriveConfig;
import io.casehub.neocortex.cognition.drive.DriveOrchestrator;
import io.casehub.neocortex.cognition.goal.GoalEscalationConfig;
import io.casehub.neocortex.cognition.goal.GoalProposalConfig;
import io.casehub.neocortex.cognition.goal.GoalProposalOrchestrator;
import io.casehub.neocortex.cognition.innerlife.InnerLifeConfig;
import io.casehub.neocortex.cognition.innerlife.InnerLifeOrchestrator;
import io.casehub.neocortex.cognition.memory.MemoryHygieneOrchestrator;
import io.casehub.neocortex.cognition.mentalmodel.MentalModelConfig;
import io.casehub.neocortex.cognition.mentalmodel.MentalModelOrchestrator;
import io.casehub.neocortex.cognition.mood.MoodConfig;
import io.casehub.neocortex.cognition.mood.MoodOrchestrator;
import io.casehub.neocortex.cognition.narrative.NarrativeOrchestrator;
import io.casehub.neocortex.cognition.strategy.StrategyLearningConfig;
import io.casehub.neocortex.cognition.strategy.StrategyLearningOrchestrator;
import io.casehub.neocortex.cognition.usermodel.UserModelConfig;
import io.casehub.neocortex.cognition.usermodel.UserModelOrchestrator;
import io.casehub.neocortex.memory.KnowledgeGapSummary;
import io.casehub.neocortex.memory.cbr.inmem.InMemoryCbrRecordStore;
import io.casehub.platform.agent.session.SessionPipeline;
import org.jspecify.annotations.Nullable;

import java.time.Duration;
import java.util.List;
import java.util.Optional;

public final class CognitionCoreTestBuilder {

    private CognitionCoreTestBuilder() {}

    public static CognitionCore buildCore(CognitionConfig config) {
        return buildCore(config, null);
    }

    public static CognitionCore buildCore(CognitionConfig config, @Nullable SessionPipeline sessionPipeline) {
        var mood = new MoodOrchestrator(MoodConfig.defaults());
        var cbrStore = new InMemoryCbrRecordStore();
        var narrativeOrch = new NarrativeOrchestrator(
                new io.casehub.neocortex.cognition.narrative.NarrativeMemory(
                        cbrStore, io.casehub.neocortex.cognition.narrative.NarrativeConfig.defaults()));

        MemoryHygieneOrchestrator noOpHygiene = new MemoryHygieneOrchestrator() {
            @Override public void tick(String agentId, String tenantId) {}
            @Override public KnowledgeGapSummary knowledgeGaps(String agentId, String tenantId) {
                return KnowledgeGapSummary.empty();
            }
        };

        io.casehub.neocortex.memory.reflection.ReflectionOrchestrator noOpReflection =
                (agentId, tenantId, since, maxEntries) -> List.of();

        var userModel = new UserModelOrchestrator(
                new io.casehub.neocortex.cognition.usermodel.UserProfileMemory(
                        cbrStore, UserModelConfig.defaults()),
                UserModelConfig.defaults(), sessionPipeline);
        var mentalModel = new MentalModelOrchestrator(
                new io.casehub.neocortex.cognition.mentalmodel.MentalModelMemory(
                        cbrStore, MentalModelConfig.defaults()),
                MentalModelConfig.defaults(), java.time.Clock.systemUTC(), sessionPipeline);
        var strategy = new StrategyLearningOrchestrator(
                new io.casehub.neocortex.cognition.strategy.StrategyMemory(
                        cbrStore, StrategyLearningConfig.defaults()),
                noOpReflection,
                StrategyLearningConfig.defaults(), java.time.Clock.systemUTC(), sessionPipeline);

        var drives = new DriveOrchestrator(
                new CuriosityDrive(noOpHygiene), new CompetenceDrive(strategy),
                new AffiliationDrive(userModel, 0.3, Duration.ofHours(1)),
                new AutonomyDrive(mentalModel, 0.5),
                mood, new DriveComposer(), DriveConfig.defaults());

        var innerLife = new InnerLifeOrchestrator(
                noOpReflection, List.of(), InnerLifeConfig.defaults(), drives, sessionPipeline);

        var goals = new GoalProposalOrchestrator(
                drives, List.of(), null, Optional.empty(),
                null, null, null,
                GoalProposalConfig.defaults(), GoalEscalationConfig.defaults(),
                java.time.Clock.systemUTC());

        return new CognitionCore(mood, drives, userModel, mentalModel, strategy,
                narrativeOrch, goals, noOpHygiene, innerLife, config, sessionPipeline);
    }
}
