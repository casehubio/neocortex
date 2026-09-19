package io.casehub.neocortex.sqlite;

import com.zaxxer.hikari.HikariDataSource;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;

import static org.assertj.core.api.Assertions.assertThat;

class SqliteDataSourceFactoryTest {

    @Test
    void create_inMemory_returnsWorkingDataSource() throws Exception {
        try (HikariDataSource ds = SqliteDataSourceFactory.create(":memory:", 1, 5000)) {
            try (Connection conn = ds.getConnection();
                 Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery("SELECT 1")) {
                assertThat(rs.next()).isTrue();
                assertThat(rs.getInt(1)).isEqualTo(1);
            }
        }
    }

    @Test
    void create_blankPath_treatedAsMemory() throws Exception {
        try (HikariDataSource ds = SqliteDataSourceFactory.create("", 5, 5000)) {
            assertThat(ds.getMaximumPoolSize()).isEqualTo(1);
        }
    }

    @Test
    void create_nullPath_treatedAsMemory() throws Exception {
        try (HikariDataSource ds = SqliteDataSourceFactory.create(null, 5, 5000)) {
            assertThat(ds.getMaximumPoolSize()).isEqualTo(1);
        }
    }

    @Test
    void create_withCacheSize_setsCache() throws Exception {
        try (HikariDataSource ds = SqliteDataSourceFactory.create(":memory:", 1, 5000, 64000)) {
            try (Connection conn = ds.getConnection();
                 Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery("PRAGMA cache_size")) {
                assertThat(rs.next()).isTrue();
                assertThat(rs.getInt(1)).isEqualTo(64000);
            }
        }
    }

    @Test
    void migrate_runsFlywayMigration() throws Exception {
        try (HikariDataSource ds = SqliteDataSourceFactory.create(":memory:", 1, 5000)) {
            SqliteDataSourceFactory.migrate(ds, "classpath:db/test/migration");

            try (Connection conn = ds.getConnection();
                 Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery(
                     "SELECT name FROM sqlite_master WHERE type='table' AND name='test_table'")) {
                assertThat(rs.next()).isTrue();
            }
        }
    }
}
