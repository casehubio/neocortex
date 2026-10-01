package io.casehub.neocortex.cognition.goal;

import io.casehub.neocortex.cognition.narrative.DerivedTheme;
import io.casehub.neocortex.cognition.narrative.NarrativeState;
import io.casehub.platform.agent.AgentEvent;
import io.casehub.platform.agent.AgentProvider;
import io.casehub.platform.agent.AgentSessionConfig;
import org.jspecify.annotations.Nullable;

import java.time.Duration;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;

public class LlmCrossAxisGoalEnricher implements CrossAxisGoalEnricher {

    private static final Logger LOG = Logger.getLogger(LlmCrossAxisGoalEnricher.class.getName());

    private static final String SYSTEM_PROMPT =
            "You are a goal formation analyst. Generate a concise, specific "
            + "compound goal description that combines the given drive axes. "
            + "Respond with a single sentence only.";
    private static final Duration TIMEOUT = Duration.ofSeconds(30);

    private final AgentProvider agentProvider;

    public LlmCrossAxisGoalEnricher(AgentProvider agentProvider) {
        this.agentProvider = agentProvider;
    }

    @Override
    public @Nullable DriveGoalProposal enrich(DriveGoalProposal heuristic,
                                               NarrativeState narrative,
                                               DerivedTheme sourceTheme) {
        String prompt = buildPrompt(heuristic, narrative, sourceTheme);
        var config = AgentSessionConfig.of(SYSTEM_PROMPT, prompt, TIMEOUT);

        String text = invokeText(config);
        if (text == null) return null;

        return new DriveGoalProposal(heuristic.axis(), heuristic.goalName(),
                text.trim(),
                "LLM-enriched cross-axis: " + sourceTheme.label(),
                heuristic.driveIntensity(), heuristic.suggestedPriority(),
                heuristic.proposalAttributes());
    }

    private @Nullable String invokeText(AgentSessionConfig config) {
        try {
            var events = agentProvider.invoke(config)
                    .collect().asList()
                    .await().atMost(Duration.ofMinutes(1));

            boolean hasError = events.stream()
                    .filter(AgentEvent.InvocationComplete.class::isInstance)
                    .map(AgentEvent.InvocationComplete.class::cast)
                    .anyMatch(AgentEvent.InvocationComplete::isError);
            if (hasError) return null;

            var text = events.stream()
                    .filter(AgentEvent.TextDelta.class::isInstance)
                    .map(AgentEvent.TextDelta.class::cast)
                    .map(AgentEvent.TextDelta::text)
                    .collect(Collectors.joining());

            return text.isBlank() ? null : text;
        } catch (Exception e) {
            LOG.log(Level.WARNING, "Agent invocation failed for cross-axis goal enrichment", e);
            return null;
        }
    }

    private String buildPrompt(DriveGoalProposal proposal, NarrativeState narrative,
                                DerivedTheme theme) {
        var sb = new StringBuilder();
        sb.append("Theme: ").append(theme.label())
          .append(" (salience: ").append(String.format("%.2f", theme.salience())).append(")\n");
        sb.append("Axes: ").append(theme.axisModulationWeights()).append("\n");
        sb.append("Current goal: ").append(proposal.goalDescription()).append("\n");
        var episodes = narrative.episodes();
        if (!episodes.isEmpty()) {
            sb.append("Recent episodes:\n");
            episodes.stream().limit(3).forEach(e ->
                    sb.append("- ").append(e.description()).append("\n"));
        }
        sb.append("\nGenerate a single sentence describing a compound goal that combines these axes.");
        return sb.toString();
    }
}
