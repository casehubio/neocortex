package io.casehub.neocortex.memory.seeding.biography;

import io.casehub.neocortex.mindmap.NodeInput;
import io.casehub.neocortex.mindmap.SubgraphTypes;
import io.casehub.neocortex.mindmap.inmem.InMemoryMindMapStore;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class PersonalityProfileHandlerTest {

    private static final String TENANT = "t1";
    private static final String AGENT = "frida-kahlo";

    @Test
    void writesFrameworkPropertiesToNode() {
        var store = new InMemoryMindMapStore();
        var sgId = BiographyUtils.ensureSubgraph(store, SubgraphTypes.PERSON, TENANT);
        store.addNode(NodeInput.of("Frida Kahlo", sgId).withProvenance("test"), TENANT);

        var handler = new PersonalityProfileHandler(store, true);
        handler.handle(buildProfile(), AGENT, TENANT);

        var node = store.resolveNode("Frida Kahlo", sgId, TENANT);
        assertThat(node.properties()).containsEntry("personality-mbti-type", "infp");
        assertThat(node.properties()).containsEntry("personality-ocean-openness", "0.85");
        assertThat(node.properties()).containsEntry("personality-ocean-neuroticism", "0.75");
        assertThat(node.properties()).containsEntry("personality-enneagram-type", "type-4w5");
        assertThat(node.properties()).containsEntry("personality-disc-primary", "influence");
        assertThat(node.properties()).containsEntry("personality-belbin-primary", "plant");
        assertThat(node.properties()).containsEntry("personality-tk-primary", "competing");
        assertThat(node.properties()).containsEntry("personality-sdi-primary", "red");
    }

    @Test
    void writesJungianFunctionWeights() {
        var store = new InMemoryMindMapStore();
        var sgId = BiographyUtils.ensureSubgraph(store, SubgraphTypes.PERSON, TENANT);
        store.addNode(NodeInput.of("Frida Kahlo", sgId).withProvenance("test"), TENANT);

        var handler = new PersonalityProfileHandler(store, true);
        handler.handle(buildProfile(), AGENT, TENANT);

        var node = store.resolveNode("Frida Kahlo", sgId, TENANT);
        assertThat(node.properties()).containsEntry("jungian-function-fi", "0.35");
        assertThat(node.properties()).containsEntry("jungian-function-ne", "0.22");
    }

    @Test
    void writesAmpdFacets() {
        var store = new InMemoryMindMapStore();
        var sgId = BiographyUtils.ensureSubgraph(store, SubgraphTypes.PERSON, TENANT);
        store.addNode(NodeInput.of("Frida Kahlo", sgId).withProvenance("test"), TENANT);

        var handler = new PersonalityProfileHandler(store, true);
        handler.handle(buildProfile(), AGENT, TENANT);

        var node = store.resolveNode("Frida Kahlo", sgId, TENANT);
        assertThat(node.properties()).containsEntry("personality-ampd-emotional-lability", "80");
        assertThat(node.properties()).containsEntry("personality-ampd-anxiousness", "70");
        assertThat(node.properties()).containsEntry("personality-ampd-callousness", "20");
    }

    @Test
    void writesAttachmentStyle() {
        var store = new InMemoryMindMapStore();
        var sgId = BiographyUtils.ensureSubgraph(store, SubgraphTypes.PERSON, TENANT);
        store.addNode(NodeInput.of("Frida Kahlo", sgId).withProvenance("test"), TENANT);

        var handler = new PersonalityProfileHandler(store, true);
        handler.handle(buildProfile(), AGENT, TENANT);

        var node = store.resolveNode("Frida Kahlo", sgId, TENANT);
        assertThat(node.properties()).containsEntry("personality-attachment-style", "anxious-preoccupied");
        assertThat(node.properties()).containsEntry("personality-attachment-anxiety", "0.75");
    }

    @Test
    void writesArchetypeSelections() {
        var store = new InMemoryMindMapStore();
        var sgId = BiographyUtils.ensureSubgraph(store, SubgraphTypes.PERSON, TENANT);
        store.addNode(NodeInput.of("Frida Kahlo", sgId).withProvenance("test"), TENANT);

        var handler = new PersonalityProfileHandler(store, true);
        handler.handle(buildProfile(), AGENT, TENANT);

        var node = store.resolveNode("Frida Kahlo", sgId, TENANT);
        assertThat(node.properties()).containsEntry("archetype-1", "artist");
        assertThat(node.properties()).containsEntry("archetype-1-family", "creator");
        assertThat(node.properties()).containsEntry("archetype-1-adjectives", "expressive,authentic,intense");
        assertThat(node.properties()).containsEntry("archetype-2", "activist");
        assertThat(node.properties()).containsEntry("archetype-2-family", "rebel");
    }

    @Test
    void writesDispositionAxes() {
        var store = new InMemoryMindMapStore();
        var sgId = BiographyUtils.ensureSubgraph(store, SubgraphTypes.PERSON, TENANT);
        store.addNode(NodeInput.of("Frida Kahlo", sgId).withProvenance("test"), TENANT);

        var handler = new PersonalityProfileHandler(store, true);
        handler.handle(buildProfile(), AGENT, TENANT);

        var node = store.resolveNode("Frida Kahlo", sgId, TENANT);
        assertThat(node.properties()).containsEntry("disposition-socialOrient", "collaborative");
        assertThat(node.properties()).containsEntry("disposition-ruleFollowing", "flexible");
        assertThat(node.properties()).containsEntry("disposition-riskAppetite", "bold");
        assertThat(node.properties()).containsEntry("disposition-autonomy", "autonomous");
        assertThat(node.properties()).containsEntry("disposition-conflictMode", "competing");
    }

    @Test
    void skipsWhenNodeNotFound() {
        var store = new InMemoryMindMapStore();
        var handler = new PersonalityProfileHandler(store, true);
        handler.handle(buildProfile(), AGENT, TENANT);
        // no exception — silently skips
    }

    private BiographyProfile buildProfile() {
        var mbti = new PersonalityProfileEntry.MbtiAssessment("infp", "Fi dominant", 0.8, "extracted");
        var ocean = new PersonalityProfileEntry.OceanAssessment(0.85, 0.35, 0.55, 0.40, 0.75, "evidence", 0.8, "extracted");
        var jungian = List.of(
            new PersonalityProfileEntry.JungianFunction("fi", 0.35, "values"),
            new PersonalityProfileEntry.JungianFunction("ne", 0.22, "possibility")
        );
        var enneagram = new PersonalityProfileEntry.EnneagramAssessment("type-4", "5", "identity", 0.7, "extracted");
        var disc = new PersonalityProfileEntry.DiscAssessment("influence", "dominance", "charismatic", 0.7, "extracted");
        var belbin = new PersonalityProfileEntry.BelbinAssessment("plant", null, "creative", 0.65, "extracted");
        var tk = new PersonalityProfileEntry.ThomasKilmannAssessment("competing", "confrontational", 0.8, "extracted");
        var sdi = new PersonalityProfileEntry.SdiAssessment("red", "action-oriented", 0.7, "extracted");
        var ampd = new PersonalityProfileEntry.AmpdAssessment(20, 15, 35, 10, 55, 40, 45, 30, 80, 70, 65, 55, 35, "volatile", 0.75, "extracted");
        var attachment = new PersonalityProfileEntry.AttachmentAssessment("anxious-preoccupied", 0.75, 0.30, "validation", 0.75, "extracted");
        var locus = new PersonalityProfileEntry.LocusAssessment("internal", Map.of("art", "internal", "health", "external"), "agency", 0.7, "extracted");

        var frameworks = new PersonalityProfileEntry.Frameworks(ocean, mbti, jungian, enneagram, disc, belbin, tk, sdi, ampd, attachment, locus);

        var archetypes = List.of(
            new PersonalityProfileEntry.ArchetypeSelection(1, "creator", "artist", List.of("expressive", "authentic", "intense"), "created from truth", 0.9),
            new PersonalityProfileEntry.ArchetypeSelection(2, "rebel", "activist", List.of("cause-driven", "passionate"), "political activism", 0.85)
        );

        var disposition = new PersonalityProfileEntry.Disposition(
            new PersonalityProfileEntry.DispositionAxis(0.7, "collaborative", "socially engaged"),
            new PersonalityProfileEntry.DispositionAxis(0.25, "flexible", "defied rules"),
            new PersonalityProfileEntry.DispositionAxis(0.75, "bold", "risk-taking"),
            new PersonalityProfileEntry.DispositionAxis(0.8, "autonomous", "self-directed"),
            new PersonalityProfileEntry.DispositionAxis(0.8, "competing", "confrontational")
        );

        var entry = new PersonalityProfileEntry("pp-1", "Frida Kahlo", frameworks, archetypes, disposition, null, List.of());

        return new BiographyProfile(AGENT, TENANT,
            List.of(), List.of(), List.of(), List.of(),
            List.of(), List.of(), List.of(), List.of(),
            List.of(), List.of(), List.of(), List.of(),
            List.of(entry), List.of());
    }
}
