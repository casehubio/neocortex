package io.casehub.neocortex.session.testing;

import io.casehub.platform.agent.AgentBackend;
import io.casehub.platform.agent.AgentEvent;
import io.casehub.platform.agent.AgentSession;
import io.casehub.platform.agent.AgentSessionConfig;
import io.casehub.platform.agent.AgentSessionInit;
import io.casehub.platform.agent.BackendInstanceRegistry;
import io.casehub.platform.agent.config.PoolDeclaration;
import io.casehub.platform.agent.gate.AimdAdmissionStrategy;
import io.casehub.platform.agent.session.SessionLifecycleManager;
import io.casehub.platform.agent.session.SessionPipeline;
import io.casehub.platform.agent.session.SessionPoolRegistry;
import io.smallrye.mutiny.Multi;
import io.smallrye.mutiny.Uni;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;

public final class StubPipelines {

    private StubPipelines() {}

    public static SessionPipeline create(Function<AgentSessionInit, AgentSession> sessionFactory) {
        AgentBackend backend = new AgentBackend() {
            @Override
            public String key() { return "stub"; }

            @Override
            public Multi<AgentEvent> invoke(AgentSessionConfig config) {
                throw new UnsupportedOperationException();
            }

            @Override
            public AgentSession openSession(AgentSessionInit init) {
                return sessionFactory.apply(init);
            }
        };

        BackendInstanceRegistry registry = new BackendInstanceRegistry() {
            @Override
            public void register(AgentBackend b) {}

            @Override
            public Optional<AgentBackend> resolve(String key, String instanceId) {
                return Optional.of(backend);
            }

            @Override
            public List<AgentBackend> resolveByKey(String key) {
                return List.of(backend);
            }
        };

        var pool = new PoolDeclaration("test", "test", "stub", 0, 20, null, Map.of(), Map.of());
        var poolRegistry = new SessionPoolRegistry(List.of(pool), registry);
        var lifecycleManager = new SessionLifecycleManager(poolRegistry);
        return new SessionPipeline(lifecycleManager, "stub",
                new AimdAdmissionStrategy(6, 5, Duration.ofSeconds(10)), 50_000);
    }

    public static SessionPipeline returning(String... textDeltas) {
        return create(init -> stubSession(textDeltas));
    }

    public static SessionPipeline empty() {
        return returning();
    }

    public static SessionPipeline returningJson(String json) {
        return returning(json);
    }

    public static SessionPipeline counting(AtomicInteger counter, String... textDeltas) {
        return create(init -> new AgentSession() {
            @Override
            public Multi<AgentEvent> query(String prompt) {
                counter.incrementAndGet();
                var items = java.util.Arrays.stream(textDeltas)
                        .map(AgentEvent.TextDelta::new)
                        .toArray(AgentEvent[]::new);
                return Multi.createFrom().items(items);
            }

            @Override
            public Uni<Void> interrupt() {
                return Uni.createFrom().voidItem();
            }

            @Override
            public void close(Duration timeout) {}
        });
    }

    private static AgentSession stubSession(String... textDeltas) {
        return new AgentSession() {
            @Override
            public Multi<AgentEvent> query(String prompt) {
                var items = java.util.Arrays.stream(textDeltas)
                        .map(AgentEvent.TextDelta::new)
                        .toArray(AgentEvent[]::new);
                return Multi.createFrom().items(items);
            }

            @Override
            public Uni<Void> interrupt() {
                return Uni.createFrom().voidItem();
            }

            @Override
            public void close(Duration timeout) {}
        };
    }
}
