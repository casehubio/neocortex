package io.casehub.neocortex.entity.token.cdi;

import io.casehub.neocortex.entity.token.EntityReferenceIndex;
import io.casehub.neocortex.sqlite.SqliteDataSourceFactory;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class SqliteEntityReferenceIndex implements EntityReferenceIndex {

    private static final String FLYWAY_LOCATION = "classpath:db/entity-reference/migration";

    private final DataSource dataSource;

    public SqliteEntityReferenceIndex(DataSource dataSource) {
        this.dataSource = dataSource;
        if (dataSource instanceof com.zaxxer.hikari.HikariDataSource hds) {
            SqliteDataSourceFactory.migrate(hds, FLYWAY_LOCATION);
        }
    }

    @Override
    public void record(String entityId, String sourceType, String sourceId, String tenantId) {
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                 "INSERT OR IGNORE INTO entity_references (entity_id, source_type, source_id, tenant_id) VALUES (?,?,?,?)")) {
            ps.setString(1, entityId);
            ps.setString(2, sourceType);
            ps.setString(3, sourceId);
            ps.setString(4, tenantId);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("Failed to record entity reference", e);
        }
    }

    @Override
    public void recordAll(Collection<String> entityIds, String sourceType,
                          String sourceId, String tenantId) {
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                 "INSERT OR IGNORE INTO entity_references (entity_id, source_type, source_id, tenant_id) VALUES (?,?,?,?)")) {
            for (String entityId : entityIds) {
                ps.setString(1, entityId);
                ps.setString(2, sourceType);
                ps.setString(3, sourceId);
                ps.setString(4, tenantId);
                ps.addBatch();
            }
            ps.executeBatch();
        } catch (SQLException e) {
            throw new RuntimeException("Failed to record entity references", e);
        }
    }

    @Override
    public Set<String> findSourcesByEntity(String entityId, String tenantId) {
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                 "SELECT source_id FROM entity_references WHERE entity_id = ? AND tenant_id = ?")) {
            ps.setString(1, entityId);
            ps.setString(2, tenantId);
            Set<String> result = new HashSet<>();
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) result.add(rs.getString(1));
            }
            return Set.copyOf(result);
        } catch (SQLException e) {
            throw new RuntimeException("Failed to find sources by entity", e);
        }
    }

    @Override
    public Set<String> findEntitiesBySource(String sourceType, String sourceId, String tenantId) {
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                 "SELECT DISTINCT entity_id FROM entity_references WHERE source_type = ? AND source_id = ? AND tenant_id = ?")) {
            ps.setString(1, sourceType);
            ps.setString(2, sourceId);
            ps.setString(3, tenantId);
            Set<String> result = new HashSet<>();
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) result.add(rs.getString(1));
            }
            return Set.copyOf(result);
        } catch (SQLException e) {
            throw new RuntimeException("Failed to find entities by source", e);
        }
    }

    @Override
    public Map<String, Long> entityMentionCounts(String tenantId) {
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                 "SELECT entity_id, COUNT(*) FROM entity_references WHERE tenant_id = ? GROUP BY entity_id")) {
            ps.setString(1, tenantId);
            Map<String, Long> result = new HashMap<>();
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) result.put(rs.getString(1), rs.getLong(2));
            }
            return Map.copyOf(result);
        } catch (SQLException e) {
            throw new RuntimeException("Failed to count entity mentions", e);
        }
    }

    @Override
    public void removeBySource(String sourceType, String sourceId, String tenantId) {
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                 "DELETE FROM entity_references WHERE source_type = ? AND source_id = ? AND tenant_id = ?")) {
            ps.setString(1, sourceType);
            ps.setString(2, sourceId);
            ps.setString(3, tenantId);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("Failed to remove by source", e);
        }
    }

    @Override
    public void removeByEntity(String entityId, String tenantId) {
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                 "DELETE FROM entity_references WHERE entity_id = ? AND tenant_id = ?")) {
            ps.setString(1, entityId);
            ps.setString(2, tenantId);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("Failed to remove by entity", e);
        }
    }

    @Override
    public void repointEntity(String fromEntityId, String toEntityId, String tenantId) {
        try (Connection conn = dataSource.getConnection()) {
            try (PreparedStatement del = conn.prepareStatement(
                     "DELETE FROM entity_references WHERE entity_id = ? AND tenant_id = ? AND (source_type, source_id) IN (SELECT source_type, source_id FROM entity_references WHERE entity_id = ? AND tenant_id = ?)")) {
                del.setString(1, toEntityId);
                del.setString(2, tenantId);
                del.setString(3, fromEntityId);
                del.setString(4, tenantId);
                del.executeUpdate();
            }
            try (PreparedStatement upd = conn.prepareStatement(
                     "UPDATE entity_references SET entity_id = ? WHERE entity_id = ? AND tenant_id = ?")) {
                upd.setString(1, toEntityId);
                upd.setString(2, fromEntityId);
                upd.setString(3, tenantId);
                upd.executeUpdate();
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to repoint entity", e);
        }
    }
}
