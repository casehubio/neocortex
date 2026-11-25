/*
 * Copyright 2026-Present The Case Hub Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.casehub.neocortex.cognitive.runtime;

import io.casehub.neocortex.cognitive.index.CognitiveDefaultsRegistry;
import io.casehub.neocortex.cognitive.index.CognitiveProfile;
import io.casehub.neocortex.cognitive.index.CognitiveProfilesReloaded;
import io.casehub.neocortex.cognition.prompt.CognitiveLifecycle;
import io.casehub.neocortex.memory.CaseMemoryStore;
import io.casehub.neocortex.mindmap.MindMapStore;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;

import java.util.ArrayList;
import java.util.concurrent.ConcurrentHashMap;

@ApplicationScoped
public class CognitiveAvatarProducer {

    private final CognitiveDefaultsRegistry registry;
    private final Instance<MindMapStore> mindMapStore;
    private final Instance<CaseMemoryStore> memoryStore;
    private final Instance<CognitiveProfile> cognitiveProfile;
    private final Instance<CognitiveLifecycle> cognitiveLifecycle;

    private final ConcurrentHashMap<String, CognitiveAvatar> cache = new ConcurrentHashMap<>();

    @Inject
    public CognitiveAvatarProducer(CognitiveDefaultsRegistry registry,
                                   Instance<MindMapStore> mindMapStore,
                                   Instance<CaseMemoryStore> memoryStore,
                                   Instance<CognitiveProfile> cognitiveProfile,
                                   Instance<CognitiveLifecycle> cognitiveLifecycle) {
        this.registry = registry;
        this.mindMapStore = mindMapStore;
        this.memoryStore = memoryStore;
        this.cognitiveProfile = cognitiveProfile;
        this.cognitiveLifecycle = cognitiveLifecycle;
    }

    public CognitiveAvatar create(String agentId, String tenantId) {
        var key = tenantId + ":" + agentId;
        return cache.computeIfAbsent(key, k -> buildAndInitAvatar(agentId, tenantId));
    }

    void onProfileReload(@Observes CognitiveProfilesReloaded event) {
        cache.clear();
    }

    private CognitiveAvatar buildAndInitAvatar(String agentId, String tenantId) {
        var profile = registry.forAgentOrDefaults(agentId);
        var capabilities = new ArrayList<CognitiveCapability>();

        if (profile.briefing() != null) {
            capabilities.add(new BriefingCapability(profile.briefing()));
        }
        if (mindMapStore.isResolvable()) {
            capabilities.add(new GoalCapability(mindMapStore.get()));
            capabilities.add(new TemporalCapability(mindMapStore.get()));
        }
        if (memoryStore.isResolvable()) {
            capabilities.add(new MemoryCapability(memoryStore.get()));
        }
        if (cognitiveProfile.isResolvable()) {
            capabilities.add(new EntityKnowledgeCapability(cognitiveProfile.get()));
        }

        capabilities.add(new DispositionCapability(profile));

        boolean cognitionActive = cognitiveLifecycle.isResolvable();
        if (mindMapStore.isResolvable()) {
            capabilities.add(new NeedsCapability(mindMapStore.get(), cognitionActive));
            capabilities.add(new ConsolidationCapability(mindMapStore.get(), cognitionActive));
        }
        if (memoryStore.isResolvable()) {
            capabilities.add(new AppraisalCapability(memoryStore.get(), cognitionActive));
            capabilities.add(new InnerThoughtsCapability(memoryStore.get(), cognitionActive));
        }

        var avatar = new CognitiveAvatar(profile, capabilities);
        avatar.initialize(agentId, tenantId);
        return avatar;
    }
}
