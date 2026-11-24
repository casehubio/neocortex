package io.casehub.neocortex.memory;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class MonotonicIdTest {

    @Test
    void generatedIdsAreLexicographicallySorted() {
        var ids = new ArrayList<String>();
        for (int i = 0; i < 100; i++) {
            ids.add(MonotonicId.generate());
        }
        var sorted = new ArrayList<>(ids);
        sorted.sort(String::compareTo);
        assertThat(ids).isEqualTo(sorted);
    }

    @Test
    void generatedIdsAreUnique() {
        var ids = new java.util.HashSet<String>();
        for (int i = 0; i < 1000; i++) {
            ids.add(MonotonicId.generate());
        }
        assertThat(ids).hasSize(1000);
    }

    @Test
    void laterIdSortsAfterEarlierIdForCursorPagination() {
        String first = MonotonicId.generate();
        String second = MonotonicId.generate();
        assertThat(second.compareTo(first))
                .as("second ID must sort after first for WHERE memory_id > cursor")
                .isGreaterThan(0);
    }

    @Test
    void idFormatIsConsistent() {
        String id = MonotonicId.generate();
        assertThat(id).matches("\\d{13}-\\d{6}-[0-9a-f]{8}");
    }
}
