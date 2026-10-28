package io.casehub.neocortex.cognition.core;

import io.casehub.eidos.api.AgentDescriptor;
import io.casehub.neocortex.mindmap.AttentionBriefing;
import org.jspecify.annotations.Nullable;

public record CognitionTickContext(
        String agentId,
        String tenantId,
        @Nullable AgentDescriptor descriptor,
        SubjectResolver resolver,
        @Nullable String observation,
        @Nullable AttentionBriefing briefing,
        @Nullable String targetAgentId
) {
    public CognitionTickContext(String agentId, String tenantId,
                                @Nullable AgentDescriptor descriptor,
                                SubjectResolver resolver) {
        this(agentId, tenantId, descriptor, resolver, null, null, null);
    }

    public CognitionTickContext(String agentId, String tenantId,
                                @Nullable AgentDescriptor descriptor,
                                SubjectResolver resolver,
                                @Nullable String observation) {
        this(agentId, tenantId, descriptor, resolver, observation, null, null);
    }

    public CognitionTickContext(String agentId, String tenantId,
                                @Nullable AgentDescriptor descriptor,
                                SubjectResolver resolver,
                                @Nullable String observation,
                                @Nullable AttentionBriefing briefing) {
        this(agentId, tenantId, descriptor, resolver, observation, briefing, null);
    }
}
