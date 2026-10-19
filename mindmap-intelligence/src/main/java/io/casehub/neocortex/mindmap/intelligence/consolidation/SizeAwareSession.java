package io.casehub.neocortex.mindmap.intelligence.consolidation;

import io.casehub.platform.agent.AgentEvent;
import io.casehub.platform.agent.AgentSession;
import io.smallrye.mutiny.Multi;
import io.smallrye.mutiny.Uni;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;

class SizeAwareSession implements AgentSession {

    private static final int CHARS_PER_TOKEN = 4;

    private final AgentSession delegate;
    private final int clearThresholdTokens;
    private final AtomicInteger estimatedTokens = new AtomicInteger();

    SizeAwareSession(AgentSession delegate, int clearThresholdTokens) {
        this.delegate = delegate;
        this.clearThresholdTokens = clearThresholdTokens;
    }

    @Override
    public Multi<AgentEvent> query(String userPrompt) {
        if (estimatedTokens.get() > clearThresholdTokens) {
            delegate.clear();
            estimatedTokens.set(0);
        }
        estimatedTokens.addAndGet(userPrompt.length() / CHARS_PER_TOKEN);
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
        delegate.clear();
        estimatedTokens.set(0);
    }

    @Override
    public void close(Duration timeout) {
        delegate.close(timeout);
    }

    @Override
    public void close() {
        delegate.close();
    }

    int estimatedTokens() {
        return estimatedTokens.get();
    }
}
