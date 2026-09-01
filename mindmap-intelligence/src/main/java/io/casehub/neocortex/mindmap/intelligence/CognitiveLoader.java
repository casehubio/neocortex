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
package io.casehub.neocortex.mindmap.intelligence;

import io.casehub.neocortex.cognitive.index.CognitiveDefaults;
import io.casehub.neocortex.cognitive.index.CognitiveDefaultsRegistry;
import io.casehub.neocortex.mindmap.MindMapStore;
import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;

import java.util.Collection;
import java.util.List;
import java.util.logging.Logger;

@ApplicationScoped
public class CognitiveLoader {

    private static final Logger LOG = Logger.getLogger(CognitiveLoader.class.getName());

    private final MindMapStore store;
    private final Collection<CognitiveDefaults> profiles;

    @Inject
    CognitiveLoader(Instance<MindMapStore> store,
                    Instance<CognitiveDefaultsRegistry> registry) {
        this.store = store.isResolvable() ? store.get() : null;
        this.profiles = registry.isResolvable()
            ? registry.get().allProfiles()
            : List.of();
    }

    CognitiveLoader(MindMapStore store, Collection<CognitiveDefaults> profiles) {
        this.store = store;
        this.profiles = profiles;
    }

    @PostConstruct
    void init() {
        if (store == null) {
            LOG.fine("No MindMapStore available — skipping vocabulary registration");
            return;
        }
        int registered = 0;
        for (CognitiveDefaults defaults : profiles) {
            if (defaults.vocabulary() != null) {
                store.registerVocabulary(defaults.vocabulary());
                registered++;
                LOG.fine("Registered vocabulary for agent '" + defaults.agentId() + "'");
            }
        }
        if (registered > 0) {
            LOG.info("Registered vocabulary from " + registered + " cognitive profile(s)");
        }
    }
}
