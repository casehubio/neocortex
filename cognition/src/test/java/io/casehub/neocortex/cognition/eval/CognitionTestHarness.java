package io.casehub.neocortex.cognition.eval;

import io.casehub.eidos.api.AgentDescriptor;
import io.casehub.neocortex.cognition.core.CognitionConfig;
import io.casehub.neocortex.cognition.core.CognitionCore;
import io.casehub.neocortex.cognition.core.CognitionPhase;
import io.casehub.neocortex.cognition.core.CognitionTestAccess;
import io.casehub.neocortex.cognition.core.ConsolidationMediator;
import io.casehub.neocortex.cognition.core.SubjectResolver;
import io.casehub.neocortex.cognition.drive.CuriosityChannel;
import io.casehub.neocortex.cognition.drive.CuriosityDrive;
import io.casehub.neocortex.cognition.drive.DriveAxis;
import io.casehub.neocortex.cognition.drive.DriveComposer;
import io.casehub.neocortex.cognition.drive.DriveConfig;
import io.casehub.neocortex.cognition.drive.DriveIntensity;
import io.casehub.neocortex.cognition.drive.DriveOrchestrator;
import io.casehub.neocortex.cognition.drive.DriveSource;
import io.casehub.neocortex.cognition.mood.MoodConfig;
import io.casehub.neocortex.cognition.mood.MoodOrchestrator;
import io.casehub.neocortex.cognition.mood.MoodSignal;
import io.casehub.neocortex.cognition.prompt.CognitionRenderContext;
import io.casehub.neocortex.cognition.search.SearchBudget;
import io.casehub.neocortex.cognition.search.SearchBudgetTickParticipant;
import io.casehub.neocortex.cognitive.index.CognitiveDefaults;
import io.casehub.neocortex.cognitive.index.CognitiveDefaultsRegistry;
import io.casehub.neocortex.cognitive.index.CognitiveDerivationEngine;
import io.casehub.neocortex.cognitive.index.DescriptorView;
import io.casehub.neocortex.cognitive.index.DispositionAxes;
import io.casehub.neocortex.memory.CaseMemoryStore;
import io.casehub.neocortex.memory.experience.ExperienceEvent;
import io.casehub.neocortex.memory.experience.ExperienceEvents;
import io.casehub.neocortex.memory.inmem.InMemoryMemoryStore;
import io.casehub.neocortex.memory.mood.MoodState;
import io.casehub.neocortex.mindmap.ConsolidationArtifact;
import io.casehub.neocortex.mindmap.ContinuumPosition;
import io.casehub.neocortex.mindmap.MindMapStore;
import io.casehub.neocortex.mindmap.RecurringThoughtAssessment;
import io.casehub.neocortex.mindmap.inmem.InMemoryMindMapStore;
import io.casehub.neocortex.mindmap.intelligence.CuriosityChannelComputer;
import io.casehub.neocortex.mindmap.intelligence.InformationGapDetector;
import io.casehub.neocortex.mindmap.intelligence.InformationGapScore;
import io.casehub.neocortex.mindmap.intelligence.consolidation.ConsolidationCompleted;
import io.casehub.neocortex.mindmap.intelligence.consolidation.ConsolidationPhase;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;
import java.util.stream.Collectors;

public class CognitionTestHarness {

    private static final SubjectResolver NO_OP_RESOLVER = (a, t) -> Set.of();

    private final CognitionCore core;
    private final MindMapStore mindMapStore;
    private final CaseMemoryStore memoryStore;
    private final CuriosityDrive curiosityDrive;
    private final MoodOrchestrator moodOrchestrator;
    private final SearchBudgetTickParticipant budgetParticipant;
    private final CognitiveDefaultsRegistry registry;
    private final ConsolidationMediator mediator;
    private final List<ConsolidationPhase> consolidationPhases;
    private final Map<String, AgentDescriptor> descriptors;
    private final Map<String, String> lastObservations = new ConcurrentHashMap<>();

    CognitionTestHarness(CognitionCore core,
                         MindMapStore mindMapStore,
                         CaseMemoryStore memoryStore,
                         CuriosityDrive curiosityDrive,
                         MoodOrchestrator moodOrchestrator,
                         SearchBudgetTickParticipant budgetParticipant,
                         CognitiveDefaultsRegistry registry,
                         ConsolidationMediator mediator,
                         List<ConsolidationPhase> consolidationPhases,
                         Map<String, AgentDescriptor> descriptors) {
        this.core = core;
        this.mindMapStore = mindMapStore;
        this.memoryStore = memoryStore;
        this.curiosityDrive = curiosityDrive;
        this.moodOrchestrator = moodOrchestrator;
        this.budgetParticipant = budgetParticipant;
        this.registry = registry;
        this.mediator = mediator;
        this.consolidationPhases = List.copyOf(consolidationPhases);
        this.descriptors = Map.copyOf(descriptors);
    }

    public static Builder builder() {
        return new Builder();
    }

    // --- Store access ---

    public MindMapStore mindMapStore() { return mindMapStore; }
    public CaseMemoryStore memoryStore() { return memoryStore; }

    // --- Observation + experience ---

    public void observe(String agentId, String tenantId, String observation) {
        lastObservations.put(agentId, observation);
    }

    public void addExperiences(String agentId, String tenantId, List<ExperienceEvent> events) {
        for (var event : events) {
            memoryStore.store(ExperienceEvents.toMemoryInput(event));
        }
    }

    public void addExperienceMemories(String agentId, String tenantId, String topicName, int count) {
        var domain = ExperienceEvents.DOMAIN;
        var subject = io.casehub.neocortex.memory.Subject.of("agent", agentId);
        for (int i = 0; i < count; i++) {
            memoryStore.store(io.casehub.neocortex.memory.MemoryInput.of(subject, domain, tenantId,
                "Observation about " + topicName + " iteration " + i)
                .withAttributes(Map.of("event-type", "observation", "subject", topicName)));
        }
    }

    // --- Pipeline execution ---

    public void tick(String agentId, String tenantId) {
        var observation = lastObservations.remove(agentId);
        var descriptor = descriptors.get(agentId);
        core.tick(agentId, tenantId, descriptor, NO_OP_RESOLVER, observation, null);
    }

    public void consolidate(String agentId, String tenantId) {
        for (var phase : consolidationPhases) {
            phase.beginTick();
            phase.run(tenantId, List.of());
        }
        for (var phase : consolidationPhases) {
            var artifacts = phase.artifacts();
            if (!artifacts.isEmpty()) {
                CognitionTestAccess.pushConsolidationArtifacts(mediator,
                    new ConsolidationCompleted(tenantId, List.of(), artifacts));
            }
        }
    }

    public void tickAndConsolidate(String agentId, String tenantId) {
        consolidate(agentId, tenantId);
        tick(agentId, tenantId);
    }

    // --- State injection ---

    public void setContinuumPosition(String agentId, String tenantId,
                                     Map<String, ContinuumPosition> positions) {
        var artifacts = positions.entrySet().stream()
            .map(e -> (ConsolidationArtifact) new ConsolidationArtifact.RecurringThoughtDetected(
                tenantId, agentId,
                new RecurringThoughtAssessment(e.getKey(), 0, 0, 0, 0, 0, 0, 0, 0, e.getValue()),
                Instant.now()))
            .toList();
        CognitionTestAccess.pushConsolidationArtifacts(mediator,
            new ConsolidationCompleted(tenantId, List.of(), artifacts));
    }

    public void recordMood(String agentId, String tenantId,
                           double pleasure, double arousal, double dominance) {
        moodOrchestrator.record(
            new MoodSignal.DirectShift(pleasure, arousal, dominance, "test-harness"),
            agentId, tenantId);
        moodOrchestrator.tick(agentId, tenantId);
    }

    // --- State access ---

    public Optional<SearchBudget> searchBudget(String agentId, String tenantId) {
        return budgetParticipant.currentBudget(agentId, tenantId);
    }

    public Map<String, ContinuumPosition> continuumMap(String agentId) {
        return CognitionTestAccess.lastContinuumMap(core);
    }

    public ContinuumPosition continuumPosition(String agentId, String topic) {
        return continuumMap(agentId).getOrDefault(topic, ContinuumPosition.HEALTHY);
    }

    public List<InformationGapScore> lastGaps(String agentId) {
        return curiosityDrive.lastGaps();
    }

    public CuriosityChannel lastChannel(String agentId) {
        return curiosityDrive.lastChannel();
    }

    public Optional<MoodState> currentMood(String agentId, String tenantId) {
        return moodOrchestrator.currentMood(agentId, tenantId);
    }

    // --- Prompt rendering ---

    public String renderPrompt(String agentId, String tenantId) {
        var context = new CognitionRenderContext(agentId, tenantId, null, List.of());
        return core.promptSections().stream()
            .map(s -> s.render(context))
            .filter(Objects::nonNull)
            .filter(s -> !s.isBlank())
            .collect(Collectors.joining("\n\n"));
    }

    // --- Builder ---

    public static class Builder {

        private MindMapStore mindMapStore;
        private CaseMemoryStore memoryStore;
        private final Map<String, CognitiveDefaults> agentDefaults = new LinkedHashMap<>();
        private final List<ConsolidationPhase> extraPhases = new ArrayList<>();
        private Supplier<Set<String>> goalNodeIds = Set::of;
        private CognitionConfig cognitionConfig;
        private boolean consolidationEnabled;
        private final Map<String, io.casehub.neocortex.cognition.appraisal.HabituationState> habituationStates = new HashMap<>();
        private final Map<String, io.casehub.neocortex.cognition.appraisal.AppraisalResult>  appraisalResults  = new HashMap<>();


        public Builder withMindMapStore(MindMapStore store) {
            this.mindMapStore = store;
            return this;
        }

        public Builder withMemoryStore(CaseMemoryStore store) {
            this.memoryStore = store;
            return this;
        }

        public Builder agentFromAxes(String agentId, String socialOrient,
                                     String ruleFollowing, String riskAppetite,
                                     String autonomy, String conflictMode) {
            var axes = new DispositionAxes(socialOrient, ruleFollowing,
                riskAppetite, autonomy, conflictMode);
            var view = DescriptorView.of(agentId, axes, List.of(), List.of());
            agentDefaults.put(agentId, CognitiveDerivationEngine.derive(view));
            return this;
        }

        public Builder agentFromDefaults(String agentId, CognitiveDefaults defaults) {
            agentDefaults.put(agentId, defaults);
            return this;
        }

        public Builder withGoalNodeIds(Supplier<Set<String>> supplier) {
            this.goalNodeIds = supplier;
            return this;
        }

        public Builder withConsolidationEnabled(boolean enabled) {
            this.consolidationEnabled = enabled;
            return this;
        }

        public Builder withConsolidationPhase(ConsolidationPhase phase) {
            this.extraPhases.add(phase);
            return this;
        }

        public Builder withCognitionConfig(CognitionConfig config) {
            this.cognitionConfig = config;
            return this;
        }

        public Builder withHabituationState(String agentId,
                io.casehub.neocortex.cognition.appraisal.HabituationState state) {
            this.habituationStates.put(agentId, state);
            return this;
        }

        public Builder withAppraisalResult(String agentId,
                                           io.casehub.neocortex.cognition.appraisal.AppraisalResult result) {
            this.appraisalResults.put(agentId, result);
            return this;
        }


        public CognitionTestHarness build() {
            var store    = mindMapStore != null ? mindMapStore : new InMemoryMindMapStore();
            var memStore = memoryStore != null ? memoryStore : createDefaultMemoryStore();

            var allDefaults = agentDefaults.values().toArray(new CognitiveDefaults[0]);
            var registry    = CognitiveDefaultsRegistry.forTesting(allDefaults);

            var gapDetector     = new InformationGapDetector(store);
            var channelComputer = new CuriosityChannelComputer();
            var curiosityDrive  = new CuriosityDrive(gapDetector, channelComputer, 0.5, goalNodeIds);

            DriveSource competenceStub  = (a, t) -> new DriveIntensity(DriveAxis.COMPETENCE, 0.1, "stub");
            DriveSource affiliationStub = (a, t) -> new DriveIntensity(DriveAxis.AFFILIATION, 0.1, "stub");
            DriveSource autonomyStub    = (a, t) -> new DriveIntensity(DriveAxis.AUTONOMY, 0.1, "stub");

            var moodOrchestrator = new MoodOrchestrator(MoodConfig.defaults());
            var driveOrchestrator = new DriveOrchestrator(
                    curiosityDrive, competenceStub, affiliationStub, autonomyStub,
                    moodOrchestrator, new DriveComposer(), DriveConfig.defaults());

            var mediator = new ConsolidationMediator();
            var config   = cognitionConfig != null ? cognitionConfig : CognitionConfig.all();

            var core = new CognitionCore(
                    moodOrchestrator, driveOrchestrator,
                    null, null, null, null, null, null,
                    null, config,
                    store, null, null, null, null, null,
                    mediator, null);

            core.setDefaultsRegistry(registry);

            io.casehub.neocortex.cognition.appraisal.AppraisalTickParticipant appraisalStub = null;
            if (!habituationStates.isEmpty() || !appraisalResults.isEmpty()) {
                appraisalStub = org.mockito.Mockito.mock(io.casehub.neocortex.cognition.appraisal.AppraisalTickParticipant.class);
                for (var entry : habituationStates.entrySet()) {
                    org.mockito.Mockito.when(appraisalStub.currentHabituation(
                               org.mockito.Mockito.eq(entry.getKey()), org.mockito.Mockito.anyString()))
                                       .thenReturn(entry.getValue());
                }
                for (var entry : appraisalResults.entrySet()) {
                    org.mockito.Mockito.when(appraisalStub.currentResult(
                               org.mockito.Mockito.eq(entry.getKey()), org.mockito.Mockito.anyString()))
                                       .thenReturn(java.util.Optional.of(entry.getValue()));
                }
            }

            var budgetParticipant = new SearchBudgetTickParticipant(
                    driveOrchestrator, appraisalStub, null, moodOrchestrator,
                    registry, config, curiosityDrive);
            core.addParticipant(CognitionPhase.DERIVED, budgetParticipant);

            core.configureSearchBudget(driveOrchestrator, moodOrchestrator,
                                       registry, store, curiosityDrive);
            core.configureCuriosity(curiosityDrive, channelComputer, store, 0.5, 0.5);

            var descriptors = new HashMap<String, AgentDescriptor>();
            for (var agentId : agentDefaults.keySet()) {
                descriptors.put(agentId, AgentDescriptor.builder().agentId(agentId).name(agentId).slot("test").tenancyId("test-tenant").build());
            }

            var phases = new ArrayList<>(extraPhases);
            if (consolidationEnabled) {
                phases.add(CognitionTestAccess.createRecurringThoughtPhase(memStore, store, registry));
            }

            return new CognitionTestHarness(core, store, memStore, curiosityDrive,
                                            moodOrchestrator, budgetParticipant, registry, mediator,
                                            phases, descriptors);
        }

        private static CaseMemoryStore createDefaultMemoryStore() {
            return new InMemoryMemoryStore(new io.casehub.platform.api.identity.CurrentPrincipal() {
                @Override public String actorId() { return "test-actor"; }
                @Override public Set<String> groups() { return Set.of(); }
                @Override public String tenancyId() { return "test-tenant"; }
                @Override public boolean isCrossTenantAdmin() { return false; }
            });
        }
    }
}
