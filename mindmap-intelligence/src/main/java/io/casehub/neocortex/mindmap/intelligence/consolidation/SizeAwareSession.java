package io.casehub.neocortex.mindmap.intelligence.consolidation;

import io.casehub.platform.agent.AgentEvent;
import io.casehub.platform.agent.AgentSession;
import io.smallrye.mutiny.Multi;
import io.smallrye.mutiny.Uni;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Logger;

class SizeAwareSession implements AgentSession {

    private static final Logger LOG = Logger.getLogger(SizeAwareSession.class.getName());
    private static final int CHARS_PER_TOKEN = 4;

    private final AgentSession delegate;
    private final int clearThresholdTokens;
    private final String label;
    private final SessionMetrics metrics;
    private final AtomicInteger estimatedTokens = new AtomicInteger();

    SizeAwareSession(AgentSession delegate, int clearThresholdTokens,
                     String label, SessionMetrics metrics) {
        this.delegate = delegate;
        this.clearThresholdTokens = clearThresholdTokens;
        this.label = label;
        this.metrics = metrics;
        metrics.sessionOpened();
    }

    @Override
    public Multi<AgentEvent> query(String userPrompt) {
        int tokensBefore = estimatedTokens.get();
        if (tokensBefore > clearThresholdTokens) {
            LOG.fine(() -> "[" + label + "] auto-clear at " + tokensBefore + " estimated tokens (threshold=" + clearThresholdTokens + ")");
            delegate.clear();
            estimatedTokens.set(0);
            metrics.clearTriggered(tokensBefore);
        }
        estimatedTokens.addAndGet(userPrompt.length() / CHARS_PER_TOKEN);
        metrics.queryStarted();
        return delegate.query(userPrompt)
            .onItem().invoke(event -> {
                if (event instanceof AgentEvent.TextDelta td) {
                    estimatedTokens.addAndGet(td.text().length() / CHARS_PER_TOKEN);
                }
            });
    }

    @Override
    public Uni<Void> interrupt() {
        return delegate.interrupt();
    }

    @Override
    public void clear() {
        int tokens = estimatedTokens.getAndSet(0);
        delegate.clear();
        metrics.clearTriggered(tokens);
    }

    @Override
    public void close(Duration timeout) {
        delegate.close(timeout);
        metrics.sessionClosed();
    }

    @Override
    public void close() {
        delegate.close();
        metrics.sessionClosed();
    }

    int estimatedTokens() {
        return estimatedTokens.get();
    }
}
