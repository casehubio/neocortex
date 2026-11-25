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
import io.casehub.neocortex.cognitive.index.DescriptorView;
import io.casehub.neocortex.cognitive.index.DescriptorViewSource;
import io.quarkus.arc.DefaultBean;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.List;
import java.util.Optional;

@DefaultBean
@ApplicationScoped
public class ProfileDescriptorViewSource implements DescriptorViewSource {

    private final CognitiveDefaultsRegistry registry;

    public ProfileDescriptorViewSource(CognitiveDefaultsRegistry registry) {
        this.registry = registry;
    }

    @Override
    public Optional<DescriptorView> find(String tenantId, String agentId) {
        return registry.forAgent(agentId)
            .filter(p -> p.tenantId() == null || p.tenantId().equals(tenantId))
            .map(d -> d.descriptor());
    }

    @Override
    public List<String> agentIds(String tenantId) {
        return registry.allProfiles().stream()
            .filter(p -> p.tenantId() == null || p.tenantId().equals(tenantId))
            .map(p -> p.agentId())
            .toList();
    }
}
