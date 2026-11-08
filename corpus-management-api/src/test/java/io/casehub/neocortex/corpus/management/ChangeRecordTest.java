package io.casehub.neocortex.corpus.management;

import io.casehub.neocortex.corpus.ChangeType;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ChangeRecordTest {

    @Test
    void shouldCreateWithAllFields() {
        var now = Instant.now();
        var record = new ChangeRecord("docs/readme.md", ChangeType.MODIFIED, now, "Alice", "# README");
        assertThat(record.path()).isEqualTo("docs/readme.md");
        assertThat(record.type()).isEqualTo(ChangeType.MODIFIED);
        assertThat(record.timestamp()).isEqualTo(now);
        assertThat(record.author()).isEqualTo("Alice");
        assertThat(record.preview()).isEqualTo("# README");
    }

    @Test
    void shouldAllowNullAuthorAndPreview() {
        var record = new ChangeRecord("file.txt", ChangeType.ADDED, Instant.now(), null, null);
        assertThat(record.author()).isNull();
        assertThat(record.preview()).isNull();
    }

    @Test
    void shouldRejectNullPath() {
        assertThatThrownBy(() -> new ChangeRecord(null, ChangeType.ADDED, Instant.now(), null, null))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void changeResultShouldWrapList() {
        var r1 = new ChangeRecord("a.txt", ChangeType.ADDED, Instant.now(), null, "content");
        var r2 = new ChangeRecord("b.txt", ChangeType.MODIFIED, Instant.now(), "Bob", "more");
        var result = new ChangeResult(List.of(r1, r2));
        assertThat(result.changes()).hasSize(2);
        assertThat(result.count()).isEqualTo(2);
    }

    @Test
    void changeResultShouldDefensiveCopy() {
        var list = new java.util.ArrayList<ChangeRecord>();
        list.add(new ChangeRecord("a.txt", ChangeType.ADDED, Instant.now(), null, null));
        var result = new ChangeResult(list);
        list.clear();
        assertThat(result.changes()).hasSize(1);
    }
}
