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

import io.casehub.eidos.api.AgentDisposition;
import io.casehub.eidos.api.DispositionValue;
import io.casehub.eidos.api.LivedDispositionSource;
import io.casehub.neocortex.cognitive.index.LivedComposition;
import io.casehub.neocortex.cognitive.index.PersonalityStateSource;
import io.casehub.neocortex.cognitive.index.WeightedTerm;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;

import java.time.Instant;

@ApplicationScoped
public class EidosLivedDispositionSource implements LivedDispositionSource {

    private final Instance<PersonalityStateSource> stateSourceInstance;
    private final PersonalityStateSource directSource;

    @Inject
    public EidosLivedDispositionSource(Instance<PersonalityStateSource> stateSourceInstance) {
        this.stateSourceInstance = stateSourceInstance;
        this.directSource = null;
    }

    public EidosLivedDispositionSource(PersonalityStateSource source) {
        this.stateSourceInstance = null;
        this.directSource = source;
    }

    @Override
    public AgentDisposition resolveEffective(String agentId, String tenancyId, AgentDisposition authored) {
        var source = resolveSource();
        if (source == null) return authored;

        var snapshot = source.find(tenancyId, agentId, Instant.now());
        if (snapshot.isEmpty()) {return authored;}
        var state  = snapshot.get();
        var result = authored;
        if (state.livedProfile() != null) {
            result = EidosCognitiveProfileBridge.livedDisposition(result, state.livedProfile());
        }
        result = EidosCognitiveProfileBridge.composeFacets(result, state);
        return result;
    }

    private PersonalityStateSource resolveSource() {
        if (directSource != null) return directSource;
        if (stateSourceInstance != null && !stateSourceInstance.isUnsatisfied()) {
            return stateSourceInstance.get();
        }
        return null;
    }
}
