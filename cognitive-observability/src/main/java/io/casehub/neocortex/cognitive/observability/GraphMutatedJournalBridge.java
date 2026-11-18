package io.casehub.neocortex.cognitive.observability;

import io.casehub.neocortex.cognition.journal.CognitiveEntry;
import io.casehub.neocortex.cognition.journal.CognitiveJournal;
import io.casehub.neocortex.cognition.journal.EventRef;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;

@ApplicationScoped
public class GraphMutatedJournalBridge {

    private static final java.util.logging.Logger LOG =
            java.util.logging.Logger.getLogger(GraphMutatedJournalBridge.class.getName());

    private final Instance<CognitiveJournal> journal;

    @Inject
    public GraphMutatedJournalBridge(Instance<CognitiveJournal> journal) {
        this.journal = journal;
    }

    public void onGraphMutation(@Observes GraphMutationRecorded event) {
        if (event.agentId() == null) {return;}
        if (journal.isUnsatisfied()) {return;}

        try {
            journal.get().record(new CognitiveEntry.GraphMutated(
                    event.tenantId(),
                    event.agentId(),
                    deriveEventRef(event.mutation().source()),
                    event.timestamp(),
                    event.mutation()));
        } catch (Exception e) {
            LOG.log(java.util.logging.Level.WARNING,
                    "Failed to record GraphMutated journal entry for tenant {0}: {1}",
                    new Object[]{event.tenantId(), e.getMessage()});
        }
    }

    private static EventRef deriveEventRef(String source) {
        if (source != null && source.startsWith("consolidation:")) {
            return EventRef.consolidation(EventRef.event(source.substring("consolidation:".length())));
        }
        return EventRef.event(source != null ? source : "unknown");
    }
}
