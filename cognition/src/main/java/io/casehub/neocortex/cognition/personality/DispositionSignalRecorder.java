package io.casehub.neocortex.cognition.personality;

import io.casehub.eidos.api.AgentRegistry;
import io.casehub.neocortex.cognition.situational.PersonalityStateService;
import io.casehub.neocortex.memory.relationship.RelationshipRecorded;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

public class DispositionSignalRecorder {

    private static final Logger LOG = Logger.getLogger(DispositionSignalRecorder.class.getName());

    private final Map<Class<?>, TraitPressureSource<?>> sources;
    private final PersonalityStateService stateService;
    private final AgentRegistry agentRegistry;
    private final DispositionSignalWriter signalWriter;

    public DispositionSignalRecorder(List<TraitPressureSource<?>> sources,
                                     PersonalityStateService stateService,
                                     AgentRegistry agentRegistry,
                                     DispositionSignalWriter signalWriter) {
        this.sources = buildSourceMap(sources);
        this.stateService = stateService;
        this.agentRegistry = agentRegistry;
        this.signalWriter = signalWriter;
    }

    public void onRelationshipRecorded(RelationshipRecorded event) {
        record(event.event(), event.event().tenantId(), event.event().agentId());
    }

    public void onInteractionAppraised(InteractionAppraised event) {
        record(event, event.tenantId(), event.agentId());
    }

    @SuppressWarnings("unchecked")
    private <E> void record(E ev, String tenantId, String agentId) {
        try {
            var source = (TraitPressureSource<E>) sources.get(ev.getClass());
            if (source == null || !source.relevant(ev)) return;

            var now = java.time.Instant.now();
            var state = stateService.read(tenantId, agentId, now);
            if (state.reflection() != null && state.reflection().disabled()) return;

            var authoredOpt = agentRegistry.findById(agentId, tenantId);
            if (authoredOpt.isEmpty()) {
                LOG.fine(() -> "No descriptor for agent " + agentId + " in tenant " + tenantId);
                return;
            }
            var authored = authoredOpt.get();

            var profile = authored.disposition().dispositionProfile();
            var profileTerms = new java.util.HashSet<String>();
            if (profile != null) {
                for (var dv : profile) profileTerms.add(dv.term());
            }

            var activations = source.translate(ev, authored);
            var terms = new ArrayList<String>();
            for (var a : activations) {
                if (profileTerms.contains(a.functionTerm())) {
                    terms.add(a.functionTerm());
                } else {
                    LOG.fine(() -> "Dropped unknown term: " + a.functionTerm());
                }
            }
            if (!terms.isEmpty()) {
                signalWriter.enqueue(tenantId, agentId, terms);
            }
        } catch (RuntimeException e) {
            LOG.log(Level.WARNING, "Signal recording failed for " + agentId + " in " + tenantId, e);
        }
    }

    private static Map<Class<?>, TraitPressureSource<?>> buildSourceMap(List<TraitPressureSource<?>> sources) {
        var map = new HashMap<Class<?>, TraitPressureSource<?>>();
        for (var source : sources) {
            var prev = map.put(source.eventType(), source);
            if (prev != null) {
                throw new IllegalStateException("Duplicate TraitPressureSource for " + source.eventType().getName());
            }
        }
        return Map.copyOf(map);
    }
}
