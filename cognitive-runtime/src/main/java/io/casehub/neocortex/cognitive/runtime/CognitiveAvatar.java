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

import io.casehub.neocortex.cognitive.index.CognitiveDefaults;
import io.casehub.neocortex.cognition.prompt.CognitionPromptRenderer;
import io.casehub.neocortex.cognition.prompt.CognitivePromptSource;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;

public class CognitiveAvatar implements CognitivePromptSource {

    private final CognitiveDefaults profile;
    private final List<CognitiveCapability> capabilities;

    public CognitiveAvatar(CognitiveDefaults profile,
                           List<CognitiveCapability> capabilities) {
        this.profile = profile;
        this.capabilities = List.copyOf(capabilities);
    }

    @Override
    public void initialize(String agentId, String tenantId) {
        var context = new CognitiveAvatarContext(agentId, tenantId, profile);
        capabilities.forEach(c -> c.initialize(context));
    }

    @Override
    public List<CognitionPromptRenderer> promptSections() {
        return capabilities.stream()
            .filter(CognitiveCapability::isActive)
            .flatMap(c -> c.promptSections().stream())
            .sorted(Comparator.comparingInt(r ->
                r.blockTag() != null ? r.blockTag().sortOrdinal() : 999))
            .toList();
    }

    public <T extends CognitiveCapability> Optional<T> capability(Class<T> type) {
        return capabilities.stream()
            .filter(type::isInstance)
            .map(type::cast)
            .findFirst();
    }

    public CognitiveDefaults profile() { return profile; }
}
