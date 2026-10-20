package io.casehub.neocortex.mindmap.intelligence.consolidation;

import io.casehub.platform.agent.AgentEvent;
import io.casehub.platform.agent.AgentSession;
import io.casehub.platform.agent.AgentSessionInit;
import io.casehub.platform.agent.SessionRegistry;
import io.smallrye.mutiny.Multi;
import io.smallrye.mutiny.Uni;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;

class ParallelLlmExecutorRegistryTest {

    @Test
    void executesAllTasksThroughRegistrySessions() {
        var registry = new StubRegistry(6);
        var completed = new AtomicInteger();

        List<Consumer<AgentSession>> tasks = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            tasks.add(session -> {
                session.query("work").subscribe().asStream().count();
                completed.incrementAndGet();
            });
        }

        var metrics = ParallelLlmExecutor.execute(registry, "system", "test-phase", 3, tasks);

        assertThat(completed.get()).isEqualTo(10);
        assertThat(registry.openCount.get()).isEqualTo(3);
        assertThat(registry.releaseCount.get()).isEqualTo(3);
        assertThat(registry.sessionCount()).isEqualTo(0);
    }

    @Test
    void usesFewerSessionsWhenCapacityLimited() {
        var registry = new StubRegistry(2);
        var completed = new AtomicInteger();

        List<Consumer<AgentSession>> tasks = new ArrayList<>();
        for (int i = 0; i < 6; i++) {
            tasks.add(session -> completed.incrementAndGet());
        }

        ParallelLlmExecutor.execute(registry, "system", "limited-phase", 4, tasks);

        assertThat(completed.get()).isEqualTo(6);
        assertThat(registry.openCount.get()).isEqualTo(2);
        assertThat(registry.releaseCount.get()).isEqualTo(2);
    }

    @Test
    void releasesSessionsEvenWhenTasksFail() {
        var registry = new StubRegistry(3);

        List<Consumer<AgentSession>> tasks = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            tasks.add(session -> { throw new RuntimeException("task failed"); });
        }

        ParallelLlmExecutor.execute(registry, "system", "failing-phase", 2, tasks);

        assertThat(registry.releaseCount.get()).isEqualTo(2);
        assertThat(registry.sessionCount()).isEqualTo(0);
    }

    @Test
    void emptyTaskListReturnsImmediately() {
        var registry = new StubRegistry(3);

        var metrics = ParallelLlmExecutor.execute(registry, "system", "empty-phase", List.of());

        assertThat(registry.openCount.get()).isEqualTo(0);
        assertThat(registry.releaseCount.get()).isEqualTo(0);
    }

    // --- Test infrastructure ---

    static class StubRegistry implements SessionRegistry {
        private final int maxSessions;
        private final ConcurrentHashMap<String, AgentSession> sessions = new ConcurrentHashMap<>();
        final AtomicInteger openCount = new AtomicInteger();
        final AtomicInteger releaseCount = new AtomicInteger();

        StubRegistry(int maxSessions) {
            this.maxSessions = maxSessions;
        }

        @Override
        public AgentSession openSession(String id, AgentSessionInit init) {
            return openSession(id, init, null);
        }

        @Override
        public AgentSession openSession(String id, AgentSessionInit init, Duration timeout) {
            if (sessions.size() >= maxSessions) return null;
            var session = new StubSession();
            sessions.put(id, session);
            openCount.incrementAndGet();
            return session;
        }

        @Override
        public void releaseSession(String id) {
            if (sessions.remove(id) != null) {
                releaseCount.incrementAndGet();
            }
        }

        @Override
        public int sessionCount() {
            return sessions.size();
        }
    }

    static class StubSession implements AgentSession {
        @Override
        public Multi<AgentEvent> query(String prompt) {
            return Multi.createFrom().item(new AgentEvent.TextDelta("response"));
        }

        @Override
        public Uni<Void> interrupt() {
            return Uni.createFrom().voidItem();
        }

        @Override
        public void clear() {}

        @Override
        public void close(Duration maxWait) {}
    }
}
