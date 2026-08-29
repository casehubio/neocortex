package io.casehub.neocortex.memory.relationship.runtime;

import io.casehub.neocortex.memory.CaseMemoryStore;
import io.casehub.neocortex.memory.experience.Action;
import io.casehub.neocortex.memory.experience.ExperienceAttributeKeys;
import io.casehub.neocortex.memory.experience.ExperienceEvent;
import io.casehub.neocortex.memory.experience.ExperienceRecorded;
import io.casehub.neocortex.memory.experience.Observation;
import io.casehub.neocortex.memory.experience.Outcome;
import io.casehub.neocortex.memory.relationship.QualitySignal;
import io.casehub.neocortex.memory.relationship.RelationshipEvent;
import io.casehub.neocortex.memory.relationship.RelationshipEvents;
import io.casehub.neocortex.memory.relationship.RelationshipRecorded;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Event;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;
import org.jboss.logging.Logger;

import java.util.Map;

@ApplicationScoped
public class RelationshipObserver {

    private static final Logger LOG = Logger.getLogger(RelationshipObserver.class);

    private final CaseMemoryStore store;
    private final Event<RelationshipRecorded> recorded;

    @Inject
    RelationshipObserver(CaseMemoryStore store, Event<RelationshipRecorded> recorded) {
        this.store = store;
        this.recorded = recorded;
    }

    public void onExperienceRecorded(@Observes ExperienceRecorded event) {
        var exp = event.event();
        String otherAgent = exp.metadata().get(ExperienceAttributeKeys.TARGET_AGENT);
        if (otherAgent == null || otherAgent.isBlank()) return;
        if (otherAgent.equals(exp.agentId())) return;

        var relEvent = new RelationshipEvent(
                exp.agentId(), otherAgent, exp.tenantId(), exp.caseId(),
                exp.turnId(), sourceEventType(exp), QualitySignal.NEUTRAL,
                exp.description(), exp.confidence(), Map.of());

        try {
            var input = RelationshipEvents.toMemoryInput(relEvent);
            var memoryId = store.store(input);
            recorded.fire(new RelationshipRecorded(relEvent, memoryId));
        } catch (SecurityException e) {
            throw e;
        } catch (Exception e) {
            LOG.warnf(e, "Relationship memory store failed for agent=%s other=%s tenant=%s",
                exp.agentId(), otherAgent, exp.tenantId());
        }
    }

    private static String sourceEventType(ExperienceEvent exp) {
        return switch (exp) {
            case Observation o -> "observation";
            case Action a      -> "action";
            case Outcome o     -> "outcome";
        };
    }
}
