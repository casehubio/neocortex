package io.casehub.neocortex.mindmap.intelligence;

import io.casehub.neocortex.mindmap.MindMapStore;
import io.casehub.neocortex.mindmap.intelligence.consolidation.RetrievalAccessTracker;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.ObservesAsync;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

@ApplicationScoped
public class ExtractionRequestedObserver {

    private static final Logger LOG = Logger.getLogger(
        ExtractionRequestedObserver.class.getName());

    private final MindMapExtractor extractor;
    private final MindMapStore store;
    private final RetrievalAccessTracker accessTracker;

    @Inject
    public ExtractionRequestedObserver(MindMapExtractor extractor,
                                        MindMapStore store,
                                        Instance<RetrievalAccessTracker> accessTracker) {
        this.extractor = extractor;
        this.store = store;
        this.accessTracker = accessTracker.isResolvable() ? accessTracker.get() : null;
    }

    ExtractionRequestedObserver(MindMapExtractor extractor,
                                 MindMapStore store,
                                 RetrievalAccessTracker accessTracker) {
        this.extractor = extractor;
        this.store = store;
        this.accessTracker = accessTracker;
    }

    public void onExtractionRequested(@ObservesAsync ExtractionRequested event) {
        try {
            var result = extractor.extract(
                event.cleanedText(), event.tenantId(), event.recentEntityNames());

            if (accessTracker != null) {
                result.entities().stream()
                    .map(ExtractedEntity::nodeId)
                    .forEach(accessTracker::recordAccess);
            }

            List<String> createdEntityIds = result.entities().stream()
                .filter(ExtractedEntity::created)
                .map(ExtractedEntity::nodeId)
                .toList();

            if (!createdEntityIds.isEmpty()) {
                for (String segmentId : event.segmentNodeIds()) {
                    try {
                        store.supersede(segmentId, createdEntityIds.getFirst(),
                            "llm-extraction", event.tenantId());
                    } catch (Exception e) {
                        LOG.log(Level.FINE, "Could not supersede segment " + segmentId, e);
                    }
                }
            }
        } catch (Exception e) {
            LOG.log(Level.WARNING, "Extraction failed for tenant " + event.tenantId(), e);
        }
    }
}
