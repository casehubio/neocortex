package io.casehub.neocortex.mindmap.intelligence.consolidation;

import io.casehub.platform.agent.AgentProvider;
import io.casehub.platform.agent.AgentSession;
import io.casehub.platform.agent.AgentSessionInit;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.logging.Logger;

final class ParallelLlmExecutor {

    private static final Logger LOG = Logger.getLogger(ParallelLlmExecutor.class.getName());
    private static final int DEFAULT_CONCURRENCY = 3;
    private static final int DEFAULT_CLEAR_THRESHOLD_TOKENS = 50_000;

    static SessionMetrics execute(AgentProvider provider, String systemPrompt,
                                   String phaseName, List<Consumer<AgentSession>> tasks) {
        return execute(provider, systemPrompt, phaseName, DEFAULT_CONCURRENCY, tasks);
    }

    static SessionMetrics execute(AgentProvider provider, String systemPrompt,
                                   String phaseName, int concurrency,
                                   List<Consumer<AgentSession>> tasks) {
        var metrics = new SessionMetrics(phaseName);
        if (tasks.isEmpty()) return metrics;
        int poolSize = Math.min(concurrency, tasks.size());

        LOG.info("[" + phaseName + "] starting " + tasks.size() + " tasks with " + poolSize + " sessions");
        long startTime = System.currentTimeMillis();

        var sessionPool = new ArrayBlockingQueue<AgentSession>(poolSize);
        for (int i = 0; i < poolSize; i++) {
            sessionPool.add(new SizeAwareSession(
                provider.openSession(AgentSessionInit.of(systemPrompt)),
                DEFAULT_CLEAR_THRESHOLD_TOKENS,
                phaseName + "-" + i,
                metrics));
        }

        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            var futures = new ArrayList<Future<?>>();
            for (Consumer<AgentSession> task : tasks) {
                futures.add(executor.submit(() -> {
                    AgentSession session;
                    try {
                        session = sessionPool.take();
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        return null;
                    }
                    try {
                        task.accept(session);
                    } catch (Exception e) {
                        LOG.log(Level.WARNING, "Parallel LLM task failed (non-fatal)", e);
                    } finally {
                        try {
                            sessionPool.put(session);
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                        }
                    }
                    return null;
                }));
            }
            for (var f : futures) {
                try { f.get(); } catch (Exception e) { /* already logged */ }
            }
        }

        for (var session : sessionPool) {
            try { session.close(); } catch (Exception e) { /* ignore */ }
        }

        metrics.executionCompleted(System.currentTimeMillis() - startTime);
        return metrics;
    }
}
