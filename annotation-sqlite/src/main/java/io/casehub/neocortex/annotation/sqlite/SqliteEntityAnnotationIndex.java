package io.casehub.neocortex.annotation.sqlite;

import io.casehub.neocortex.annotation.EntityAnnotation;
import io.casehub.neocortex.annotation.EntityAnnotationIndex;
import io.casehub.neocortex.sqlite.SqliteDataSourceFactory;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class SqliteEntityAnnotationIndex implements EntityAnnotationIndex {

    private static final String FLYWAY_LOCATION = "classpath:db/annotation/migration";

    private final DataSource dataSource;

    public SqliteEntityAnnotationIndex(DataSource dataSource) {
        this.dataSource = dataSource;
        if (dataSource instanceof com.zaxxer.hikari.HikariDataSource hds) {
            SqliteDataSourceFactory.migrate(hds, FLYWAY_LOCATION);
        }
    }

    @Override
    public String addAnnotation(EntityAnnotation a) {
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                 "INSERT INTO entity_annotations (annotation_id, tenant_id, source_type, source_id, entity_node_id, start_offset, end_offset, mention_text, confidence, method, owner_subject_id, created_at) VALUES (?,?,?,?,?,?,?,?,?,?,?,?)")) {
            setAnnotationParams(ps, a);
            ps.executeUpdate();
        } catch (SQLException e) { throw new RuntimeException(e); }
        return a.annotationId();
    }

    @Override
    public List<String> addAnnotations(List<EntityAnnotation> annotations) {
        if (annotations.isEmpty()) return List.of();
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                 "INSERT INTO entity_annotations (annotation_id, tenant_id, source_type, source_id, entity_node_id, start_offset, end_offset, mention_text, confidence, method, owner_subject_id, created_at) VALUES (?,?,?,?,?,?,?,?,?,?,?,?)")) {
            conn.setAutoCommit(false);
            List<String> ids = new ArrayList<>();
            for (EntityAnnotation a : annotations) {
                setAnnotationParams(ps, a);
                ps.addBatch();
                ids.add(a.annotationId());
            }
            ps.executeBatch();
            conn.commit();
            return ids;
        } catch (SQLException e) { throw new RuntimeException(e); }
    }

    @Override
    public void removeAnnotation(String annotationId, String tenantId) {
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                 "DELETE FROM entity_annotations WHERE annotation_id = ? AND tenant_id = ?")) {
            ps.setString(1, annotationId);
            ps.setString(2, tenantId);
            ps.executeUpdate();
        } catch (SQLException e) { throw new RuntimeException(e); }
    }

    @Override
    public void removeBySource(String sourceType, String sourceId, String tenantId) {
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                 "DELETE FROM entity_annotations WHERE tenant_id = ? AND source_type = ? AND source_id = ?")) {
            ps.setString(1, tenantId);
            ps.setString(2, sourceType);
            ps.setString(3, sourceId);
            ps.executeUpdate();
        } catch (SQLException e) { throw new RuntimeException(e); }
    }

    @Override
    public void removeByOwnerSubject(String sourceType, String ownerSubjectId, String tenantId) {
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                 "DELETE FROM entity_annotations WHERE tenant_id = ? AND source_type = ? AND owner_subject_id = ?")) {
            ps.setString(1, tenantId);
            ps.setString(2, sourceType);
            ps.setString(3, ownerSubjectId);
            ps.executeUpdate();
        } catch (SQLException e) { throw new RuntimeException(e); }
    }

    @Override
    public int repointEntity(String fromEntityNodeId, String toEntityNodeId, String tenantId) {
        try (Connection conn = dataSource.getConnection()) {
            conn.setAutoCommit(false);

            int count;
            try (PreparedStatement countPs = conn.prepareStatement(
                     "SELECT COUNT(*) FROM entity_annotations WHERE tenant_id = ? AND entity_node_id = ?")) {
                countPs.setString(1, tenantId);
                countPs.setString(2, fromEntityNodeId);
                ResultSet rs = countPs.executeQuery();
                rs.next();
                count = rs.getInt(1);
            }
            if (count == 0) { conn.rollback(); return 0; }

            try (PreparedStatement updatePs = conn.prepareStatement(
                     "UPDATE entity_annotations SET entity_node_id = ? WHERE tenant_id = ? AND entity_node_id = ?")) {
                updatePs.setString(1, toEntityNodeId);
                updatePs.setString(2, tenantId);
                updatePs.setString(3, fromEntityNodeId);
                updatePs.executeUpdate();
            }

            try (PreparedStatement dedupPs = conn.prepareStatement("""
                    DELETE FROM entity_annotations WHERE rowid NOT IN (
                      SELECT MIN(rowid) FROM entity_annotations
                      WHERE tenant_id = ? AND entity_node_id = ?
                      GROUP BY source_type, source_id, start_offset, end_offset
                    ) AND tenant_id = ? AND entity_node_id = ?""")) {
                dedupPs.setString(1, tenantId);
                dedupPs.setString(2, toEntityNodeId);
                dedupPs.setString(3, tenantId);
                dedupPs.setString(4, toEntityNodeId);
                dedupPs.executeUpdate();
            }

            conn.commit();
            return count;
        } catch (SQLException e) { throw new RuntimeException(e); }
    }

    @Override
    public List<EntityAnnotation> findByEntity(String entityNodeId, String tenantId) {
        return query("SELECT * FROM entity_annotations WHERE tenant_id = ? AND entity_node_id = ?",
            tenantId, entityNodeId);
    }

    @Override
    public List<EntityAnnotation> findByEntityPair(String entityNodeId1, String entityNodeId2,
                                                    String tenantId) {
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement("""
                 SELECT * FROM entity_annotations
                 WHERE tenant_id = ?
                   AND entity_node_id IN (?, ?)
                   AND (source_type || ':' || source_id) IN (
                     SELECT source_type || ':' || source_id
                     FROM entity_annotations
                     WHERE tenant_id = ? AND entity_node_id IN (?, ?)
                     GROUP BY source_type, source_id
                     HAVING COUNT(DISTINCT entity_node_id) = 2
                   )""")) {
            ps.setString(1, tenantId);
            ps.setString(2, entityNodeId1);
            ps.setString(3, entityNodeId2);
            ps.setString(4, tenantId);
            ps.setString(5, entityNodeId1);
            ps.setString(6, entityNodeId2);
            return readAll(ps.executeQuery());
        } catch (SQLException e) { throw new RuntimeException(e); }
    }

    @Override
    public List<EntityAnnotation> findByEntities(Set<String> entityNodeIds, String tenantId) {
        if (entityNodeIds.isEmpty()) return List.of();
        String placeholders = String.join(",", entityNodeIds.stream().map(id -> "?").toList());
        String sql = "SELECT * FROM entity_annotations WHERE tenant_id = ? AND entity_node_id IN (" + placeholders + ")";
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, tenantId);
            int idx = 2;
            for (String id : entityNodeIds) { ps.setString(idx++, id); }
            return readAll(ps.executeQuery());
        } catch (SQLException e) { throw new RuntimeException(e); }
    }

    @Override
    public List<EntityAnnotation> findBySource(String sourceType, String sourceId, String tenantId) {
        return query("SELECT * FROM entity_annotations WHERE tenant_id = ? AND source_type = ? AND source_id = ?",
            tenantId, sourceType, sourceId);
    }

    @Override
    public Set<String> distinctEntitiesInSource(String sourceType, String sourceId, String tenantId) {
        Set<String> result = new HashSet<>();
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                 "SELECT DISTINCT entity_node_id FROM entity_annotations WHERE tenant_id = ? AND source_type = ? AND source_id = ?")) {
            ps.setString(1, tenantId);
            ps.setString(2, sourceType);
            ps.setString(3, sourceId);
            ResultSet rs = ps.executeQuery();
            while (rs.next()) { result.add(rs.getString(1)); }
        } catch (SQLException e) { throw new RuntimeException(e); }
        return result;
    }

    @Override
    public Map<String, Long> entityMentionCounts(String tenantId) {
        Map<String, Long> result = new LinkedHashMap<>();
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                 "SELECT entity_node_id, COUNT(*) FROM entity_annotations WHERE tenant_id = ? GROUP BY entity_node_id")) {
            ps.setString(1, tenantId);
            ResultSet rs = ps.executeQuery();
            while (rs.next()) { result.put(rs.getString(1), rs.getLong(2)); }
        } catch (SQLException e) { throw new RuntimeException(e); }
        return result;
    }

    private void setAnnotationParams(PreparedStatement ps, EntityAnnotation a) throws SQLException {
        ps.setString(1, a.annotationId());
        ps.setString(2, a.tenantId());
        ps.setString(3, a.sourceType());
        ps.setString(4, a.sourceId());
        ps.setString(5, a.entityNodeId());
        ps.setInt(6, a.startOffset());
        ps.setInt(7, a.endOffset());
        ps.setString(8, a.mentionText());
        if (a.confidence() != null) { ps.setDouble(9, a.confidence()); } else { ps.setNull(9, java.sql.Types.REAL); }
        ps.setString(10, a.method());
        ps.setString(11, a.ownerSubjectId());
        ps.setString(12, a.createdAt().toString());
    }

    private List<EntityAnnotation> query(String sql, String... params) {
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            for (int i = 0; i < params.length; i++) { ps.setString(i + 1, params[i]); }
            return readAll(ps.executeQuery());
        } catch (SQLException e) { throw new RuntimeException(e); }
    }

    private List<EntityAnnotation> readAll(ResultSet rs) throws SQLException {
        List<EntityAnnotation> result = new ArrayList<>();
        while (rs.next()) { result.add(readRow(rs)); }
        return result;
    }

    private EntityAnnotation readRow(ResultSet rs) throws SQLException {
        Double confidence = rs.getDouble("confidence");
        if (rs.wasNull()) confidence = null;
        return new EntityAnnotation(
            rs.getString("annotation_id"),
            rs.getString("tenant_id"),
            rs.getString("source_type"),
            rs.getString("source_id"),
            rs.getString("entity_node_id"),
            rs.getInt("start_offset"),
            rs.getInt("end_offset"),
            rs.getString("mention_text"),
            confidence,
            rs.getString("method"),
            rs.getString("owner_subject_id"),
            Instant.parse(rs.getString("created_at"))
        );
    }
}
