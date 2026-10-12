package io.casehub.neocortex.memory.seeding.biography;

import io.casehub.neocortex.memory.CaseMemoryStore;
import io.casehub.neocortex.memory.MemoryDomain;
import io.casehub.neocortex.memory.MemoryInput;
import io.casehub.neocortex.memory.Subject;
import io.casehub.neocortex.memory.experience.ExperienceEvents;
import io.casehub.neocortex.memory.experience.SubThoughtAttributeKeys;
import io.casehub.neocortex.memory.experience.SubThoughtTypes;
import io.casehub.neocortex.memory.reflection.ReflectionEvents;
import io.casehub.neocortex.memory.relationship.RelationshipEvents;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.util.HashMap;
import java.util.Set;

@ApplicationScoped
public class LifeEventHandler implements BiographyHandler {

    private final CaseMemoryStore memoryStore;

    @Inject
    public LifeEventHandler(CaseMemoryStore memoryStore) {
        this.memoryStore = memoryStore;
    }

    @Override public Set<String> handledTypes() { return Set.of(BiographyTemplateTypes.LIFE_EVENT); }

    @Override
    public void handle(BiographyProfile profile, String agentId, String tenantId) {
        for (var entry : profile.lifeEvents()) {
            var attrs = new HashMap<String, String>();
            attrs.put("event-type", entry.eventType() != null ? entry.eventType() : "observation");
            attrs.put("provenance", "biographical-import");
            attrs.put("template-ref", "life-events.yaml#" + entry.id());
            if (entry.sourceRef() != null) attrs.put("source-ref", entry.sourceRef());
            if (entry.timestamp() != null) attrs.put("timestamp", entry.timestamp());
            if (entry.subject() != null) attrs.put("subject", entry.subject());
            if (entry.period() != null) attrs.put("period", entry.period());

            storeSubThoughts(entry, attrs);
            storeOccEmotions(entry, attrs);
            storeSecAppraisal(entry, attrs);
            storeActionTendencies(entry, attrs);
            storeCognitiveDistortions(entry, attrs);
            storeDriveImpact(entry, attrs);
            storeCapsInputs(entry, attrs);
            storeGoalImpacts(entry, attrs);
            storeHabituation(entry, attrs);
            storeGutFeeling(entry, attrs);
            storeRelatedEntities(entry, attrs);

            MemoryDomain domain = resolveDomain(entry.domain());
            var input = MemoryInput.of(
                Subject.of("agent", agentId), domain, tenantId,
                entry.description())
                .withAttributes(attrs);

            if (entry.pad() != null) {
                input = input.withPad(entry.pad().pleasure(), entry.pad().arousal(), entry.pad().dominance());
            }

            memoryStore.store(input);
        }
    }

    private static MemoryDomain resolveDomain(String domain) {
        if (domain == null) return ExperienceEvents.DOMAIN;
        return switch (domain) {
            case "relationship" -> RelationshipEvents.DOMAIN;
            case "reflection" -> ReflectionEvents.DOMAIN;
            default -> ExperienceEvents.DOMAIN;
        };
    }

    private static void storeSubThoughts(LifeEventEntry entry, HashMap<String, String> attrs) {
        if (entry.subThoughts() != null && !entry.subThoughts().isEmpty()) {
            attrs.put(SubThoughtAttributeKeys.COUNT, String.valueOf(entry.subThoughts().size()));
            for (int i = 0; i < entry.subThoughts().size(); i++) {
                var st = entry.subThoughts().get(i);
                SubThoughtTypes.validate(st.type());
                attrs.put(SubThoughtAttributeKeys.type(i), st.type());
                attrs.put(SubThoughtAttributeKeys.text(i), st.text());
            }
        }
    }

    private static void storeOccEmotions(LifeEventEntry entry, HashMap<String, String> attrs) {
        if (entry.occEmotions() == null || entry.occEmotions().isEmpty()) return;
        attrs.put(EnrichmentAttributeKeys.OCC_EMOTION_COUNT, String.valueOf(entry.occEmotions().size()));
        for (int i = 0; i < entry.occEmotions().size(); i++) {
            var occ = entry.occEmotions().get(i);
            if (occ.type() != null) attrs.put(EnrichmentAttributeKeys.occEmotionType(i), occ.type());
            if (occ.intensity() != null) attrs.put(EnrichmentAttributeKeys.occEmotionIntensity(i), String.valueOf(occ.intensity()));
            if (occ.source() != null) attrs.put(EnrichmentAttributeKeys.occEmotionSource(i), occ.source());
        }
    }

    private static void storeSecAppraisal(LifeEventEntry entry, HashMap<String, String> attrs) {
        if (entry.secAppraisal() == null) return;
        var sec = entry.secAppraisal();
        if (sec.relevance() != null) attrs.put(EnrichmentAttributeKeys.SEC_RELEVANCE, String.valueOf(sec.relevance()));
        if (sec.novelty() != null) attrs.put(EnrichmentAttributeKeys.SEC_NOVELTY, String.valueOf(sec.novelty()));
        if (sec.urgency() != null) attrs.put(EnrichmentAttributeKeys.SEC_URGENCY, String.valueOf(sec.urgency()));
        if (sec.conduciveness() != null) attrs.put(EnrichmentAttributeKeys.SEC_CONDUCIVENESS, String.valueOf(sec.conduciveness()));
        if (sec.controllability() != null) attrs.put(EnrichmentAttributeKeys.SEC_CONTROLLABILITY, String.valueOf(sec.controllability()));
        if (sec.adjustability() != null) attrs.put(EnrichmentAttributeKeys.SEC_ADJUSTABILITY, String.valueOf(sec.adjustability()));
        if (sec.internalStandards() != null) attrs.put(EnrichmentAttributeKeys.SEC_INTERNAL_STANDARDS, String.valueOf(sec.internalStandards()));
        if (sec.externalStandards() != null) attrs.put(EnrichmentAttributeKeys.SEC_EXTERNAL_STANDARDS, String.valueOf(sec.externalStandards()));
    }

    private static void storeActionTendencies(LifeEventEntry entry, HashMap<String, String> attrs) {
        if (entry.actionTendencies() == null || entry.actionTendencies().isEmpty()) return;
        attrs.put(EnrichmentAttributeKeys.ACTION_TENDENCY_COUNT, String.valueOf(entry.actionTendencies().size()));
        for (int i = 0; i < entry.actionTendencies().size(); i++) {
            var at = entry.actionTendencies().get(i);
            if (at.type() != null) attrs.put(EnrichmentAttributeKeys.actionTendencyType(i), at.type());
            if (at.intensity() != null) attrs.put(EnrichmentAttributeKeys.actionTendencyIntensity(i), String.valueOf(at.intensity()));
        }
    }

    private static void storeCognitiveDistortions(LifeEventEntry entry, HashMap<String, String> attrs) {
        if (entry.cognitiveDistortions() == null || entry.cognitiveDistortions().isEmpty()) return;
        attrs.put(EnrichmentAttributeKeys.COGNITIVE_DISTORTION_COUNT, String.valueOf(entry.cognitiveDistortions().size()));
        for (int i = 0; i < entry.cognitiveDistortions().size(); i++) {
            var cd = entry.cognitiveDistortions().get(i);
            if (cd.type() != null) attrs.put(EnrichmentAttributeKeys.cognitiveDistortionType(i), cd.type());
            if (cd.strength() != null) attrs.put(EnrichmentAttributeKeys.cognitiveDistortionStrength(i), String.valueOf(cd.strength()));
        }
    }

    private static void storeDriveImpact(LifeEventEntry entry, HashMap<String, String> attrs) {
        if (entry.driveImpact() == null) return;
        var d = entry.driveImpact();
        if (d.curiosity() != null) attrs.put(EnrichmentAttributeKeys.DRIVE_CURIOSITY, String.valueOf(d.curiosity()));
        if (d.competence() != null) attrs.put(EnrichmentAttributeKeys.DRIVE_COMPETENCE, String.valueOf(d.competence()));
        if (d.affiliation() != null) attrs.put(EnrichmentAttributeKeys.DRIVE_AFFILIATION, String.valueOf(d.affiliation()));
        if (d.autonomy() != null) attrs.put(EnrichmentAttributeKeys.DRIVE_AUTONOMY, String.valueOf(d.autonomy()));
    }

    private static void storeCapsInputs(LifeEventEntry entry, HashMap<String, String> attrs) {
        if (entry.capsInputs() == null) return;
        for (var ci : entry.capsInputs().entrySet()) {
            String sanitized = ci.getKey().replaceAll("[^a-z0-9_-]", "_");
            attrs.put(EnrichmentAttributeKeys.capsInput(sanitized), String.valueOf(ci.getValue()));
        }
    }

    private static void storeGoalImpacts(LifeEventEntry entry, HashMap<String, String> attrs) {
        if (entry.goalImpacts() == null || entry.goalImpacts().isEmpty()) return;
        attrs.put(EnrichmentAttributeKeys.GOAL_IMPACT_COUNT, String.valueOf(entry.goalImpacts().size()));
        for (int i = 0; i < entry.goalImpacts().size(); i++) {
            var gi = entry.goalImpacts().get(i);
            if (gi.goal() != null) attrs.put(EnrichmentAttributeKeys.goalImpactGoal(i), gi.goal());
            if (gi.relationship() != null) attrs.put(EnrichmentAttributeKeys.goalImpactRelationship(i), gi.relationship());
            if (gi.urgencyDelta() != null) attrs.put(EnrichmentAttributeKeys.goalImpactUrgencyDelta(i), String.valueOf(gi.urgencyDelta()));
        }
    }

    private static void storeHabituation(LifeEventEntry entry, HashMap<String, String> attrs) {
        if (entry.habituation() == null) return;
        var h = entry.habituation();
        if (h.novelty() != null) attrs.put(EnrichmentAttributeKeys.HABITUATION_NOVELTY, h.novelty());
        if (h.habituationScore() != null) attrs.put(EnrichmentAttributeKeys.HABITUATION_SCORE, String.valueOf(h.habituationScore()));
        if (h.echoes() != null) attrs.put(EnrichmentAttributeKeys.HABITUATION_ECHOES, h.echoes());
    }

    private static void storeGutFeeling(LifeEventEntry entry, HashMap<String, String> attrs) {
        if (entry.gutFeeling() == null) return;
        var g = entry.gutFeeling();
        if (g.valence() != null) attrs.put(EnrichmentAttributeKeys.GUT_VALENCE, g.valence());
        if (g.intensity() != null) attrs.put(EnrichmentAttributeKeys.GUT_INTENSITY, String.valueOf(g.intensity()));
        if (g.resonance() != null) attrs.put(EnrichmentAttributeKeys.GUT_RESONANCE, g.resonance());
    }

    private static void storeRelatedEntities(LifeEventEntry entry, HashMap<String, String> attrs) {
        if (entry.relatedEntities() == null || entry.relatedEntities().isEmpty()) return;
        attrs.put(EnrichmentAttributeKeys.RELATED_ENTITY_COUNT, String.valueOf(entry.relatedEntities().size()));
        for (int i = 0; i < entry.relatedEntities().size(); i++) {
            attrs.put(EnrichmentAttributeKeys.relatedEntity(i), entry.relatedEntities().get(i));
        }
    }
}
