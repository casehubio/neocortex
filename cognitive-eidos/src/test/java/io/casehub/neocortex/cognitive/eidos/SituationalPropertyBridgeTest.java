package io.casehub.neocortex.cognitive.eidos;

import io.casehub.eidos.api.AgentDescriptor;
import io.casehub.eidos.api.AgentDisposition;
import io.casehub.eidos.api.AmpdFacet;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SituationalPropertyBridgeTest {

    @Test
    void extractsParsesStressProfile() {
        var props = Map.of(
                "situational-stress-anxiousness", "15",
                "situational-stress-emotional-lability", "10",
                "situational-stress-hostility", "5",
                "situational-stress-provenance", "biographical");

        var profiles = SituationalPropertyBridge.extract(props);
        assertEquals(1, profiles.size());
        var profile = profiles.getFirst();
        assertEquals("stress", profile.mode());
        assertEquals("biographical", profile.provenance());
        assertEquals(3, profile.shifts().size());
    }

    @Test
    void extractsMultipleModes() {
        var props = new LinkedHashMap<String, String>();
        props.put("situational-stress-anxiousness", "15");
        props.put("situational-flow-withdrawal", "-15");

        var profiles = SituationalPropertyBridge.extract(props);
        assertEquals(2, profiles.size());
        var modes = profiles.stream().map(p -> p.mode()).sorted().toList();
        assertEquals(List.of("flow", "stress"), modes);
    }

    @Test
    void convertsKebabCaseToCanonicalFacetName() {
        var props = Map.of("situational-stress-emotional-lability", "12");

        var profiles = SituationalPropertyBridge.extract(props);
        var shift = profiles.getFirst().shifts().getFirst();
        assertEquals("Emotional Lability", shift.facet());
        assertEquals(12, shift.delta());
    }

    @Test
    void skipsInvalidModes() {
        var props = Map.of("situational-unknown-anxiousness", "10");

        var profiles = SituationalPropertyBridge.extract(props);
        assertTrue(profiles.isEmpty());
    }

    @Test
    void skipsNonNumericValues() {
        var props = Map.of(
                "situational-stress-anxiousness", "not-a-number",
                "situational-stress-hostility", "5");

        var profiles = SituationalPropertyBridge.extract(props);
        assertEquals(1, profiles.size());
        assertEquals(1, profiles.getFirst().shifts().size());
        assertEquals("Hostility", profiles.getFirst().shifts().getFirst().facet());
    }

    @Test
    void defaultsProvenanceToBiographical() {
        var props = Map.of("situational-stress-anxiousness", "10");

        var profiles = SituationalPropertyBridge.extract(props);
        assertEquals("biographical", profiles.getFirst().provenance());
    }

    @Test
    void emptyPropertiesReturnsEmptyList() {
        assertTrue(SituationalPropertyBridge.extract(Map.of()).isEmpty());
        assertTrue(SituationalPropertyBridge.extract(null).isEmpty());
    }

    @Test
    void ignoresNonSituationalProperties() {
        var props = Map.of(
                "personality-ampd-anxiousness", "62",
                "provenance", "biographical");

        assertTrue(SituationalPropertyBridge.extract(props).isEmpty());
    }

    @Test
    void enrichAddsProfilesToDescriptor() {
        var descriptor = AgentDescriptor.builder()
                .agentId("agent-1").name("Test").slot("test-slot").tenancyId("t1")
                .disposition(AgentDisposition.builder()
                        .ampdFacets(List.of(new AmpdFacet("Anxiousness", 62)))
                        .build())
                .build();

        var props = Map.of(
                "situational-stress-anxiousness", "15",
                "situational-stress-provenance", "biographical");

        var enriched = SituationalPropertyBridge.enrich(descriptor, props);
        assertEquals(1, enriched.disposition().situationalProfiles().size());
        assertEquals("stress", enriched.disposition().situationalProfiles().getFirst().mode());
        assertEquals("agent-1", enriched.agentId());
    }

    @Test
    void enrichPreservesExistingDispositionFields() {
        var descriptor = AgentDescriptor.builder()
                .agentId("agent-1").name("Test").slot("test-slot").tenancyId("t1")
                .disposition(AgentDisposition.builder()
                        .socialOrient("cooperative")
                        .ampdFacets(List.of(new AmpdFacet("Anxiousness", 62)))
                        .build())
                .build();

        var enriched = SituationalPropertyBridge.enrich(descriptor,
                Map.of("situational-stress-anxiousness", "10"));

        assertEquals(descriptor.disposition().socialOrient(), enriched.disposition().socialOrient());
        assertEquals(1, enriched.disposition().ampdFacets().size());
    }

    @Test
    void enrichReturnsOriginalWhenNoSituationalProperties() {
        var descriptor = AgentDescriptor.builder()
                .agentId("agent-1").name("Test").slot("test-slot").tenancyId("t1")
                .disposition(AgentDisposition.builder().build())
                .build();

        var result = SituationalPropertyBridge.enrich(descriptor, Map.of("other", "value"));
        assertTrue(result.disposition().situationalProfiles().isEmpty());
    }

    @Test
    void handlesUnderscoreModesCorrectly() {
        var props = Map.of("situational-social_collaborative-anxiousness", "8");

        var profiles = SituationalPropertyBridge.extract(props);
        assertEquals(1, profiles.size());
        assertEquals("social_collaborative", profiles.getFirst().mode());
    }
}
