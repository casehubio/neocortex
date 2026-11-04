package io.casehub.neocortex.entity.token.cdi;

import io.casehub.neocortex.memory.seeding.biography.ActivityEntry;
import io.casehub.neocortex.memory.seeding.biography.BeliefEntry;
import io.casehub.neocortex.memory.seeding.biography.BiographyProfile;
import io.casehub.neocortex.memory.seeding.biography.BiographyProfilePreprocessor;
import io.casehub.neocortex.memory.seeding.biography.CulturalContextEntry;
import io.casehub.neocortex.memory.seeding.biography.GoalEntry;
import io.casehub.neocortex.memory.seeding.biography.LifeEventEntry;
import io.casehub.neocortex.memory.seeding.biography.ProjectEntry;
import io.casehub.neocortex.memory.seeding.biography.RelationshipEntry;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.util.List;
import java.util.regex.Pattern;

@ApplicationScoped
public class TokenisingBiographyPreprocessor implements BiographyProfilePreprocessor {

    private static final Pattern BRACKET_PATTERN = Pattern.compile("(\\S+)\\[([a-zA-Z0-9_-]+)]");

    private final SurfaceFormTrieManager trieManager;

    @Inject
    public TokenisingBiographyPreprocessor(SurfaceFormTrieManager trieManager) {
        this.trieManager = trieManager;
    }

    @Override
    public BiographyProfile preprocess(BiographyProfile profile, String agentId, String tenantId) {
        List<CulturalContextEntry> contexts = profile.culturalContexts().stream()
            .map(this::stripContext).toList();
        List<LifeEventEntry> lifeEvents = profile.lifeEvents().stream()
            .map(this::stripLifeEvent).toList();
        List<BeliefEntry> beliefs = profile.beliefs().stream()
            .map(this::stripBelief).toList();
        List<ActivityEntry> activities = profile.activities().stream()
            .map(this::stripActivity).toList();
        List<ProjectEntry> projects = profile.projects().stream()
            .map(this::stripProject).toList();
        List<RelationshipEntry> relationships = profile.relationships().stream()
            .map(this::stripRelationship).toList();
        List<GoalEntry> goals = profile.goals().stream()
            .map(this::stripGoal).toList();

        return new BiographyProfile(
            profile.agentId(), profile.tenantId(),
            contexts, profile.formativeExperiences(),
            lifeEvents, profile.places(), activities,
            projects, profile.organisations(), relationships,
            goals, beliefs, profile.entityEnrichments(),
            profile.dispositionProfiles(), profile.personalityProfiles(),
            profile.currentStates());
    }

    @Override
    public void afterImport(String agentId, String tenantId) {
        trieManager.rebuildNow(tenantId);
    }

    static String stripBrackets(String text) {
        if (text == null || !text.contains("[")) return text;
        return BRACKET_PATTERN.matcher(text).replaceAll("$1");
    }

    private CulturalContextEntry stripContext(CulturalContextEntry entry) {
        String cleaned = stripBrackets(entry.description());
        if (cleaned == entry.description()) return entry;
        return new CulturalContextEntry(
            entry.id(), entry.sourceRef(), cleaned,
            entry.norms(), entry.contextProperties(), entry.associations());
    }

    private LifeEventEntry stripLifeEvent(LifeEventEntry entry) {
        String cleaned = stripBrackets(entry.description());
        if (cleaned == entry.description()) return entry;
        return new LifeEventEntry(
            entry.id(), entry.sourceRef(), entry.confidenceOrigin(), entry.timestamp(),
            cleaned, entry.domain(), entry.eventType(), entry.subject(),
            entry.period(), entry.relatedEntities(), entry.pad(), entry.occEmotions(),
            entry.secAppraisal(), entry.actionTendencies(), entry.cognitiveDistortions(),
            entry.driveImpact(), entry.capsInputs(), entry.goalImpacts(),
            entry.habituation(), entry.gutFeeling(), entry.subThoughts(), entry.entityRefs());
    }

    private BeliefEntry stripBelief(BeliefEntry entry) {
        String cleaned = stripBrackets(entry.description());
        if (cleaned == entry.description()) return entry;
        return new BeliefEntry(
            entry.id(), entry.sourceRef(), entry.confidenceOrigin(), entry.confidence(),
            cleaned, entry.cognitiveKind(), entry.entityRefs(),
            entry.properties(), entry.associations());
    }

    private ActivityEntry stripActivity(ActivityEntry entry) {
        String cleaned = stripBrackets(entry.notes());
        if (cleaned == entry.notes()) return entry;
        return new ActivityEntry(
            entry.id(), entry.sourceRef(), entry.name(), entry.activityType(),
            entry.date(), entry.placeRef(), entry.participants(),
            cleaned, entry.associations());
    }

    private ProjectEntry stripProject(ProjectEntry entry) {
        String cleaned = stripBrackets(entry.description());
        if (cleaned == entry.description()) return entry;
        return new ProjectEntry(
            entry.id(), entry.sourceRef(), entry.name(), cleaned,
            entry.activityRefs(), entry.properties(), entry.associations());
    }

    private RelationshipEntry stripRelationship(RelationshipEntry entry) {
        if (entry.bdi() == null) return entry;
        RelationshipEntry.BdiValues bdi = entry.bdi();
        String beliefs = stripBrackets(bdi.beliefs());
        String desires = stripBrackets(bdi.desires());
        String intentions = stripBrackets(bdi.intentions());
        if (beliefs == bdi.beliefs() && desires == bdi.desires() && intentions == bdi.intentions()) {
            return entry;
        }
        return new RelationshipEntry(
            entry.id(), entry.sourceRef(), entry.confidenceOrigin(), entry.name(),
            entry.traits(), new RelationshipEntry.BdiValues(beliefs, desires, intentions),
            entry.affect(), entry.dynamics(), entry.properties(), entry.associations());
    }

    private GoalEntry stripGoal(GoalEntry entry) {
        if (entry.subGoals() == null || entry.subGoals().isEmpty()) return entry;
        boolean anyChanged = entry.subGoals().stream()
            .anyMatch(sg -> sg.description() != null && sg.description().contains("["));
        if (!anyChanged) return entry;
        List<GoalEntry.SubGoal> cleaned = entry.subGoals().stream()
            .map(sg -> {
                String desc = stripBrackets(sg.description());
                return desc == sg.description() ? sg : new GoalEntry.SubGoal(sg.id(), sg.tier(), desc);
            }).toList();
        return new GoalEntry(
            entry.id(), entry.sourceRef(), entry.confidenceOrigin(), entry.name(),
            entry.tier(), entry.horizon(), entry.status(), entry.urgency(),
            entry.priority(), entry.feasibility(), entry.needTier(),
            entry.contentDomain(), entry.contentSubdomain(), entry.motivationOrigin(),
            entry.clarity(), entry.evidenceBasis(), entry.dependencies(), entry.pad(),
            cleaned, entry.associations());
    }
}
