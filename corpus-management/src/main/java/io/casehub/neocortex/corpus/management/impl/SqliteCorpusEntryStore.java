package io.casehub.neocortex.corpus.management.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.casehub.neocortex.corpus.ChangeType;
import io.casehub.neocortex.corpus.CorpusReader;
import io.casehub.neocortex.corpus.CorpusStore;
import io.casehub.neocortex.corpus.VersionInfo;
import io.casehub.neocortex.corpus.management.Annotation;
import io.casehub.neocortex.corpus.management.ChangeRecord;
import io.casehub.neocortex.corpus.management.ContentSerializer;
import io.casehub.neocortex.corpus.management.CorpusEntry;
import io.casehub.neocortex.corpus.management.CorpusEntryStore;
import io.casehub.neocortex.corpus.management.EntryRelationship;
import io.casehub.neocortex.corpus.management.StructuralChunker;
import io.casehub.neocortex.corpus.management.StructuredChunk;
import io.casehub.neocortex.rag.ChunkInput;
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

    private final DataSource          dataSource;
    private final IngestionPipeline   pipeline;
    private final IngestionContext    defaultContext;
    private final CorpusStore         backendStore;
    private final ContentSerializer   serializer;
    private final CorpusReader        reader;
    private final StructuralChunker   structuralChunker;

    public SqliteCorpusEntryStore(DataSource dataSource, IngestionPipeline pipeline,
                                  IngestionContext defaultContext,
                                  CorpusStore backendStore, ContentSerializer serializer,
                                  CorpusReader reader) {
        this(dataSource, pipeline, defaultContext, backendStore, serializer, reader, null);
    }

    public SqliteCorpusEntryStore(DataSource dataSource, IngestionPipeline pipeline,
                                  IngestionContext defaultContext,
                                  CorpusStore backendStore, ContentSerializer serializer,
                                  CorpusReader reader, StructuralChunker structuralChunker) {
        this.dataSource        = dataSource;
        this.pipeline          = pipeline;
        this.defaultContext    = defaultContext;
        this.backendStore      = backendStore;
        this.serializer        = serializer;
        this.reader            = reader;
        this.structuralChunker = structuralChunker;
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
        return add(corpus, entry, null);
    }

    @Override
    public String add(CorpusRef corpus, CorpusEntry entry, String actorId) {
        String now = Instant.now().toString();
        String sql = "INSERT INTO corpus_entries (id, corpus_tenant_id, corpus_name, frontmatter_json, body, artifact_ref, created_at, last_modified_at, created_by, updated_by) VALUES (?,?,?,?,?,?,?,?,?,?)";
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
            ps.setString(9, actorId);
            ps.setString(10, actorId);
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
        update(corpus, entryId, entry, null);
    }

    @Override
    public void update(CorpusRef corpus, String entryId, CorpusEntry entry, String actorId) {
        String now = Instant.now().toString();
        String sql = "UPDATE corpus_entries SET frontmatter_json = ?, body = ?, artifact_ref = ?, last_modified_at = ?, updated_by = ? WHERE id = ? AND corpus_tenant_id = ? AND corpus_name = ?";
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, toJson(entry.metadata()));
            ps.setString(2, entry.body());
            ps.setString(3, entry.artifactRef());
            ps.setString(4, now);
            ps.setString(5, actorId);
            ps.setString(6, entryId);
            ps.setString(7, corpus.tenantId());
            ps.setString(8, corpus.corpusName());
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

    @Override
    public Optional<io.casehub.neocortex.corpus.management.AuditInfo> auditInfo(CorpusRef corpus, String entryId) {
        String sql = "SELECT created_by, created_at, updated_by, last_modified_at FROM corpus_entries WHERE id = ? AND corpus_tenant_id = ? AND corpus_name = ?";
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, entryId);
            ps.setString(2, corpus.tenantId());
            ps.setString(3, corpus.corpusName());
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    String createdAtStr = rs.getString("created_at");
                    String updatedAtStr = rs.getString("last_modified_at");
                    return Optional.of(new io.casehub.neocortex.corpus.management.AuditInfo(
                            rs.getString("created_by"),
                            createdAtStr != null ? Instant.parse(createdAtStr) : null,
                            rs.getString("updated_by"),
                            updatedAtStr != null ? Instant.parse(updatedAtStr) : null));
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to get audit info for entry " + entryId, e);
        }
        return Optional.empty();
    }

    @Override
    public List<CorpusEntry> findByMetadataField(CorpusRef corpus, String key, String value) {
        String sql = "SELECT id, frontmatter_json, body, artifact_ref FROM corpus_entries WHERE corpus_tenant_id = ? AND corpus_name = ? AND json_extract(frontmatter_json, '$.' || ?) = ?";
        List<CorpusEntry> results = new ArrayList<>();
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, corpus.tenantId());
            ps.setString(2, corpus.corpusName());
            ps.setString(3, key);
            ps.setString(4, value);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    results.add(fromRow(rs));
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to find entries by metadata " + key + "=" + value, e);
        }
        return results;
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

    @Override
    public String addAnnotation(CorpusRef corpus, String entryId,
                                String note, String annotationType, String annotatedBy) {
        String id = "ANN-" + java.time.LocalDate.now().format(java.time.format.DateTimeFormatter.BASIC_ISO_DATE)
                    + "-" + String.format("%06x", java.util.concurrent.ThreadLocalRandom.current().nextInt(0xFFFFFF));
        String now = Instant.now().toString();
        String sql = "INSERT INTO corpus_annotations (id, entry_id, corpus_tenant_id, corpus_name, note, annotation_type, annotated_by, created_at) VALUES (?,?,?,?,?,?,?,?)";
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, id);
            ps.setString(2, entryId);
            ps.setString(3, corpus.tenantId());
            ps.setString(4, corpus.corpusName());
            ps.setString(5, note);
            ps.setString(6, annotationType);
            ps.setString(7, annotatedBy);
            ps.setString(8, now);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("Failed to add annotation for entry " + entryId, e);
        }
        return id;
    }

    @Override
    public List<Annotation> annotations(CorpusRef corpus, String entryId) {
        String           sql    = "SELECT id, entry_id, note, annotation_type, annotated_by, created_at FROM corpus_annotations WHERE entry_id = ? AND corpus_tenant_id = ? AND corpus_name = ? ORDER BY created_at ASC";
        List<Annotation> result = new ArrayList<>();
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, entryId);
            ps.setString(2, corpus.tenantId());
            ps.setString(3, corpus.corpusName());
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    result.add(new Annotation(
                            rs.getString("id"),
                            rs.getString("entry_id"),
                            rs.getString("note"),
                            rs.getString("annotation_type"),
                            rs.getString("annotated_by"),
                            Instant.parse(rs.getString("created_at"))));
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to get annotations for entry " + entryId, e);
        }
        return List.copyOf(result);
    }

    @Override
    public Map<String, Integer> annotationCounts(CorpusRef corpus, List<String> entryIds) {
        if (entryIds == null || entryIds.isEmpty()) {return Map.of();}
        String               placeholders = String.join(",", entryIds.stream().map(id -> "?").toList());
        String               sql          = "SELECT entry_id, COUNT(*) AS cnt FROM corpus_annotations WHERE entry_id IN (" + placeholders + ") AND corpus_tenant_id = ? AND corpus_name = ? GROUP BY entry_id";
        Map<String, Integer> result       = new HashMap<>();
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            int idx = 1;
            for (String id : entryIds) {ps.setString(idx++, id);}
            ps.setString(idx++, corpus.tenantId());
            ps.setString(idx, corpus.corpusName());
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    result.put(rs.getString("entry_id"), rs.getInt("cnt"));
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to get annotation counts", e);
        }
        return Map.copyOf(result);
    }

    @Override
    public String addRelationship(CorpusRef corpus, String fromEntryId, String toEntryId,
                                  String relationshipType, String createdBy) {
        String id = "REL-" + java.time.LocalDate.now().format(java.time.format.DateTimeFormatter.BASIC_ISO_DATE)
                    + "-" + String.format("%06x", java.util.concurrent.ThreadLocalRandom.current().nextInt(0xFFFFFF));
        String now = Instant.now().toString();
        String sql = "INSERT INTO corpus_entry_relationships (id, from_entry_id, to_entry_id, corpus_tenant_id, corpus_name, relationship_type, created_by, created_at) VALUES (?,?,?,?,?,?,?,?)";
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, id);
            ps.setString(2, fromEntryId);
            ps.setString(3, toEntryId);
            ps.setString(4, corpus.tenantId());
            ps.setString(5, corpus.corpusName());
            ps.setString(6, relationshipType);
            ps.setString(7, createdBy);
            ps.setString(8, now);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("Failed to add relationship from " + fromEntryId + " to " + toEntryId, e);
        }
        return id;
    }

    @Override
    public List<EntryRelationship> relationships(CorpusRef corpus, String entryId) {
        String                  sql    = "SELECT id, from_entry_id, to_entry_id, relationship_type, created_by, created_at FROM corpus_entry_relationships WHERE (from_entry_id = ? OR to_entry_id = ?) AND corpus_tenant_id = ? AND corpus_name = ? ORDER BY created_at ASC";
        List<EntryRelationship> result = new ArrayList<>();
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, entryId);
            ps.setString(2, entryId);
            ps.setString(3, corpus.tenantId());
            ps.setString(4, corpus.corpusName());
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    result.add(new EntryRelationship(
                            rs.getString("id"),
                            rs.getString("from_entry_id"),
                            rs.getString("to_entry_id"),
                            rs.getString("relationship_type"),
                            rs.getString("created_by"),
                            Instant.parse(rs.getString("created_at"))));
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to get relationships for entry " + entryId, e);
        }
        return List.copyOf(result);
    }

    @Override
    public Map<String, Integer> relationshipCounts(CorpusRef corpus, List<String> entryIds) {
        if (entryIds == null || entryIds.isEmpty()) {return Map.of();}
        String placeholders = String.join(",", entryIds.stream().map(id -> "?").toList());
        String sql = "SELECT entry_id, SUM(cnt) AS total FROM ("
                     + "SELECT from_entry_id AS entry_id, COUNT(*) AS cnt FROM corpus_entry_relationships WHERE from_entry_id IN (" + placeholders + ") AND corpus_tenant_id = ? AND corpus_name = ? GROUP BY from_entry_id "
                     + "UNION ALL "
                     + "SELECT to_entry_id AS entry_id, COUNT(*) AS cnt FROM corpus_entry_relationships WHERE to_entry_id IN (" + placeholders + ") AND corpus_tenant_id = ? AND corpus_name = ? GROUP BY to_entry_id"
                     + ") GROUP BY entry_id";
        Map<String, Integer> result = new HashMap<>();
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            int idx = 1;
            for (String id : entryIds) {ps.setString(idx++, id);}
            ps.setString(idx++, corpus.tenantId());
            ps.setString(idx++, corpus.corpusName());
            for (String id : entryIds) {ps.setString(idx++, id);}
            ps.setString(idx++, corpus.tenantId());
            ps.setString(idx, corpus.corpusName());
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    result.put(rs.getString("entry_id"), rs.getInt("total"));
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to get relationship counts", e);
        }
        return Map.copyOf(result);
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
        if (structuralChunker != null
                && "positional-markdown".equals(entry.metadata().get("content_format"))) {
            ingestStructural(corpus, entry);
            return;
        }
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

    private void ingestStructural(CorpusRef corpus, CorpusEntry entry) {
        List<StructuredChunk> chunks = structuralChunker.chunk(entry.body(), 512);
        List<ChunkInput> inputs = chunks.stream()
                .map(sc -> new ChunkInput(sc.text(), entry.id(), mergeChunkMetadata(entry, sc)))
                .toList();
        if (!inputs.isEmpty()) {
            pipeline.ingestor().ingest(corpus, inputs);
        }
    }

    private static Map<String, String> mergeChunkMetadata(CorpusEntry entry, StructuredChunk chunk) {
        var merged = new HashMap<String, String>();
        for (var e : entry.metadata().entrySet()) {
            if (e.getValue() instanceof String s) {
                merged.put(e.getKey(), s);
            }
        }
        merged.putAll(chunk.metadata());
        return merged;
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
