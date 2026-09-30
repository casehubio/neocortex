package io.casehub.neocortex.cognition.prompt;

import io.casehub.eidos.api.AgentConstraint;
import org.jspecify.annotations.Nullable;

import java.util.List;

public class ConstraintPromptSection implements CognitionPromptRenderer {

    private final List<AgentConstraint> constraints;

    public ConstraintPromptSection(List<AgentConstraint> constraints) {
        this.constraints = List.copyOf(constraints);
    }

    @Override
    public @Nullable String render(CognitionRenderContext context) {
        if (constraints.isEmpty()) return null;
        var sb = new StringBuilder("Your constraints:");
        for (var c : constraints) {
            sb.append("\n- [").append(c.severity().name()).append("] ")
              .append(c.description());
        }
        return sb.toString();
    }
}
