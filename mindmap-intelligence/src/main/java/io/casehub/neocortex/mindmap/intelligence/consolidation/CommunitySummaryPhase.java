package io.casehub.neocortex.mindmap.intelligence.consolidation;

import io.casehub.neocortex.mindmap.*;
import io.casehub.neocortex.mindmap.runtime.MindMapAnalyzer;
import io.casehub.platform.agent.AgentProvider;
import jakarta.annotation.Priority;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.*;
import java.util.logging.Logger;
import java.util.stream.Collectors;

@ApplicationScoped
@Priority(30)
public class CommunitySummaryPhase implements ConsolidationPhase {

    private static final Logger LOG = Logger.getLogger(
        CommunitySummaryPhase.class.getName());

    private final MindMapStore store;
    private final AgentProvider agentProvider;
    private final int k;
    private final int minClusterSize;
    private final int maxPerPass;

    @Inject
    public CommunitySummaryPhase(MindMapStore store,
                                  Instance<AgentProvider> agentProvider) {
        this(store,
            agentProvider.isResolvable() ? agentProvider.get() : null,
            3, 4, 5);
    }

    CommunitySummaryPhase(MindMapStore store, AgentProvider agentProvider,
                           int k, int minClusterSize, int maxPerPass) {
        this.store = store;
        this.agentProvider = agentProvider;
        this.k = k;
        this.minClusterSize = minClusterSize;
        this.maxPerPass = maxPerPass;
    }

    @Override
    public String name() {
        return "community-summary";
    }

    @Override
    public void run(String tenantId, List<String> subgraphPriority) {
        List<String> sgIds = orderedSubgraphs(tenantId, subgraphPriority);
        int generated = 0;

        for (String sgId : sgIds) {
            if (generated >= maxPerPass) break;
            List<MindMapAnalyzer.KCore> rawCores = MindMapAnalyzer.kCores(store, sgId, tenantId, k);
            List<MindMapAnalyzer.KCore> cores = rawCores.stream()
                .map(core -> {
                    Set<String> filtered = core.nodeIds().stream()
                        .filter(id -> {
                            try { return !store.getNode(id, tenantId).traits().contains("Summary"); }
                            catch (Exception e) { return true; }
                        })
                        .collect(Collectors.toSet());
                    return new MindMapAnalyzer.KCore(filtered, core.density());
                })
                .filter(core -> !core.nodeIds().isEmpty())
                .toList();
            Set<String> currentHashes = new HashSet<>();

            for (MindMapAnalyzer.KCore core : cores) {
                String coreHash = hashIds(core.nodeIds());
                currentHashes.add(coreHash);
            }

            cleanupStaleSummaries(sgId, tenantId, currentHashes);

            for (MindMapAnalyzer.KCore core : cores) {
                if (generated >= maxPerPass) break;
                if (core.nodeIds().size() < minClusterSize) continue;

                String coreHash = hashIds(core.nodeIds());
                String memberHash = computeMemberHash(core.nodeIds(), tenantId);

                var existing = findSummaryByCoreHash(sgId, tenantId, coreHash);
                if (existing.isPresent()) {
                    MindMapNode summary = existing.get();
                    if (memberHash.equals(summary.property("memberHash").orElse(""))) {
                        continue;
                    }
                    String title = generateTitle(core.nodeIds(), tenantId);
                    store.updateNode(summary.id(),
                        NodeUpdate.empty().withName(title).withPropertiesToSet(Map.of(
                            "memberHash", memberHash,
                            "memberCount", String.valueOf(core.nodeIds().size()),
                            "generatedAt", Instant.now().toString())),
                        tenantId);
                    generated++;
                } else {
                    String title = generateTitle(core.nodeIds(), tenantId);
                    String summaryId = store.addNode(
                        NodeInput.of(title, sgId)
                            .withTraits(Set.of("Summary"))
                            .withProperties(Map.of(
                                "coreHash", coreHash,
                                "memberHash", memberHash,
                                "memberCount", String.valueOf(core.nodeIds().size()),
                                "generatedAt", Instant.now().toString())),
                        tenantId);
                    for (String nodeId : core.nodeIds()) {
                        store.addEdge(EdgeInput.of(summaryId, nodeId, "summarizes"), tenantId);
                    }
                    generated++;
                }
            }
        }
    }

    private void cleanupStaleSummaries(String sgId, String tenantId,
                                        Set<String> currentHashes) {
        List<MindMapNode> summaries = store.nodesIn(sgId, tenantId).stream()
            .filter(n -> n.traits().contains("Summary"))
            .toList();
        for (MindMapNode summary : summaries) {
            String hash = summary.property("coreHash").orElse("");
            if (!currentHashes.contains(hash)) {
                store.eraseNode(summary.id(), tenantId);
            }
        }
    }

    private Optional<MindMapNode> findSummaryByCoreHash(String sgId, String tenantId,
                                                          String coreHash) {
        return store.nodesIn(sgId, tenantId).stream()
            .filter(n -> n.traits().contains("Summary"))
            .filter(n -> coreHash.equals(n.property("coreHash").orElse("")))
            .findFirst();
    }

    private String generateTitle(Set<String> nodeIds, String tenantId) {
        List<String> names = nodeIds.stream()
            .map(id -> { try { return store.getNode(id, tenantId).name(); }
                         catch (Exception e) { return id; } })
            .sorted()
            .toList();
        return "Summary: " + String.join(", ",
            names.subList(0, Math.min(3, names.size())));
    }

    private String computeMemberHash(Set<String> nodeIds, String tenantId) {
        List<String> parts = nodeIds.stream().sorted().map(id -> {
            try {
                MindMapNode node = store.getNode(id, tenantId);
                return id + ":" + (node != null ? node.name() : "");
            } catch (Exception e) { return id; }
        }).toList();
        return hashString(String.join("|", parts));
    }

    private String hashIds(Set<String> ids) {
        return hashString(ids.stream().sorted().collect(Collectors.joining("|")));
    }

    private String hashString(String input) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] hash = md.digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < 8; i++) {
                sb.append(String.format("%02x", hash[i]));
            }
            return sb.toString();
        } catch (Exception e) {
            return Integer.toHexString(input.hashCode());
        }
    }

    private List<String> orderedSubgraphs(String tenantId, List<String> priority) {
        List<String> all = store.listSubgraphs(tenantId).stream()
            .map(MindMapSubgraph::id)
            .collect(Collectors.toCollection(ArrayList::new));
        List<String> ordered = new ArrayList<>();
        for (String sgId : priority) {
            if (all.remove(sgId)) ordered.add(sgId);
        }
        ordered.addAll(all);
        return ordered;
    }
}
