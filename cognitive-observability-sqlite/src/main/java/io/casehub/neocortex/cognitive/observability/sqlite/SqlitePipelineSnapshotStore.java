package io.casehub.neocortex.cognitive.observability.sqlite;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.zaxxer.hikari.HikariDataSource;
import io.casehub.neocortex.cognitive.observability.PipelineSnapshotStore;
import io.casehub.neocortex.cognition.trace.PipelineTrace;
import io.casehub.neocortex.sqlite.SqliteDataSourceFactory;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class SqlitePipelineSnapshotStore implements PipelineSnapshotStore, AutoCloseable {

    private final HikariDataSource dataSource;
    private final ObjectMapper mapper;

    public SqlitePipelineSnapshotStore(String path) {
        this.dataSource = SqliteDataSourceFactory.create(path, 3, 5000);
        SqliteDataSourceFactory.migrate(dataSource, "classpath:db/observability");
        this.mapper = new ObjectMapper()
                .findAndRegisterModules()
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    }

    @Override
    public void store(String tenantId, String agentId, int tick, Instant capturedAt, PipelineTrace trace) {
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                     "INSERT OR REPLACE INTO pipeline_snapshot (tenant_id, agent_id, tick_number, captured_at, trace_json) VALUES (?, ?, ?, ?, ?)")) {
            ps.setString(1, tenantId);
            ps.setString(2, agentId);
            ps.setInt(3, tick);
            ps.setString(4, capturedAt.toString());
            ps.setString(5, toJson(trace));
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("Failed to store pipeline snapshot", e);
        }
    }

    @Override
    public List<PipelineSnapshot> find(String tenantId, String agentId, int fromTick, int toTick) {
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                     "SELECT tenant_id, agent_id, tick_number, captured_at, trace_json FROM pipeline_snapshot WHERE tenant_id = ? AND agent_id = ? AND tick_number >= ? AND tick_number <= ? ORDER BY tick_number")) {
            ps.setString(1, tenantId);
            ps.setString(2, agentId);
            ps.setInt(3, fromTick);
            ps.setInt(4, toTick);
            return readSnapshots(ps);
        } catch (SQLException e) {
            throw new RuntimeException("Failed to find pipeline snapshots", e);
        }
    }

    @Override
    public Optional<PipelineSnapshot> findByTick(String tenantId, String agentId, int tick) {
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                     "SELECT tenant_id, agent_id, tick_number, captured_at, trace_json FROM pipeline_snapshot WHERE tenant_id = ? AND agent_id = ? AND tick_number = ?")) {
            ps.setString(1, tenantId);
            ps.setString(2, agentId);
            ps.setInt(3, tick);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) {
                return Optional.of(readSnapshot(rs));
            }
            return Optional.empty();
        } catch (SQLException e) {
            throw new RuntimeException("Failed to find pipeline snapshot by tick", e);
        }
    }

    @Override
    public List<PipelineSnapshot> findLatest(String tenantId, String agentId, int limit) {
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                     "SELECT tenant_id, agent_id, tick_number, captured_at, trace_json FROM pipeline_snapshot WHERE tenant_id = ? AND agent_id = ? ORDER BY tick_number DESC LIMIT ?")) {
            ps.setString(1, tenantId);
            ps.setString(2, agentId);
            ps.setInt(3, limit);
            return readSnapshots(ps);
        } catch (SQLException e) {
            throw new RuntimeException("Failed to find latest pipeline snapshots", e);
        }
    }

    @Override
    public long count(String tenantId) {
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                     "SELECT COUNT(*) FROM pipeline_snapshot WHERE tenant_id = ?")) {
            ps.setString(1, tenantId);
            ResultSet rs = ps.executeQuery();
            return rs.next() ? rs.getLong(1) : 0;
        } catch (SQLException e) {
            throw new RuntimeException("Failed to count pipeline snapshots", e);
        }
    }

    @Override
    public void purge(Instant olderThan) {
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                     "DELETE FROM pipeline_snapshot WHERE captured_at < ?")) {
            ps.setString(1, olderThan.toString());
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("Failed to purge pipeline snapshots", e);
        }
    }

    private List<PipelineSnapshot> readSnapshots(PreparedStatement ps) throws SQLException {
        ResultSet rs = ps.executeQuery();
        List<PipelineSnapshot> results = new ArrayList<>();
        while (rs.next()) {
            results.add(readSnapshot(rs));
        }
        return results;
    }

    private PipelineSnapshot readSnapshot(ResultSet rs) throws SQLException {
        return new PipelineSnapshot(
                rs.getString("tenant_id"),
                rs.getString("agent_id"),
                rs.getInt("tick_number"),
                Instant.parse(rs.getString("captured_at")),
                fromJson(rs.getString("trace_json"))
        );
    }

    private String toJson(PipelineTrace trace) {
        try {
            return mapper.writeValueAsString(trace);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("JSON serialization failed", e);
        }
    }

    private PipelineTrace fromJson(String json) {
        try {
            return mapper.readValue(json, PipelineTrace.class);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("JSON deserialization failed", e);
        }
    }

    @Override
    public void close() {
        if (dataSource != null) dataSource.close();
    }
}
