package io.casehub.neocortex.cognitive.eidos;

import io.casehub.eidos.api.AgentDescriptor;
import io.casehub.eidos.api.AmpdFacet;
import io.casehub.eidos.api.SituationalProfile;
import io.casehub.eidos.api.AgentDisposition;
import io.casehub.eidos.api.DispositionValue;
import io.casehub.neocortex.cognitive.index.LivedComposition;
import io.casehub.neocortex.cognitive.index.LivedProfile;
import io.casehub.neocortex.cognitive.index.WeightedTerm;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class EidosCognitiveProfileBridgeLivedTest {

    private static final List<DispositionValue> TI_NE_PROFILE = List.of(
            new DispositionValue("Ti", 0.35),
            new DispositionValue("Ne", 0.20),
            new DispositionValue("Si", 0.15),
            new DispositionValue("Fe", 0.10));

    private static final List<DispositionValue> NE_TI_PROFILE = List.of(
            new DispositionValue("Ne", 0.35),
            new DispositionValue("Ti", 0.20),
            new DispositionValue("Si", 0.15),
            new DispositionValue("Fe", 0.10));

    private AgentDescriptor authoredDescriptor(List<DispositionValue> profile) {
        return AgentDescriptor.builder()
                .agentId("test-agent")
                .name("Test")
                .slot("test")
                .tenancyId("t1")
                .disposition(AgentDisposition.builder()
                        .socialOrient("cooperative")
                        .dispositionProfile(profile)
                        .build())
                .build();
    }

    @Test
    void livedDescriptorWithMatchingFingerprintReturnsLivedProfile() {
        var authored = authoredDescriptor(TI_NE_PROFILE);
        var authoredTerms = TI_NE_PROFILE.stream()
                .map(dv -> new WeightedTerm(dv.term(), dv.weight()))
                .toList();
        var fingerprint = LivedComposition.fingerprint(authoredTerms);
        var livedTerms = NE_TI_PROFILE.stream()
                .map(dv -> new WeightedTerm(dv.term(), dv.weight()))
                .toList();
        var lived = new LivedProfile(livedTerms, fingerprint);

        var result = EidosCognitiveProfileBridge.livedDescriptor(authored, lived);

        var resultProfile = result.disposition().dispositionProfile();
        assertEquals("Ne", resultProfile.get(0).term());
        assertEquals(0.35, resultProfile.get(0).weight(), 0.001);
        assertEquals("Ti", resultProfile.get(1).term());
        assertEquals(0.20, resultProfile.get(1).weight(), 0.001);
    }

    @Test
    void livedDescriptorWithMismatchedFingerprintReturnsAuthoredUnchanged() {
        var authored = authoredDescriptor(TI_NE_PROFILE);
        var livedTerms = NE_TI_PROFILE.stream()
                .map(dv -> new WeightedTerm(dv.term(), dv.weight()))
                .toList();
        var lived = new LivedProfile(livedTerms, "wrong-fingerprint");

        var result = EidosCognitiveProfileBridge.livedDescriptor(authored, lived);

        var resultProfile = result.disposition().dispositionProfile();
        assertEquals("Ti", resultProfile.get(0).term());
        assertEquals(0.35, resultProfile.get(0).weight(), 0.001);
    }

    @Test
    void livedDescriptorWithNullLivedReturnsAuthoredUnchanged() {
        var authored = authoredDescriptor(TI_NE_PROFILE);

        var result = EidosCognitiveProfileBridge.livedDescriptor(authored, null);

        assertEquals(authored.disposition().dispositionProfile(), result.disposition().dispositionProfile());
    }

    @Test
    void livedDescriptorPreservesNonDispositionFields() {
        var authored = authoredDescriptor(TI_NE_PROFILE);
        var authoredTerms = TI_NE_PROFILE.stream()
                .map(dv -> new WeightedTerm(dv.term(), dv.weight()))
                .toList();
        var lived = new LivedProfile(
                NE_TI_PROFILE.stream().map(dv -> new WeightedTerm(dv.term(), dv.weight())).toList(),
                LivedComposition.fingerprint(authoredTerms));

        var result = EidosCognitiveProfileBridge.livedDescriptor(authored, lived);

        assertEquals("test-agent", result.agentId());
        assertEquals("Test", result.name());
        assertEquals("test", result.slot());
        assertEquals("cooperative", result.disposition().socialOrient().getFirst().term());
    }

    @Test
    void livedDescriptorPreservesAmpdFacetsAndSituationalProfiles() {
        var facets   = List.of(new AmpdFacet("anxiousness", 70));
        var profiles = List.of(new SituationalProfile("stress", List.of(), "test"));
        var authored = AgentDescriptor.builder()
                                      .agentId("test-agent")
                                      .name("Test")
                                      .slot("test")
                                      .tenancyId("t1")
                                      .disposition(AgentDisposition.builder()
                                                                   .socialOrient("cooperative")
                                                                   .dispositionProfile(TI_NE_PROFILE)
                                                                   .ampdFacets(facets)
                                                                   .situationalProfiles(profiles)
                                                                   .build())
                                      .build();
        var authoredTerms = TI_NE_PROFILE.stream()
                                         .map(dv -> new WeightedTerm(dv.term(), dv.weight()))
                                         .toList();
        var lived = new LivedProfile(
                NE_TI_PROFILE.stream().map(dv -> new WeightedTerm(dv.term(), dv.weight())).toList(),
                LivedComposition.fingerprint(authoredTerms));

        var result = EidosCognitiveProfileBridge.livedDescriptor(authored, lived);

        assertEquals(facets, result.disposition().ampdFacets());
        assertEquals(profiles, result.disposition().situationalProfiles());
    }
}
