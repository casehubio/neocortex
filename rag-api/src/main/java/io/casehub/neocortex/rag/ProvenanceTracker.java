package io.casehub.neocortex.rag;

import java.time.Instant;
import java.util.List;

public interface ProvenanceTracker {
    String record(String retrievalContext, String actionId, String actionType,
                  List<String> documentIds, String recordedBy);

    List<ProvenanceRecord> forwardLineage(String actionId, String actionType);

    List<ProvenanceRecord> reverseLineage(String documentId);

    ProvenanceStats stats(String retrievalContext);

    int purgeOlderThan(Instant cutoff);
}
