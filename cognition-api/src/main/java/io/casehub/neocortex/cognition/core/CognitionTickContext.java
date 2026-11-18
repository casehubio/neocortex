package io.casehub.neocortex.cognition.core;

import io.casehub.eidos.api.AgentDescriptor;
import io.casehub.neocortex.cognition.journal.EventRef;
import io.casehub.neocortex.cognition.regulation.RegulationContext;
import io.casehub.neocortex.mindmap.AttentionBriefing;
import org.jspecify.annotations.Nullable;

public record CognitionTickContext(
        String agentId,
        String tenantId,
        @Nullable AgentDescriptor descriptor,
        SubjectResolver resolver,
        @Nullable String observation,
        @Nullable AttentionBriefing briefing,
        @Nullable String targetAgentId,
        @Nullable RegulationContext regulationContext,
        @Nullable EventRef tickEventRef
) {
    public CognitionTickContext(String agentId, String tenantId,
                                @Nullable AgentDescriptor descriptor,
                                SubjectResolver resolver) {
        this(agentId, tenantId, descriptor, resolver, null, null, null, null, null);
    }

    public CognitionTickContext(String agentId, String tenantId,
                                @Nullable AgentDescriptor descriptor,
                                SubjectResolver resolver,
                                @Nullable String observation) {
        this(agentId, tenantId, descriptor, resolver, observation, null, null, null, null);
    }

    public CognitionTickContext(String agentId, String tenantId,
                                @Nullable AgentDescriptor descriptor,
                                SubjectResolver resolver,
                                @Nullable String observation,
                                @Nullable AttentionBriefing briefing) {
        this(agentId, tenantId, descriptor, resolver, observation, briefing, null, null, null);
    }

    public CognitionTickContext(String agentId, String tenantId,
                                @Nullable AgentDescriptor descriptor,
                                SubjectResolver resolver,
                                @Nullable String observation,
                                @Nullable AttentionBriefing briefing,
                                @Nullable String targetAgentId) {
        this(agentId, tenantId, descriptor, resolver, observation, briefing, targetAgentId, null, null);
    }

    public CognitionTickContext(String agentId, String tenantId,
                                @Nullable AgentDescriptor descriptor,
                                SubjectResolver resolver,
                                @Nullable String observation,
                                @Nullable AttentionBriefing briefing,
                                @Nullable String targetAgentId,
                                @Nullable RegulationContext regulationContext) {
        this(agentId, tenantId, descriptor, resolver, observation, briefing, targetAgentId, regulationContext, null);
    }
}
