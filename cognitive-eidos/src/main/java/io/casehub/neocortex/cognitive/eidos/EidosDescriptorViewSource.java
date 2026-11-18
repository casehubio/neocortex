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
package io.casehub.neocortex.cognitive.eidos;

import io.casehub.eidos.api.AgentQuery;
import io.casehub.eidos.api.AgentRegistry;
import io.casehub.neocortex.cognitive.index.DescriptorView;
import io.casehub.neocortex.cognitive.index.DescriptorViewSource;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;

import java.util.List;
import java.util.Optional;

@ApplicationScoped
public class EidosDescriptorViewSource implements DescriptorViewSource {

    private final Instance<AgentRegistry> registryInstance;
    private final AgentRegistry directRegistry;

    @Inject
    public EidosDescriptorViewSource(Instance<AgentRegistry> registryInstance) {
        this.registryInstance = registryInstance;
        this.directRegistry = null;
    }

    public EidosDescriptorViewSource(AgentRegistry registry) {
        this.registryInstance = null;
        this.directRegistry = registry;
    }

    @Override
    public Optional<DescriptorView> find(String tenantId, String agentId) {
        var registry = resolveRegistry();
        if (registry == null) return Optional.empty();
        return registry.findById(agentId, tenantId)
                .map(EidosCognitiveProfileBridge::toDescriptorView);
    }

    @Override
    public List<String> agentIds(String tenantId) {
        var registry = resolveRegistry();
        if (registry == null) return List.of();
        return registry.find(AgentQuery.all(tenantId)).stream()
                .map(m -> m.descriptor().agentId())
                .toList();
    }

    private AgentRegistry resolveRegistry() {
        if (directRegistry != null) return directRegistry;
        if (registryInstance != null && !registryInstance.isUnsatisfied()) {
            return registryInstance.get();
        }
        return null;
    }
}
