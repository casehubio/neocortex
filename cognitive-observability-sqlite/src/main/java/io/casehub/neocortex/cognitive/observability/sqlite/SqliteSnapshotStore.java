package io.casehub.neocortex.cognitive.observability.sqlite;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import io.casehub.neocortex.cognitive.observability.ConsolidationAuditEntry;
import io.casehub.neocortex.cognitive.observability.GraphMutation;
import io.casehub.neocortex.cognitive.observability.GraphSnapshot;
import io.casehub.neocortex.cognitive.observability.SnapshotRetentionPolicy;
import io.casehub.neocortex.cognitive.observability.SnapshotStore;
import org.flywaydb.core.Flyway;
import org.sqlite.SQLiteConfig;
import org.sqlite.SQLiteDataSource;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

public class SqliteSnapshotStore implements SnapshotStore, AutoCloseable {

    private final HikariDataSource dataSource;
    private final ObjectMapper mapper;

    public SqliteSnapshotStore(String path) {
        boolean isMemory = ":memory:".equals(path) || path.isBlank();

        SQLiteConfig config = new SQLiteConfig();
        if (!isMemory) {
            config.setJournalMode(SQLiteConfig.JournalMode.WAL);
        }
        config.setSynchronous(SQLiteConfig.SynchronousMode.NORMAL);
        config.setBusyTimeout(5000);

        SQLiteDataSource sqDs = new SQLiteDataSource(config);
        sqDs.setUrl("jdbc:sqlite:" + path);

        HikariConfig hikari = new HikariConfig();
        hikari.setDataSource(sqDs);
        hikari.setMaximumPoolSize(isMemory ? 1 : 3);
        hikari.setPoolName("observability-snapshot");
        this.dataSource = new HikariDataSource(hikari);

        this.mapper = new ObjectMapper()
            .findAndRegisterModules()
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

        Flyway.configure()
            .dataSource(dataSource)
            .locations("classpath:db/observability")
            .load()
            .migrate();
    }

    @Override
    public void storeMutation(String tenantId, GraphMutation mutation) {
        String id = UUID.randomUUID().toString();
        String mutationType = mutation.getClass().getSimpleName();
        String subgraphId = subgraphIdOf(mutation);
        String source = mutation.source();
        String timestamp = mutation.timestamp().toString();
        String data = toJson(mutation);
        Set<String> nodeIds = extractNodeIds(mutation);

        try (Connection conn = dataSource.getConnection()) {
            try (PreparedStatement ps = conn.prepareStatement(
                "INSERT INTO mutations (id, tenant_id, subgraph_id, source, mutation_type, timestamp, data) VALUES (?, ?, ?, ?, ?, ?, ?)")) {
                ps.setString(1, id);
                ps.setString(2, tenantId);
                ps.setString(3, subgraphId);
                ps.setString(4, source);
                ps.setString(5, mutationType);
                ps.setString(6, timestamp);
                ps.setString(7, data);
                ps.executeUpdate();
            }
            if (!nodeIds.isEmpty()) {
                try (PreparedStatement ps = conn.prepareStatement(
                    "INSERT INTO mutation_nodes (mutation_id, node_id, tenant_id) VALUES (?, ?, ?)")) {
                    for (String nodeId : nodeIds) {
                        ps.setString(1, id);
                        ps.setString(2, nodeId);
                        ps.setString(3, tenantId);
                        ps.addBatch();
                    }
                    ps.executeBatch();
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to store mutation", e);
        }
    }

    @Override
    public List<GraphMutation> findMutations(String tenantId, String subgraphId, Instant from, Instant to) {
        String sql = subgraphId != null
            ? "SELECT data FROM mutations WHERE tenant_id = ? AND (subgraph_id = ? OR subgraph_id IS NULL) AND timestamp >= ? AND timestamp <= ? ORDER BY timestamp"
            : "SELECT data FROM mutations WHERE tenant_id = ? AND timestamp >= ? AND timestamp <= ? ORDER BY timestamp";
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            int idx = 1;
            ps.setString(idx++, tenantId);
            if (subgraphId != null) ps.setString(idx++, subgraphId);
            ps.setString(idx++, from.toString());
            ps.setString(idx++, to.toString());
            return readMutations(ps);
        } catch (SQLException e) {
            throw new RuntimeException("Failed to find mutations", e);
        }
    }

    @Override
    public List<GraphMutation> findMutationsForEntity(String tenantId, String nodeId, Instant from, Instant to) {
        String sql = "SELECT m.data FROM mutations m JOIN mutation_nodes mn ON m.id = mn.mutation_id WHERE mn.tenant_id = ? AND mn.node_id = ? AND m.timestamp >= ? AND m.timestamp <= ? ORDER BY m.timestamp";
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, tenantId);
            ps.setString(2, nodeId);
            ps.setString(3, from.toString());
            ps.setString(4, to.toString());
            return readMutations(ps);
        } catch (SQLException e) {
            throw new RuntimeException("Failed to find mutations for entity", e);
        }
    }

    @Override
    public void storeKeyframe(GraphSnapshot keyframe) {
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                 "INSERT INTO keyframes (id, tenant_id, subgraph_id, captured_at, data) VALUES (?, ?, ?, ?, ?)")) {
            ps.setString(1, keyframe.snapshotId());
            ps.setString(2, keyframe.tenantId());
            ps.setString(3, keyframe.subgraphId());
            ps.setString(4, keyframe.capturedAt().toString());
            ps.setString(5, toJson(keyframe));
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("Failed to store keyframe", e);
        }
    }

    @Override
    public GraphSnapshot reconstruct(String tenantId, String subgraphId, Instant pointInTime) {
        return latestKeyframeBefore(tenantId, subgraphId, pointInTime).orElse(null);
    }

    @Override
    public Optional<GraphSnapshot> latestKeyframe(String tenantId, String subgraphId) {
        return latestKeyframeBefore(tenantId, subgraphId, Instant.now());
    }

    private Optional<GraphSnapshot> latestKeyframeBefore(String tenantId, String subgraphId, Instant before) {
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                 "SELECT data FROM keyframes WHERE tenant_id = ? AND subgraph_id = ? AND captured_at <= ? ORDER BY captured_at DESC LIMIT 1")) {
            ps.setString(1, tenantId);
            ps.setString(2, subgraphId);
            ps.setString(3, before.toString());
            ResultSet rs = ps.executeQuery();
            if (rs.next()) {
                return Optional.of(fromJson(rs.getString("data"), GraphSnapshot.class));
            }
            return Optional.empty();
        } catch (SQLException e) {
            throw new RuntimeException("Failed to find keyframe", e);
        }
    }

    @Override
    public long mutationCountSinceKeyframe(String tenantId, String subgraphId) {
        Optional<Instant> since = latestKeyframe(tenantId, subgraphId).map(GraphSnapshot::capturedAt);
        String ts = since.orElse(Instant.EPOCH).toString();
        String sql = subgraphId != null
            ? "SELECT COUNT(*) FROM mutations WHERE tenant_id = ? AND (subgraph_id = ? OR subgraph_id IS NULL) AND timestamp > ?"
            : "SELECT COUNT(*) FROM mutations WHERE tenant_id = ? AND timestamp > ?";
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            int idx = 1;
            ps.setString(idx++, tenantId);
            if (subgraphId != null) ps.setString(idx++, subgraphId);
            ps.setString(idx++, ts);
            ResultSet rs = ps.executeQuery();
            return rs.next() ? rs.getLong(1) : 0;
        } catch (SQLException e) {
            throw new RuntimeException("Failed to count mutations", e);
        }
    }

    @Override
    public void storeAuditEntry(ConsolidationAuditEntry entry) {
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                 "INSERT INTO audit_entries (id, tenant_id, started_at, completed_at, data) VALUES (?, ?, ?, ?, ?)")) {
            ps.setString(1, UUID.randomUUID().toString());
            ps.setString(2, entry.tenantId());
            ps.setString(3, entry.startedAt().toString());
            ps.setString(4, entry.completedAt().toString());
            ps.setString(5, toJson(entry));
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("Failed to store audit entry", e);
        }
    }

    @Override
    public List<ConsolidationAuditEntry> findAuditEntries(String tenantId, Instant from, Instant to) {
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                 "SELECT data FROM audit_entries WHERE tenant_id = ? AND completed_at >= ? AND completed_at <= ? ORDER BY completed_at")) {
            ps.setString(1, tenantId);
            ps.setString(2, from.toString());
            ps.setString(3, to.toString());
            ResultSet rs = ps.executeQuery();
            List<ConsolidationAuditEntry> results = new ArrayList<>();
            while (rs.next()) {
                results.add(fromJson(rs.getString("data"), ConsolidationAuditEntry.class));
            }
            return results;
        } catch (SQLException e) {
            throw new RuntimeException("Failed to find audit entries", e);
        }
    }

    @Override
    public Optional<Instant> lastConsolidationTime(String tenantId) {
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                 "SELECT MAX(completed_at) FROM audit_entries WHERE tenant_id = ?")) {
            ps.setString(1, tenantId);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) {
                String val = rs.getString(1);
                return val != null ? Optional.of(Instant.parse(val)) : Optional.empty();
            }
            return Optional.empty();
        } catch (SQLException e) {
            throw new RuntimeException("Failed to find last consolidation time", e);
        }
    }

    @Override
    public void purge(SnapshotRetentionPolicy policy) {
        Instant cutoff = Instant.now().minus(policy.maxAge());
        String cutoffStr = cutoff.toString();
        try (Connection conn = dataSource.getConnection()) {
            purgeTable(conn, "mutations", "timestamp", cutoffStr, policy.tenantId());
            purgeTable(conn, "keyframes", "captured_at", cutoffStr, policy.tenantId());
            purgeTable(conn, "audit_entries", "completed_at", cutoffStr, policy.tenantId());
            try (PreparedStatement ps = conn.prepareStatement("DELETE FROM mutation_nodes WHERE mutation_id NOT IN (SELECT id FROM mutations)")) {
                ps.executeUpdate();
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to purge", e);
        }
    }

    private void purgeTable(Connection conn, String table, String timeCol, String cutoff, String tenantId) throws SQLException {
        String sql = tenantId != null
            ? "DELETE FROM " + table + " WHERE tenant_id = ? AND " + timeCol + " < ?"
            : "DELETE FROM " + table + " WHERE " + timeCol + " < ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            int idx = 1;
            if (tenantId != null) ps.setString(idx++, tenantId);
            ps.setString(idx, cutoff);
            ps.executeUpdate();
        }
    }

    @Override
    public long count(String tenantId) {
        try (Connection conn = dataSource.getConnection()) {
            long mutations = countTable(conn, "mutations", tenantId);
            long keyframes = countTable(conn, "keyframes", tenantId);
            return mutations + keyframes;
        } catch (SQLException e) {
            throw new RuntimeException("Failed to count", e);
        }
    }

    private long countTable(Connection conn, String table, String tenantId) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(
            "SELECT COUNT(*) FROM " + table + " WHERE tenant_id = ?")) {
            ps.setString(1, tenantId);
            ResultSet rs = ps.executeQuery();
            return rs.next() ? rs.getLong(1) : 0;
        }
    }

    private List<GraphMutation> readMutations(PreparedStatement ps) throws SQLException {
        ResultSet rs = ps.executeQuery();
        List<GraphMutation> results = new ArrayList<>();
        while (rs.next()) {
            results.add(fromJson(rs.getString("data"), GraphMutation.class));
        }
        return results;
    }

    private String toJson(Object value) {
        try {
            return mapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("JSON serialization failed", e);
        }
    }

    private <T> T fromJson(String json, Class<T> type) {
        try {
            return mapper.readValue(json, type);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("JSON deserialization failed", e);
        }
    }

    private static Set<String> extractNodeIds(GraphMutation mutation) {
        return switch (mutation) {
            case GraphMutation.NodeAdded na -> Set.of(na.nodeId());
            case GraphMutation.NodeUpdated nu -> Set.of(nu.nodeId());
            case GraphMutation.NodeErased ne -> Set.of(ne.nodeId());
            case GraphMutation.EdgeAdded ea -> Set.of(ea.sourceNodeId(), ea.targetNodeId());
            case GraphMutation.EdgeRemoved er -> Set.of(er.sourceNodeId(), er.targetNodeId());
            case GraphMutation.NodesMerged nm -> Set.of(nm.survivorId(), nm.absorbedId());
            case GraphMutation.NodeSuperseded ns -> Set.of(ns.supersededId(), ns.supersedingId());
            case GraphMutation.NodeReinstated nr -> Set.of(nr.nodeId());
            case GraphMutation.AliasAdded aa -> Set.of(aa.nodeId());
            case GraphMutation.AliasRemoved ar -> Set.of(ar.nodeId());
            case GraphMutation.SubgraphCreated sc -> Set.of();
            case GraphMutation.SubgraphErased se -> Set.of();
            case GraphMutation.EntityErased ee -> Set.of();
        };
    }

    private static String subgraphIdOf(GraphMutation mutation) {
        return switch (mutation) {
            case GraphMutation.NodeAdded na -> na.subgraphId();
            case GraphMutation.NodeUpdated nu -> nu.subgraphId();
            case GraphMutation.NodeErased ne -> ne.subgraphId();
            case GraphMutation.SubgraphCreated sc -> sc.subgraphId();
            case GraphMutation.SubgraphErased se -> se.subgraphId();
            default -> null;
        };
    }

    public void close() {
        if (dataSource != null) dataSource.close();
    }
}
