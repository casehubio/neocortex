package io.casehub.neocortex.corpus.testing;

import io.casehub.neocortex.corpus.ChangeSource;
import io.casehub.neocortex.corpus.ChangeType;
import io.casehub.neocortex.corpus.CorpusStore;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public abstract class ChangeSourceContractTest {

    protected abstract ChangeSource changeSource();

    protected abstract CorpusStore store();

    @Test
    void fullScanReturnsNonNullChangeSet() {
        var changeSet = changeSource().fullScan();
        assertThat(changeSet).isNotNull();
        assertThat(changeSet.entries()).isNotNull();
    }

    @Test
    void fullScanEmptyReturnsEmptyEntries() {
        var changeSet = changeSource().fullScan();
        assertThat(changeSet.entries()).isEmpty();
        assertThat(changeSet.newCursor()).isNotNull();
    }

    @Test
    void fullScanAfterAppendsIncludesEntries() {
        store().append("a.txt", "a".getBytes());
        store().append("b.txt", "b".getBytes());

        var changeSet = changeSource().fullScan();
        assertThat(changeSet.entries()).hasSize(2);
        assertThat(changeSet.entries()).allMatch(e -> e.type() == ChangeType.ADDED);
        assertThat(changeSet.entries()).extracting("path")
            .containsExactlyInAnyOrder("a.txt", "b.txt");
    }

    @Test
    void changesSinceWithNullCursorBehavesLikeFullScan() {
        store().append("file.txt", "content".getBytes());

        var changeSet = changeSource().changesSince(null);
        assertThat(changeSet.entries()).isNotEmpty();
        assertThat(changeSet.entries()).allMatch(e -> e.type() == ChangeType.ADDED);
    }

    @Test
    void changesSinceAfterScanReturnsOnlyNewChanges() {
        store().append("existing.txt", "content".getBytes());
        var cursor = changeSource().fullScan().newCursor();

        store().append("new.txt", "new content".getBytes());
        var changeSet = changeSource().changesSince(cursor);

        assertThat(changeSet.entries()).hasSize(1);
        assertThat(changeSet.entries().get(0).path()).isEqualTo("new.txt");
        assertThat(changeSet.entries().get(0).type()).isEqualTo(ChangeType.ADDED);
    }

    @Test
    void changesSinceWithNothingNewReturnsEmpty() {
        store().append("file.txt", "content".getBytes());
        var cursor = changeSource().fullScan().newCursor();

        var changeSet = changeSource().changesSince(cursor);
        assertThat(changeSet.entries()).isEmpty();
        assertThat(changeSet.newCursor()).isNotNull();
    }

    @Test
    void changesSinceDetectsDeletes() {
        store().append("file.txt", "content".getBytes());
        var cursor = changeSource().fullScan().newCursor();

        store().delete("file.txt");
        var changeSet = changeSource().changesSince(cursor);

        assertThat(changeSet.entries()).hasSize(1);
        assertThat(changeSet.entries().get(0).path()).isEqualTo("file.txt");
        assertThat(changeSet.entries().get(0).type()).isEqualTo(ChangeType.DELETED);
    }
}
