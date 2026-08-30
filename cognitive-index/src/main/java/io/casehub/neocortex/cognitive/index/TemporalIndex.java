package io.casehub.neocortex.cognitive.index;

import io.casehub.neocortex.memory.CaseMemoryStore;
import io.casehub.neocortex.memory.Memory;
import io.casehub.neocortex.memory.MemoryDomain;
import io.casehub.neocortex.memory.MemoryOrder;
import io.casehub.neocortex.memory.MemoryQuery;
import io.casehub.neocortex.memory.cbr.CbrCaseMemoryStore;
import io.casehub.neocortex.mindmap.MindMapNode;
import io.casehub.neocortex.mindmap.MindMapQuery;
import io.casehub.neocortex.mindmap.MindMapStore;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Cross-store temporal aggregator — queries MindMap, Memory, and CBR stores
 * on demand and merges results into a single chronological timeline.
 *
 * <p>This is a <strong>stateless derived view</strong>, not a store. It holds
 * no persistent state and re-queries the underlying stores on every call.
 * All source data is owned by the originating stores — this class is pure
 * orchestration. The same architectural pattern as {@code MindMapAnalyzer}
 * and {@code RetrievalAnalyzer}.
 *
 * <p>Stores are injected via {@link Instance} for graceful degradation —
 * missing stores are silently skipped. An app using only MindMap gets
 * temporal indexing for MindMap nodes without pulling in memory backends.
 */
@ApplicationScoped
public class TemporalIndex {

    private static final MemoryDomain EXPERIENCE_DOMAIN = new MemoryDomain("experience");

    private final MindMapStore mindMapStore;
    private final CaseMemoryStore memoryStore;
    private final CbrCaseMemoryStore cbrStore;

    @Inject
    public TemporalIndex(Instance<MindMapStore> mindMapStore,
                         Instance<CaseMemoryStore> memoryStore,
                         Instance<CbrCaseMemoryStore> cbrStore) {
        this.mindMapStore = mindMapStore != null && mindMapStore.isResolvable() ? mindMapStore.get() : null;
        this.memoryStore = memoryStore != null && memoryStore.isResolvable() ? memoryStore.get() : null;
        this.cbrStore = cbrStore != null && cbrStore.isResolvable() ? cbrStore.get() : null;
    }

    TemporalIndex(MindMapStore mindMapStore, CaseMemoryStore memoryStore, CbrCaseMemoryStore cbrStore) {
        this.mindMapStore = mindMapStore;
        this.memoryStore = memoryStore;
        this.cbrStore = cbrStore;
    }

    public List<TemporalEntry> query(TemporalQuery query) {
        List<TemporalEntry> results = new ArrayList<>();

        if (query.sources().contains(TemporalQuery.StoreKind.MINDMAP) && mindMapStore != null) {
            results.addAll(queryMindMap(query));
        }

        if (query.sources().contains(TemporalQuery.StoreKind.MEMORY)
                && memoryStore != null && !query.entityIds().isEmpty()) {
            results.addAll(queryMemory(query));
        }

        Collections.sort(results);
        if (results.size() > query.limit()) {
            results = new ArrayList<>(results.subList(0, query.limit()));
        }
        return results;
    }

    private List<TemporalEntry> queryMindMap(TemporalQuery query) {
        List<TemporalEntry> entries = new ArrayList<>();

        for (String tenantId : query.tenantIds()) {
            MindMapQuery mmQuery;
            if (query.upcoming()) {
                mmQuery = new MindMapQuery(tenantId, null, null, null, null,
                    null, null, false, query.from(), query.to(), null, query.limit());
            } else {
                mmQuery = new MindMapQuery(tenantId, null, null, null, null,
                    null, null, false, null, null, query.from(), query.limit());
            }

            List<MindMapNode> nodes = mindMapStore.search(mmQuery);
            for (MindMapNode node : nodes) {
                Instant timestamp = query.upcoming() ? node.validFrom() : node.updatedAt();
                if (timestamp == null) continue;
                if (query.to() != null && !timestamp.isBefore(query.to())) continue;
                entries.add(new TemporalEntry(timestamp, new TemporalSource.FromMindMap(node),
                    tenantId, node.confidence()));
            }
        }
        return entries;
    }

    private List<TemporalEntry> queryMemory(TemporalQuery query) {
        List<TemporalEntry> entries = new ArrayList<>();

        for (String tenantId : query.tenantIds()) {
            MemoryQuery mq = MemoryQuery.forEntities(List.copyOf(query.entityIds()), EXPERIENCE_DOMAIN, tenantId)
                .withLimit(query.limit())
                .withOrder(MemoryOrder.CHRONOLOGICAL);
            if (query.from() != null) {
                mq = mq.withSince(query.from());
            }

            List<Memory> memories = memoryStore.query(mq);
            for (Memory memory : memories) {
                if (query.to() != null && memory.createdAt() != null
                        && !memory.createdAt().isBefore(query.to())) continue;
                Instant timestamp = memory.createdAt() != null ? memory.createdAt() : Instant.EPOCH;
                entries.add(new TemporalEntry(timestamp, new TemporalSource.FromMemory(memory),
                    tenantId, memory.confidence()));
            }
        }
        return entries;
    }


}
