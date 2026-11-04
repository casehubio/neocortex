package io.casehub.neocortex.corpus.management.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.casehub.neocortex.corpus.management.CorpusCatalogue;
import io.casehub.neocortex.corpus.management.CorpusDescriptor;
import io.casehub.neocortex.corpus.management.CorpusLifecycle;
import io.casehub.neocortex.corpus.management.CorpusType;
import io.casehub.neocortex.corpus.management.FederationRef;
import io.casehub.neocortex.corpus.management.HealthReport;
import io.casehub.neocortex.rag.CorpusRef;
import io.casehub.neocortex.sqlite.SqliteDataSourceFactory;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

public class SqliteCorpusCatalogue implements CorpusCatalogue {

    private static final String FLYWAY_LOCATION = "classpath:db/corpus-catalogue";
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final TypeReference<List<FederationRefJson>> FED_REF_TYPE = new TypeReference<>() {};
    private static final TypeReference<Map<String, String>> MAP_TYPE = new TypeReference<>() {};

    private final DataSource dataSource;

    public SqliteCorpusCatalogue(DataSource dataSource) {
        this.dataSource = dataSource;
        if (dataSource instanceof com.zaxxer.hikari.HikariDataSource hds) {
            SqliteDataSourceFactory.migrate(hds, FLYWAY_LOCATION);
        }
    }

    public CorpusDescriptor register(String name, CorpusType type, String tenantId,
                                      String qdrantCollection, List<FederationRef> upstream,
                                      List<FederationRef> peers, Map<String, String> metadata) {
        String id = UUID.randomUUID().toString();
        Instant now = Instant.now();
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                 "INSERT INTO corpus_catalogue (id, corpus_name, tenant_id, type_name, lifecycle, qdrant_collection, upstream_json, peers_json, metadata_json, created_at, last_modified_at) VALUES (?,?,?,?,?,?,?,?,?,?,?)")) {
            ps.setString(1, id);
            ps.setString(2, name);
            ps.setString(3, tenantId);
            ps.setString(4, type.name());
            ps.setString(5, CorpusLifecycle.ACTIVE.name());
            ps.setString(6, qdrantCollection);
            ps.setString(7, toJson(upstream));
            ps.setString(8, toJson(peers));
            ps.setString(9, toJsonMap(metadata));
            ps.setString(10, now.toString());
            ps.setString(11, now.toString());
            ps.executeUpdate();
        } catch (SQLException e) {
            if (e.getMessage() != null && e.getMessage().contains("UNIQUE constraint failed")) {
                throw new IllegalArgumentException(
                    "Corpus name '" + name + "' already exists for tenant '" + tenantId + "'");
            }
            throw new RuntimeException(e);
        }
        return new CorpusDescriptor(id, name, type, CorpusLifecycle.ACTIVE,
            new CorpusRef(tenantId, name), qdrantCollection,
            List.copyOf(upstream), List.copyOf(peers), Map.copyOf(metadata), now, now);
    }

    @Override
    public List<CorpusDescriptor> list(String tenantId, CorpusLifecycle status) {
        var results = new ArrayList<CorpusDescriptor>();
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                 "SELECT * FROM corpus_catalogue WHERE tenant_id = ? AND lifecycle = ?")) {
            ps.setString(1, tenantId);
            ps.setString(2, status.name());
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    results.add(fromRow(rs));
                }
            }
        } catch (SQLException e) { throw new RuntimeException(e); }
        return results;
    }

    @Override
    public Optional<CorpusDescriptor> get(CorpusRef corpus) {
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                 "SELECT * FROM corpus_catalogue WHERE tenant_id = ? AND corpus_name = ?")) {
            ps.setString(1, corpus.tenantId());
            ps.setString(2, corpus.corpusName());
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(fromRow(rs));
                }
            }
        } catch (SQLException e) { throw new RuntimeException(e); }
        return Optional.empty();
    }

    @Override
    public List<CorpusDescriptor> forType(CorpusType type, String tenantId) {
        var results = new ArrayList<CorpusDescriptor>();
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                 "SELECT * FROM corpus_catalogue WHERE tenant_id = ? AND type_name = ?")) {
            ps.setString(1, tenantId);
            ps.setString(2, type.name());
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    results.add(fromRow(rs));
                }
            }
        } catch (SQLException e) { throw new RuntimeException(e); }
        return results;
    }

    @Override
    public List<CorpusDescriptor> searchScope(CorpusRef corpus) {
        var result = new LinkedHashSet<CorpusDescriptor>();
        var visited = new HashSet<CorpusRef>();
        walkScope(corpus, result, visited);
        return List.copyOf(result);
    }

    private void walkScope(CorpusRef ref, Set<CorpusDescriptor> result, Set<CorpusRef> visited) {
        if (!visited.add(ref)) return;
        var desc = get(ref);
        if (desc.isEmpty()) return;
        var d = desc.get();
        result.add(d);
        for (FederationRef upstream : d.upstream()) {
            if (upstream.localRef() != null) {
                walkScope(upstream.localRef(), result, visited);
            }
        }
        for (FederationRef peer : d.peers()) {
            if (peer.localRef() != null && visited.add(peer.localRef())) {
                get(peer.localRef()).ifPresent(result::add);
            }
        }
    }

    @Override
    public HealthReport reconcile(String tenantId) {
        return HealthReport.HEALTHY;
    }

    public void updateLifecycle(CorpusRef corpus, CorpusLifecycle lifecycle) {
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                 "UPDATE corpus_catalogue SET lifecycle = ?, last_modified_at = ? WHERE tenant_id = ? AND corpus_name = ?")) {
            ps.setString(1, lifecycle.name());
            ps.setString(2, Instant.now().toString());
            ps.setString(3, corpus.tenantId());
            ps.setString(4, corpus.corpusName());
            ps.executeUpdate();
        } catch (SQLException e) { throw new RuntimeException(e); }
    }

    public void updateFederation(CorpusRef corpus, List<FederationRef> upstream, List<FederationRef> peers) {
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                 "UPDATE corpus_catalogue SET upstream_json = ?, peers_json = ?, last_modified_at = ? WHERE tenant_id = ? AND corpus_name = ?")) {
            ps.setString(1, toJson(upstream));
            ps.setString(2, toJson(peers));
            ps.setString(3, Instant.now().toString());
            ps.setString(4, corpus.tenantId());
            ps.setString(5, corpus.corpusName());
            ps.executeUpdate();
        } catch (SQLException e) { throw new RuntimeException(e); }
    }

    public void deregister(CorpusRef corpus) {
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                 "DELETE FROM corpus_catalogue WHERE tenant_id = ? AND corpus_name = ?")) {
            ps.setString(1, corpus.tenantId());
            ps.setString(2, corpus.corpusName());
            ps.executeUpdate();
        } catch (SQLException e) { throw new RuntimeException(e); }
    }

    private CorpusDescriptor fromRow(ResultSet rs) throws SQLException {
        String typeName = rs.getString("type_name");
        CorpusType type = resolveType(typeName);
        String tenantId = rs.getString("tenant_id");
        String corpusName = rs.getString("corpus_name");
        return new CorpusDescriptor(
            rs.getString("id"),
            corpusName,
            type,
            CorpusLifecycle.valueOf(rs.getString("lifecycle")),
            new CorpusRef(tenantId, corpusName),
            rs.getString("qdrant_collection"),
            fromJsonUpstream(rs.getString("upstream_json")),
            fromJsonPeers(rs.getString("peers_json")),
            fromJsonMap(rs.getString("metadata_json")),
            Instant.parse(rs.getString("created_at")),
            Instant.parse(rs.getString("last_modified_at"))
        );
    }

    private static CorpusType resolveType(String name) {
        return switch (name) {
            case "knowledge-garden" -> BuiltInCorpusTypes.KNOWLEDGE_GARDEN;
            case "research" -> BuiltInCorpusTypes.RESEARCH;
            case "transcript" -> BuiltInCorpusTypes.TRANSCRIPT;
            default -> new CorpusType(name, Map.of(), Map.of(), null);
        };
    }

    private String toJson(List<FederationRef> refs) {
        try {
            var jsonRefs = refs.stream().map(r -> new FederationRefJson(
                r.localRef() != null ? r.localRef().tenantId() : null,
                r.localRef() != null ? r.localRef().corpusName() : null,
                r.url(), r.idPrefix(), r.searchOrder()
            )).toList();
            return MAPPER.writeValueAsString(jsonRefs);
        } catch (JsonProcessingException e) { throw new RuntimeException(e); }
    }

    private List<FederationRef> fromJsonUpstream(String json) {
        return fromJsonFedRefs(json);
    }

    private List<FederationRef> fromJsonPeers(String json) {
        return fromJsonFedRefs(json);
    }

    private List<FederationRef> fromJsonFedRefs(String json) {
        try {
            List<FederationRefJson> raw = MAPPER.readValue(json, FED_REF_TYPE);
            return raw.stream().map(r -> new FederationRef(
                r.tenantId != null && r.corpusName != null
                    ? new CorpusRef(r.tenantId, r.corpusName) : null,
                r.url, r.idPrefix, r.searchOrder
            )).toList();
        } catch (JsonProcessingException e) { throw new RuntimeException(e); }
    }

    private String toJsonMap(Map<String, String> map) {
        try { return MAPPER.writeValueAsString(map); }
        catch (JsonProcessingException e) { throw new RuntimeException(e); }
    }

    private Map<String, String> fromJsonMap(String json) {
        try { return MAPPER.readValue(json, MAP_TYPE); }
        catch (JsonProcessingException e) { throw new RuntimeException(e); }
    }

    record FederationRefJson(String tenantId, String corpusName, String url,
                             String idPrefix, String searchOrder) {}
}
