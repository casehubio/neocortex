package io.casehub.neocortex.cognition.personality;

import io.casehub.eidos.api.AgentDescriptor;
import io.casehub.eidos.api.AgentDisposition;
import io.casehub.eidos.api.AgentRegistry;
import io.casehub.eidos.api.CapabilityHealth;
import io.casehub.eidos.api.DispositionEvolution;
import io.casehub.eidos.api.DispositionHealth;
import io.casehub.eidos.api.DispositionHealth.DispositionStatus;
import io.casehub.eidos.api.DispositionValue;
import io.casehub.eidos.api.EvolutionType;
import io.casehub.eidos.api.DispositionSignalStore;
import io.casehub.neocortex.cognitive.index.CognitiveDefaults;
import io.casehub.neocortex.cognitive.index.EffectiveCognitiveDefaults;
import io.casehub.neocortex.cognitive.index.LivedComposition;
import io.casehub.neocortex.cognitive.index.WeightedTerm;
import io.casehub.neocortex.cognition.journal.CognitiveEntry;
import io.casehub.neocortex.cognition.journal.CognitiveJournal;
import io.casehub.neocortex.cognition.situational.PersonalityState;
import io.casehub.neocortex.cognition.situational.PersonalityStateService;
import io.casehub.neocortex.cognition.situational.ReflectionLedger;
import io.casehub.neocortex.memory.cbr.CbrRecordStore;
import io.casehub.neocortex.mindmap.inmem.InMemoryMindMapStore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DispositionEvolutionPhaseTest {

    private static final String TENANT = "t1";
    private static final String AGENT = "agent-1";
    private static final Instant NOW = Instant.parse("2026-06-15T12:00:00Z");
    private static final Duration REFLECTION_PERIOD = Duration.ofHours(24);
    private static final List<DispositionValue> TI_NE_PROFILE = List.of(
            new DispositionValue("Ti", 0.35), new DispositionValue("Ne", 0.20));
    private static final Set<String> FACET_NAMES = Set.of("Anxiousness", "Emotional Lability");

    private DispositionHealth health;
    private DispositionEvolution evolution;
    private AgentRegistry agentRegistry;
    private CbrRecordStore cbrStore;
    private PersonalityStateService stateService;
    private DispositionSignalWriter signalWriter;
    private EffectiveCognitiveDefaults effectiveDefaults;
    private List<CognitiveEntry> journalEntries;
    private CognitiveJournal journal;
    private DispositionEvolutionPhase phase;
    private AgentDescriptor descriptor;

    @BeforeEach
    void setUp() {
        health = mock(DispositionHealth.class);
        evolution = mock(DispositionEvolution.class);
        agentRegistry = mock(AgentRegistry.class);
        cbrStore = mock(CbrRecordStore.class);

        var mindMapStore = new InMemoryMindMapStore();
        stateService = new PersonalityStateService(mindMapStore, FACET_NAMES);

        var signalStore = mock(DispositionSignalStore.class);
        signalWriter = new DispositionSignalWriter(signalStore, Runnable::run);

        effectiveDefaults = mock(EffectiveCognitiveDefaults.class);
        when(effectiveDefaults.agentIds(TENANT)).thenReturn(Set.of(AGENT));

        var defaults = CognitiveDefaults.empty(AGENT);
        when(effectiveDefaults.restingFor(eq(TENANT), eq(AGENT))).thenReturn(
                io.casehub.neocortex.cognitive.index.LearningProfile.from(defaults));

        journalEntries = new ArrayList<>();
        journal = journalEntries::add;

        descriptor = AgentDescriptor.builder()
                .agentId(AGENT).name("Test").slot("test").tenancyId(TENANT)
                .disposition(AgentDisposition.builder()
                        .socialOrient("cooperative")
                        .dispositionProfile(TI_NE_PROFILE)
                        .build())
                .build();
        when(agentRegistry.findById(AGENT, TENANT)).thenReturn(Optional.of(descriptor));

        phase = new DispositionEvolutionPhase(
                health, evolution, agentRegistry, cbrStore,
                effectiveDefaults, stateService, signalWriter,
                DispositionEvolutionConfig.defaults(),
                Clock.fixed(NOW, ZoneOffset.UTC));
        phase.setCognitiveJournal(journal);
    }

    private void seedState(Duration agoFromNow) {
        var reflection = ReflectionLedger.initial(NOW.minus(agoFromNow));
        stateService.transition(TENANT, AGENT, NOW.minus(agoFromNow),
                st -> st.withReflection(reflection));
    }

    @Test
    void stableReflection_noSignals_advancesLedger() {
        seedState(Duration.ofHours(25));
        when(health.probe(any(), any()))
                .thenReturn(new DispositionStatus.Aligned(Map.of("Ti", 0.35, "Ne", 0.20)));

        phase.run(TENANT, List.of());

        var state = stateService.read(TENANT, AGENT, NOW);
        assertThat(state.reflection()).isNotNull();
        assertThat(state.reflection().lastReflectionAt())
                .isAfter(NOW.minus(Duration.ofHours(25)));
        var reflected = journalEntries.stream()
                .filter(e -> e instanceof CognitiveEntry.DispositionReflected)
                .map(e -> (CognitiveEntry.DispositionReflected) e)
                .findFirst();
        assertThat(reflected).isPresent();
        assertThat(reflected.get().outcomeType()).isEqualTo("Stable");
    }

    @Test
    void wallClockGate_notDueYet_skipsReflection() {
        seedState(Duration.ofHours(12));
        phase.run(TENANT, List.of());

        verify(health, never()).probe(any(), any());
        assertThat(journalEntries.stream()
                .filter(e -> e instanceof CognitiveEntry.DispositionReflected)
                .count()).isZero();
    }

    @Test
    void evolvedPath_commitsLivedProfile() {
        seedState(Duration.ofHours(25));
        var newProfile = List.of(
                new DispositionValue("Ne", 0.35), new DispositionValue("Ti", 0.20));
        EvolutionType swapType = () -> "DOMINANT_AUXILIARY_SWAP";
        when(health.probe(any(), any()))
                .thenReturn(new DispositionStatus.EvolutionPending(
                        swapType, "Ne", Map.of("Ne", 0.36, "Ti", 0.30)));
        when(evolution.evaluate(any(), any()))
                .thenReturn(new DispositionEvolution.EvolutionResult.Evolved(
                        newProfile, "TI-NE", "NE-TI"));

        phase.run(TENANT, List.of());

        var state = stateService.read(TENANT, AGENT, NOW);
        assertThat(state.livedProfile()).isNotNull();
        assertThat(state.livedProfile().profile()).hasSize(2);
        assertThat(state.livedProfile().profile().get(0).term()).isEqualTo("Ne");
        assertThat(state.reflection().pendingEvolution()).isNull();
    }

    @Test
    void evolvedPath_rateBound_defersTooSoon() {
        seedState(Duration.ofHours(25));
        var reflection = stateService.read(TENANT, AGENT, NOW).reflection()
                .withLastTransitionAt(NOW.minus(Duration.ofDays(10)));
        stateService.transition(TENANT, AGENT, NOW, st -> st.withReflection(reflection));

        var newProfile = List.of(
                new DispositionValue("Ne", 0.35), new DispositionValue("Ti", 0.20));
        EvolutionType swapType = () -> "DOMINANT_AUXILIARY_SWAP";
        when(health.probe(any(), any()))
                .thenReturn(new DispositionStatus.EvolutionPending(
                        swapType, "Ne", Map.of("Ne", 0.36, "Ti", 0.30)));
        when(evolution.evaluate(any(), any()))
                .thenReturn(new DispositionEvolution.EvolutionResult.Evolved(
                        newProfile, "TI-NE", "NE-TI"));

        phase.run(TENANT, List.of());

        var state = stateService.read(TENANT, AGENT, NOW);
        assertThat(state.livedProfile()).isNull();
        var reflected = journalEntries.stream()
                .filter(e -> e instanceof CognitiveEntry.DispositionReflected)
                .map(e -> (CognitiveEntry.DispositionReflected) e)
                .findFirst();
        assertThat(reflected).isPresent();
        assertThat(reflected.get().outcomeType()).isEqualTo("Deferred");
    }

    @Test
    void disabledAgent_rcZero_skipsProbe() {
        seedState(Duration.ofHours(25));
        var disabledDefaults = CognitiveDefaults.empty(AGENT)
                .withCapacityConfig(new io.casehub.neocortex.cognitive.CognitiveCapacityConfig(0.0, 0.75, 0.75));
        when(effectiveDefaults.restingFor(eq(TENANT), eq(AGENT))).thenReturn(
                io.casehub.neocortex.cognitive.index.LearningProfile.from(disabledDefaults));

        phase.run(TENANT, List.of());

        verify(health, never()).probe(any(), any());
        var reflected = journalEntries.stream()
                .filter(e -> e instanceof CognitiveEntry.DispositionReflected)
                .map(e -> (CognitiveEntry.DispositionReflected) e)
                .findFirst();
        assertThat(reflected).isPresent();
        assertThat(reflected.get().outcomeType()).isEqualTo("Disabled");
    }

    @Test
    void dampenedPath_decaysSignals() {
        seedState(Duration.ofHours(25));
        EvolutionType swapType = () -> "DOMINANT_AUXILIARY_SWAP";
        when(health.probe(any(), any()))
                .thenReturn(new DispositionStatus.EvolutionPending(
                        swapType, "Ne", Map.of()));
        when(evolution.evaluate(any(), any()))
                .thenReturn(new DispositionEvolution.EvolutionResult.Dampened(0.2));

        phase.run(TENANT, List.of());

        var reflected = journalEntries.stream()
                .filter(e -> e instanceof CognitiveEntry.DispositionReflected)
                .map(e -> (CognitiveEntry.DispositionReflected) e)
                .findFirst();
        assertThat(reflected).isPresent();
        assertThat(reflected.get().outcomeType()).isEqualTo("Dampened");
    }

    @Test
    void recovery_pendingEvolution_clearsAndCloses() {
        seedState(Duration.ofHours(25));
        var pending = new ReflectionLedger.PendingEvolution(
                "intent-1", "TI-NE", "NE-TI", null,
                NOW.minus(Duration.ofHours(26)), Duration.ofDays(30));
        stateService.transition(TENANT, AGENT, NOW,
                st -> st.withReflection(st.reflection().withPendingEvolution(pending)));

        when(health.probe(any(), any()))
                .thenReturn(new DispositionStatus.Aligned(Map.of()));

        phase.run(TENANT, List.of());

        var state = stateService.read(TENANT, AGENT, NOW);
        assertThat(state.reflection().pendingEvolution()).isNull();
        assertThat(state.reflection().lastTransitionAt()).isNotNull();
    }
}
