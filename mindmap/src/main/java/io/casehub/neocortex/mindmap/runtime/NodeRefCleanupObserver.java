package io.casehub.neocortex.mindmap.runtime;

import io.casehub.neocortex.memory.MemoryEntityErased;
import io.casehub.neocortex.memory.cbr.CbrCasesErased;
import io.casehub.neocortex.mindmap.MindMapNode;
import io.casehub.neocortex.mindmap.MindMapQuery;
import io.casehub.neocortex.mindmap.MindMapStore;
import io.casehub.neocortex.mindmap.NodeRef;
import io.casehub.neocortex.mindmap.NodeUpdate;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;

import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;

@ApplicationScoped
public class NodeRefCleanupObserver {

    private static final Logger LOG = Logger.getLogger(NodeRefCleanupObserver.class.getName());

    private final MindMapStore store;

    @Inject
    public NodeRefCleanupObserver(MindMapStore store) {
        this.store = store;
    }

    public void onMemoryEntityErased(@Observes MemoryEntityErased.ByEntity event) {
        removeRefs("memory", event.subject().id(), event.tenantId());
    }

    public void onCbrCasesErased(@Observes CbrCasesErased.ByEntity event) {
        removeRefs("cbr", event.subject().id(), event.tenantId());
    }

    private void removeRefs(String scheme, String refId, String tenantId) {
        try {
            var query = new MindMapQuery(tenantId, null, null, null, null,
                                         null, null, false, null, null, null, 10_000);
            for (MindMapNode node : store.search(query)) {
                Set<NodeRef> toRemove = node.refs().stream()
                    .filter(r -> r.scheme().equals(scheme) && r.id().equals(refId))
                    .collect(Collectors.toSet());
                if (!toRemove.isEmpty()) {
                    store.updateNode(node.id(),
                        new NodeUpdate(null, null,
                            null, null, null, toRemove,
                            null, null, null, null, null, null, null),
                        tenantId);
                }
            }
        } catch (Exception e) {
            LOG.log(Level.WARNING, "NodeRef cleanup failed for " + scheme + ":" + refId, e);
        }
    }
}
