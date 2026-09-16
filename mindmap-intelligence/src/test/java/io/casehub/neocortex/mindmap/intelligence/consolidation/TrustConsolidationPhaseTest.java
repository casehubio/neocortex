package io.casehub.neocortex.mindmap.intelligence.consolidation;

import io.casehub.blocks.trust.OverlayTrustPropertyModel;
import io.casehub.blocks.trust.TrustEvolutionConfig;
import io.casehub.blocks.trust.TrustEvolutionConfig.ConsolidationConfig;
import io.casehub.blocks.trust.TrustEvolutionConfig.LevelConfig;
import io.casehub.blocks.trust.TrustEvolutionConfig.ScoringConfig;
import io.casehub.blocks.trust.TrustEvolutionConfig.TrustEventMapping;
import io.casehub.ledger.api.model.AttestationSummary;
import io.casehub.ledger.api.model.AttestationVerdict;
import io.casehub.ledger.api.model.LedgerAttestation;
import io.casehub.ledger.api.model.LedgerEntry;
import io.casehub.ledger.api.model.LedgerEntryType;
import io.casehub.ledger.api.spi.LedgerEntryRepository;
import io.casehub.neocortex.mindmap.MindMapNode;
import io.casehub.neocortex.mindmap.MindMapStore;
import io.casehub.neocortex.mindmap.NodeInput;
import io.casehub.neocortex.mindmap.OverlayRef;
import io.casehub.neocortex.mindmap.SubgraphInput;
import io.casehub.neocortex.mindmap.inmem.InMemoryMindMapStore;
import io.casehub.platform.api.identity.PrincipalId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

class TrustConsolidationPhaseTest {

    private static final String TENANT = "test-tenant";

    private InMemoryMindMapStore mindMapStore;
    private TestLedgerRepo ledgerRepo;
    private TrustConsolidationPhase phase;

    @BeforeEach
    void setUp() {
        mindMapStore = new InMemoryMindMapStore();
        ledgerRepo = new TestLedgerRepo();
        var config = new TrustEvolutionConfig(
            List.of(new TrustEventMapping("STEAL", AttestationVerdict.FLAGGED, 0.9, 0.5)),
            new ScoringConfig(30, 1.5),
            new ConsolidationConfig("trust-score", 0.15),
            new LevelConfig(0.7, 0.4, 0.2));
        phase = new TrustConsolidationPhase(mindMapStore, ledgerRepo, config);
    }

    @Test
    void updatesOverlayNodeWithTrustScoreAfterConsolidation() {
        String subgraphId = seedPeopleSubgraph("observer", "target");
        String overlayId = getOverlayNodeId(subgraphId, "observer");

        addSingleNegativeAttestation("target", "observer");

        phase.run(TENANT, List.of());

        MindMapNode updated = mindMapStore.getNode(overlayId, TENANT);
        assertThat(updated.property(OverlayTrustPropertyModel.TRUST_SCORE)).isPresent();
        double trustScore = Double.parseDouble(updated.property(OverlayTrustPropertyModel.TRUST_SCORE).get());
        assertThat(trustScore).isLessThan(0.5);

        assertThat(updated.property(OverlayTrustPropertyModel.TRUST_ALPHA)).isPresent();
        assertThat(updated.property(OverlayTrustPropertyModel.TRUST_BETA)).isPresent();
    }

    @Test
    void perRelationshipFilteringProducesDifferentScores() {
        String subgraphId = seedPeopleSubgraphThreeAgents("observerA", "observerB", "target");
        String overlayAId = getOverlayNodeId(subgraphId, "observerA");
        String overlayBId = getOverlayNodeId(subgraphId, "observerB");

        addAttestationsForTwoObservers("target", "observerA", "observerB");

        phase.run(TENANT, List.of());

        MindMapNode overlayA = mindMapStore.getNode(overlayAId, TENANT);
        MindMapNode overlayB = mindMapStore.getNode(overlayBId, TENANT);

        double scoreA = Double.parseDouble(overlayA.property(OverlayTrustPropertyModel.TRUST_SCORE).get());
        double scoreB = Double.parseDouble(overlayB.property(OverlayTrustPropertyModel.TRUST_SCORE).get());
        assertThat(scoreA).isNotEqualTo(scoreB);
    }

    @Test
    void emptyAttestationHistoryDoesNotWriteTrustScore() {
        String subgraphId = seedPeopleSubgraph("observer", "target");
        String overlayId = getOverlayNodeId(subgraphId, "observer");

        phase.run(TENANT, List.of());

        MindMapNode overlay = mindMapStore.getNode(overlayId, TENANT);
        assertThat(overlay.property(OverlayTrustPropertyModel.TRUST_SCORE)).isEmpty();
    }

    @Test
    void thresholdGatingSuppressesSmallChanges() {
        String subgraphId = seedPeopleSubgraph("observer", "target");
        String overlayId = getOverlayNodeId(subgraphId, "observer");

        addSingleNegativeAttestation("target", "observer");
        phase.run(TENANT, List.of());

        MindMapNode afterFirst = mindMapStore.getNode(overlayId, TENANT);
        assertThat(afterFirst.property(OverlayTrustPropertyModel.TRUST_LAST_RENDERED)).isPresent();

        addSingleNegativeAttestation("target", "observer");
        phase.run(TENANT, List.of());

        MindMapNode afterSecond = mindMapStore.getNode(overlayId, TENANT);
        double score = Double.parseDouble(afterSecond.property(OverlayTrustPropertyModel.TRUST_SCORE).get());
        double lastRendered = Double.parseDouble(afterSecond.property(OverlayTrustPropertyModel.TRUST_LAST_RENDERED).get());
        assertThat(Math.abs(score - lastRendered)).isLessThanOrEqualTo(0.15);
    }

    @Test
    void phaseNameIsCorrect() {
        assertThat(phase.name()).isEqualTo("trust-consolidation");
    }

    private String seedPeopleSubgraph(String observer, String target) {
        String sgId = mindMapStore.createSubgraph(new SubgraphInput("people", "social", null), TENANT);

        String targetNodeId = mindMapStore.addNode(
            NodeInput.of(target, sgId)
                .withTraits(Set.of("Entitylike"))
                .withProperties(Map.of("agentId", target))
                .withPad(0.0, 0.0, 0.0),
            TENANT);

        mindMapStore.addNode(
            NodeInput.of(observer + "-sees-" + target, sgId)
                .withTraits(Set.of("overlay"))
                .withRefs(Set.of(OverlayRef.of(targetNodeId)))
                .withProperties(Map.of(OverlayRef.AGENT_ID, observer))
                .withPrincipalId(PrincipalId.agent(observer))
                .withPad(0.0, 0.0, 0.0),
            TENANT);

        return sgId;
    }

    private String seedPeopleSubgraphThreeAgents(String obsA, String obsB, String target) {
        String sgId = mindMapStore.createSubgraph(new SubgraphInput("people", "social", null), TENANT);

        String targetNodeId = mindMapStore.addNode(
            NodeInput.of(target, sgId)
                .withTraits(Set.of("Entitylike"))
                .withProperties(Map.of("agentId", target))
                .withPad(0.0, 0.0, 0.0),
            TENANT);

        mindMapStore.addNode(
            NodeInput.of(obsA + "-sees-" + target, sgId)
                .withTraits(Set.of("overlay"))
                .withRefs(Set.of(OverlayRef.of(targetNodeId)))
                .withProperties(Map.of(OverlayRef.AGENT_ID, obsA))
                .withPrincipalId(PrincipalId.agent(obsA))
                .withPad(0.0, 0.0, 0.0),
            TENANT);

        mindMapStore.addNode(
            NodeInput.of(obsB + "-sees-" + target, sgId)
                .withTraits(Set.of("overlay"))
                .withRefs(Set.of(OverlayRef.of(targetNodeId)))
                .withProperties(Map.of(OverlayRef.AGENT_ID, obsB))
                .withPrincipalId(PrincipalId.agent(obsB))
                .withPad(0.0, 0.0, 0.0),
            TENANT);

        return sgId;
    }

    private String getOverlayNodeId(String subgraphId, String observer) {
        return mindMapStore.nodesIn(subgraphId, TENANT).stream()
            .filter(n -> n.traits().contains("overlay"))
            .filter(n -> observer.equals(n.property(OverlayRef.AGENT_ID).orElse(null)))
            .map(MindMapNode::id)
            .findFirst()
            .orElseThrow();
    }

    private void addSingleNegativeAttestation(String target, String observer) {
        var entry = new TestLedgerEntry();
        entry.id = UUID.randomUUID();
        entry.actorId = target;
        entry.entryType = LedgerEntryType.EVENT;
        entry.subjectId = UUID.nameUUIDFromBytes(target.getBytes());
        entry.occurredAt = Instant.now();
        ledgerRepo.entries.add(entry);

        var attestation = new LedgerAttestation();
        attestation.id = UUID.randomUUID();
        attestation.ledgerEntryId = entry.id;
        attestation.attestorId = observer;
        attestation.verdict = AttestationVerdict.FLAGGED;
        attestation.confidence = 0.9;
        attestation.occurredAt = Instant.now();
        ledgerRepo.attestations.add(attestation);
    }

    private void addAttestationsForTwoObservers(String target, String obsA, String obsB) {
        var entry = new TestLedgerEntry();
        entry.id = UUID.randomUUID();
        entry.actorId = target;
        entry.entryType = LedgerEntryType.EVENT;
        entry.subjectId = UUID.nameUUIDFromBytes(target.getBytes());
        entry.occurredAt = Instant.now();
        ledgerRepo.entries.add(entry);

        var attA = new LedgerAttestation();
        attA.id = UUID.randomUUID();
        attA.ledgerEntryId = entry.id;
        attA.attestorId = obsA;
        attA.verdict = AttestationVerdict.FLAGGED;
        attA.confidence = 0.9;
        attA.occurredAt = Instant.now();
        ledgerRepo.attestations.add(attA);

        var attB = new LedgerAttestation();
        attB.id = UUID.randomUUID();
        attB.ledgerEntryId = entry.id;
        attB.attestorId = obsB;
        attB.verdict = AttestationVerdict.SOUND;
        attB.confidence = 0.7;
        attB.occurredAt = Instant.now();
        ledgerRepo.attestations.add(attB);
    }

    static class TestLedgerEntry extends LedgerEntry {}

    static class TestLedgerRepo implements LedgerEntryRepository {
        final List<LedgerEntry> entries = new ArrayList<>();
        final List<LedgerAttestation> attestations = new ArrayList<>();

        @Override
        public List<LedgerEntry> findByActorId(String actorId, Instant from, Instant to, String tenancyId) {
            return entries.stream().filter(e -> actorId.equals(e.actorId)).toList();
        }

        @Override
        public List<LedgerAttestation> findAttestationsByEntryId(UUID entryId, String tenancyId) {
            return attestations.stream().filter(a -> entryId.equals(a.ledgerEntryId)).toList();
        }

        @Override public LedgerEntry save(LedgerEntry e, String t) { entries.add(e); return e; }
        @Override public LedgerAttestation saveAttestation(LedgerAttestation a, String t) { attestations.add(a); return a; }
        @Override public List<LedgerEntry> findBySubjectId(UUID s, String t) { return List.of(); }
        @Override public List<LedgerEntry> findBySubjectIdAndTimeRange(UUID s, Instant f, Instant to, String t) { return List.of(); }
        @Override public Optional<LedgerEntry> findLatestBySubjectId(UUID s, String t) { return Optional.empty(); }
        @Override public Optional<LedgerEntry> findEntryById(UUID i, String t) { return Optional.empty(); }
        @Override public List<LedgerEntry> findByActorRole(String r, Instant f, Instant t, String te) { return List.of(); }
        @Override public List<LedgerEntry> findCausedBy(UUID e, String t) { return List.of(); }
        @Override public List<LedgerAttestation> findAttestationsByEntryIdAndCapabilityTag(UUID e, String c, String t) { return List.of(); }
        @Override public List<LedgerAttestation> findAttestationsByEntryIdGlobal(UUID e, String t) { return List.of(); }
        @Override public List<LedgerAttestation> findAttestationsByAttestorIdAndCapabilityTag(String a, String c, String t) { return List.of(); }
        @Override public Stream<LedgerEntry> streamBySubjectId(UUID s, String t) { return Stream.empty(); }
        @Override public Stream<LedgerEntry> streamByActorId(String a, Instant f, Instant t, String te) { return Stream.empty(); }
        @Override public List<LedgerEntry> findBySubjectIdPaged(UUID s, int a, int l, String t) { return List.of(); }
        @Override public Map<AttestationVerdict, Long> countByActorAndVerdict(String a, Instant f, Instant t, String te) { return Map.of(); }
        @Override public Map<AttestationVerdict, Long> countBySubjectAndVerdict(UUID s, Instant f, Instant t, String te) { return Map.of(); }
        @Override public AttestationSummary summariseAttestationsByActor(String a, Instant f, Instant t, String te) { return AttestationSummary.EMPTY; }
    }
}
