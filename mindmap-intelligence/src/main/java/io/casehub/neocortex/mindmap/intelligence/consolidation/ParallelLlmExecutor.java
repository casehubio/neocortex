package io.casehub.neocortex.mindmap.intelligence.consolidation;

import io.casehub.platform.agent.AgentProvider;
import io.casehub.platform.agent.AgentSession;
import io.casehub.platform.agent.AgentSessionInit;
import io.casehub.platform.agent.SessionRegistry;

import java.time.Duration;
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
    private static final int DEFAULT_CONCURRENCY = 6;
    private static final int DEFAULT_CLEAR_THRESHOLD_TOKENS = 50_000;
    private static final int MAX_OPEN_RETRIES = 6;
    private static final long RETRY_DELAY_MS = 10_000;
    private static final long REGISTRY_TIMEOUT_SECONDS = 120;

    private static AgentSession openWithRetry(AgentProvider provider, String systemPrompt,
                                               String label, SessionMetrics metrics) {
        for (int attempt = 0; attempt <= MAX_OPEN_RETRIES; attempt++) {
            try {
                return new SizeAwareSession(
                    provider.openSession(AgentSessionInit.of(systemPrompt)),
                    DEFAULT_CLEAR_THRESHOLD_TOKENS, label, metrics);
            } catch (io.casehub.platform.agent.AgentSessionLimitException e) {
                if (attempt < MAX_OPEN_RETRIES) {
                    LOG.info("[" + label + "] session limit hit, waiting " + RETRY_DELAY_MS + "ms (attempt " + (attempt + 1) + "/" + MAX_OPEN_RETRIES + ")");
                    try { Thread.sleep(RETRY_DELAY_MS); } catch (InterruptedException ie) { Thread.currentThread().interrupt(); return null; }
                } else {
                    LOG.warning("[" + label + "] could not open session after " + MAX_OPEN_RETRIES + " retries");
                    return null;
                }
            }
        }
        return null;
    }

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
        int opened = 0;
        for (int i = 0; i < poolSize; i++) {
            AgentSession session = openWithRetry(provider, systemPrompt, phaseName + "-" + i, metrics);
            if (session != null) {
                sessionPool.add(session);
                opened++;
            }
        }
        if (opened == 0) {
            LOG.warning("[" + phaseName + "] could not open any sessions — skipping");
            return metrics;
        }
        if (opened < poolSize) {
            LOG.info("[" + phaseName + "] opened " + opened + "/" + poolSize + " sessions (backend limit)");
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

    static SessionMetrics execute(SessionRegistry registry, String systemPrompt,
                                  String phaseName, List<Consumer<AgentSession>> tasks) {
        return execute(registry, systemPrompt, phaseName, DEFAULT_CONCURRENCY, tasks);
    }

    static SessionMetrics execute(SessionRegistry registry, String systemPrompt,
                                  String phaseName, int concurrency,
                                  List<Consumer<AgentSession>> tasks) {
        var metrics = new SessionMetrics(phaseName);
        if (tasks.isEmpty()) {return metrics;}
        int poolSize = Math.min(concurrency, tasks.size());

        LOG.info("[" + phaseName + "] starting " + tasks.size() + " tasks with " + poolSize + " sessions (registry)");
        long startTime = System.currentTimeMillis();

        var sessionIds  = new ArrayList<String>();
        var sessionPool = new ArrayBlockingQueue<AgentSession>(poolSize);
        for (int i = 0; i < poolSize; i++) {
            String id = phaseName + ":" + i;
            AgentSession session = registry.openSession(id,
                                                        AgentSessionInit.of(systemPrompt),
                                                        Duration.ofSeconds(REGISTRY_TIMEOUT_SECONDS));
            if (session != null) {
                sessionIds.add(id);
                sessionPool.add(session);
            }
        }
        if (sessionIds.isEmpty()) {
            LOG.warning("[" + phaseName + "] could not open any sessions — skipping");
            return metrics;
        }
        if (sessionIds.size() < poolSize) {
            LOG.info("[" + phaseName + "] opened " + sessionIds.size() + "/" + poolSize
                     + " sessions (capacity limited)");
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
                try {f.get();} catch (Exception e) { /* already logged */ }
            }
        }

        for (String id : sessionIds) {
            registry.releaseSession(id);
        }

        metrics.executionCompleted(System.currentTimeMillis() - startTime);
        return metrics;
    }
}
