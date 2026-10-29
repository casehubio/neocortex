package io.casehub.neocortex.cognition.relationship;

import io.casehub.neocortex.cognitive.index.CognitiveDefaultsRegistry;
import io.casehub.neocortex.cognitive.index.ConflictInterpretation;
import io.casehub.neocortex.cognitive.index.PersonalityFacet;
import io.casehub.neocortex.cognitive.index.RelationalSchema;
import io.casehub.neocortex.memory.CaseMemoryStore;
import io.casehub.neocortex.memory.relationship.RelationshipQuery;
import io.casehub.neocortex.mindmap.MindMapNode;
import io.casehub.neocortex.mindmap.MindMapStore;
import io.casehub.neocortex.mindmap.NodeUpdate;
import io.casehub.neocortex.mindmap.OverlayRef;
import io.casehub.neocortex.mindmap.intelligence.consolidation.ConsolidationPhase;
import jakarta.annotation.Priority;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jspecify.annotations.Nullable;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

@ApplicationScoped
@Priority(19)
public class RelationalSchemaEvolutionPhase implements ConsolidationPhase {

    private static final Logger LOG = Logger.getLogger(RelationalSchemaEvolutionPhase.class.getName());
    private static final double POSITIVE_THRESHOLD = 0.1;
    private static final double NEGATIVE_THRESHOLD = -0.1;

    private final MindMapStore mindMapStore;
    private final CaseMemoryStore memoryStore;
    private final @Nullable CognitiveDefaultsRegistry defaultsRegistry;
    private final String peopleSubgraphName;

    @Inject
    public RelationalSchemaEvolutionPhase(
            MindMapStore mindMapStore,
            CaseMemoryStore memoryStore,
            Instance<CognitiveDefaultsRegistry> defaultsRegistryInstance,
            @ConfigProperty(name = "casehub.cognition.relationship.subgraph",
                            defaultValue = "person") String peopleSubgraphName) {
        this(mindMapStore, memoryStore,
             defaultsRegistryInstance.isResolvable() ? defaultsRegistryInstance.get() : null,
             peopleSubgraphName);
    }

    RelationalSchemaEvolutionPhase(
            MindMapStore mindMapStore,
            CaseMemoryStore memoryStore,
            @Nullable CognitiveDefaultsRegistry defaultsRegistry,
            String peopleSubgraphName) {
        this.mindMapStore = mindMapStore;
        this.memoryStore = memoryStore;
        this.defaultsRegistry = defaultsRegistry;
        this.peopleSubgraphName = peopleSubgraphName;
    }

    @Override
    public String name() {
        return "relational-schema-evolution";
    }

    @Override
    public void run(String tenantId, List<String> subgraphPriority) {
        var subgraphs = mindMapStore.listSubgraphs(tenantId);
        var peopleSg = subgraphs.stream()
            .filter(sg -> peopleSubgraphName.equals(sg.name()))
            .findFirst();

        if (peopleSg.isEmpty()) { return; }

        var nodes = mindMapStore.nodesIn(peopleSg.get().id(), tenantId);

        var sharedNodes = new HashMap<String, MindMapNode>();
        for (var node : nodes) {
            if (!node.traits().contains("overlay")) {
                node.property("agentId").ifPresent(aid -> sharedNodes.put(node.id(), node));
            }
        }

        for (var overlay : nodes) {
            if (!overlay.traits().contains("overlay")) continue;
            var observerId = overlay.property(OverlayRef.AGENT_ID).orElse(null);
            if (observerId == null) continue;

            var targetNodeId = OverlayRef.sharedNodeId(overlay).orElse(null);
            if (targetNodeId == null) continue;
            var sharedNode = sharedNodes.get(targetNodeId);
            if (sharedNode == null) continue;
            var targetAgentId = sharedNode.property("agentId").orElse(null);
            if (targetAgentId == null) continue;

            updateSchema(overlay, observerId, targetAgentId, tenantId);
        }
    }

    private void updateSchema(MindMapNode overlay, String observerId,
                               String targetAgentId, String tenantId) {
        var memories = memoryStore.query(
            RelationshipQuery.forPair(observerId, targetAgentId, tenantId)
                .withLimit(500));

        int positive = 0, negative = 0, neutral = 0;
        for (var memory : memories) {
            Double pleasure = memory.pleasure();
            if (pleasure != null && pleasure > POSITIVE_THRESHOLD) positive++;
            else if (pleasure != null && pleasure < NEGATIVE_THRESHOLD) negative++;
            else neutral++;
        }

        int total = positive + negative + neutral;
        if (total == 0) { return; }

        var existingSchema = RelationalSchema.fromProperties(overlay.properties());
        double currentTrust = existingSchema != null ? existingSchema.trust() : 0.0;
        double currentIntimacy = existingSchema != null ? existingSchema.intimacy() : 0.0;
        String currentRole = existingSchema != null ? existingSchema.role() : "stranger";
        double currentUtility = existingSchema != null ? existingSchema.utility() : 0.0;
        String currentDescription = existingSchema != null ? existingSchema.description() : null;

        double trustFormationRate = 0.5;
        var conflictMode = ConflictInterpretation.NEUTRAL;
        double callousness = 0.0;
        double avoidance = 0.0;
        double anxiety = 0.0;

        if (defaultsRegistry != null) {
            var defaults = defaultsRegistry.forAgentOrDefaults(observerId);
            if (defaults.socialCognition() != null) {
                trustFormationRate = defaults.socialCognition().trustFormationRate();
                conflictMode = defaults.socialCognition().conflictInterpretation();
            }
            if (defaults.personalityProfile() != null) {
                if (defaults.personalityProfile().attachment() != null) {
                    avoidance = defaults.personalityProfile().attachment().avoidance();
                    anxiety = defaults.personalityProfile().attachment().anxiety();
                }
                callousness = facetScore(defaults.personalityProfile().facets(),
                                          PersonalityFacet.CALLOUSNESS);
            }
        }

        double valence = (double) (positive - negative) / total;
        valence = clamp(valence, -1.0, 1.0);

        double conflictMultiplier = switch (conflictMode) {
            case REPAIR -> 0.6;
            case INFORMATION -> 0.9;
            case NEUTRAL -> 1.0;
            case DISENGAGE -> 1.4;
        };

        double trustDelta;
        if (valence >= 0) {
            trustDelta = trustFormationRate * valence * (1.0 - callousness);
        } else {
            trustDelta = trustFormationRate * valence * conflictMultiplier;
        }

        double trustCeiling = 1.0 - avoidance;
        double newTrust = clamp(currentTrust + trustDelta, -1.0, trustCeiling);

        double intimacyDelta = trustFormationRate * Math.max(0, valence) * (1.0 - avoidance);
        intimacyDelta *= (1.0 + 0.5 * anxiety);
        double newIntimacy = clamp(currentIntimacy + intimacyDelta, 0.0, 1.0);

        var newSchema = new RelationalSchema(
            targetAgentId, currentRole, newTrust, newIntimacy, currentUtility, currentDescription);

        mindMapStore.updateNode(overlay.id(),
            NodeUpdate.empty().withPropertiesToSet(newSchema.toProperties()), tenantId);
    }

    private static double facetScore(List<PersonalityFacet> facets, String name) {
        if (facets == null) return 0.0;
        return facets.stream()
            .filter(f -> name.equals(f.facet()))
            .findFirst()
            .map(PersonalityFacet::score)
            .orElse(0.0);
    }

    private static double clamp(double v, double min, double max) {
        return Math.max(min, Math.min(max, v));
    }
}
