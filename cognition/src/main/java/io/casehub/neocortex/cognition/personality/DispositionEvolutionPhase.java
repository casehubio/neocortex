package io.casehub.neocortex.cognition.personality;

import io.casehub.eidos.api.AgentDescriptor;
import io.casehub.eidos.api.AgentRegistry;
import io.casehub.eidos.api.CapabilityHealth;
import io.casehub.eidos.api.DispositionEvolution;
import io.casehub.eidos.api.DispositionHealth;
import io.casehub.eidos.api.DispositionHealth.DispositionStatus;
import io.casehub.neocortex.cognition.journal.CognitiveEntry;
import io.casehub.neocortex.cognition.journal.CognitiveJournal;
import io.casehub.neocortex.cognition.journal.EventRef;
import io.casehub.neocortex.cognition.situational.PersonalityState;
import io.casehub.neocortex.cognition.situational.PersonalityStateService;
import io.casehub.neocortex.cognition.situational.ReflectionLedger;
import io.casehub.neocortex.cognitive.index.EffectiveCognitiveDefaults;
import io.casehub.neocortex.cognitive.index.LivedComposition;
import io.casehub.neocortex.cognitive.index.LivedProfile;
import io.casehub.neocortex.cognitive.index.WeightedTerm;
import io.casehub.neocortex.memory.cbr.CbrRecordStore;
import io.casehub.neocortex.mindmap.AttentionSignal;
import io.casehub.neocortex.mindmap.SignalCategory;
import io.casehub.neocortex.mindmap.intelligence.consolidation.ConsolidationPhase;
import jakarta.annotation.Priority;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;

@ApplicationScoped
@Priority(50)
public class DispositionEvolutionPhase implements ConsolidationPhase {

    private static final Logger                        LOG                = Logger.getLogger(DispositionEvolutionPhase.class.getName());
    private static final CapabilityHealth.ProbeContext REFLECTION_CONTEXT =
            CapabilityHealth.ProbeContext.of("personality-reflection");

    private final DispositionHealth          health;
    private final DispositionEvolution       evolution;
    private final AgentRegistry              agentRegistry;
    private final CbrRecordStore             cbrStore;
    private final EffectiveCognitiveDefaults effectiveDefaults;
    private final PersonalityStateService    stateService;
    private final DispositionSignalWriter    signalWriter;
    private final DispositionEvolutionConfig config;
    private final Clock                      clock;
    private       CognitiveJournal           journal        = CognitiveJournal.NOOP;
    private final List<AttentionSignal>      pendingSignals = new ArrayList<>();

    @Inject
    public DispositionEvolutionPhase(
            DispositionHealth health,
            DispositionEvolution evolution,
            AgentRegistry agentRegistry,
            Instance<CbrRecordStore> cbrStore,
            EffectiveCognitiveDefaults effectiveDefaults,
            PersonalityStateService stateService,
            DispositionSignalWriter signalWriter,
            DispositionEvolutionConfig config,
            Clock clock) {
        this(health, evolution, agentRegistry,
             cbrStore.isResolvable() ? cbrStore.get() : null,
             effectiveDefaults, stateService, signalWriter, config, clock);
    }

    DispositionEvolutionPhase(
            DispositionHealth health,
            DispositionEvolution evolution,
            AgentRegistry agentRegistry,
            CbrRecordStore cbrStore,
            EffectiveCognitiveDefaults effectiveDefaults,
            PersonalityStateService stateService,
            DispositionSignalWriter signalWriter,
            DispositionEvolutionConfig config,
            Clock clock) {
        this.health            = health;
        this.evolution         = evolution;
        this.agentRegistry     = agentRegistry;
        this.cbrStore          = cbrStore;
        this.effectiveDefaults = effectiveDefaults;
        this.stateService      = stateService;
        this.signalWriter      = signalWriter;
        this.config            = config;
        this.clock             = clock;
    }

    @Override
    public String name() {
        return "disposition-evolution";
    }

    @Override
    public void setCognitiveJournal(CognitiveJournal journal) {
        this.journal = journal != null ? journal : CognitiveJournal.NOOP;
    }

    @Override
    public void beginTick() {
        pendingSignals.clear();
    }

    @Override
    public List<AttentionSignal> signals() {
        return List.copyOf(pendingSignals);
    }

    @Override
    public void run(String tenantId, List<String> subgraphPriority) {
        var now       = clock.instant();
        var allAgents = new LinkedHashSet<String>();
        allAgents.addAll(effectiveDefaults.agentIds(tenantId));
        allAgents.addAll(stateService.agentIds(tenantId));
        for (String agentId : allAgents) {
            try {
                reflectAgent(tenantId, agentId, now);
            } catch (RuntimeException e) {
                LOG.log(Level.WARNING, "Disposition evolution failed for " + tenantId + "/" + agentId, e);
            }
        }
    }

    private void reflectAgent(String tenantId, String agentId, Instant now) {
        var s = stateService.read(tenantId, agentId, now);
        var r = s.reflection();
        if (r == null) {
            r = ReflectionLedger.initial(now);
            s = stateService.transition(tenantId, agentId, now, st -> st.withReflection(ReflectionLedger.initial(now)));
            r = s.reflection();
        }

        // R. Recovery
        s = handleRecovery(tenantId, agentId, now, s);
        r = s.reflection();

        // G. Wall-clock gate
        if (now.isBefore(r.lastReflectionAt())) {
            stateService.transition(tenantId, agentId, now, st -> st.withReflection(st.reflection().clampedTo(now)));
            journal.record(new CognitiveEntry.ReflectionClockRegressed(tenantId, agentId,
                                                                       EventRef.consolidation(EventRef.tick(0)), now, r.lastReflectionAt(), now));
            return;
        }
        long n = Duration.between(r.lastReflectionAt(), now).toMillis() / config.reflectionPeriod().toMillis();
        if (n <= 0) {return;}
        if (r.pendingEvolution() != null) {return;}

        // Look up authored descriptor
        var authoredOpt = agentRegistry.findById(agentId, tenantId);
        if (authoredOpt.isEmpty()) {
            advanceLedger(tenantId, agentId, now, n);
            return;
        }
        var authored   = authoredOpt.get();
        var descriptor = LivedDescriptors.compose(authored, s);
        if (authored.disposition() == null || authored.disposition().dispositionProfile().isEmpty()) {
            advanceLedger(tenantId, agentId, now, n);
            return;
        }

        // Drain signals
        signalWriter.drain(tenantId, agentId);

        // 0a. Epoch fingerprint
        var authoredTerms = authored.disposition().dispositionProfile().stream()
                                    .map(dv -> new WeightedTerm(dv.term(), dv.weight()))
                                    .toList();
        var fp = LivedComposition.fingerprint(authoredTerms);
        s = stateService.read(tenantId, agentId, now);
        r = s.reflection();
        if (r.epochFingerprint() == null) {
            s = stateService.transition(tenantId, agentId, now,
                                        st -> st.withReflection(st.reflection().withEpochFingerprint(fp)));
        } else if (!r.epochFingerprint().equals(fp)) {
            signalWriter.clear(tenantId, agentId);
            var oldFp = r.epochFingerprint();
            s = stateService.transition(tenantId, agentId, now,
                                        st -> st.withReflection(st.reflection().withEpochFingerprint(fp)));
            journal.record(new CognitiveEntry.DispositionSignalEpochReset(tenantId, agentId,
                                                                          EventRef.consolidation(EventRef.tick(0)), now, oldFp, fp));
        }
        r = s.reflection();

        // 0a(a). Reauthoring reconciliation (option A)
        var reauthorResult = reconcileReauthoring(tenantId, agentId, now, s, fp);
        s = reauthorResult.state;
        boolean reauthorPending = reauthorResult.pending;
        r = s.reflection();

        // D. Decay
        s = handleDecay(tenantId, agentId, now, s, n);
        r = s.reflection();

        // 0. Enablement check
        var    resting = effectiveDefaults.restingFor(tenantId, agentId);
        var    cc      = resting.capacityConfig();
        double rc      = cc != null ? cc.relationalComplexityOrDefault() : 1.0;
        if (rc == 0.0) {
            boolean wasDisabled = r.disabled();
            var     period      = config.reflectionPeriod();
            stateService.transition(tenantId, agentId, now,
                                    st -> st.withReflection(st.reflection().advancedBy(period.multipliedBy(n)).withDisabled(true)));
            if (!wasDisabled) {
                journal.record(new CognitiveEntry.DispositionEvolutionEnablementChanged(tenantId, agentId,
                                                                                        EventRef.consolidation(EventRef.tick(0)), now, false, rc));
            }
            recordOutcome(tenantId, agentId, now, new ReflectionOutcome.Disabled(rc));
            return;
        }
        boolean  reenabled = r.disabled();
        Duration interval  = Duration.ofMillis((long) (config.minTransitionInterval().toMillis() / rc));

        // 2. Evaluate
        ReflectionOutcome decision;
        try {
            var status = health.probe(descriptor, REFLECTION_CONTEXT);
            decision = switch (status) {
                case DispositionStatus.Aligned a -> new ReflectionOutcome.Stable();
                case DispositionStatus.Drifted d -> new ReflectionOutcome.Drifting(d.driftMagnitude());
                case DispositionStatus.EvolutionPending p -> {
                    var result = evolution.evaluate(descriptor, p);
                    yield switch (result) {
                        case DispositionEvolution.EvolutionResult.Evolved e -> new ReflectionOutcome.Evolved(e.previousTypeLabel(), e.newTypeLabel(),
                                                                                                             e.newProfile(), p.type().name());
                        case DispositionEvolution.EvolutionResult.Dampened d -> new ReflectionOutcome.Dampened(d.decayFactor());
                    };
                }
            };
        } catch (RuntimeException ex) {
            decision = new ReflectionOutcome.Failed("evaluate", ex.getMessage());
        }

        // 3. Commit
        if (decision instanceof ReflectionOutcome.Evolved evolved) {
            if (reauthorPending) {
                decision = new ReflectionOutcome.Deferred(evolved.previousLabel(), evolved.newLabel(),
                                                          evolved.triggerType(),
                                                          s.reflection().reauthor() != null
                                                          ? s.reflection().reauthor().since().plus(config.reflectionPeriod())
                                                          : now.plus(config.reflectionPeriod()));
            } else if (r.lastTransitionAt() != null
                       && Duration.between(r.lastTransitionAt(), now).compareTo(interval) < 0) {
                decision = new ReflectionOutcome.Deferred(evolved.previousLabel(), evolved.newLabel(),
                                                          evolved.triggerType(), r.lastTransitionAt().plus(interval));
            } else {
                commitEvolution(tenantId, agentId, now, s, evolved, authored, interval);
            }
        }
        if (decision instanceof ReflectionOutcome.Dampened dampened) {
            signalWriter.decay(tenantId, agentId, dampened.decayFactor());
        }

        // 4. Record
        var                     period  = config.reflectionPeriod();
        final ReflectionOutcome outcome = decision;
        stateService.transition(tenantId, agentId, now,
                                st -> st.withReflection(st.reflection().advancedBy(period.multipliedBy(n)).withDisabled(false)));
        if (reenabled) {
            journal.record(new CognitiveEntry.DispositionEvolutionEnablementChanged(tenantId, agentId,
                                                                                    EventRef.consolidation(EventRef.tick(0)), now, true, rc));
        }
        recordOutcome(tenantId, agentId, now, outcome);
    }

    private PersonalityState handleRecovery(String tenantId, String agentId, Instant now, PersonalityState s) {
        var r = s.reflection();
        if (r == null || r.pendingEvolution() == null) {return s;}
        signalWriter.clear(tenantId, agentId);
        s = stateService.transition(tenantId, agentId, now, st -> st.withReflection(st.reflection().closeEpoch()));
        return s;
    }

    private PersonalityState handleDecay(String tenantId, String agentId, Instant now,
                                         PersonalityState s, long n) {
        var  r              = s.reflection();
        var  decayedThrough = r.decayedThrough() != null ? r.decayedThrough() : r.lastReflectionAt();
        long k              = n - Duration.between(r.lastReflectionAt(), decayedThrough).toMillis() / config.reflectionPeriod().toMillis();
        if (k <= 0) {return s;}
        var marker = r.lastReflectionAt().plus(config.reflectionPeriod().multipliedBy(n));
        var prev   = decayedThrough;
        s = stateService.transition(tenantId, agentId, now,
                                    st -> st.withReflection(st.reflection().withDecayedThrough(marker)));
        try {
            signalWriter.decay(tenantId, agentId, Math.pow(config.retentionPerReflection(), k));
        } catch (RuntimeException e) {
            stateService.transition(tenantId, agentId, now, st -> {
                if (st.reflection().decayedThrough().equals(marker)) {
                    return st.withReflection(st.reflection().withDecayedThrough(prev));
                }
                return st;
            });
            throw e;
        }
        return stateService.read(tenantId, agentId, now);
    }

    private record ReauthorResult(PersonalityState state, boolean pending) {}

    private ReauthorResult reconcileReauthoring(String tenantId, String agentId, Instant now,
                                                PersonalityState s, String authoredFingerprint) {
        if (s.livedProfile() == null) {return new ReauthorResult(s, false);}
        var livedFp = s.livedProfile().authoredFingerprint();
        var r       = s.reflection();
        if (livedFp.equals(authoredFingerprint)) {
            if (r.reauthor() != null) {
                s = stateService.transition(tenantId, agentId, now,
                                            st -> st.withReflection(st.reflection().withReauthor(null)));
                journal.record(new CognitiveEntry.DispositionReauthorReverted(tenantId, agentId,
                                                                              EventRef.consolidation(EventRef.tick(0)), now));
            }
            return new ReauthorResult(s, false);
        }
        if (r.reauthor() == null) {
            var reauthor = new ReflectionLedger.Reauthor(authoredFingerprint, now, false);
            s = stateService.transition(tenantId, agentId, now,
                                        st -> st.withReflection(st.reflection().withReauthor(reauthor)));
            journal.record(new CognitiveEntry.DispositionReauthored(tenantId, agentId,
                                                                    EventRef.consolidation(EventRef.tick(0)), now));
            return new ReauthorResult(s, true);
        }
        if (!r.reauthor().confirmed()
            && Duration.between(r.reauthor().since(), now).compareTo(config.reflectionPeriod()) >= 0) {
            var confirmedReauthor = new ReflectionLedger.Reauthor(r.reauthor().fingerprint(), r.reauthor().since(), true);
            s = stateService.transition(tenantId, agentId, now,
                                        st -> st.withReflection(st.reflection()
                                                                  .withReauthor(confirmedReauthor)
                                                                  .withLastTransitionAt(now)));
            return new ReauthorResult(s, false);
        }
        return new ReauthorResult(s, !r.reauthor().confirmed());
    }

    private void commitEvolution(String tenantId, String agentId, Instant now,
                                 PersonalityState s, ReflectionOutcome.Evolved evolved,
                                 AgentDescriptor authored, Duration interval) {
        var authoredTerms = authored.disposition().dispositionProfile().stream()
                                    .map(dv -> new WeightedTerm(dv.term(), dv.weight()))
                                    .toList();
        var livedTerms = evolved.newProfile().stream()
                                .map(dv -> new WeightedTerm(dv.term(), dv.weight()))
                                .toList();
        var livedProfile = new LivedProfile(livedTerms, LivedComposition.fingerprint(authoredTerms));
        var intentId     = UUID.randomUUID().toString();
        var pending = new ReflectionLedger.PendingEvolution(
                intentId, evolved.previousLabel(), evolved.newLabel(),
                null, now, interval);

        // Reversal detection: A→B→A pattern
        var r = s.reflection();
        boolean isReversal = r.lastTransitionFromLabel() != null
                             && r.lastTransitionFromLabel().equals(evolved.newLabel());
        int newStreak = isReversal ? r.reversalStreak() + 1 : 0;

        stateService.transition(tenantId, agentId, now,
                                st -> st.withReflection(st.reflection()
                                                          .withPendingEvolution(pending)
                                                          .withReauthor(null)
                                                          .withReversalStreak(newStreak))
                                        .withLivedProfile(livedProfile));
        signalWriter.clear(tenantId, agentId);
        stateService.transition(tenantId, agentId, now,
                                st -> st.withReflection(st.reflection().closeEpoch()));

        if (newStreak >= 2) {
            journal.record(new CognitiveEntry.DispositionReversal(tenantId, agentId,
                                                                  EventRef.consolidation(EventRef.tick(0)), now, newStreak));
        }

        pendingSignals.add(new AttentionSignal(agentId, tenantId, SignalCategory.PERSONALITY_EVOLVED,
                                               null, "disposition-evolution", 0.8,
                                               "Disposition evolved: " + evolved.previousLabel() + " → " + evolved.newLabel()));
    }

    private void advanceLedger(String tenantId, String agentId, Instant now, long n) {
        var period = config.reflectionPeriod();
        stateService.transition(tenantId, agentId, now,
                                st -> st.withReflection(st.reflection() != null
                                                        ? st.reflection().advancedBy(period.multipliedBy(n))
                                                        : ReflectionLedger.initial(now)));
    }

    private void recordOutcome(String tenantId, String agentId, Instant now, ReflectionOutcome outcome) {
        double drift     = outcome instanceof ReflectionOutcome.Drifting d ? d.magnitude() : 0.0;
        String prevLabel = null;
        String newLabel  = null;
        if (outcome instanceof ReflectionOutcome.Evolved e) {
            prevLabel = e.previousLabel();
            newLabel  = e.newLabel();
        } else if (outcome instanceof ReflectionOutcome.Deferred d) {
            prevLabel = d.previousLabel();
            newLabel  = d.newLabel();
        }
        journal.record(new CognitiveEntry.DispositionReflected(tenantId, agentId,
                                                               EventRef.consolidation(EventRef.tick(0)), now,
                                                               outcome.getClass().getSimpleName(), drift, prevLabel, newLabel));
    }
}
