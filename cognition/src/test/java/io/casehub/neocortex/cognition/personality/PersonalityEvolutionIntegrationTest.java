package io.casehub.neocortex.cognition.personality;

import io.casehub.eidos.api.AgentDescriptor;
import io.casehub.eidos.api.AgentDisposition;
import io.casehub.eidos.api.AgentRegistry;
import io.casehub.eidos.api.DispositionEvolution;
import io.casehub.eidos.api.DispositionHealth;
import io.casehub.eidos.api.DispositionHealth.DispositionStatus;
import io.casehub.eidos.api.DispositionSignalStore;
import io.casehub.eidos.api.DispositionValue;
import io.casehub.eidos.api.EvolutionType;
import io.casehub.neocortex.cognitive.CognitiveCapacityConfig;
import io.casehub.neocortex.cognitive.index.CognitiveDefaults;
import io.casehub.neocortex.cognitive.index.EffectiveCognitiveDefaults;
import io.casehub.neocortex.cognitive.index.LearningProfile;
import io.casehub.neocortex.cognition.journal.CognitiveEntry;
import io.casehub.neocortex.cognition.journal.CognitiveJournal;
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
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class PersonalityEvolutionIntegrationTest {

    private static final String TENANT = "t1";
    private static final String AGENT = "agent-1";
    private static final Instant START = Instant.parse("2026-01-01T00:00:00Z");
    private static final Duration PERIOD = Duration.ofHours(24);
    private static final List<DispositionValue> TI_NE = List.of(
            new DispositionValue("Ti", 0.35), new DispositionValue("Ne", 0.20));
    private static final List<DispositionValue> NE_TI = List.of(
            new DispositionValue("Ne", 0.35), new DispositionValue("Ti", 0.20));

    private PersonalityStateService stateService;
    private DispositionSignalWriter signalWriter;
    private DispositionHealth health;
    private DispositionEvolution evolution;
    private AgentRegistry registry;
    private List<CognitiveEntry> journal;

    @BeforeEach
    void setUp() {
        var store = new InMemoryMindMapStore();
        stateService = new PersonalityStateService(store, Set.of("Anxiousness"));
        var signalStore = mock(DispositionSignalStore.class);
        signalWriter = new DispositionSignalWriter(signalStore, Runnable::run);
        health = mock(DispositionHealth.class);
        evolution = mock(DispositionEvolution.class);
        registry = mock(AgentRegistry.class);
        journal = new ArrayList<>();
    }

    private DispositionEvolutionPhase phaseAt(Instant now) {
        var defaults = mock(EffectiveCognitiveDefaults.class);
        when(defaults.agentIds(TENANT)).thenReturn(Set.of(AGENT));
        when(defaults.restingFor(TENANT, AGENT)).thenReturn(
                LearningProfile.from(CognitiveDefaults.empty(AGENT)));
        var phase = new DispositionEvolutionPhase(
                health, evolution, registry, mock(CbrRecordStore.class),
                defaults, stateService, signalWriter,
                DispositionEvolutionConfig.defaults(),
                Clock.fixed(now, ZoneOffset.UTC));
        phase.setCognitiveJournal(journal::add);
        return phase;
    }

    private AgentDescriptor descriptor(List<DispositionValue> profile) {
        when(registry.findById(AGENT, TENANT)).thenReturn(Optional.of(
                AgentDescriptor.builder()
                        .agentId(AGENT).name("Test").slot("test").tenancyId(TENANT)
                        .disposition(AgentDisposition.builder()
                                .socialOrient("cooperative")
                                .dispositionProfile(profile)
                                .build())
                        .build()));
        return registry.findById(AGENT, TENANT).orElseThrow();
    }

    @Test
    void fullPipeline_stableToEvolvedToLivedProfile() {
        stateService.transition(TENANT, AGENT, START,
                st -> st.withReflection(ReflectionLedger.initial(START)));

        // Period 1: stable — no signals, aligned
        var t1 = START.plus(Duration.ofHours(25));
        descriptor(TI_NE);
        when(health.probe(any(), any())).thenReturn(
                new DispositionStatus.Aligned(Map.of("Ti", 0.35, "Ne", 0.20)));
        phaseAt(t1).run(TENANT, List.of());

        var s1 = stateService.read(TENANT, AGENT, t1);
        assertThat(s1.livedProfile()).isNull();
        assertThat(journal.stream().filter(e -> e instanceof CognitiveEntry.DispositionReflected)
                .map(e -> ((CognitiveEntry.DispositionReflected) e).outcomeType())
                .toList()).containsExactly("Stable");

        // Period 2: evolution — signals accumulated, Eidos says evolve
        journal.clear();
        var t2 = t1.plus(Duration.ofHours(25));
        EvolutionType swap = () -> "DOMINANT_AUXILIARY_SWAP";
        when(health.probe(any(), any())).thenReturn(
                new DispositionStatus.EvolutionPending(swap, "Ne", Map.of()));
        when(evolution.evaluate(any(), any())).thenReturn(
                new DispositionEvolution.EvolutionResult.Evolved(NE_TI, "TI-NE", "NE-TI"));
        phaseAt(t2).run(TENANT, List.of());

        var s2 = stateService.read(TENANT, AGENT, t2);
        assertThat(s2.livedProfile()).isNotNull();
        assertThat(s2.livedProfile().profile().getFirst().term()).isEqualTo("Ne");
        assertThat(s2.reflection().pendingEvolution()).isNull();
        assertThat(s2.reflection().lastTransitionAt()).isNotNull();
    }

    @Test
    void rateBound_preventsTooFrequentTransitions() {
        stateService.transition(TENANT, AGENT, START,
                st -> st.withReflection(ReflectionLedger.initial(START)));

        // First evolution at t1
        var t1 = START.plus(Duration.ofHours(25));
        descriptor(TI_NE);
        EvolutionType swap = () -> "DOMINANT_AUXILIARY_SWAP";
        when(health.probe(any(), any())).thenReturn(
                new DispositionStatus.EvolutionPending(swap, "Ne", Map.of()));
        when(evolution.evaluate(any(), any())).thenReturn(
                new DispositionEvolution.EvolutionResult.Evolved(NE_TI, "TI-NE", "NE-TI"));
        phaseAt(t1).run(TENANT, List.of());
        assertThat(stateService.read(TENANT, AGENT, t1).livedProfile()).isNotNull();

        // Second evolution attempt at t1 + 2 days (< 30 day interval) → deferred
        journal.clear();
        var t2 = t1.plus(Duration.ofDays(2));
        when(health.probe(any(), any())).thenReturn(
                new DispositionStatus.EvolutionPending(swap, "Ti", Map.of()));
        when(evolution.evaluate(any(), any())).thenReturn(
                new DispositionEvolution.EvolutionResult.Evolved(TI_NE, "NE-TI", "TI-NE"));
        phaseAt(t2).run(TENANT, List.of());

        var reflected = journal.stream()
                .filter(e -> e instanceof CognitiveEntry.DispositionReflected)
                .map(e -> (CognitiveEntry.DispositionReflected) e)
                .findFirst().orElseThrow();
        assertThat(reflected.outcomeType()).isEqualTo("Deferred");
    }

    @Test
    void disabledAgent_neverProbes() {
        stateService.transition(TENANT, AGENT, START,
                st -> st.withReflection(ReflectionLedger.initial(START)));

        var t1 = START.plus(Duration.ofHours(25));
        descriptor(TI_NE);

        var defaults = mock(EffectiveCognitiveDefaults.class);
        when(defaults.agentIds(TENANT)).thenReturn(Set.of(AGENT));
        var disabledDefaults = CognitiveDefaults.empty(AGENT)
                .withCapacityConfig(new CognitiveCapacityConfig(0.0, 0.75, 0.75));
        when(defaults.restingFor(TENANT, AGENT)).thenReturn(
                LearningProfile.from(disabledDefaults));

        var phase = new DispositionEvolutionPhase(
                health, evolution, registry, mock(CbrRecordStore.class),
                defaults, stateService, signalWriter,
                DispositionEvolutionConfig.defaults(),
                Clock.fixed(t1, ZoneOffset.UTC));
        phase.setCognitiveJournal(journal::add);
        phase.run(TENANT, List.of());

        var reflected = journal.stream()
                .filter(e -> e instanceof CognitiveEntry.DispositionReflected)
                .map(e -> (CognitiveEntry.DispositionReflected) e)
                .findFirst().orElseThrow();
        assertThat(reflected.outcomeType()).isEqualTo("Disabled");
    }
}
