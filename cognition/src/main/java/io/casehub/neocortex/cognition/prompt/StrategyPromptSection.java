package io.casehub.neocortex.cognition.prompt;

import io.casehub.neocortex.cognition.strategy.StrategyLearningOrchestrator;
import org.jspecify.annotations.Nullable;

public class StrategyPromptSection implements CognitionPromptRenderer {

    private final StrategyLearningOrchestrator strategy;

    public StrategyPromptSection(StrategyLearningOrchestrator strategy) {
        this.strategy = strategy;
    }

    @Override
    public @Nullable String render(CognitionRenderContext context) {
        return strategy.currentStrategy(context.agentId(), context.tenantId())
                .map(profile -> {
                    String section = profile.toPromptSection();
                    return section.isEmpty() ? null : section.strip();
                })
                .orElse(null);
    }
}
