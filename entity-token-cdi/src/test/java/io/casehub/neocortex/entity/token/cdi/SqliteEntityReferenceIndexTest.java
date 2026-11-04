package io.casehub.neocortex.entity.token.cdi;

import io.casehub.neocortex.entity.token.EntityReferenceIndex;
import io.casehub.neocortex.sqlite.SqliteDataSourceFactory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.zaxxer.hikari.HikariDataSource;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class SqliteEntityReferenceIndexTest {

    private HikariDataSource dataSource;
    private EntityReferenceIndex index;

    @BeforeEach
    void setUp() {
        dataSource = SqliteDataSourceFactory.create(":memory:", 1, 3000);
        index = new SqliteEntityReferenceIndex(dataSource);
    }

    @AfterEach
    void tearDown() {
        if (dataSource != null) dataSource.close();
    }

    @Test
    void recordAndFindSourcesByEntity() {
        index.record("e1", "memory", "m1", "t1");
        index.record("e1", "memory", "m2", "t1");
        assertThat(index.findSourcesByEntity("e1", "t1"))
            .containsExactlyInAnyOrder("m1", "m2");
    }

    @Test
    void findEntitiesBySource() {
        index.record("e1", "memory", "m1", "t1");
        index.record("e2", "memory", "m1", "t1");
        assertThat(index.findEntitiesBySource("memory", "m1", "t1"))
            .containsExactlyInAnyOrder("e1", "e2");
    }

    @Test
    void entityMentionCounts() {
        index.record("e1", "memory", "m1", "t1");
        index.record("e1", "memory", "m2", "t1");
        index.record("e2", "memory", "m3", "t1");
        var counts = index.entityMentionCounts("t1");
        assertThat(counts).containsEntry("e1", 2L);
        assertThat(counts).containsEntry("e2", 1L);
    }

    @Test
    void removeBySource() {
        index.record("e1", "memory", "m1", "t1");
        index.record("e1", "memory", "m2", "t1");
        index.removeBySource("memory", "m1", "t1");
        assertThat(index.findSourcesByEntity("e1", "t1")).containsExactly("m2");
    }

    @Test
    void removeByEntity() {
        index.record("e1", "memory", "m1", "t1");
        index.record("e1", "memory", "m2", "t1");
        index.record("e2", "memory", "m1", "t1");
        index.removeByEntity("e1", "t1");
        assertThat(index.findSourcesByEntity("e1", "t1")).isEmpty();
        assertThat(index.findEntitiesBySource("memory", "m1", "t1")).containsExactly("e2");
    }

    @Test
    void repointEntity() {
        index.record("e1", "memory", "m1", "t1");
        index.record("e1", "memory", "m2", "t1");
        index.repointEntity("e1", "e2", "t1");
        assertThat(index.findSourcesByEntity("e1", "t1")).isEmpty();
        assertThat(index.findSourcesByEntity("e2", "t1"))
            .containsExactlyInAnyOrder("m1", "m2");
    }

    @Test
    void repointEntityDeduplicates() {
        index.record("e1", "memory", "m1", "t1");
        index.record("e2", "memory", "m1", "t1");
        index.repointEntity("e1", "e2", "t1");
        assertThat(index.findSourcesByEntity("e2", "t1")).containsExactly("m1");
        assertThat(index.entityMentionCounts("t1")).containsEntry("e2", 1L);
    }

    @Test
    void tenantIsolation() {
        index.record("e1", "memory", "m1", "t1");
        index.record("e1", "memory", "m1", "t2");
        assertThat(index.findSourcesByEntity("e1", "t1")).containsExactly("m1");
        index.removeByEntity("e1", "t1");
        assertThat(index.findSourcesByEntity("e1", "t1")).isEmpty();
        assertThat(index.findSourcesByEntity("e1", "t2")).containsExactly("m1");
    }

    @Test
    void recordAllBatch() {
        index.recordAll(List.of("e1", "e2", "e3"), "memory", "m1", "t1");
        assertThat(index.findEntitiesBySource("memory", "m1", "t1"))
            .containsExactlyInAnyOrder("e1", "e2", "e3");
    }

    @Test
    void duplicateRecordIsIdempotent() {
        index.record("e1", "memory", "m1", "t1");
        index.record("e1", "memory", "m1", "t1");
        assertThat(index.entityMentionCounts("t1")).containsEntry("e1", 1L);
    }
}
