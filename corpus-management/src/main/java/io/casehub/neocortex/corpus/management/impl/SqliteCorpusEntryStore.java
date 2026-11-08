package io.casehub.neocortex.corpus.management.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.casehub.neocortex.corpus.ChangeType;
import io.casehub.neocortex.corpus.CorpusReader;
import io.casehub.neocortex.corpus.CorpusStore;
import io.casehub.neocortex.corpus.VersionInfo;
import io.casehub.neocortex.corpus.management.ChangeRecord;
import io.casehub.neocortex.corpus.management.ContentSerializer;
import io.casehub.neocortex.corpus.management.CorpusEntry;
import io.casehub.neocortex.corpus.management.CorpusEntryStore;
import io.casehub.neocortex.rag.CorpusRef;
import io.casehub.neocortex.rag.ExtractionResult;
import io.casehub.neocortex.rag.runtime.IngestionContext;
import io.casehub.neocortex.rag.runtime.IngestionPipeline;
import io.casehub.neocortex.sqlite.SqliteDataSourceFactory;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public class SqliteCorpusEntryStore implements CorpusEntryStore {

    private static final String                             FLYWAY_LOCATION = "classpath:db/corpus-catalogue";
    private static final ObjectMapper                       MAPPER          = new ObjectMapper();
    private static final TypeReference<Map<String, Object>> MAP_TYPE        = new TypeReference<>() {};

    private final DataSource         dataSource;
    private final IngestionPipeline  pipeline;
    private final IngestionContext   defaultContext;
    private final CorpusStore        backendStore;
    private final ContentSerializer  serializer;
    private final CorpusReader       reader;

    public SqliteCorpusEntryStore(DataSource dataSource, IngestionPipeline pipeline,
                                  IngestionContext defaultContext,
                                  CorpusStore backendStore, ContentSerializer serializer,
                                  CorpusReader reader) {
        this.dataSource     = dataSource;
        this.pipeline       = pipeline;
        this.defaultContext = defaultContext;
        this.backendStore   = backendStore;
        this.serializer     = serializer;
        this.reader         = reader;
        if (dataSource instanceof com.zaxxer.hikari.HikariDataSource hds) {
            SqliteDataSourceFactory.migrate(hds, FLYWAY_LOCATION);
        }
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

    @Override
    public String add(CorpusRef corpus, CorpusEntry entry) {
        String now = Instant.now().toString();
        String sql = "INSERT INTO corpus_entries (id, corpus_tenant_id, corpus_name, frontmatter_json, body, artifact_ref, created_at, last_modified_at) VALUES (?,?,?,?,?,?,?,?)";
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, entry.id());
            ps.setString(2, corpus.tenantId());
            ps.setString(3, corpus.corpusName());
            ps.setString(4, toJson(entry.metadata()));
            ps.setString(5, entry.body());
            ps.setString(6, entry.artifactRef());
            ps.setString(7, now);
            ps.setString(8, now);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("Failed to add entry " + entry.id(), e);
        }

        persistToBackend(entry);
        ingestEntry(corpus, entry);
        return entry.id();
    }

    @Override
    public void update(CorpusRef corpus, String entryId, CorpusEntry entry) {
        String now = Instant.now().toString();
        String sql = "UPDATE corpus_entries SET frontmatter_json = ?, body = ?, artifact_ref = ?, last_modified_at = ? WHERE id = ? AND corpus_tenant_id = ? AND corpus_name = ?";
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, toJson(entry.metadata()));
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

        persistToBackend(entry);
        pipeline.delete(entryId, corpus);
        ingestEntry(corpus, entry);
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

        deleteFromBackend(entryId);
        pipeline.delete(entryId, corpus);
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

    private static final int DEFAULT_PREVIEW_LENGTH = 500;

    @Override
    public List<ChangeRecord> recentChanges(CorpusRef corpus, Instant since) {
        if (reader == null) {return List.of();}
        List<String>       paths   = reader.list();
        List<ChangeRecord> changes = new ArrayList<>();

        for (String path : paths) {
            List<VersionInfo> versions = reader.versions(path);
            if (versions.isEmpty()) {continue;}

            List<VersionInfo> afterSince = versions.stream()
                                                   .filter(v -> v.timestamp().isAfter(since))
                                                   .toList();

            if (afterSince.isEmpty()) {continue;}

            boolean    allAfterSince = afterSince.size() == versions.size();
            ChangeType type          = allAfterSince ? ChangeType.ADDED : ChangeType.MODIFIED;

            VersionInfo latest = afterSince.stream()
                                           .max(java.util.Comparator.comparing(VersionInfo::timestamp))
                                           .orElseThrow();

            String preview = loadPreview(path);

            changes.add(new ChangeRecord(path, type, latest.timestamp(), latest.author(), preview));
        }

        changes.sort(java.util.Comparator.comparing(ChangeRecord::timestamp).reversed());
        return List.copyOf(changes);
    }

    @Override
    public List<VersionInfo> versions(CorpusRef corpus, String entryId) {
        if (reader == null) {return List.of();}
        return reader.versions(entryId);
    }

    @Override
    public Optional<CorpusEntry> getVersion(CorpusRef corpus, String entryId, int version) {
        if (reader == null) {return Optional.empty();}
        return reader.readVersion(entryId, version)
                     .map(bytes -> EntryContentParser.parse(entryId, bytes));
    }


    private String loadPreview(String path) {
        return reader.read(path)
                     .map(bytes -> new String(bytes, java.nio.charset.StandardCharsets.UTF_8))
                     .map(content -> content.length() > DEFAULT_PREVIEW_LENGTH
                                     ? content.substring(0, DEFAULT_PREVIEW_LENGTH)
                                     : content)
                     .orElse(null);
    }


    private void ingestEntry(CorpusRef corpus, CorpusEntry entry) {
        var metadata     = new HashMap<String, String>();
        var listMetadata = new HashMap<String, List<String>>();
        for (var e : entry.metadata().entrySet()) {
            if (e.getValue() instanceof String s) {
                metadata.put(e.getKey(), s);
            } else if (e.getValue() instanceof List<?> list) {
                listMetadata.put(e.getKey(), list.stream().map(String::valueOf).toList());
            }
        }
        var extracted = new ExtractionResult(entry.body(), metadata, listMetadata);
        pipeline.ingest(entry.id(), extracted, defaultContext);
    }

    private void persistToBackend(CorpusEntry entry) {
        if (backendStore == null || serializer == null) {return;}
        byte[] content = serializer.serialize(entry);
        backendStore.write(entry.id(), content);
        backendStore.flush();
    }

    private void deleteFromBackend(String entryId) {
        if (backendStore == null) {return;}
        backendStore.delete(entryId);
        backendStore.flush();
    }

}
