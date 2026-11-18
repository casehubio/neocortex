package io.casehub.neocortex.cognition.prompt;

import io.casehub.eidos.api.AgentDescriptor;
import io.casehub.neocortex.cognition.innerlife.InnerLifeOrchestrator;
import io.casehub.neocortex.cognition.innerlife.InnerLifeTick;

import java.util.List;

public class ProactiveSpeechSupport {

    private static final System.Logger LOG = System.getLogger(ProactiveSpeechSupport.class.getName());

    private final InnerLifeOrchestrator innerLife;
    private final AgentDescriptor       descriptor;

    public ProactiveSpeechSupport(InnerLifeOrchestrator innerLife, AgentDescriptor descriptor) {
        this.innerLife  = innerLife;
        this.descriptor = descriptor;
    }

    public InnerLifeTick evaluateProactive(String channelContext) {
        return evaluateProactive(channelContext, List.of());
    }

    public InnerLifeTick evaluateProactive(String channelContext,
                                           List<CognitionPromptRenderer> additionalSections) {
        InnerLifeTick tick = innerLife.tick(descriptor, channelContext, additionalSections);
        if (tick instanceof InnerLifeTick.Initiated i) {
            LOG.log(System.Logger.Level.DEBUG, "Proactive initiation: score={0}", i.motivationScore());
        } else if (tick instanceof InnerLifeTick.NonVerbal nv) {
            LOG.log(System.Logger.Level.DEBUG, "Non-verbal bid: tag={0}, score={1}",
                    nv.tag(), nv.motivationScore());
        }
        return tick;
    }
}
