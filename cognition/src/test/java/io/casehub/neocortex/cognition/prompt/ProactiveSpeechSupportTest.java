package io.casehub.neocortex.cognition.prompt;

import io.casehub.eidos.api.AgentDescriptor;
import io.casehub.neocortex.cognition.innerlife.InnerLifeOrchestrator;
import io.casehub.neocortex.cognition.innerlife.InnerLifeTick;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ProactiveSpeechSupportTest {

    @Test
    void evaluateProactiveReturnsSilentTick() {
        var innerLife = mock(InnerLifeOrchestrator.class);
        var descriptor = mock(AgentDescriptor.class);
        when(innerLife.tick(eq(descriptor), eq("ctx"), any())).thenReturn(new InnerLifeTick.Silent(null));

        var support = new ProactiveSpeechSupport(innerLife, descriptor);
        var result = support.evaluateProactive("ctx");

        assertThat(result).isInstanceOf(InnerLifeTick.Silent.class);
    }

    @Test
    void evaluateProactiveReturnsInitiatedTick() {
        var innerLife = mock(InnerLifeOrchestrator.class);
        var descriptor = mock(AgentDescriptor.class);
        when(innerLife.tick(eq(descriptor), eq("ctx"), any()))
                .thenReturn(new InnerLifeTick.Initiated("Hello!", "#general", 0.9));

        var support = new ProactiveSpeechSupport(innerLife, descriptor);
        var result = support.evaluateProactive("ctx");

        assertThat(result).isInstanceOf(InnerLifeTick.Initiated.class);
        assertThat(((InnerLifeTick.Initiated) result).content()).isEqualTo("Hello!");
    }

    @Test
    void evaluateProactiveReturnsNonVerbalTick() {
        var innerLife = mock(InnerLifeOrchestrator.class);
        var descriptor = mock(AgentDescriptor.class);
        when(innerLife.tick(eq(descriptor), eq("ctx"), any()))
                .thenReturn(new InnerLifeTick.NonVerbal("knock", 0.75));

        var support = new ProactiveSpeechSupport(innerLife, descriptor);
        var result = support.evaluateProactive("ctx");

        assertThat(result).isInstanceOf(InnerLifeTick.NonVerbal.class);
        assertThat(((InnerLifeTick.NonVerbal) result).tag()).isEqualTo("knock");
    }

    @Test
    void evaluateProactivePassesSectionsToOrchestrator() {
        var innerLife = mock(InnerLifeOrchestrator.class);
        var descriptor = mock(AgentDescriptor.class);
        CognitionPromptRenderer section = ctx -> "gesture instructions";
        var sections = List.<CognitionPromptRenderer>of(section);
        when(innerLife.tick(descriptor, "ctx", sections)).thenReturn(new InnerLifeTick.Silent(null));

        var support = new ProactiveSpeechSupport(innerLife, descriptor);
        support.evaluateProactive("ctx", sections);

        verify(innerLife).tick(descriptor, "ctx", sections);
    }
}
