package io.casehub.neocortex.cognitive.eidos;

import io.casehub.eidos.api.AgentDisposition;
import io.casehub.eidos.api.DispositionValue;
import io.casehub.neocortex.cognitive.index.LivedComposition;
import io.casehub.neocortex.cognitive.index.LivedProfile;
import io.casehub.neocortex.cognitive.index.PersonalityStateSnapshot;
import io.casehub.neocortex.cognitive.index.PersonalityStateSource;
import io.casehub.neocortex.cognitive.index.WeightedTerm;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;


class EidosLivedDispositionSourceTest {

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

    private static String fingerprint(List<DispositionValue> profile) {
        var terms = profile.stream()
                .map(dv -> new WeightedTerm(dv.term(), dv.weight()))
                .toList();
        return LivedComposition.fingerprint(terms);
    }

    private static PersonalityStateSource sourceReturning(PersonalityStateSnapshot snapshot) {
        return (tenantId, agentId, now) -> Optional.of(snapshot);
    }

    @Test
    void returnsComposedDispositionWhenLivedProfileExists() {
        var authored = AgentDisposition.builder()
                .socialOrient("cooperative")
                .dispositionProfile(TI_NE_PROFILE)
                .build();

        var livedTerms = NE_TI_PROFILE.stream()
                .map(dv -> new WeightedTerm(dv.term(), dv.weight()))
                .toList();
        var lived = new LivedProfile(livedTerms, fingerprint(TI_NE_PROFILE));
        var snapshot = new PersonalityStateSnapshot(Map.of(), 1, 1, lived);

        var sut = new EidosLivedDispositionSource(sourceReturning(snapshot));
        var result = sut.resolveEffective("agent-1", "tenant-1", authored);

        assertEquals("Ne", result.dispositionProfile().get(0).term());
        assertEquals(0.35, result.dispositionProfile().get(0).weight(), 0.001);
        assertEquals("Ti", result.dispositionProfile().get(1).term());
        assertEquals(0.20, result.dispositionProfile().get(1).weight(), 0.001);
    }

    @Test
    void returnsAuthoredWhenNoLivedProfile() {
        var sut = new EidosLivedDispositionSource(sourceReturning(PersonalityStateSnapshot.empty()));
        var authored = AgentDisposition.builder()
                .socialOrient("cooperative")
                .dispositionProfile(TI_NE_PROFILE)
                .build();

        var result = sut.resolveEffective("agent-1", "tenant-1", authored);

        assertSame(authored, result);
    }

    @Test
    void returnsAuthoredWhenSnapshotEmpty() {
        PersonalityStateSource source = (tid, aid, now) -> Optional.empty();
        var sut = new EidosLivedDispositionSource(source);
        var authored = AgentDisposition.builder()
                .socialOrient("cooperative")
                .build();

        var result = sut.resolveEffective("agent-1", "tenant-1", authored);

        assertSame(authored, result);
    }

    @Test
    void returnsAuthoredWhenNoStateSource() {
        var sut = new EidosLivedDispositionSource((PersonalityStateSource) null);
        var authored = AgentDisposition.builder()
                .socialOrient("cooperative")
                .build();

        var result = sut.resolveEffective("agent-1", "tenant-1", authored);

        assertSame(authored, result);
    }

    @Test
    void returnsAuthoredWhenFingerprintMismatches() {
        var authored = AgentDisposition.builder()
                .socialOrient("cooperative")
                .dispositionProfile(TI_NE_PROFILE)
                .build();

        var livedTerms = NE_TI_PROFILE.stream()
                .map(dv -> new WeightedTerm(dv.term(), dv.weight()))
                .toList();
        var lived = new LivedProfile(livedTerms, "wrong-fingerprint");
        var snapshot = new PersonalityStateSnapshot(Map.of(), 1, 1, lived);

        var sut = new EidosLivedDispositionSource(sourceReturning(snapshot));
        var result = sut.resolveEffective("agent-1", "tenant-1", authored);

        assertSame(authored, result);
    }

    @Test
    void resolveEffectiveComposesFacetsWhenDisplacementPresent() {
        var authored = AgentDisposition.builder()
                                       .socialOrient("cooperative")
                                       .dispositionProfile(TI_NE_PROFILE)
                                       .ampdFacets(List.of(
                                               new io.casehub.eidos.api.AmpdFacet("Anxiousness", 50),
                                               new io.casehub.eidos.api.AmpdFacet("Callousness", 20)))
                                       .build();
        var facets = Map.of(
                "Anxiousness", new PersonalityStateSnapshot.FacetDisplacement(0.1, 0.0, 0.05),
                "Callousness", new PersonalityStateSnapshot.FacetDisplacement(-0.1, 0.0, 0.0));
        var                    snapshot = new PersonalityStateSnapshot(facets, 1, 1, null);
        PersonalityStateSource source   = (tenantId, agentId, now) -> Optional.of(snapshot);

        var sut    = new EidosLivedDispositionSource(source);
        var result = sut.resolveEffective("agent-1", "tenant-1", authored);

        var anxiousness = result.ampdFacets().stream()
                                .filter(f -> "Anxiousness".equals(f.facet())).findFirst().orElseThrow();
        assertEquals(65, anxiousness.score());

        var callousness = result.ampdFacets().stream()
                                .filter(f -> "Callousness".equals(f.facet())).findFirst().orElseThrow();
        assertEquals(10, callousness.score());
    }

    @Test
    void resolveEffectiveClampsFacetScoresTo0And100() {
        var authored = AgentDisposition.builder()
                                       .socialOrient("cooperative")
                                       .dispositionProfile(TI_NE_PROFILE)
                                       .ampdFacets(List.of(new io.casehub.eidos.api.AmpdFacet("Anxiousness", 90)))
                                       .build();
        var facets = Map.of(
                "Anxiousness", new PersonalityStateSnapshot.FacetDisplacement(0.3, 0.0, 0.0));
        var                    snapshot = new PersonalityStateSnapshot(facets, 1, 1, null);
        PersonalityStateSource source   = (tenantId, agentId, now) -> Optional.of(snapshot);

        var sut    = new EidosLivedDispositionSource(source);
        var result = sut.resolveEffective("agent-1", "tenant-1", authored);

        var anxiousness = result.ampdFacets().stream()
                                .filter(f -> "Anxiousness".equals(f.facet())).findFirst().orElseThrow();
        assertEquals(100, anxiousness.score());
    }

    @Test
    void resolveEffectiveIgnoresTransientDisplacement() {
        var authored = AgentDisposition.builder()
                                       .socialOrient("cooperative")
                                       .dispositionProfile(TI_NE_PROFILE)
                                       .ampdFacets(List.of(new io.casehub.eidos.api.AmpdFacet("Anxiousness", 50)))
                                       .build();
        var facets = Map.of(
                "Anxiousness", new PersonalityStateSnapshot.FacetDisplacement(0.0, 0.3, 0.0));
        var                    snapshot = new PersonalityStateSnapshot(facets, 1, 1, null);
        PersonalityStateSource source   = (tenantId, agentId, now) -> Optional.of(snapshot);

        var sut    = new EidosLivedDispositionSource(source);
        var result = sut.resolveEffective("agent-1", "tenant-1", authored);

        var anxiousness = result.ampdFacets().stream()
                                .filter(f -> "Anxiousness".equals(f.facet())).findFirst().orElseThrow();
        assertEquals(50, anxiousness.score());
    }
}
