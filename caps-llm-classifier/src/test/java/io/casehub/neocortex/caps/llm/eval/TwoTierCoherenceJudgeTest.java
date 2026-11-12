package io.casehub.neocortex.caps.llm.eval;

import io.casehub.platform.agent.AgentEvent;
import io.casehub.platform.agent.AgentProvider;
import io.casehub.platform.agent.AgentSessionConfig;
import io.smallrye.mutiny.Multi;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.ArgumentCaptor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class TwoTierCoherenceJudgeTest {

    @ParameterizedTest
    @CsvSource({"0.0,false", "1.49,false", "1.5,true", "2.5,true", "3.5,true", "3.51,false", "4.0,false", "5.0,false"})
    void escalatesOnlyAmbiguousHaikuScores(double initialScore, boolean escalate) {
        var provider = mock(AgentProvider.class);
        when(provider.invoke(any(AgentSessionConfig.class))).thenAnswer(invocation -> {
            var config = invocation.<AgentSessionConfig>getArgument(0);
            boolean haiku = "haiku".equals(config.model());
            double score = haiku ? initialScore : 4.25;
            String reasoning = haiku ? "Initial assessment" : "Second opinion";
            return Multi.createFrom().items(
                    new AgentEvent.TextDelta("{\"score\":" + score + ","),
                    new AgentEvent.TextDelta("\"reasoning\":\"" + reasoning + "\"}"));
        });
        var transition = new CoherenceJudge.StageTransition(
                "appraisal to regulation", "Fear", "Seeking reassurance", "Regulation addresses the appraised fear");

        var result = new CoherenceJudge.TwoTierCoherenceJudge(provider).judge(transition);

        assertThat(result).isEqualTo(new CoherenceJudge.CoherenceScore(
                escalate ? 4.25 : initialScore, escalate ? "Second opinion" : "Initial assessment"));
        var requests = ArgumentCaptor.forClass(AgentSessionConfig.class);
        verify(provider, times(escalate ? 2 : 1)).invoke(requests.capture());
        assertThat(requests.getAllValues().getFirst().model()).isEqualTo("haiku");
        assertThat(requests.getAllValues().getFirst().userPrompt())
                .contains(transition.name(), transition.fromStageData(), transition.toStageData(), transition.coherenceExpectation());
        if (escalate) {
            var first = requests.getAllValues().getFirst();
            var second = requests.getAllValues().getLast();
            assertThat(second.model()).isEqualTo("opus");
            assertThat(second.userPrompt()).isEqualTo(first.userPrompt());
            assertThat(second.systemPrompt()).isEqualTo(first.systemPrompt());
        }
    }
}
