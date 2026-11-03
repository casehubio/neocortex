package io.casehub.neocortex.corpus.management.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.casehub.neocortex.corpus.management.CorpusEntry;
import io.casehub.neocortex.corpus.management.CorpusEntryStore;
import io.casehub.neocortex.rag.ChunkInput;
import io.casehub.neocortex.rag.CorpusRef;
import io.casehub.neocortex.rag.EmbeddingIngestor;
import io.casehub.neocortex.sqlite.SqliteDataSourceFactory;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public class SqliteCorpusEntryStore implements CorpusEntryStore {

    private static final String FLYWAY_LOCATION = "classpath:db/corpus-catalogue";
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {};

    private final DataSource dataSource;
    private final EmbeddingIngestor embeddingIngestor;

    public SqliteCorpusEntryStore(DataSource dataSource, EmbeddingIngestor embeddingIngestor) {
        this.dataSource = dataSource;
        this.embeddingIngestor = embeddingIngestor;
        if (dataSource instanceof com.zaxxer.hikari.HikariDataSource hds) {
            SqliteDataSourceFactory.migrate(hds, FLYWAY_LOCATION);
        }
    }

    @Override
    public String add(CorpusRef corpus, CorpusEntry entry) {
        String now = Instant.now().toString();
        String sql = "INSERT INTO corpus_entries (id, corpus_tenant_id, corpus_name, frontmatter_json, body, artifact_ref, created_at, last_modified_at) VALUES (?,?,?,?,?,?,?,?)";
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, entry.id());
            ps.setString(2, corpus.tenantId());
            ps.setString(3, corpus.corpusName());
            ps.setString(4, toJson(entry.frontmatter()));
            ps.setString(5, entry.body());
            ps.setString(6, entry.artifactRef());
            ps.setString(7, now);
            ps.setString(8, now);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("Failed to add entry " + entry.id(), e);
        }

        ingestEmbedding(corpus, entry);
        return entry.id();
    }

    @Override
    public void update(CorpusRef corpus, String entryId, CorpusEntry entry) {
        String now = Instant.now().toString();
        String sql = "UPDATE corpus_entries SET frontmatter_json = ?, body = ?, artifact_ref = ?, last_modified_at = ? WHERE id = ? AND corpus_tenant_id = ? AND corpus_name = ?";
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, toJson(entry.frontmatter()));
            ps.setString(2, entry.body());
            ps.setString(3, entry.artifactRef());
            ps.setString(4, now);
            ps.setString(5, entryId);
            ps.setString(6, corpus.tenantId());
            ps.setString(7, corpus.corpusName());
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("Failed to update entry " + entryId, e);
        }

        embeddingIngestor.deleteDocument(corpus, entryId);
        ingestEmbedding(corpus, entry);
    }

    @Override
    public void remove(CorpusRef corpus, String entryId) {
        String sql = "DELETE FROM corpus_entries WHERE id = ? AND corpus_tenant_id = ? AND corpus_name = ?";
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, entryId);
            ps.setString(2, corpus.tenantId());
            ps.setString(3, corpus.corpusName());
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("Failed to remove entry " + entryId, e);
        }

        embeddingIngestor.deleteDocument(corpus, entryId);
    }

    @Override
    public Optional<CorpusEntry> get(CorpusRef corpus, String entryId) {
        String sql = "SELECT id, frontmatter_json, body, artifact_ref FROM corpus_entries WHERE id = ? AND corpus_tenant_id = ? AND corpus_name = ?";
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, entryId);
            ps.setString(2, corpus.tenantId());
            ps.setString(3, corpus.corpusName());
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(fromRow(rs));
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to get entry " + entryId, e);
        }
        return Optional.empty();
    }

    private void ingestEmbedding(CorpusRef corpus, CorpusEntry entry) {
        var metadata = new HashMap<String, String>();
        for (var e : entry.frontmatter().entrySet()) {
            if (e.getValue() instanceof String s) {
                metadata.put(e.getKey(), s);
            }
        }
        var chunk = new ChunkInput(entry.body(), entry.id(), metadata);
        embeddingIngestor.ingest(corpus, List.of(chunk));
    }

    private static CorpusEntry fromRow(ResultSet rs) throws SQLException {
        return new CorpusEntry(
            rs.getString("id"),
            fromJson(rs.getString("frontmatter_json")),
            rs.getString("body"),
            rs.getString("artifact_ref"));
    }

    private static String toJson(Map<String, Object> map) {
        try {
            return MAPPER.writeValueAsString(map);
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }
    }

    private static Map<String, Object> fromJson(String json) {
        try {
            return MAPPER.readValue(json, MAP_TYPE);
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }
    }
}
