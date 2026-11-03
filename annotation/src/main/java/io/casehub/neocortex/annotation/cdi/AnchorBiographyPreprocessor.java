package io.casehub.neocortex.annotation.cdi;

import io.casehub.neocortex.annotation.AnchorAnnotationParser;
import io.casehub.neocortex.annotation.AnchorParseResult;
import io.casehub.neocortex.annotation.AnnotationMethod;
import io.casehub.neocortex.annotation.EntityAnnotation;
import io.casehub.neocortex.annotation.EntityAnnotationIndex;
import io.casehub.neocortex.annotation.ParsedAnchor;
import io.casehub.neocortex.memory.seeding.biography.ActivityEntry;
import io.casehub.neocortex.memory.seeding.biography.BeliefEntry;
import io.casehub.neocortex.memory.seeding.biography.BiographyProfile;
import io.casehub.neocortex.memory.seeding.biography.BiographyProfilePreprocessor;
import io.casehub.neocortex.memory.seeding.biography.CulturalContextEntry;
import io.casehub.neocortex.memory.seeding.biography.GoalEntry;
import io.casehub.neocortex.memory.seeding.biography.LifeEventEntry;
import io.casehub.neocortex.memory.seeding.biography.ProjectEntry;
import io.casehub.neocortex.memory.seeding.biography.RelationshipEntry;
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
    private final List<UnbracketedEntry> unbracketedEntries = new ArrayList<>();

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

        List<CulturalContextEntry> cleanedContexts = profile.culturalContexts().stream()
                                                            .map(entry -> processCulturalContext(entry, tenantId))
                                                            .toList();

        List<LifeEventEntry> cleanedLifeEvents = profile.lifeEvents().stream()
                                                        .map(entry -> processLifeEvent(entry, tenantId))
                                                        .toList();

        List<BeliefEntry> cleanedBeliefs = profile.beliefs().stream()
                                                  .map(entry -> processBelief(entry, tenantId))
                                                  .toList();

        List<ActivityEntry> cleanedActivities = profile.activities().stream()
                                                       .map(entry -> processActivity(entry, tenantId))
                                                       .toList();

        List<ProjectEntry> cleanedProjects = profile.projects().stream()
                                                    .map(entry -> processProject(entry, tenantId))
                                                    .toList();

        List<RelationshipEntry> cleanedRelationships = profile.relationships().stream()
                                                              .map(entry -> processRelationship(entry, tenantId))
                                                              .toList();

        List<GoalEntry> cleanedGoals = profile.goals().stream()
                                              .map(entry -> processGoal(entry, tenantId))
                                              .toList();

        return new BiographyProfile(
                profile.agentId(), profile.tenantId(),
                cleanedContexts, profile.formativeExperiences(),
                cleanedLifeEvents, profile.places(), cleanedActivities,
                cleanedProjects, profile.organisations(), cleanedRelationships,
                cleanedGoals, cleanedBeliefs, profile.entityEnrichments(),
                profile.dispositionProfiles(), profile.personalityProfiles(),
                profile.currentStates());
    }

    @Override
    public void afterImport(String agentId, String tenantId) {
        if (pendingAnchors.isEmpty() && unbracketedEntries.isEmpty()) return;
        if (pendingAnchors.isEmpty()) {
            scanUnbracketedEntries(tenantId);
            unbracketedEntries.clear();
            return;
        }

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

        scanUnbracketedEntries(tenantId);
        unbracketedEntries.clear();
    }

    private LifeEventEntry processLifeEvent(LifeEventEntry entry, String tenantId) {
        String leSourceId = entry.id() != null ? entry.id() : UUID.randomUUID().toString();
        trackUnbracketed(leSourceId + "-subject", entry.subject(), tenantId);
        if (entry.description() == null || !entry.description().contains("[")) {
            trackUnbracketed(leSourceId, entry.description(), tenantId);
            return entry;
        }

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
        if (entry.description() == null || !entry.description().contains("[")) {
            trackUnbracketed(entry.id(), entry.description(), tenantId);
            return entry;
        }

        AnchorParseResult result   = parser.parse(entry.description());
        String            sourceId = entry.id() != null ? entry.id() : UUID.randomUUID().toString();
        collectAnchors(result, sourceId, tenantId);

        return new BeliefEntry(
                entry.id(), entry.sourceRef(), entry.confidenceOrigin(), entry.confidence(),
                result.cleanText(), entry.cognitiveKind(), entry.entityRefs(),
                entry.properties(), entry.associations());
    }

    private CulturalContextEntry processCulturalContext(CulturalContextEntry entry, String tenantId) {
        if (entry.description() == null || !entry.description().contains("[")) {
            trackUnbracketed(entry.id(), entry.description(), tenantId);
            return entry;
        }
        AnchorParseResult result   = parser.parse(entry.description());
        String            sourceId = entry.id() != null ? entry.id() : UUID.randomUUID().toString();
        collectAnchors(result, sourceId, tenantId);
        return new CulturalContextEntry(
                entry.id(), entry.sourceRef(), result.cleanText(),
                entry.norms(), entry.contextProperties(), entry.associations());
    }

    private ActivityEntry processActivity(ActivityEntry entry, String tenantId) {
        if (entry.notes() == null || !entry.notes().contains("[")) {
            trackUnbracketed(entry.id(), entry.notes(), tenantId);
            return entry;
        }
        AnchorParseResult result   = parser.parse(entry.notes());
        String            sourceId = entry.id() != null ? entry.id() : UUID.randomUUID().toString();
        collectAnchors(result, sourceId, tenantId);
        return new ActivityEntry(
                entry.id(), entry.sourceRef(), entry.name(), entry.activityType(),
                entry.date(), entry.placeRef(), entry.participants(),
                result.cleanText(), entry.associations());
    }

    private ProjectEntry processProject(ProjectEntry entry, String tenantId) {
        if (entry.description() == null || !entry.description().contains("[")) {
            trackUnbracketed(entry.id(), entry.description(), tenantId);
            return entry;
        }
        AnchorParseResult result   = parser.parse(entry.description());
        String            sourceId = entry.id() != null ? entry.id() : UUID.randomUUID().toString();
        collectAnchors(result, sourceId, tenantId);
        return new ProjectEntry(
                entry.id(), entry.sourceRef(), entry.name(), result.cleanText(),
                entry.activityRefs(), entry.properties(), entry.associations());
    }

    private RelationshipEntry processRelationship(RelationshipEntry entry, String tenantId) {
        String relSourceId = entry.id() != null ? entry.id() : UUID.randomUUID().toString();
        trackUnbracketed(relSourceId, entry.name(), tenantId);
        if (entry.bdi() == null) return entry;
        RelationshipEntry.BdiValues bdi = entry.bdi();
        boolean hasBrackets = (bdi.beliefs() != null && bdi.beliefs().contains("["))
                              || (bdi.desires() != null && bdi.desires().contains("["))
                              || (bdi.intentions() != null && bdi.intentions().contains("["));
        if (!hasBrackets) {
            String sourceId = entry.id() != null ? entry.id() : UUID.randomUUID().toString();
            trackUnbracketed(sourceId + "-beliefs", bdi.beliefs(), tenantId);
            trackUnbracketed(sourceId + "-desires", bdi.desires(), tenantId);
            trackUnbracketed(sourceId + "-intentions", bdi.intentions(), tenantId);
            return entry;
        }

        String sourceId        = entry.id() != null ? entry.id() : UUID.randomUUID().toString();
        String cleanBeliefs    = processTextField(bdi.beliefs(), sourceId + "-beliefs", tenantId);
        String cleanDesires    = processTextField(bdi.desires(), sourceId + "-desires", tenantId);
        String cleanIntentions = processTextField(bdi.intentions(), sourceId + "-intentions", tenantId);

        RelationshipEntry.BdiValues cleanedBdi = new RelationshipEntry.BdiValues(cleanBeliefs, cleanDesires, cleanIntentions);
        return new RelationshipEntry(
                entry.id(), entry.sourceRef(), entry.confidenceOrigin(), entry.name(),
                entry.traits(), cleanedBdi, entry.affect(), entry.dynamics(),
                entry.properties(), entry.associations());
    }

    private GoalEntry processGoal(GoalEntry entry, String tenantId) {
        String goalSourceId = entry.id() != null ? entry.id() : UUID.randomUUID().toString();
        trackUnbracketed(goalSourceId, entry.name(), tenantId);
        if (entry.subGoals() == null || entry.subGoals().isEmpty()) return entry;
        boolean hasBrackets = entry.subGoals().stream()
                                   .anyMatch(sg -> sg.description() != null && sg.description().contains("["));
        if (!hasBrackets) {
            entry.subGoals().forEach(sg -> trackUnbracketed(
                sg.id() != null ? sg.id() : UUID.randomUUID().toString(),
                sg.description(), tenantId));
            return entry;
        }

        List<GoalEntry.SubGoal> cleanedSubGoals = entry.subGoals().stream()
                                             .map(sg -> processSubGoal(sg, tenantId))
                                             .toList();
        return new GoalEntry(
                entry.id(), entry.sourceRef(), entry.confidenceOrigin(), entry.name(),
                entry.tier(), entry.horizon(), entry.status(), entry.urgency(),
                entry.priority(), entry.feasibility(), entry.needTier(),
                entry.contentDomain(), entry.contentSubdomain(), entry.motivationOrigin(),
                entry.clarity(), entry.evidenceBasis(), entry.dependencies(), entry.pad(),
                cleanedSubGoals, entry.associations());
    }

    private GoalEntry.SubGoal processSubGoal(GoalEntry.SubGoal subGoal, String tenantId) {
        if (subGoal.description() == null || !subGoal.description().contains("[")) {return subGoal;}
        AnchorParseResult result   = parser.parse(subGoal.description());
        String            sourceId = subGoal.id() != null ? subGoal.id() : UUID.randomUUID().toString();
        collectAnchors(result, sourceId, tenantId);
        return new GoalEntry.SubGoal(subGoal.id(), subGoal.tier(), result.cleanText());
    }

    private String processTextField(String text, String sourceId, String tenantId) {
        if (text == null || !text.contains("[")) {return text;}
        AnchorParseResult result = parser.parse(text);
        collectAnchors(result, sourceId, tenantId);
        return result.cleanText();
    }

    private void trackUnbracketed(String entryId, String text, String tenantId) {
        if (text == null || text.isBlank()) {return;}
        String sourceId = entryId != null ? entryId : UUID.randomUUID().toString();
        unbracketedEntries.add(new UnbracketedEntry(sourceId, text, tenantId));
    }

    private void scanUnbracketedEntries(String tenantId) {
        if (unbracketedEntries.isEmpty()) {return;}

        List<MemoryAnnotationObserver.EntityCandidate> candidates =
                MemoryAnnotationObserver.buildCandidates(mindMapStore, tenantId);
        if (candidates.isEmpty()) {return;}

        Instant                now         = Instant.now();
        List<EntityAnnotation> annotations = new ArrayList<>();

        for (UnbracketedEntry ue : unbracketedEntries) {
            List<MemoryAnnotationObserver.MatchedSpan> matches =
                    MemoryAnnotationObserver.scanForMatches(ue.text(), candidates);
            for (var m : matches) {
                annotations.add(new EntityAnnotation(
                        UUID.randomUUID().toString(), ue.tenantId(),
                        "biography", ue.sourceId(), m.nodeId(),
                        m.startOffset(), m.endOffset(), m.mentionText(),
                        0.8, AnnotationMethod.ENTITY_SCAN, null, now));
            }
        }

        if (!annotations.isEmpty()) {
            annotationIndex.addAnnotations(annotations);
        }
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

    record UnbracketedEntry(String sourceId, String text, String tenantId) {}


}
