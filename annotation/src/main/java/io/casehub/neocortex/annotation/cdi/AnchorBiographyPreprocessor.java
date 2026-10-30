package io.casehub.neocortex.annotation.cdi;

import io.casehub.neocortex.annotation.AnchorAnnotationParser;
import io.casehub.neocortex.annotation.AnchorParseResult;
import io.casehub.neocortex.annotation.AnnotationMethod;
import io.casehub.neocortex.annotation.EntityAnnotation;
import io.casehub.neocortex.annotation.EntityAnnotationIndex;
import io.casehub.neocortex.annotation.ParsedAnchor;
import io.casehub.neocortex.memory.seeding.biography.BeliefEntry;
import io.casehub.neocortex.memory.seeding.biography.BiographyProfile;
import io.casehub.neocortex.memory.seeding.biography.BiographyProfilePreprocessor;
import io.casehub.neocortex.memory.seeding.biography.LifeEventEntry;
import io.casehub.neocortex.mindmap.MindMapNode;
import io.casehub.neocortex.mindmap.MindMapQuery;
import io.casehub.neocortex.mindmap.MindMapStore;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;

@ApplicationScoped
public class AnchorBiographyPreprocessor implements BiographyProfilePreprocessor {

    private static final Logger LOG = Logger.getLogger(AnchorBiographyPreprocessor.class.getName());

    private final EntityAnnotationIndex annotationIndex;
    private final MindMapStore mindMapStore;
    private final AnchorAnnotationParser parser = new AnchorAnnotationParser();
    private final List<PendingAnchor> pendingAnchors = new ArrayList<>();

    @Inject
    public AnchorBiographyPreprocessor(EntityAnnotationIndex annotationIndex,
                                        MindMapStore mindMapStore) {
        this.annotationIndex = annotationIndex;
        this.mindMapStore = mindMapStore;
    }

    AnchorBiographyPreprocessor(EntityAnnotationIndex annotationIndex, MindMapStore mindMapStore,
                                 AnchorAnnotationParser parser) {
        this.annotationIndex = annotationIndex;
        this.mindMapStore = mindMapStore;
    }

    @Override
    public BiographyProfile preprocess(BiographyProfile profile, String agentId, String tenantId) {
        pendingAnchors.clear();

        List<LifeEventEntry> cleanedLifeEvents = profile.lifeEvents().stream()
            .map(entry -> processLifeEvent(entry, tenantId))
            .toList();

        List<BeliefEntry> cleanedBeliefs = profile.beliefs().stream()
            .map(entry -> processBelief(entry, tenantId))
            .toList();

        return new BiographyProfile(
            profile.agentId(), profile.tenantId(),
            profile.culturalContexts(), profile.formativeExperiences(),
            cleanedLifeEvents, profile.places(), profile.activities(),
            profile.projects(), profile.organisations(), profile.relationships(),
            profile.goals(), cleanedBeliefs, profile.entityEnrichments(),
            profile.dispositionProfiles(), profile.personalityProfiles(),
            profile.currentStates());
    }

    @Override
    public void afterImport(String agentId, String tenantId) {
        if (pendingAnchors.isEmpty()) return;

        Map<String, String> aliasToNodeId = resolveAliases(tenantId);

        List<EntityAnnotation> annotations = new ArrayList<>();
        Instant now = Instant.now();

        for (PendingAnchor pa : pendingAnchors) {
            String nodeId = aliasToNodeId.get(pa.alias());
            if (nodeId == null) {
                LOG.log(Level.FINE, "Unresolved alias ''{0}'' in entry {1}", new Object[]{pa.alias(), pa.sourceId()});
                continue;
            }

            annotations.add(new EntityAnnotation(
                UUID.randomUUID().toString(),
                tenantId,
                "biography",
                pa.sourceId(),
                nodeId,
                pa.startOffset(),
                pa.endOffset(),
                pa.surfaceForm(),
                1.0,
                AnnotationMethod.YAML_ANCHOR,
                null,
                now));
        }

        if (!annotations.isEmpty()) {
            annotationIndex.addAnnotations(annotations);
        }

        pendingAnchors.clear();
    }

    private LifeEventEntry processLifeEvent(LifeEventEntry entry, String tenantId) {
        if (entry.description() == null || !entry.description().contains("[")) return entry;

        AnchorParseResult result = parser.parse(entry.description());
        String sourceId = entry.id() != null ? entry.id() : UUID.randomUUID().toString();
        collectAnchors(result, sourceId, tenantId);

        return new LifeEventEntry(
            entry.id(), entry.sourceRef(), entry.confidenceOrigin(), entry.timestamp(),
            result.cleanText(), entry.domain(), entry.eventType(), entry.subject(),
            entry.period(), entry.relatedEntities(), entry.pad(), entry.occEmotions(),
            entry.secAppraisal(), entry.actionTendencies(), entry.cognitiveDistortions(),
            entry.driveImpact(), entry.capsInputs(), entry.goalImpacts(),
            entry.habituation(), entry.gutFeeling(), entry.subThoughts(), entry.entityRefs());
    }

    private BeliefEntry processBelief(BeliefEntry entry, String tenantId) {
        if (entry.description() == null || !entry.description().contains("[")) {return entry;}

        AnchorParseResult result   = parser.parse(entry.description());
        String            sourceId = entry.id() != null ? entry.id() : UUID.randomUUID().toString();
        collectAnchors(result, sourceId, tenantId);

        return new BeliefEntry(
                entry.id(), entry.sourceRef(), entry.confidenceOrigin(), entry.confidence(),
                result.cleanText(), entry.cognitiveKind(), entry.entityRefs(),
                entry.properties(), entry.associations());
    }

    private void collectAnchors(AnchorParseResult result, String sourceId, String tenantId) {
        for (ParsedAnchor anchor : result.anchors()) {
            pendingAnchors.add(new PendingAnchor(
                anchor.alias(), anchor.surfaceForm(),
                anchor.startOffset(), anchor.endOffset(),
                sourceId, tenantId));
        }
    }

    private Map<String, String> resolveAliases(String tenantId) {
        Map<String, String> aliasToNodeId = new HashMap<>();
        for (PendingAnchor pa : pendingAnchors) {
            if (aliasToNodeId.containsKey(pa.alias())) {continue;}

            List<MindMapNode> matches = mindMapStore.search(
                    MindMapQuery.of(tenantId, 1).withText(pa.alias()));
            if (!matches.isEmpty()) {
                aliasToNodeId.put(pa.alias(), matches.getFirst().id());
            } else {
                matches = mindMapStore.search(
                        MindMapQuery.of(tenantId, 1).withText(pa.surfaceForm()));
                if (!matches.isEmpty()) {
                    aliasToNodeId.put(pa.alias(), matches.getFirst().id());
                }
            }
        }
        return aliasToNodeId;
    }

    record PendingAnchor(String alias, String surfaceForm, int startOffset, int endOffset,
                          String sourceId, String tenantId) {}
}
