package io.casehub.neocortex.memory.seeding.biography;

import io.casehub.neocortex.mindmap.MindMapStore;
import io.casehub.neocortex.mindmap.NodeUpdate;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.util.HashMap;
import java.util.Set;

@ApplicationScoped
public class DispositionProfileHandler implements BiographyHandler {

    private final MindMapStore store;

    @Inject
    public DispositionProfileHandler(MindMapStore store) { this.store = store; }

    DispositionProfileHandler(MindMapStore store, @SuppressWarnings("unused") boolean testMarker) {
        this.store = store;
    }

    @Override public Set<String> handledTypes() { return Set.of(BiographyTemplateTypes.DISPOSITION_PROFILE); }

    @Override
    public void handle(BiographyProfile profile, String agentId, String tenantId) {
        for (var entry : profile.dispositionProfiles()) {
            String nodeId = AssociationProcessor.resolveAcrossSubgraphs(entry.entityRef(), store, tenantId);
            if (nodeId == null) continue;

            var props = new HashMap<String, String>();
            if (entry.socialOrient() != null) props.put("disposition-socialOrient", entry.socialOrient());
            if (entry.ruleFollowing() != null) props.put("disposition-ruleFollowing", entry.ruleFollowing());
            if (entry.riskAppetite() != null) props.put("disposition-riskAppetite", entry.riskAppetite());
            if (entry.autonomy() != null) props.put("disposition-autonomy", entry.autonomy());
            if (entry.conflictMode() != null) props.put("disposition-conflictMode", entry.conflictMode());
            props.put("provenance", "biographical-import");

            if (props.size() > 1) { // more than just "provenance"
                store.updateNode(nodeId, NodeUpdate.empty().withPropertiesToSet(props), tenantId);
            }
        }
    }
}
