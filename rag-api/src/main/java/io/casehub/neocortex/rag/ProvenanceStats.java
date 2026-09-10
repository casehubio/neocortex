package io.casehub.neocortex.rag;

import java.util.List;

public record ProvenanceStats(
    long totalRecords,
    long uniqueDocuments,
    long uniqueActions,
    List<DocumentRefCount> topReferenced,
    long unreferencedCount
) {
    public record DocumentRefCount(String documentId, long count) {}

    public ProvenanceStats {
        topReferenced = List.copyOf(topReferenced);
    }

    public static final ProvenanceStats EMPTY = new ProvenanceStats(0, 0, 0, List.of(), 0);
}
