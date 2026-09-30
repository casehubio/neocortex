package io.casehub.neocortex.cognition.prompt;

import io.casehub.neocortex.cognition.innerlife.InnerLifeOrchestrator;
import io.casehub.neocortex.cognition.innerlife.InnerLifeTick;
import io.casehub.eidos.api.AgentDescriptor;
import org.jspecify.annotations.Nullable;

public class ProactiveSpeechSupport {

    private static final System.Logger LOG = System.getLogger(ProactiveSpeechSupport.class.getName());

    private final InnerLifeOrchestrator innerLife;
    private final AgentDescriptor descriptor;

    public ProactiveSpeechSupport(InnerLifeOrchestrator innerLife, AgentDescriptor descriptor) {
        this.innerLife = innerLife;
        this.descriptor = descriptor;
    }

    public @Nullable String evaluateProactive(String channelContext) {
        InnerLifeTick tick = innerLife.tick(descriptor, channelContext);
        return switch (tick) {
            case InnerLifeTick.Initiated i -> {
                LOG.log(System.Logger.Level.DEBUG, "Proactive initiation: score={0}", i.motivationScore());
                yield i.content();
            }
            case InnerLifeTick.Silent s -> null;
        };
    }
}
