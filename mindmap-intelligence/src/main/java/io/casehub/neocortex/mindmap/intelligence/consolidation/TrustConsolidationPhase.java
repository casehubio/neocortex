package io.casehub.neocortex.mindmap.intelligence.consolidation;

import io.casehub.blocks.trust.OverlayTrustPropertyModel;
import io.casehub.blocks.trust.TrustEvolutionConfig;
import io.casehub.ledger.api.model.AttestationVerdict;
import io.casehub.ledger.api.model.LedgerAttestation;
import io.casehub.ledger.api.model.LedgerEntry;
import io.casehub.ledger.api.spi.LedgerEntryRepository;
import io.casehub.ledger.core.trust.DecayFunction;
import io.casehub.ledger.core.trust.TrustScoreComputer;
import io.casehub.neocortex.mindmap.MindMapNode;
import io.casehub.neocortex.mindmap.MindMapStore;
import io.casehub.neocortex.mindmap.MindMapSubgraph;
import io.casehub.neocortex.mindmap.NodeUpdate;
import io.casehub.neocortex.mindmap.OverlayRef;
import jakarta.annotation.Priority;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;

@ApplicationScoped
@Priority(22)
public class TrustConsolidationPhase implements ConsolidationPhase {

    private static final Logger LOG = Logger.getLogger(TrustConsolidationPhase.class.getName());
    private static final String PEOPLE_SUBGRAPH = "people";

    private final MindMapStore mindMapStore;
    private final LedgerEntryRepository ledgerRepo;
    private final TrustEvolutionConfig config;

    @Inject
    public TrustConsolidationPhase(MindMapStore mindMapStore,
                                    Instance<LedgerEntryRepository> ledgerRepo,
                                    Instance<TrustEvolutionConfig> config) {
        this.mindMapStore = mindMapStore;
        this.ledgerRepo = ledgerRepo.isResolvable() ? ledgerRepo.get() : null;
        this.config = config.isResolvable() ? config.get() : null;
    }

    TrustConsolidationPhase(MindMapStore mindMapStore,
                             LedgerEntryRepository ledgerRepo,
                             TrustEvolutionConfig config) {
        this.mindMapStore = mindMapStore;
        this.ledgerRepo = ledgerRepo;
        this.config = config;
    }

    @Override
    public String name() {
        return "trust-consolidation";
    }

    @Override
    public void run(String tenantId, List<String> subgraphPriority) {
        if (config == null || ledgerRepo == null) return;

        Optional<String> peopleSubgraphId = mindMapStore.listSubgraphs(tenantId).stream()
            .filter(sg -> PEOPLE_SUBGRAPH.equals(sg.name()))
            .map(MindMapSubgraph::id)
            .findFirst();

        if (peopleSubgraphId.isEmpty()) return;

        List<MindMapNode> nodes = mindMapStore.nodesIn(peopleSubgraphId.get(), tenantId);

        Map<String, MindMapNode> sharedNodes = new HashMap<>();
        for (MindMapNode node : nodes) {
            if (!node.traits().contains("overlay")) {
                node.property("agentId").ifPresent(aid -> sharedNodes.put(node.id(), node));
            }
        }

        Instant now = Instant.now();
        DecayFunction decay = buildDecayFunction();
        TrustScoreComputer computer = new TrustScoreComputer(decay);

        for (MindMapNode overlay : nodes) {
            if (!overlay.traits().contains("overlay")) continue;

            try {
                processOverlayNode(overlay, sharedNodes, computer, now, tenantId);
            } catch (Exception e) {
                LOG.log(Level.WARNING, "Failed to process trust for overlay " + overlay.id(), e);
            }
        }
    }

    private void processOverlayNode(MindMapNode overlay, Map<String, MindMapNode> sharedNodes,
                                     TrustScoreComputer computer, Instant now, String tenantId) {
        String observerId = overlay.property(OverlayRef.AGENT_ID).orElse(null);
        if (observerId == null) return;

        String targetNodeId = OverlayRef.sharedNodeId(overlay).orElse(null);
        if (targetNodeId == null) return;

        MindMapNode sharedNode = sharedNodes.get(targetNodeId);
        if (sharedNode == null) return;

        String targetId = sharedNode.property("agentId").orElse(null);
        if (targetId == null) return;

        List<LedgerEntry> entries = ledgerRepo.findByActorId(
            targetId, Instant.EPOCH, now, tenantId);
        if (entries.isEmpty()) return;

        Map<UUID, List<LedgerAttestation>> attestationMap = new HashMap<>();
        for (LedgerEntry entry : entries) {
            List<LedgerAttestation> allAttestations = ledgerRepo.findAttestationsByEntryId(
                entry.id, tenantId);
            List<LedgerAttestation> filtered = allAttestations.stream()
                .filter(a -> observerId.equals(a.attestorId))
                .toList();
            if (!filtered.isEmpty()) {
                attestationMap.put(entry.id, filtered);
            }
        }

        if (attestationMap.isEmpty()) return;

        TrustScoreComputer.ActorScore score = computer.compute(entries, attestationMap, now);

        Map<String, String> properties = new HashMap<>();
        properties.put(OverlayTrustPropertyModel.TRUST_SCORE, String.valueOf(score.trustScore()));
        properties.put(OverlayTrustPropertyModel.TRUST_ALPHA, String.valueOf(score.alpha()));
        properties.put(OverlayTrustPropertyModel.TRUST_BETA, String.valueOf(score.beta()));

        String previousRendered = overlay.property(OverlayTrustPropertyModel.TRUST_LAST_RENDERED).orElse(null);
        double previousScore = previousRendered != null ? Double.parseDouble(previousRendered) : -1.0;
        if (Math.abs(score.trustScore() - previousScore) > config.consolidation().significantChangeThreshold()) {
            properties.put(OverlayTrustPropertyModel.TRUST_LAST_RENDERED, String.valueOf(score.trustScore()));
        }

        mindMapStore.updateNode(overlay.id(),
            NodeUpdate.empty().withPropertiesToSet(properties), tenantId);
    }

    private DecayFunction buildDecayFunction() {
        int halfLife = config.scoring().decayHalfLifeDays();
        double negMultiplier = config.scoring().negativeDecayMultiplier();
        return (ageInDays, verdict) -> {
            int effectiveHalfLife = (verdict == AttestationVerdict.FLAGGED || verdict == AttestationVerdict.CHALLENGED)
                ? (int) (halfLife * negMultiplier)
                : halfLife;
            return Math.pow(2.0, -(double) ageInDays / effectiveHalfLife);
        };
    }
}
