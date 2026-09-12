package io.casehub.neocortex.mindmap.runtime;

import io.casehub.neocortex.mindmap.MindMapNode;
import io.casehub.neocortex.mindmap.MindMapQuery;
import io.casehub.neocortex.mindmap.MindMapStore;
import io.casehub.neocortex.mindmap.NodeRef;
import io.casehub.neocortex.mindmap.NodeUpdate;

import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;

public class NodeRefCleanupProcessor {

    private static final Logger LOG = Logger.getLogger(NodeRefCleanupProcessor.class.getName());

    private final MindMapStore store;

    public NodeRefCleanupProcessor(MindMapStore store) {
        this.store = store;
    }

    public void removeRefs(String scheme, String refId, String tenantId) {
        try {
            var query = MindMapQuery.of(tenantId, 10_000);
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
