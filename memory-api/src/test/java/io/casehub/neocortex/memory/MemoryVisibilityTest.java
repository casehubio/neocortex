package io.casehub.neocortex.memory;

import org.junit.jupiter.api.Test;
import java.util.Set;
import static org.assertj.core.api.Assertions.assertThat;

class MemoryVisibilityTest {

    @Test
    void nullCaller_seesEverything() {
        assertThat(MemoryVisibility.isVisible(null, "alice", Set.of())).isTrue();
        assertThat(MemoryVisibility.isVisible(null, null, Set.of())).isTrue();
    }

    @Test
    void trulyShared_visibleToAnyCaller() {
        assertThat(MemoryVisibility.isVisible("bob", null, Set.of())).isTrue();
    }

    @Test
    void owner_seesOwnMemory() {
        assertThat(MemoryVisibility.isVisible("alice", "alice", Set.of())).isTrue();
    }

    @Test
    void nonOwner_cannotSeePrivate() {
        assertThat(MemoryVisibility.isVisible("bob", "alice", Set.of())).isFalse();
    }

    @Test
    void sharedWith_canSee() {
        assertThat(MemoryVisibility.isVisible("bob", "alice", Set.of("bob", "charlie"))).isTrue();
    }

    @Test
    void notSharedWith_cannotSee() {
        assertThat(MemoryVisibility.isVisible("dave", "alice", Set.of("bob", "charlie"))).isFalse();
    }

    @Test
    void nullSharedWith_treatedAsEmpty() {
        assertThat(MemoryVisibility.isVisible("bob", "alice", null)).isFalse();
    }
}
