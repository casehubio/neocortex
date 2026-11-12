package io.casehub.neocortex.caps.llm.eval;

import io.casehub.platform.agent.AgentEvent;
import io.casehub.platform.agent.AgentProvider;
import io.casehub.platform.agent.AgentSessionConfig;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.time.Duration;
import java.util.stream.Collectors;

interface CoherenceJudge {

    CoherenceScore judge(StageTransition transition);

    record StageTransition(String name, String fromStageData, String toStageData, String coherenceExpectation) {}

    record CoherenceScore(double score, String reasoning) {}

    class TwoTierCoherenceJudge implements CoherenceJudge {

        private final SingleTierCoherenceJudge haiku;
        private final SingleTierCoherenceJudge opus;

        TwoTierCoherenceJudge(AgentProvider agentProvider) {
            haiku = new SingleTierCoherenceJudge(agentProvider, "haiku");
            opus = new SingleTierCoherenceJudge(agentProvider, "opus");
        }

        @Override
        public CoherenceScore judge(StageTransition transition) {
            var score = haiku.judge(transition);
            return score.score() >= 1.5 && score.score() <= 3.5 ? opus.judge(transition) : score;
        }
    }

    class SingleTierCoherenceJudge implements CoherenceJudge {

        private static final String SYSTEM_PROMPT = """
                You are a cognitive pipeline coherence evaluator. Given two adjacent \
                pipeline stages and an expectation of what coherent transition looks \
                like, score the coherence 0.0-5.0.

                0.0 = completely incoherent (output contradicts input)
                2.5 = partially coherent (plausible but with unexplained gaps)
                5.0 = fully coherent (output is the expected consequence of input)

                Respond with JSON only: {"score": 4.2, "reasoning": "..."}""";

        private static final ObjectMapper MAPPER = new ObjectMapper();
        private static final int MAX_RETRIES = 3;

        private final AgentProvider agentProvider;
        private final String model;

        SingleTierCoherenceJudge(AgentProvider agentProvider) {
            this(agentProvider, null);
        }

        SingleTierCoherenceJudge(AgentProvider agentProvider, String model) {
            this.agentProvider = agentProvider;
            this.model = model;
        }

        @Override
        public CoherenceScore judge(StageTransition transition) {
            var userPrompt = """
                    ## Transition: %s

                    ### From stage
                    %s

                    ### To stage
                    %s

                    ### Expected coherence
                    %s""".formatted(
                    transition.name(),
                    transition.fromStageData(),
                    transition.toStageData(),
                    transition.coherenceExpectation());

            for (int attempt = 1; attempt <= MAX_RETRIES; attempt++) {
                try {
                    String response = invoke(SYSTEM_PROMPT, userPrompt);
                    String json = extractJson(response);
                    JsonNode node = MAPPER.readTree(json);
                    return new CoherenceScore(node.get("score").asDouble(), node.get("reasoning").asText());
                } catch (Exception e) {
                    System.err.printf("  [attempt %d/%d] judge failed for %s: %s%n",
                            attempt, MAX_RETRIES, transition.name(), e.getMessage());
                    if (attempt == MAX_RETRIES) {
                        return new CoherenceScore(0.0, "Judge failed after " + MAX_RETRIES + " attempts: " + e.getMessage());
                    }
                    try { Thread.sleep(5000L * attempt); } catch (InterruptedException ie) { Thread.currentThread().interrupt(); }
                }
            }
            return new CoherenceScore(0.0, "unreachable");
        }

        private String invoke(String systemPrompt, String userPrompt) {
            var config = AgentSessionConfig.of(systemPrompt, userPrompt);
            if (model != null) config = config.withModel(model);
            return agentProvider.invoke(config)
                    .filter(e -> e instanceof AgentEvent.TextDelta)
                    .map(e -> ((AgentEvent.TextDelta) e).text())
                    .collect().with(Collectors.joining())
                    .await().atMost(Duration.ofSeconds(120));
        }

        private static String extractJson(String response) {
            int start = response.indexOf('{');
            int end = response.lastIndexOf('}');
            if (start < 0 || end < 0 || end <= start) {
                throw new IllegalArgumentException("No JSON object found in response: " + response);
            }
            return response.substring(start, end + 1);
        }
    }

    class TrajectoryCoherenceJudge {

        private static final String SYSTEM_PROMPT = """
                You are a cognitive pipeline trajectory evaluator. Given a sequence \
                of pipeline traces from successive ticks with their observations, \
                assess whether the trajectory is internally coherent.

                Evaluate:
                1. Mood trajectory: does emotional state follow logically from the \
                   observation sequence? Are shifts proportional to stimulus intensity?
                2. Drive adaptation: do activated drives change consistently with \
                   experience? No random walks — changes should have causes.
                3. Appraisal consistency: do emotional labels evolve coherently? \
                   A shift from anxiety to relief needs a relieving observation.
                4. No contradictions: no abrupt reversals without cause in the \
                   observation text.

                Score 0.0-5.0. Respond with JSON only: {"score": 4.2, "reasoning": "..."}""";

        private static final ObjectMapper MAPPER = new ObjectMapper();
        private static final int MAX_RETRIES = 3;

        private final AgentProvider agentProvider;

        TrajectoryCoherenceJudge(AgentProvider agentProvider) {
            this.agentProvider = agentProvider;
        }

        CoherenceScore judge(String trajectoryDocument) {
            for (int attempt = 1; attempt <= MAX_RETRIES; attempt++) {
                try {
                    String response = agentProvider.invoke(AgentSessionConfig.of(SYSTEM_PROMPT, trajectoryDocument))
                            .filter(e -> e instanceof AgentEvent.TextDelta)
                            .map(e -> ((AgentEvent.TextDelta) e).text())
                            .collect().with(Collectors.joining())
                            .await().atMost(Duration.ofSeconds(120));
                    int start = response.indexOf('{');
                    int end = response.lastIndexOf('}');
                    if (start < 0 || end <= start) throw new IllegalArgumentException("No JSON in response");
                    JsonNode node = MAPPER.readTree(response.substring(start, end + 1));
                    return new CoherenceScore(node.get("score").asDouble(), node.get("reasoning").asText());
                } catch (Exception e) {
                    System.err.printf("  [attempt %d/%d] trajectory judge failed: %s%n",
                            attempt, MAX_RETRIES, e.getMessage());
                    if (attempt == MAX_RETRIES) {
                        return new CoherenceScore(0.0, "Trajectory judge failed: " + e.getMessage());
                    }
                    try { Thread.sleep(5000L * attempt); } catch (InterruptedException ie) { Thread.currentThread().interrupt(); }
                }
            }
            return new CoherenceScore(0.0, "unreachable");
        }
    }
}
