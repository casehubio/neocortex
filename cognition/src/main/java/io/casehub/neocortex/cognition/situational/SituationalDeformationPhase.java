package io.casehub.neocortex.cognition.situational;

import io.casehub.neocortex.cognition.journal.CognitiveEntry;
import io.casehub.neocortex.cognition.journal.CognitiveJournal;
import io.casehub.neocortex.cognition.journal.EventRef;
import io.casehub.neocortex.cognitive.index.EffectiveCognitiveDefaults;
import io.casehub.neocortex.cognitive.index.PersonalityStateSnapshot;
import io.casehub.neocortex.mindmap.intelligence.consolidation.ConsolidationPhase;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;

public class SituationalDeformationPhase implements ConsolidationPhase {

    private static final double EPSILON_PUBLISH = 0.005;

    private final SituationalStateService stateService;
    private final PersonalityStateIntegrator integrator;
    private final EffectiveCognitiveDefaults effectiveDefaults;
    private volatile CognitiveJournal journal;
    private final Clock clock;

    public SituationalDeformationPhase(
            SituationalStateService stateService,
            PersonalityStateIntegrator integrator,
            EffectiveCognitiveDefaults effectiveDefaults,
            CognitiveJournal journal,
            Clock clock) {
        this.stateService = stateService;
        this.integrator = integrator;
        this.effectiveDefaults = effectiveDefaults;
        this.journal = journal;
        this.clock = clock;
    }

    @Override
    public String name() {
        return "situational-deformation";
    }

    public void setCognitiveJournal(CognitiveJournal journal) {
        this.journal = journal;
    }

    @Override
    public void run(String tenantId, List<String> subgraphPriority) {
        var now = clock.instant();

        for (var agentId : stateService.cachedAgentIds(tenantId)) {
            var state = stateService.currentState(tenantId, agentId);
            if (state == null) continue;

            var before = state.cursorTime();
            state = integrator.advance(state, now);
            var after = state.cursorTime();

            int hoursAdvanced = (int) Duration.between(before, after).toHours();
            int facetsWithDrift = countFacetsWithDrift(state);

            if (facetsWithDrift > 0) {
                var snapshot = toSnapshot(state);
                effectiveDefaults.updateState(tenantId, agentId, snapshot,
                        "consolidation-deformation");
            }

            stateService.persist(tenantId, agentId, state);

            journal.record(new CognitiveEntry.SituationalDeformationConsolidated(
                    tenantId, agentId, EventRef.consolidation(EventRef.event("situational-deformation")),
                    now, hoursAdvanced, facetsWithDrift));
        }
    }

    private PersonalityStateSnapshot toSnapshot(PersonalityState state) {
        return SituationalStateService.toSnapshot(state);
    }

    private int countFacetsWithDrift(PersonalityState state) {
        int count = 0;
        for (var fs : state.facets().values()) {
            if (Math.abs(fs.homeOffset()) >= EPSILON_PUBLISH || Math.abs(fs.displacement()) >= EPSILON_PUBLISH) {
                count++;
            }
        }
        return count;
    }
}
