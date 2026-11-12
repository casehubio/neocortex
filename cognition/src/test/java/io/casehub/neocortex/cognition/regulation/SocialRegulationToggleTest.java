package io.casehub.neocortex.cognition.regulation;

import io.casehub.neocortex.cognition.appraisal.AppraisalResult;
import io.casehub.neocortex.cognition.core.CognitionTickContext;
import io.casehub.platform.agent.AgentEvent;
import io.casehub.platform.agent.AgentSessionInit;
import io.casehub.platform.agent.session.ManagedSession;
import io.casehub.platform.agent.session.SessionPipeline;
import io.smallrye.mutiny.Multi;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.ArgumentCaptor;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class SocialRegulationToggleTest {

    @ParameterizedTest
    @EnumSource(value = EmotionRegulationStrategy.class, names = {
            "SOCIAL_SUPPORT_SEEKING", "SOCIAL_SHARING", "SOCIAL_WITHDRAWAL", "CO_REGULATION", "DELIBERATE_PROVOCATION"})
    void disablingSocialRegulationRejectsEverySocialStrategy(EmotionRegulationStrategy strategy) {
        assertThat(generate(strategy, "false")).isNull();
    }

    @Test
    void disabledSocialRegulationStillAllowsIntrapersonalStrategies() {
        assertThat(generate(EmotionRegulationStrategy.COGNITIVE_CHANGE, "false").strategy())
                .isEqualTo(EmotionRegulationStrategy.COGNITIVE_CHANGE);
    }

    @Test
    void socialRegulationIsEnabledByDefault() {
        assertThat(generate(EmotionRegulationStrategy.SOCIAL_SUPPORT_SEEKING, null).isSocialStrategy()).isTrue();
    }

    private EmotionRegulationResult generate(EmotionRegulationStrategy strategy, String setting) {
        String property = "manor.regulation.social.enabled";
        String previous = System.getProperty(property);
        try {
            if (setting == null) System.clearProperty(property);
            else System.setProperty(property, setting);
            var pipeline = mock(SessionPipeline.class);
            var session = mock(ManagedSession.class);
            when(pipeline.openSession(eq("emotion-regulation-agent"), any(AgentSessionInit.class))).thenReturn(session);
            when(session.query(any())).thenReturn(Multi.createFrom().item(new AgentEvent.TextDelta(
                    "{\"strategy\":\"" + strategy + "\",\"targetAgentId\":\"friend\"}")));
            var phase = new EmotionRegulationGenerationPhase(pipeline);
            var context = new CognitionTickContext("agent", "tenant", null, (a, t) -> Set.of(),
                    "A distressing conversation", null, null, new RegulationContext("room", List.of("friend"), "conversation"));

            var result = phase.generate(context, new AppraisalResult(List.of(), List.of(), null), List.of());

            var init = ArgumentCaptor.forClass(AgentSessionInit.class);
            verify(pipeline).openSession(eq("emotion-regulation-agent"), init.capture());
            String prompt = init.getValue().systemPrompt();
            if ("false".equals(setting)) {
                assertThat(prompt).contains("Social regulation is disabled", "COGNITIVE_CHANGE")
                        .doesNotContain("SOCIAL_SUPPORT_SEEKING", "SOCIAL_SHARING", "SOCIAL_WITHDRAWAL",
                                "CO_REGULATION", "DELIBERATE_PROVOCATION");
            } else {
                assertThat(prompt).contains("SOCIAL_SUPPORT_SEEKING");
            }
            return result;
        } finally {
            if (previous == null) System.clearProperty(property);
            else System.setProperty(property, previous);
        }
    }
}
