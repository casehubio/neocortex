package io.casehub.neocortex.memory.seeding.biography;

import io.casehub.neocortex.cognitive.Confidence;
import io.casehub.neocortex.mindmap.EdgeInput;
import io.casehub.neocortex.mindmap.GoalVocabulary;
import io.casehub.neocortex.mindmap.MindMapNode;
import io.casehub.neocortex.mindmap.MindMapStore;
import io.casehub.neocortex.mindmap.NodeInput;
import io.casehub.neocortex.mindmap.SubgraphTypes;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.time.Instant;
import java.util.HashMap;
import java.util.Set;
import java.util.logging.Logger;

@ApplicationScoped
public class GoalHandler implements BiographyHandler {


    private static final Logger LOG = Logger.getLogger(GoalHandler.class.getName());
    private final MindMapStore store;

    @Inject
    public GoalHandler(MindMapStore store) { this.store = store; }

    @Override public Set<String> handledTypes() { return Set.of(BiographyTemplateTypes.GOAL); }

    @Override
    public void handle(BiographyProfile profile, String agentId, String tenantId) {
        String subgraphId = BiographyUtils.ensureSubgraph(store, SubgraphTypes.GOAL, tenantId);

        for (var entry : profile.goals()) {
            MindMapNode existing = store.resolveNode(entry.name(), subgraphId, tenantId);
            if (existing != null) continue;

            var props = new HashMap<String, String>();
            props.put("provenance", "biographical-import");
            props.put("template-ref", "goals.yaml#" + entry.id());
            if (entry.sourceRef() != null) props.put("source-ref", entry.sourceRef());
            if (entry.tier() != null) props.put("tier", entry.tier());
            if (entry.horizon() != null) props.put("horizon", entry.horizon());
            if (entry.status() != null) props.put("status", entry.status());
            if (entry.urgency() != null) props.put("urgency", entry.urgency());
            if (entry.priority() != null) props.put("priority", entry.priority());
            if (entry.feasibility() != null) props.put("feasibility", entry.feasibility());
            if (entry.needTier() != null) props.put("need-tier", entry.needTier());
            if (entry.contentDomain() != null) {
                if (GoalVocabulary.CONTENT_DOMAINS.contains(entry.contentDomain())) {
                    props.put("contentDomain", entry.contentDomain());
                } else {
                    LOG.warning("Rejected invalid contentDomain '" + entry.contentDomain() + "' for goal '" + entry.name() + "'");
                }
            }
            if (entry.contentSubdomain() != null) {
                if (GoalVocabulary.CONTENT_SUBDOMAINS.contains(entry.contentSubdomain())) {
                    props.put("contentSubdomain", entry.contentSubdomain());
                } else {
                    LOG.warning("Rejected invalid contentSubdomain '" + entry.contentSubdomain() + "' for goal '" + entry.name() + "'");
                }
            }
            if (entry.motivationOrigin() != null) {
                if (GoalVocabulary.MOTIVATION_ORIGINS.contains(entry.motivationOrigin())) {
                    props.put("motivationOrigin", entry.motivationOrigin());
                } else {
                    LOG.warning("Rejected invalid motivationOrigin '" + entry.motivationOrigin() + "' for goal '" + entry.name() + "'");
                }
            }
            if (entry.clarity() != null) props.put("clarity", String.valueOf(entry.clarity()));
            if (entry.evidenceBasis() != null) props.put("evidenceBasis", entry.evidenceBasis());
            props.put("recognitionSource", "biographical-import");

            var origin = PlaceHandler.resolveOrigin(entry.confidenceOrigin());
            var input = NodeInput.of(entry.name(), subgraphId)
                .withConfidence(new Confidence(origin, 0.8, Instant.now()))
                .withProvenance("biographical-import")
                .withProperties(props);

            if (entry.pad() != null) {
                input = input.withPad(entry.pad().pleasure(), entry.pad().arousal(), entry.pad().dominance());
            }

            String goalId = store.addNode(input, tenantId);

            if (entry.subGoals() != null) {
                for (var sub : entry.subGoals()) {
                    var subProps = new HashMap<String, String>();
                    subProps.put("provenance", "biographical-import");
                    if (sub.tier() != null) subProps.put("tier", sub.tier());
                    String subGoalId = store.addNode(
                        NodeInput.of(sub.description(), subgraphId)
                            .withConfidence(Confidence.stated(0.7, Instant.now()))
                            .withProvenance("biographical-import")
                            .withProperties(subProps),
                        tenantId);
                    store.addEdge(EdgeInput.of(goalId, subGoalId, "decomposes-into")
                        .withProvenance("biographical-import"), tenantId);
                }
            }

            if (entry.dependencies() != null) {
                for (var dep : entry.dependencies()) {
                    String targetId = AssociationProcessor.resolveAcrossSubgraphs(dep.ref(), store, tenantId);
                    if (targetId != null) {
                        store.addEdge(EdgeInput.of(goalId, targetId, dep.edgeType())
                            .withProvenance("biographical-import"), tenantId);
                    }
                }
            }

            AssociationProcessor.process(entry.associations(), goalId, store, tenantId);
        }
    }
}
