package io.casehub.neocortex.caps.llm.eval;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.casehub.platform.agent.AgentEvent;
import io.casehub.platform.agent.AgentProvider;
import io.casehub.platform.agent.AgentSessionConfig;

import java.time.Duration;
import java.util.stream.Collectors;

final class EvalLlmJudge {

    private static final int MAX_RETRIES = 3;
    private static final long RETRY_BACKOFF_MS = 5000;
    private static final ObjectMapper JSON = new ObjectMapper();

    private final AgentProvider agentProvider;

    EvalLlmJudge(AgentProvider agentProvider) {
        this.agentProvider = agentProvider;
    }

    String askCharacter(String observation) {
        return agentProvider.invoke(AgentSessionConfig.of(
                        EvalAgentResponse.CHARACTER_SYSTEM_PROMPT, observation))
                .filter(e -> e instanceof AgentEvent.TextDelta)
                .map(e -> ((AgentEvent.TextDelta) e).text())
                .collect().with(Collectors.joining())
                .await().atMost(Duration.ofSeconds(120));
    }

    int judge(String responseText, String criteria, String testName) {
        var judgePrompt = String.format("""
                You are evaluating whether an AI character's response reflects a specific cognitive state.

                CHARACTER RESPONSE:
                %s

                EVALUATION CRITERIA:
                %s

                Score the response 0-5:
                0 = No evidence of the cognitive element influencing behavior
                1 = Weak or ambiguous evidence
                2 = Some evidence but inconsistent
                3 = Clear evidence in at least one response field
                4 = Strong evidence across multiple fields
                5 = The cognitive element clearly drives the entire response

                Respond with JSON only: {"score": N, "reasoning": "one sentence"}""",
                responseText, criteria);

        for (int attempt = 1; attempt <= MAX_RETRIES; attempt++) {
            try {
                var judgeResponse = agentProvider.invoke(
                                AgentSessionConfig.of("You are a precise evaluation judge. Respond only with JSON.",
                                        judgePrompt))
                        .filter(e -> e instanceof AgentEvent.TextDelta)
                        .map(e -> ((AgentEvent.TextDelta) e).text())
                        .collect().with(Collectors.joining())
                        .await().atMost(Duration.ofSeconds(60));

                var json = extractJson(judgeResponse);
                var node = JSON.readTree(json);
                int score = node.get("score").asInt();
                String reasoning = node.has("reasoning") ? node.get("reasoning").asText() : "";
                System.out.printf("[judge:%s] score=%d reasoning=%s%n", testName, score, reasoning);
                return score;
            } catch (Exception e) {
                System.err.printf("[judge:%s] attempt %d/%d failed: %s%n",
                        testName, attempt, MAX_RETRIES, e.getMessage());
                if (attempt < MAX_RETRIES) {
                    try { Thread.sleep(RETRY_BACKOFF_MS * attempt); }
                    catch (InterruptedException ie) { Thread.currentThread().interrupt(); return 0; }
                }
            }
        }
        return 0;
    }

    private static String extractJson(String text) {
        text = text.strip();
        if (text.startsWith("{")) return text.substring(0, text.lastIndexOf('}') + 1);
        int start = text.indexOf('{');
        int end = text.lastIndexOf('}');
        if (start >= 0 && end > start) return text.substring(start, end + 1);
        return text;
    }
}
