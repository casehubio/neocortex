package io.casehub.neocortex.mindmap.intelligence.consolidation;

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

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;

final class StubPipelines {

    private StubPipelines() {}

    static SessionPipeline create(Function<AgentSessionInit, AgentSession> sessionFactory) {
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
}
