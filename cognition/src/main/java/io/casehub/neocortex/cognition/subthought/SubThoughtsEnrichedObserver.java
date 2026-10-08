package io.casehub.neocortex.cognition.subthought;

import io.casehub.neocortex.mindmap.intelligence.SubThoughtsEnriched;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;

@ApplicationScoped
public class SubThoughtsEnrichedObserver {

    @Inject
    SubThoughtTickParticipant participant;

    void onSubThoughtsEnriched(@Observes SubThoughtsEnriched event) {
        participant.handleEnriched(event);
    }
}
