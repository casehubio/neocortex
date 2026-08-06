package io.casehub.neocortex.rag.cache;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import io.casehub.neocortex.inference.MultiModalEmbedding;
import org.flywaydb.core.Flyway;
import org.sqlite.SQLiteConfig;
import org.sqlite.SQLiteConfig.JournalMode;
import org.sqlite.SQLiteConfig.SynchronousMode;
import org.sqlite.SQLiteDataSource;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;

public class EmbeddingCache {

    private static final Logger LOG =
            Logger.getLogger(EmbeddingCache.class.getName());

    private final String path;
    private final String modelVersion;
    private HikariDataSource dataSource;

    public EmbeddingCache(String path, String modelVersion) {
        this.path = path;
        this.modelVersion = modelVersion;
    }

    public void init() {
        boolean isMemory = ":memory:".equals(path) || path.isBlank();
        SQLiteConfig sqLiteConfig = new SQLiteConfig();
        if (!isMemory) {
            sqLiteConfig.setJournalMode(JournalMode.WAL);
        }
        sqLiteConfig.setSynchronous(SynchronousMode.NORMAL);
        sqLiteConfig.setBusyTimeout(5000);

        SQLiteDataSource sqLiteDataSource = new SQLiteDataSource(sqLiteConfig);
        sqLiteDataSource.setUrl("jdbc:sqlite:" + path);

        HikariConfig hikari = new HikariConfig();
        hikari.setDataSource(sqLiteDataSource);
        hikari.setMaximumPoolSize(isMemory ? 1 : 3);
        hikari.setMinimumIdle(1);
        dataSource = new HikariDataSource(hikari);

        Flyway.configure()
                .dataSource(dataSource)
                .locations("classpath:db/embedding-cache/migration")
                .load()
                .migrate();
    }

    public void shutdown() {
        if (dataSource != null) {
            dataSource.close();
        }
    }

    public Optional<MultiModalEmbedding> get(String contentHash) {
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                     "SELECT dense, sparse, colbert FROM embedding_cache "
                     + "WHERE content_hash = ? AND model_version = ?")) {
            ps.setString(1, contentHash);
            ps.setString(2, modelVersion);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(EmbeddingSerializer.deserialize(
                            rs.getBytes("dense"),
                            rs.getBytes("sparse"),
                            rs.getBytes("colbert")));
                }
            }
        } catch (SQLException e) {
            LOG.log(Level.WARNING, "Cache read failed for " + contentHash, e);
        }
        return Optional.empty();
    }

    public Map<String, MultiModalEmbedding> getBatch(List<String> contentHashes) {
        if (contentHashes.isEmpty()) return Map.of();
        Map<String, MultiModalEmbedding> result = new LinkedHashMap<>();
        String placeholders = String.join(",",
                Collections.nCopies(contentHashes.size(), "?"));
        String sql = "SELECT content_hash, dense, sparse, colbert "
                + "FROM embedding_cache WHERE content_hash IN ("
                + placeholders + ") AND model_version = ?";
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            int idx = 1;
            for (String hash : contentHashes) {
                ps.setString(idx++, hash);
            }
            ps.setString(idx, modelVersion);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    String hash = rs.getString("content_hash");
                    result.put(hash, EmbeddingSerializer.deserialize(
                            rs.getBytes("dense"),
                            rs.getBytes("sparse"),
                            rs.getBytes("colbert")));
                }
            }
        } catch (SQLException e) {
            LOG.log(Level.WARNING, "Batch cache read failed", e);
        }
        return result;
    }

    public void put(String contentHash, MultiModalEmbedding embedding) {
        String sql = "INSERT OR REPLACE INTO embedding_cache "
                + "(content_hash, model_version, dense, sparse, colbert) "
                + "VALUES (?, ?, ?, ?, ?)";
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, contentHash);
            ps.setString(2, modelVersion);
            ps.setBytes(3, EmbeddingSerializer.serializeDense(embedding));
            ps.setBytes(4, EmbeddingSerializer.serializeSparse(embedding));
            ps.setBytes(5, EmbeddingSerializer.serializeColbert(embedding));
            ps.executeUpdate();
        } catch (SQLException e) {
            LOG.log(Level.WARNING, "Cache write failed for " + contentHash, e);
        }
    }

    public void putBatch(Map<String, MultiModalEmbedding> entries) {
        if (entries.isEmpty()) return;
        String sql = "INSERT OR REPLACE INTO embedding_cache "
                + "(content_hash, model_version, dense, sparse, colbert) "
                + "VALUES (?, ?, ?, ?, ?)";
        try (Connection conn = dataSource.getConnection()) {
            conn.setAutoCommit(false);
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                for (var entry : entries.entrySet()) {
                    ps.setString(1, entry.getKey());
                    ps.setString(2, modelVersion);
                    ps.setBytes(3, EmbeddingSerializer.serializeDense(entry.getValue()));
                    ps.setBytes(4, EmbeddingSerializer.serializeSparse(entry.getValue()));
                    ps.setBytes(5, EmbeddingSerializer.serializeColbert(entry.getValue()));
                    ps.addBatch();
                }
                ps.executeBatch();
                conn.commit();
            } catch (Exception e) {
                conn.rollback();
                LOG.log(Level.WARNING, "Batch cache write failed", e);
            } finally {
                conn.setAutoCommit(true);
            }
        } catch (SQLException e) {
            LOG.log(Level.WARNING, "Batch cache write failed (connection)", e);
        }
    }
}
