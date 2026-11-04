package io.casehub.neocortex.caps.llm.eval;

import io.casehub.platform.agent.AgentEvent;
import io.casehub.platform.agent.AgentSessionConfig;
import io.casehub.platform.agent.AgentSessionInit;
import io.casehub.platform.agent.session.SessionPipeline;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Minimal reproducer: compare invoke() vs openSession().query() on the same
 * backend with the same system prompt and user message.
 *
 * If invoke() produces text but openSession().query() does not, the issue
 * is in the session/pipeline layer. If both work, the issue is in the
 * AppraisalTickParticipant wiring upstream.
 */
@Tag("llm-eval")
class SessionVsInvokeReproducerTest {

    private static final String SYSTEM = """
            You are a character's subconscious. Respond with JSON only:
            {"narrative":"gut reaction","dimensions":{"relevance":0.9,"conduciveness":-0.8,"controllability":0.6,"novelty":0.7,"internal-standards":0.3,"external-standards":0.4}}""";

    private static final String USER = """
            Drives:
            - affiliation: 0.50
            - autonomy: 0.50

            Situation:
            Your trusted manager has been embezzling for years. You just found proof.""";

    private static SessionPipeline sessionPipeline;

    @BeforeAll
    static void setUp() {
        sessionPipeline = EvalTestSupport.sessionPipeline();
    }

    @Test
    void invoke_producesTextResponse() {
        var provider = EvalTestSupport.agentProvider();
        var raw = provider.invoke(AgentSessionConfig.of(SYSTEM, USER))
                .filter(e -> e instanceof AgentEvent.TextDelta)
                .map(e -> ((AgentEvent.TextDelta) e).text())
                .collect().with(Collectors.joining())
                .await().atMost(Duration.ofSeconds(60));

        System.out.printf("invoke() response (%d chars): %.200s%n", raw.length(), raw);
        assertThat(raw).as("invoke() should produce text").isNotBlank();
    }

    @Test
    void openSession_producesTextResponse() {
        var session = sessionPipeline.openSession("reproducer-appraisal",
                AgentSessionInit.of(SYSTEM));
        var responseText = new StringBuilder();
        session.query(USER).subscribe().asStream()
                .filter(e -> e instanceof AgentEvent.TextDelta)
                .map(e -> ((AgentEvent.TextDelta) e).text())
                .forEach(responseText::append);

        System.out.printf("openSession() response (%d chars): %.200s%n",
                responseText.length(), responseText);
        assertThat(responseText.toString()).as("openSession().query() should produce text").isNotBlank();
    }

    @Test
    void bareSession_producesTextResponse() {
        // Bypass SessionPipeline entirely — go straight to backend.openSession()
        var backend = EvalTestSupport.backend();
        var session = backend.openSession(AgentSessionInit.of(SYSTEM));
        try {
            var responseText = new StringBuilder();
            session.query(USER).subscribe().asStream()
                    .filter(e -> e instanceof AgentEvent.TextDelta)
                    .map(e -> ((AgentEvent.TextDelta) e).text())
                    .forEach(responseText::append);

            System.out.printf("bareSession() response (%d chars): %.200s%n",
                    responseText.length(), responseText);
            assertThat(responseText.toString()).as("bare openSession().query() should produce text").isNotBlank();
        } finally {
            session.close();
        }
    }
}
