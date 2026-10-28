package io.casehub.neocortex.memory.seeding.biography;

import io.casehub.neocortex.mindmap.MindMapStore;
import io.casehub.neocortex.mindmap.NodeUpdate;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.util.HashMap;
import java.util.Set;

@ApplicationScoped
public class PersonalityProfileHandler implements BiographyHandler {

    private final MindMapStore store;

    @Inject
    public PersonalityProfileHandler(MindMapStore store) { this.store = store; }

    PersonalityProfileHandler(MindMapStore store, @SuppressWarnings("unused") boolean testMarker) {
        this.store = store;
    }

    @Override public Set<String> handledTypes() { return Set.of(BiographyTemplateTypes.PERSONALITY_PROFILE); }

    @Override
    public void handle(BiographyProfile profile, String agentId, String tenantId) {
        for (var entry : profile.personalityProfiles()) {
            String nodeId = AssociationProcessor.resolveAcrossSubgraphs(entry.entityRef(), store, tenantId);
            if (nodeId == null) continue;

            var props = new HashMap<String, String>();
            props.put("provenance", "biographical-import");

            writeFrameworks(entry.frameworks(), props);
            writeArchetypes(entry.archetypes(), props);
            writeDisposition(entry.disposition(), props);

            if (!props.isEmpty()) {
                store.updateNode(nodeId, NodeUpdate.empty().withPropertiesToSet(props), tenantId);
            }
        }
    }

    private void writeFrameworks(PersonalityProfileEntry.Frameworks fw, HashMap<String, String> props) {
        if (fw == null) return;

        if (fw.mbti() != null && fw.mbti().type() != null) {
            props.put("personality-mbti-type", fw.mbti().type());
        }

        if (fw.ocean() != null) {
            putIfNonNull(props, "personality-ocean-openness", fw.ocean().openness());
            putIfNonNull(props, "personality-ocean-conscientiousness", fw.ocean().conscientiousness());
            putIfNonNull(props, "personality-ocean-extraversion", fw.ocean().extraversion());
            putIfNonNull(props, "personality-ocean-agreeableness", fw.ocean().agreeableness());
            putIfNonNull(props, "personality-ocean-neuroticism", fw.ocean().neuroticism());
        }

        if (fw.enneagram() != null && fw.enneagram().type() != null) {
            String enneagram = fw.enneagram().type();
            if (fw.enneagram().wing() != null) enneagram += "w" + fw.enneagram().wing();
            props.put("personality-enneagram-type", enneagram);
        }

        if (fw.disc() != null && fw.disc().primary() != null) {
            props.put("personality-disc-primary", fw.disc().primary());
        }

        if (fw.belbin() != null && fw.belbin().primary() != null) {
            props.put("personality-belbin-primary", fw.belbin().primary());
        }

        if (fw.thomasKilmann() != null && fw.thomasKilmann().primary() != null) {
            props.put("personality-tk-primary", fw.thomasKilmann().primary());
        }

        if (fw.sdi() != null && fw.sdi().primary() != null) {
            props.put("personality-sdi-primary", fw.sdi().primary());
        }

        for (var jf : fw.jungianStack()) {
            if (jf.functionName() != null && jf.weight() != null) {
                props.put("jungian-function-" + jf.functionName(), String.valueOf(jf.weight()));
            }
        }

        if (fw.ampd() != null) {
            putIfNonNull(props, "personality-ampd-callousness", fw.ampd().callousness());
            putIfNonNull(props, "personality-ampd-manipulativeness", fw.ampd().manipulativeness());
            putIfNonNull(props, "personality-ampd-grandiosity", fw.ampd().grandiosity());
            putIfNonNull(props, "personality-ampd-deceitfulness", fw.ampd().deceitfulness());
            putIfNonNull(props, "personality-ampd-attention-seeking", fw.ampd().attentionSeeking());
            putIfNonNull(props, "personality-ampd-hostility", fw.ampd().hostility());
            putIfNonNull(props, "personality-ampd-suspiciousness", fw.ampd().suspiciousness());
            putIfNonNull(props, "personality-ampd-intimacy-avoidance", fw.ampd().intimacyAvoidance());
            putIfNonNull(props, "personality-ampd-emotional-lability", fw.ampd().emotionalLability());
            putIfNonNull(props, "personality-ampd-anxiousness", fw.ampd().anxiousness());
            putIfNonNull(props, "personality-ampd-risk-taking", fw.ampd().riskTaking());
            putIfNonNull(props, "personality-ampd-impulsivity", fw.ampd().impulsivity());
            putIfNonNull(props, "personality-ampd-withdrawal", fw.ampd().withdrawal());
        }

        if (fw.attachment() != null && fw.attachment().style() != null) {
            props.put("personality-attachment-style", fw.attachment().style());
            putIfNonNull(props, "personality-attachment-anxiety", fw.attachment().anxiety());
            putIfNonNull(props, "personality-attachment-avoidance", fw.attachment().avoidance());
        }
    }

    private void writeArchetypes(java.util.List<PersonalityProfileEntry.ArchetypeSelection> archetypes,
                                  HashMap<String, String> props) {
        if (archetypes == null || archetypes.isEmpty()) return;

        for (var arch : archetypes) {
            String prefix = "archetype-" + (arch.rank() != null ? arch.rank() : 0);
            if (arch.subArchetype() != null) props.put(prefix, arch.subArchetype());
            if (arch.family() != null) props.put(prefix + "-family", arch.family());
            if (!arch.adjectives().isEmpty()) {
                props.put(prefix + "-adjectives", String.join(",", arch.adjectives()));
            }
        }
    }

    private void writeDisposition(PersonalityProfileEntry.Disposition disposition,
                                   HashMap<String, String> props) {
        if (disposition == null) return;

        writeDispositionAxis(props, "disposition-socialOrient", disposition.socialOrient());
        writeDispositionAxis(props, "disposition-ruleFollowing", disposition.ruleFollowing());
        writeDispositionAxis(props, "disposition-riskAppetite", disposition.riskAppetite());
        writeDispositionAxis(props, "disposition-autonomy", disposition.autonomy());
        writeDispositionAxis(props, "disposition-conflictMode", disposition.conflictMode());
    }

    private void writeDispositionAxis(HashMap<String, String> props, String key,
                                       PersonalityProfileEntry.DispositionAxis axis) {
        if (axis != null && axis.term() != null) {
            props.put(key, axis.term());
        }
    }

    private void putIfNonNull(HashMap<String, String> props, String key, Number value) {
        if (value != null) props.put(key, String.valueOf(value));
    }
}
