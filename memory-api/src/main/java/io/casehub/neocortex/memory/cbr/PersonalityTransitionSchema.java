package io.casehub.neocortex.memory.cbr;

import java.util.List;

/**
 * Schema convention for personality transition CBR cases.
 *
 * <p>Records personality evolution events — when an agent's cognitive function
 * profile shifts (e.g. dominant Ti→Fe after JPAF reflection). The engine stores
 * transitions; CBR retrieval finds similar past transitions to inform routing
 * decisions: "last time this agent shifted dominant function, what happened?"
 *
 * <p>caseType: {@value CASE_TYPE}
 *
 * <p>Features:
 * <ul>
 *   <li>{@code agent_id} — Categorical: the agent whose personality evolved</li>
 *   <li>{@code old_dominant} — Categorical: previous dominant cognitive function (Ti, Te, Fi, Fe, Ni, Ne, Si, Se)</li>
 *   <li>{@code new_dominant} — Categorical: new dominant cognitive function</li>
 *   <li>{@code old_auxiliary} — Categorical: previous auxiliary function</li>
 *   <li>{@code new_auxiliary} — Categorical: new auxiliary function</li>
 *   <li>{@code trigger_type} — Categorical: what caused the shift (reflection, compensation, reinforcement, manual)</li>
 *   <li>{@code outcome} — Categorical: observed effect on agent performance (improved, degraded, neutral, unknown)</li>
 * </ul>
 *
 * <p>problem: human-readable description of the transition context
 * <p>solution: the routing/adaptation action taken in response
 *
 * <p>Consumers: engine personality-adaptive routing (engine#790)
 * <p>Producers: engine JPAF reflection mechanism (engine#790 sub-issues)
 * <p>Data model: eidos weighted disposition profiles (eidos#111)
 */
public final class PersonalityTransitionSchema {

    public static final String CASE_TYPE = "personality-transition";

    public static CbrFeatureSchema schema() {
        return new CbrFeatureSchema(CASE_TYPE, List.of(
                FeatureField.categorical("agent_id"),
                FeatureField.categorical("old_dominant"),
                FeatureField.categorical("new_dominant"),
                FeatureField.categorical("old_auxiliary"),
                FeatureField.categorical("new_auxiliary"),
                FeatureField.categorical("trigger_type"),
                FeatureField.categorical("outcome")
        ), null);
    }

    private PersonalityTransitionSchema() {}
}
