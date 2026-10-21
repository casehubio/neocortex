package io.casehub.neocortex.mindmap.intelligence.consolidation;

import io.casehub.platform.agent.AgentBackend;
import io.casehub.platform.agent.AgentEvent;
import io.casehub.platform.agent.AgentSession;
import io.casehub.platform.agent.AgentSessionInit;
import io.casehub.platform.agent.BackendInstanceRegistry;
import io.casehub.platform.agent.config.PoolDeclaration;
import io.casehub.platform.agent.gate.AimdAdmissionStrategy;
import io.casehub.platform.agent.session.SessionLifecycleManager;
import io.casehub.platform.agent.session.SessionPipeline;
import io.casehub.platform.agent.session.SessionPoolRegistry;
import io.smallrye.mutiny.Multi;
import io.smallrye.mutiny.Uni;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;

class ParallelLlmExecutorRegistryTest {

    private SessionPoolRegistry poolRegistry;
    private SessionLifecycleManager sessionManager;
    private SessionPipeline pipeline;

    @BeforeEach
    void setUp() {
        var decl = new PoolDeclaration("test-pool", "test", "stub",
                0, 20, null, Map.of(), Map.of());
        poolRegistry = new SessionPoolRegistry(List.of(decl), stubBackendRegistry());
        sessionManager = new SessionLifecycleManager(poolRegistry);
    }

    @AfterEach
    void tearDown() {
        if (pipeline != null) pipeline.shutdown();
        sessionManager.shutdown();
        poolRegistry.shutdown();
    }

    @Test
    void executesAllTasksThroughPipelineSessions() {
        var admission = new AimdAdmissionStrategy(6, 5, Duration.ofSeconds(10));
        pipeline = new SessionPipeline(sessionManager, "stub", admission, 50_000);
        var completed = new AtomicInteger();

        List<Consumer<AgentSession>> tasks = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            tasks.add(session -> {
                session.query("work").subscribe().asStream().count();
                completed.incrementAndGet();
            });
        }

        var metrics = ParallelLlmExecutor.execute(pipeline, "system", "test-phase", 3, tasks);

        assertThat(completed.get()).isEqualTo(10);
        assertThat(pipeline.sessionCount()).isEqualTo(0);
    }

    @Test
    void releasesSessionsEvenWhenTasksFail() {
        var admission = new AimdAdmissionStrategy(6, 5, Duration.ofSeconds(10));
        pipeline = new SessionPipeline(sessionManager, "stub", admission, 50_000);

        List<Consumer<AgentSession>> tasks = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            tasks.add(session -> { throw new RuntimeException("task failed"); });
        }

        ParallelLlmExecutor.execute(pipeline, "system", "failing-phase", 2, tasks);

        assertThat(pipeline.sessionCount()).isEqualTo(0);
    }

    @Test
    void emptyTaskListReturnsImmediately() {
        var admission = new AimdAdmissionStrategy(6, 5, Duration.ofSeconds(10));
        pipeline = new SessionPipeline(sessionManager, "stub", admission, 50_000);

        var metrics = ParallelLlmExecutor.execute(pipeline, "system", "empty-phase", List.of());

        assertThat(pipeline.sessionCount()).isEqualTo(0);
    }

    @Test
    void aimdPermitsControlConcurrency() {
        var admission = new AimdAdmissionStrategy(2, 5, Duration.ofSeconds(10));
        pipeline = new SessionPipeline(sessionManager, "stub", admission, 50_000);
        var maxConcurrent = new AtomicInteger(0);
        var currentConcurrent = new AtomicInteger(0);

        List<Consumer<AgentSession>> tasks = new ArrayList<>();
        for (int i = 0; i < 8; i++) {
            tasks.add(session -> {
                int c = currentConcurrent.incrementAndGet();
                maxConcurrent.updateAndGet(m -> Math.max(m, c));
                try { Thread.sleep(50); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
                currentConcurrent.decrementAndGet();
            });
        }

        ParallelLlmExecutor.execute(pipeline, "system", "concurrency-phase", 4, tasks);

        assertThat(maxConcurrent.get()).isLessThanOrEqualTo(2);
        assertThat(admission.activeCount()).isEqualTo(0);
    }

    private static BackendInstanceRegistry stubBackendRegistry() {
        return new BackendInstanceRegistry() {
            private final AgentBackend stub = new StubBackend();
            @Override public void register(AgentBackend backend) {}
            @Override public Optional<AgentBackend> resolve(String key, String instanceId) {
                return "stub".equals(key) ? Optional.of(stub) : Optional.empty();
            }
            @Override public List<AgentBackend> resolveByKey(String key) {
                return "stub".equals(key) ? List.of(stub) : List.of();
            }
        };
    }

    static class StubBackend implements AgentBackend {
        @Override public String key() { return "stub"; }
        @Override public Multi<AgentEvent> invoke(io.casehub.platform.agent.AgentSessionConfig config) {
            return Multi.createFrom().item(new AgentEvent.TextDelta("response"));
        }
        @Override public AgentSession openSession(AgentSessionInit init) {
            return new StubSession();
        }
    }

    static class StubSession implements AgentSession {
        @Override
        public Multi<AgentEvent> query(String prompt) {
            return Multi.createFrom().item(new AgentEvent.TextDelta("response"));
        }
        @Override public Uni<Void> interrupt() { return Uni.createFrom().voidItem(); }
        @Override public void clear() {}
        @Override public void close(Duration maxWait) {}
    }
}
