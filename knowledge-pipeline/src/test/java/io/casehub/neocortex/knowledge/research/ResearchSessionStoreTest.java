package io.casehub.neocortex.knowledge.research;

import io.casehub.neocortex.knowledge.ResearchSession;
import io.casehub.neocortex.knowledge.ResearchState;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class ResearchSessionStoreTest {

    private ResearchSessionStore store;

    @BeforeEach
    void setUp() {
        store = new ResearchSessionStore(":memory:");
    }

    @AfterEach
    void tearDown() {
        store.close();
    }

    private ResearchSession testSession(String id, String tenantId) {
        return new ResearchSession(id, "Edinburgh trip",
            "{\"maxPrice\":150}", "sg-1", ResearchState.ACTIVE,
            tenantId, Instant.now(), Instant.now());
    }

    @Test
    void insertAndGetRoundTrips() {
        store.insert(testSession("s1", "t1"));
        var session = store.get("s1");
        assertThat(session).isPresent();
        assertThat(session.get().name()).isEqualTo("Edinburgh trip");
        assertThat(session.get().state()).isEqualTo(ResearchState.ACTIVE);
    }

    @Test
    void getReturnsEmptyForUnknown() {
        assertThat(store.get("nonexistent")).isEmpty();
    }

    @Test
    void updateStateChangesStateAndLastActive() throws InterruptedException {
        store.insert(testSession("s1", "t1"));
        Thread.sleep(10);
        Instant newTime = Instant.now();
        store.updateState("s1", ResearchState.PAUSED, newTime);
        var session = store.get("s1").get();
        assertThat(session.state()).isEqualTo(ResearchState.PAUSED);
        assertThat(session.lastActive()).isEqualTo(newTime);
    }

    @Test
    void listByStateFiltersCorrectly() {
        store.insert(testSession("s1", "t1"));
        store.insert(testSession("s2", "t1"));
        store.updateState("s2", ResearchState.PAUSED, Instant.now());
        var active = store.listByState("t1", ResearchState.ACTIVE);
        assertThat(active).hasSize(1);
        assertThat(active.get(0).id()).isEqualTo("s1");
    }

    @Test
    void listByStateTenantIsolation() {
        store.insert(testSession("s1", "t1"));
        store.insert(testSession("s2", "t2"));
        assertThat(store.listByState("t1", ResearchState.ACTIVE)).hasSize(1);
        assertThat(store.listByState("t2", ResearchState.ACTIVE)).hasSize(1);
    }
}
