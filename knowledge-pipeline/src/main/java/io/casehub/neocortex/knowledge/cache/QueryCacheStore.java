package io.casehub.neocortex.knowledge.cache;

import com.zaxxer.hikari.HikariDataSource;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

public class QueryCacheStore {

    private final HikariDataSource ds;

    public QueryCacheStore(HikariDataSource ds) {
        this.ds = ds;
    }

    public record QueryCacheEntry(String cacheKey, String tenantId,
                                   List<String> entityIds, Instant answeredAt,
                                   Instant expiresAt) {}

    public Optional<QueryCacheEntry> lookup(String cacheKey, String tenantId) {
        try (Connection c = ds.getConnection();
             PreparedStatement ps = c.prepareStatement(
                 "SELECT * FROM query_cache WHERE cache_key = ? AND tenant_id = ?")) {
            ps.setString(1, cacheKey);
            ps.setString(2, tenantId);
            ResultSet rs = ps.executeQuery();
            if (!rs.next()) return Optional.empty();
            Instant expiresAt = Instant.parse(rs.getString("expires_at"));
            if (Instant.now().isAfter(expiresAt)) return Optional.empty();
            return Optional.of(new QueryCacheEntry(
                rs.getString("cache_key"),
                rs.getString("tenant_id"),
                parseEntityIds(rs.getString("entity_ids")),
                Instant.parse(rs.getString("answered_at")),
                expiresAt));
        } catch (SQLException e) { throw new RuntimeException(e); }
    }

    public void record(String cacheKey, String tenantId, List<String> entityIds,
                       Instant expiresAt) {
        String now = Instant.now().toString();
        String idsJson = serializeEntityIds(entityIds);
        try (Connection c = ds.getConnection();
             PreparedStatement ps = c.prepareStatement(
                 "INSERT OR REPLACE INTO query_cache "
                 + "(cache_key, tenant_id, entity_ids, answered_at, expires_at) "
                 + "VALUES (?, ?, ?, ?, ?)")) {
            ps.setString(1, cacheKey);
            ps.setString(2, tenantId);
            ps.setString(3, idsJson);
            ps.setString(4, now);
            ps.setString(5, expiresAt.toString());
            ps.executeUpdate();
        } catch (SQLException e) { throw new RuntimeException(e); }
    }

    static String serializeEntityIds(List<String> ids) {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < ids.size(); i++) {
            if (i > 0) sb.append(",");
            sb.append("\"").append(ids.get(i).replace("\"", "\\\"")).append("\"");
        }
        return sb.append("]").toString();
    }

    static List<String> parseEntityIds(String json) {
        if (json == null || json.equals("[]")) return List.of();
        String inner = json.substring(1, json.length() - 1);
        var result = new java.util.ArrayList<String>();
        for (String part : inner.split(",")) {
            String trimmed = part.trim();
            if (trimmed.startsWith("\"") && trimmed.endsWith("\"")) {
                result.add(trimmed.substring(1, trimmed.length() - 1));
            }
        }
        return List.copyOf(result);
    }
}
